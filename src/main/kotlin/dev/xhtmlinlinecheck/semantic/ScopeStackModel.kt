package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.BindingId
import dev.xhtmlinlinecheck.domain.BindingKind
import dev.xhtmlinlinecheck.domain.BindingOrigin
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.ScopeId
import dev.xhtmlinlinecheck.domain.SourceGraphParameter
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.rules.BindingCreationRule
import dev.xhtmlinlinecheck.syntax.LogicalAttribute
import dev.xhtmlinlinecheck.syntax.LogicalElementNode
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.LogicalNode
import dev.xhtmlinlinecheck.syntax.LogicalNodePath
import dev.xhtmlinlinecheck.syntax.LogicalTextNode
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree

enum class ScopeLookupPosition {
    NODE,
    DESCENDANT,
}

data class ScopeBinding(
    val id: BindingId,
    val scopeId: ScopeId,
    val parentScopeId: ScopeId?,
    val writtenName: String,
    val kind: BindingKind,
    val location: SourceLocation,
    val provenance: Provenance,
    val origin: BindingOrigin,
    val valueExpression: String? = null,
)

data class ScopeFrame(
    val id: ScopeId,
    val parentScopeId: ScopeId?,
    val bindingIds: List<BindingId> = emptyList(),
)

data class ScopeSnapshot(
    val nodeScopeId: ScopeId,
    val descendantScopeId: ScopeId,
)

data class ScopeStackModel(
    val rootScopeId: ScopeId,
    val scopes: Map<ScopeId, ScopeFrame>,
    val bindings: List<ScopeBinding>,
    val nodeScopes: Map<LogicalNodePath, ScopeSnapshot>,
) {
    private val bindingsById: Map<BindingId, ScopeBinding> = bindings.associateBy { it.id }

    fun snapshotAt(path: LogicalNodePath): ScopeSnapshot =
        requireNotNull(nodeScopes[path]) { "unknown syntax node path: ${path.segments}" }

    fun visibleBindingsAt(
        path: LogicalNodePath,
        position: ScopeLookupPosition = ScopeLookupPosition.NODE,
    ): List<ScopeBinding> {
        val scopeId = scopeIdAt(path, position)
        return bindingsVisibleFrom(scopeId)
    }

    fun resolve(
        name: String,
        path: LogicalNodePath,
        position: ScopeLookupPosition = ScopeLookupPosition.NODE,
    ): ScopeBinding? =
        visibleBindingsAt(path, position).firstOrNull { it.writtenName == name }

    private fun scopeIdAt(path: LogicalNodePath, position: ScopeLookupPosition): ScopeId {
        val snapshot = snapshotAt(path)
        return when (position) {
            ScopeLookupPosition.NODE -> snapshot.nodeScopeId
            ScopeLookupPosition.DESCENDANT -> snapshot.descendantScopeId
        }
    }

    private fun bindingsVisibleFrom(scopeId: ScopeId): List<ScopeBinding> {
        val visible = mutableListOf<ScopeBinding>()
        var currentScopeId: ScopeId? = scopeId
        while (currentScopeId != null) {
            val scope = requireNotNull(scopes[currentScopeId]) { "unknown scope id ${currentScopeId.value}" }
            scope.bindingIds
                .asReversed()
                .map(bindingsById::getValue)
                .forEach(visible::add)
            currentScopeId = scope.parentScopeId
        }
        return visible
    }

    companion object {
        fun fromSyntaxTree(syntaxTree: XhtmlSyntaxTree): ScopeStackModel = ScopeStackBuilder().build(syntaxTree)
    }
}

private class ScopeStackBuilder {
    private var nextScopeId: Int = 1
    private var nextBindingId: Int = 1
    private val scopes = linkedMapOf<ScopeId, ScopeFrame>()
    private val bindings = mutableListOf<ScopeBinding>()
    private val nodeScopes = linkedMapOf<LogicalNodePath, ScopeSnapshot>()

    fun build(syntaxTree: XhtmlSyntaxTree): ScopeStackModel {
        val rootScopeId = createScope(parentScopeId = null, bindings = emptyList())
        syntaxTree.root?.let { visit(it, LogicalNodePath.root(), rootScopeId) }
        return ScopeStackModel(
            rootScopeId = rootScopeId,
            scopes = scopes.toMap(),
            bindings = bindings.toList(),
            nodeScopes = nodeScopes.toMap(),
        )
    }

    private fun visit(node: LogicalNode, path: LogicalNodePath, currentScopeId: ScopeId): ScopeId {
        val transition = enterNode(node, path, currentScopeId)

        var childScopeId = transition.descendantScopeId
        val children =
            when (node) {
                is LogicalElementNode -> node.children
                is LogicalIncludeNode -> node.children
                is LogicalTextNode -> emptyList()
            }
        children.forEachIndexed { index, child ->
            childScopeId = visit(child, path.child(index), childScopeId)
        }

        return leaveNode(node, transition, childScopeId)
    }

    private fun enterNode(
        node: LogicalNode,
        path: LogicalNodePath,
        currentScopeId: ScopeId,
    ): ScopeTransition {
        val introducedBindings =
            when (node) {
                is LogicalElementNode -> elementBindings(node, currentScopeId)
                is LogicalIncludeNode -> includeBindings(node, currentScopeId)
                is LogicalTextNode -> emptyList()
            }
        val descendantScopeId = createChildScopeIfNeeded(currentScopeId, introducedBindings)
        nodeScopes[path] = ScopeSnapshot(nodeScopeId = currentScopeId, descendantScopeId = descendantScopeId)
        return ScopeTransition(
            nodeScopeId = currentScopeId,
            descendantScopeId = descendantScopeId,
            introducedBindings = introducedBindings,
        )
    }

    private fun leaveNode(
        node: LogicalNode,
        transition: ScopeTransition,
        childScopeId: ScopeId,
    ): ScopeId =
        when (node) {
            is LogicalElementNode -> node.exitScopeId(transition, childScopeId)
            is LogicalIncludeNode -> transition.nodeScopeId
            is LogicalTextNode -> transition.nodeScopeId
        }

    private fun elementBindings(node: LogicalElementNode, currentScopeId: ScopeId): List<ScopeBinding> =
        node.tagRule.bindingRules.mapNotNull { rule ->
            bindingFromRule(node, rule, currentScopeId)
        }

    private fun includeBindings(node: LogicalIncludeNode, currentScopeId: ScopeId): List<ScopeBinding> =
        node.parameters.map { parameter ->
            bindingFromParameter(parameter)
        }

    private fun createChildScopeIfNeeded(
        parentScopeId: ScopeId,
        scopeBindings: List<ScopeBinding>,
    ): ScopeId =
        if (scopeBindings.isEmpty()) {
            parentScopeId
        } else {
            createScope(parentScopeId, scopeBindings)
        }

    private fun createScope(parentScopeId: ScopeId?, bindings: List<ScopeBinding>): ScopeId {
        val scopeId = ScopeId(nextScopeId++)
        val scopedBindings =
            bindings.map { binding ->
                binding.copy(
                    scopeId = scopeId,
                    parentScopeId = parentScopeId,
                )
            }
        scopes[scopeId] = ScopeFrame(id = scopeId, parentScopeId = parentScopeId, bindingIds = scopedBindings.map { it.id })
        this.bindings += scopedBindings
        return scopeId
    }

    private fun bindingFromRule(
        node: LogicalElementNode,
        rule: BindingCreationRule,
        currentScopeId: ScopeId,
    ): ScopeBinding? {
        val nameAttribute = node.attributeNamed(rule.nameAttribute) ?: return null
        val writtenName = nameAttribute.value.trim()
        if (writtenName.isEmpty()) {
            return null
        }
        val valueExpression = rule.valueAttribute?.let(node::attributeNamed)?.value
        return ScopeBinding(
            id = BindingId(nextBindingId++),
            scopeId = currentScopeId,
            parentScopeId = null,
            writtenName = writtenName,
            kind = rule.kind,
            location = nameAttribute.location,
            provenance = node.provenance.atBindingLocation(nameAttribute.location),
            origin = BindingOrigin(descriptor = describeRuleOrigin(node, rule.kind, writtenName)),
            valueExpression = valueExpression,
        )
    }

    private fun bindingFromParameter(parameter: SourceGraphParameter): ScopeBinding =
        ScopeBinding(
            id = BindingId(nextBindingId++),
            scopeId = ScopeId(0),
            parentScopeId = null,
            writtenName = parameter.name,
            kind = BindingKind.UI_PARAM,
            location = parameter.provenance.physicalLocation,
            provenance = parameter.provenance,
            origin = BindingOrigin(descriptor = "ui:param name=${parameter.name}"),
            valueExpression = parameter.valueExpression,
        )

    private fun describeRuleOrigin(
        node: LogicalElementNode,
        kind: BindingKind,
        writtenName: String,
    ): String {
        val tagName = node.name.prefix?.let { "$it:${node.name.localName}" } ?: node.name.localName
        return when (kind) {
            BindingKind.UI_PARAM -> "ui:param name=$writtenName"
            BindingKind.ITERATION_VAR -> "$tagName var=$writtenName"
            BindingKind.VAR_STATUS -> "$tagName varStatus=$writtenName"
            BindingKind.C_SET -> "$tagName var=$writtenName"
            BindingKind.C_FOR_EACH -> "$tagName var=$writtenName"
            BindingKind.IMPLICIT_GLOBAL -> "implicit global $writtenName"
        }
    }

    private fun LogicalElementNode.attributeNamed(localName: String): LogicalAttribute? =
        attributes.firstOrNull { it.name.localName == localName }
}

private data class ScopeTransition(
    val nodeScopeId: ScopeId,
    val descendantScopeId: ScopeId,
    val introducedBindings: List<ScopeBinding>,
) {
    val persistsIntoLaterSiblings: Boolean
        get() = introducedBindings.any(ScopeBinding::persistsIntoLaterSiblings)
}

private fun LogicalElementNode.exitScopeId(
    transition: ScopeTransition,
    childScopeId: ScopeId,
): ScopeId =
    if (transition.persistsIntoLaterSiblings) {
        childScopeId
    } else {
        transition.nodeScopeId
    }

private fun ScopeBinding.persistsIntoLaterSiblings(): Boolean = kind == BindingKind.C_SET

private fun Provenance.atBindingLocation(location: SourceLocation): Provenance =
    if (includeStack.isEmpty()) {
        copy(
            physicalLocation = location,
            logicalLocation = location,
        )
    } else {
        copy(physicalLocation = location)
    }
