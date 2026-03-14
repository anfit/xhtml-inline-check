package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.BindingKind
import dev.xhtmlinlinecheck.rules.SyntaxRole
import dev.xhtmlinlinecheck.syntax.LogicalAttribute
import dev.xhtmlinlinecheck.syntax.LogicalElementNode
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.LogicalNode
import dev.xhtmlinlinecheck.syntax.LogicalNodePath
import dev.xhtmlinlinecheck.syntax.LogicalTextNode
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree

object SemanticNodeExtractor {
    fun extract(
        syntaxTree: XhtmlSyntaxTree,
        scopeModel: ScopeStackModel,
        elOccurrences: List<SemanticElOccurrence>,
        normalizedElOccurrences: List<NormalizedSemanticElOccurrence>,
    ): List<SemanticNode> {
        val normalizedByOccurrence = normalizedElOccurrences.associateBy(NormalizedSemanticElOccurrence::occurrence)
        val elFactsByPath =
            elOccurrences
                .map { occurrence ->
                    occurrence.nodePath to occurrence.toSemanticFact(normalizedByOccurrence[occurrence])
                }.groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )
        val nodes = mutableListOf<SemanticNode>()

        fun visit(
            node: LogicalNode,
            path: LogicalNodePath,
            structuralContext: SemanticStructuralContext,
        ) {
            val nodeId = semanticNodeIdFor(path)
            val currentElFacts = elFactsByPath[path].orEmpty()
            val semanticNode =
                when (node) {
                    is LogicalElementNode ->
                        node.toSemanticNode(
                            nodeId = nodeId,
                            nodePath = path,
                            elFacts = currentElFacts,
                            structuralContext = structuralContext,
                        )

                    is LogicalIncludeNode ->
                        node.toSemanticNode(
                            nodeId = nodeId,
                            nodePath = path,
                            elFacts = currentElFacts,
                            structuralContext = structuralContext,
                        )

                    is LogicalTextNode ->
                        node.toSemanticNode(
                            nodeId = nodeId,
                            nodePath = path,
                            elFacts = currentElFacts,
                            structuralContext = structuralContext,
                        )
                }
            nodes += semanticNode

            val nextStructuralContext = structuralContext.extend(semanticNode, scopeModel, path)

            when (node) {
                is LogicalElementNode ->
                    node.children.forEachIndexed { index, child ->
                        visit(
                            node = child,
                            path = path.child(index),
                            structuralContext = nextStructuralContext,
                        )
                    }

                is LogicalIncludeNode ->
                    node.children.forEachIndexed { index, child ->
                        visit(
                            node = child,
                            path = path.child(index),
                            structuralContext = nextStructuralContext,
                        )
                    }

                is LogicalTextNode -> Unit
            }
        }

        syntaxTree.root?.let { visit(it, LogicalNodePath.root(), SemanticStructuralContext()) }
        return nodes
    }
}

private fun LogicalElementNode.toSemanticNode(
    nodeId: SemanticNodeId,
    nodePath: LogicalNodePath,
    elFacts: List<SemanticNodeElFact>,
    structuralContext: SemanticStructuralContext,
): SemanticNode =
    SemanticNode(
        nodeId = nodeId,
        nodePath = nodePath,
        kind = SemanticNodeKind.ELEMENT,
        nodeName = renderedTagName(),
        logicalName = name,
        syntaxRole = tagRule.syntaxRole,
        location = location,
        provenance = provenance,
        isTransparentStructureWrapper = tagRule.isTransparentStructureWrapper,
        isForm = tagRule.isForm,
        isNamingContainer = tagRule.isNamingContainer,
        explicitIdAttribute = attributeNamed("id")?.toSemanticAttribute(),
        renderedAttribute = elFacts.firstOrNull { it.attributeName == "rendered" },
        componentTargetAttributes =
            attributes
                .filter { it.name.localName in tagRule.targetAttributeNames }
                .mapNotNull(LogicalAttribute::toComponentTargetAttribute),
        elFacts = elFacts,
        structuralContext = structuralContext,
    )

private fun LogicalIncludeNode.toSemanticNode(
    nodeId: SemanticNodeId,
    nodePath: LogicalNodePath,
    elFacts: List<SemanticNodeElFact>,
    structuralContext: SemanticStructuralContext,
): SemanticNode =
    SemanticNode(
        nodeId = nodeId,
        nodePath = nodePath,
        kind = SemanticNodeKind.INCLUDE,
        nodeName = "ui:include",
        syntaxRole = SyntaxRole.INCLUDE,
        location = location,
        provenance = provenance,
        isTransparentStructureWrapper = true,
        isForm = false,
        isNamingContainer = false,
        renderedAttribute = elFacts.firstOrNull { it.attributeName == "rendered" },
        elFacts = elFacts,
        structuralContext = structuralContext,
    )

private fun LogicalTextNode.toSemanticNode(
    nodeId: SemanticNodeId,
    nodePath: LogicalNodePath,
    elFacts: List<SemanticNodeElFact>,
    structuralContext: SemanticStructuralContext,
): SemanticNode =
    SemanticNode(
        nodeId = nodeId,
        nodePath = nodePath,
        kind = SemanticNodeKind.TEXT,
        nodeName = "#text",
        location = location,
        provenance = provenance,
        isTransparentStructureWrapper = false,
        isForm = false,
        isNamingContainer = false,
        elFacts = elFacts,
        structuralContext = structuralContext,
    )

private fun SemanticStructuralContext.extend(
    semanticNode: SemanticNode,
    scopeModel: ScopeStackModel,
    path: LogicalNodePath,
): SemanticStructuralContext {
    val nextAncestor = semanticNode.asAncestor()
    val nextFormAncestry =
        if (semanticNode.isForm) {
            formAncestry + nextAncestor
        } else {
            formAncestry
        }
    val nextNamingContainerAncestry =
        if (semanticNode.isNamingContainer) {
            namingContainerAncestry + nextAncestor
        } else {
            namingContainerAncestry
        }
    val nextIterationAncestry =
        scopeModel.iterationAncestorFor(path, semanticNode)?.let { iterationAncestry + it } ?: iterationAncestry

    return SemanticStructuralContext(
        formAncestry = nextFormAncestry,
        namingContainerAncestry = nextNamingContainerAncestry,
        iterationAncestry = nextIterationAncestry,
    )
}

private fun ScopeStackModel.iterationAncestorFor(
    path: LogicalNodePath,
    semanticNode: SemanticNode,
): SemanticIterationAncestor? {
    val snapshot = snapshotAt(path)
    if (snapshot.nodeScopeId == snapshot.descendantScopeId) {
        return null
    }
    val descendantScope = scopes.getValue(snapshot.descendantScopeId)
    val iterationBindings =
        descendantScope.bindingIds
            .map { bindingId -> this.bindings.first { it.id == bindingId } }
            .filter { it.kind == BindingKind.ITERATION_VAR || it.kind == BindingKind.VAR_STATUS }
    if (iterationBindings.isEmpty()) {
        return null
    }

    return SemanticIterationAncestor(
        nodeId = semanticNode.nodeId,
        nodeName = semanticNode.nodeName,
        location = semanticNode.location,
        provenance = semanticNode.provenance,
        bindingIds = iterationBindings.map { it.id },
        bindingOrigins = iterationBindings.map(ScopeBinding::origin),
    )
}

private fun SemanticNode.asAncestor(): SemanticNodeAncestor =
    SemanticNodeAncestor(
        nodeId = nodeId,
        nodeName = nodeName,
        location = location,
        provenance = provenance,
    )

private fun SemanticElOccurrence.toSemanticFact(normalizedOccurrence: NormalizedSemanticElOccurrence?): SemanticNodeElFact =
    SemanticNodeElFact(
        carrierKind = carrierKind,
        ownerName = ownerName,
        attributeName = attributeName,
        rawValue = rawValue,
        location = location,
        provenance = provenance,
        normalizedTemplate = normalizedOccurrence?.normalizedTemplate,
        bindingReferences = normalizedOccurrence?.bindingReferences.orEmpty(),
        globalReferences = normalizedOccurrence?.globalReferences.orEmpty(),
        parseFailure = parseFailure,
    )

private fun LogicalAttribute.toSemanticAttribute(): SemanticNodeAttribute =
    SemanticNodeAttribute(
        attributeName = name.localName,
        rawValue = value,
        location = location,
    )

private fun LogicalAttribute.toComponentTargetAttribute(): ComponentTargetAttribute? =
    ComponentTargetReferenceParser.parse(toSemanticAttribute())

private fun LogicalElementNode.attributeNamed(localName: String): LogicalAttribute? =
    attributes.firstOrNull { it.name.localName == localName }

private fun LogicalElementNode.renderedTagName(): String =
    name.prefix?.let { "$it:${name.localName}" } ?: name.localName

private fun semanticNodeIdFor(path: LogicalNodePath): SemanticNodeId =
    SemanticNodeId(
        if (path.segments.isEmpty()) {
            "node:/"
        } else {
            "node:/${path.segments.joinToString("/")}"
        },
    )
