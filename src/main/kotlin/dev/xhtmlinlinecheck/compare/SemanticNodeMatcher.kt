package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.domain.BindingKind
import dev.xhtmlinlinecheck.rules.SyntaxRole
import dev.xhtmlinlinecheck.semantic.CanonicalBindingId
import dev.xhtmlinlinecheck.semantic.ComponentTargetAttribute
import dev.xhtmlinlinecheck.semantic.NormalizedElTemplate
import dev.xhtmlinlinecheck.semantic.SemanticElBindingReference
import dev.xhtmlinlinecheck.semantic.SemanticNode
import dev.xhtmlinlinecheck.semantic.SemanticNodeAncestor
import dev.xhtmlinlinecheck.semantic.SemanticNodeElFact
import dev.xhtmlinlinecheck.semantic.SemanticNodeId
import dev.xhtmlinlinecheck.semantic.SemanticIterationAncestor
import dev.xhtmlinlinecheck.syntax.LogicalName

data class SemanticNodeMatch(
    val oldNodeId: SemanticNodeId,
    val newNodeId: SemanticNodeId,
    val reason: SemanticNodeMatchReason,
)

enum class SemanticNodeMatchReason {
    EXPLICIT_ID,
    STRUCTURAL_SIGNATURE,
}

data class SemanticNodeMatchResult(
    val matches: List<SemanticNodeMatch>,
    val unmatchedOldNodeIds: List<SemanticNodeId>,
    val unmatchedNewNodeIds: List<SemanticNodeId>,
)

internal data class SemanticMatchSignature(
    val tagIdentity: SemanticTagIdentity,
    val renderedGuard: String?,
    val componentTargetAttributes: List<String>,
    val localElFacts: List<String>,
    val formAncestry: List<SemanticAncestorSignature>,
    val namingContainerAncestry: List<SemanticAncestorSignature>,
    val iterationContext: List<SemanticIterationContextSignature>,
) {
    fun render(): String =
        buildString {
            append(tagIdentity.render())
            append("|rendered=")
            append(renderedGuard ?: "<none>")
            append("|targets=")
            append(componentTargetAttributes.joinToString(","))
            append("|el=")
            append(localElFacts.joinToString(","))
            append("|forms=")
            append(formAncestry.joinToString(">") { it.render() })
            append("|naming=")
            append(namingContainerAncestry.joinToString(">") { it.render() })
            append("|iteration=")
            append(iterationContext.joinToString(">") { it.render() })
        }
}

internal data class SemanticTagIdentity(
    val namespaceUri: String?,
    val localName: String,
    val syntaxRole: SyntaxRole?,
) {
    fun render(): String =
        buildString {
            append(namespaceUri ?: "<none>")
            append(":")
            append(localName)
            append("@")
            append(syntaxRole?.name ?: "<none>")
        }
}

internal data class SemanticAncestorSignature(
    val tagIdentity: SemanticTagIdentity,
    val explicitId: String?,
) {
    fun render(): String = explicitId?.let { "${tagIdentity.render()}#$it" } ?: tagIdentity.render()
}

internal data class SemanticIterationContextSignature(
    val tagIdentity: SemanticTagIdentity,
    val bindingKinds: List<BindingKind>,
) {
    fun render(): String =
        buildString {
            append(tagIdentity.render())
            append("[")
            append(bindingKinds.joinToString(",") { it.name })
            append("]")
        }
}

object SemanticNodeMatcher {
    fun matchStructuralCandidates(
        oldNodes: List<SemanticNode>,
        newNodes: List<SemanticNode>,
    ): SemanticNodeMatchResult {
        val oldCandidates = oldNodes.filter(SemanticNode::participatesInStructuralMatching)
        val newCandidates = newNodes.filter(SemanticNode::participatesInStructuralMatching)
        val matches = mutableListOf<SemanticNodeMatch>()
        val unmatchedOld = oldCandidates.toMutableList()
        val unmatchedNew = newCandidates.toMutableList()

        val oldByExplicitId = unmatchedOld.groupByExplicitId()
        val newByExplicitId = unmatchedNew.groupByExplicitId()
        oldByExplicitId.keys
            .intersect(newByExplicitId.keys)
            .sorted()
            .forEach { explicitId ->
                val oldMatches = oldByExplicitId.getValue(explicitId)
                val newMatches = newByExplicitId.getValue(explicitId)
                if (oldMatches.size == 1 && newMatches.size == 1) {
                    val oldNode = oldMatches.single()
                    val newNode = newMatches.single()
                    matches +=
                        SemanticNodeMatch(
                            oldNodeId = oldNode.nodeId,
                            newNodeId = newNode.nodeId,
                            reason = SemanticNodeMatchReason.EXPLICIT_ID,
                        )
                    unmatchedOld.remove(oldNode)
                    unmatchedNew.remove(newNode)
                }
            }

        val oldBySignature = unmatchedOld.groupBy(::semanticSignatureFor)
        val newBySignature = unmatchedNew.groupBy(::semanticSignatureFor)
        oldBySignature.keys
            .intersect(newBySignature.keys)
            .sortedBy(SemanticMatchSignature::render)
            .forEach { signature ->
                val oldMatches = oldBySignature.getValue(signature)
                val newMatches = newBySignature.getValue(signature)
                if (oldMatches.size == newMatches.size) {
                    oldMatches.zip(newMatches).forEach { (oldNode, newNode) ->
                        matches +=
                            SemanticNodeMatch(
                                oldNodeId = oldNode.nodeId,
                                newNodeId = newNode.nodeId,
                                reason = SemanticNodeMatchReason.STRUCTURAL_SIGNATURE,
                            )
                        unmatchedOld.remove(oldNode)
                        unmatchedNew.remove(newNode)
                    }
                }
            }

        return SemanticNodeMatchResult(
            matches = matches,
            unmatchedOldNodeIds = unmatchedOld.map(SemanticNode::nodeId),
            unmatchedNewNodeIds = unmatchedNew.map(SemanticNode::nodeId),
        )
    }
}

private fun List<SemanticNode>.groupByExplicitId(): Map<String, List<SemanticNode>> =
    filter { it.explicitIdAttribute != null }.groupBy { it.explicitIdAttribute!!.rawValue }

internal fun semanticSignatureFor(node: SemanticNode): SemanticMatchSignature =
    SemanticMatchSignature(
        tagIdentity = node.semanticTagIdentity(),
        renderedGuard = node.renderedAttribute?.matchFingerprint(),
        componentTargetAttributes = node.componentTargetAttributes.map(ComponentTargetAttribute::renderResolved),
        localElFacts =
            node.elFacts
                .filterNot { it.attributeName == "rendered" }
                .map(SemanticNodeElFact::matchFingerprint),
        formAncestry = node.formAncestry.map(SemanticNodeAncestor::toSignature),
        namingContainerAncestry = node.namingContainerAncestry.map(SemanticNodeAncestor::toSignature),
        iterationContext = node.iterationAncestry.map(SemanticIterationAncestor::toSignature),
    )

private fun SemanticNode.semanticTagIdentity(): SemanticTagIdentity =
    logicalName.toTagIdentity(nodeName, syntaxRole)

private fun SemanticNodeAncestor.toSignature(): SemanticAncestorSignature =
    SemanticAncestorSignature(
        tagIdentity = logicalName.toTagIdentity(nodeName, syntaxRole),
        explicitId = explicitId,
    )

private fun SemanticIterationAncestor.toSignature(): SemanticIterationContextSignature =
    SemanticIterationContextSignature(
        tagIdentity = logicalName.toTagIdentity(nodeName, syntaxRole),
        bindingKinds = bindingKinds,
    )

private fun LogicalName?.toTagIdentity(
    fallbackName: String,
    syntaxRole: SyntaxRole?,
): SemanticTagIdentity =
    if (this == null) {
        SemanticTagIdentity(
            namespaceUri = null,
            localName = fallbackName,
            syntaxRole = syntaxRole,
        )
    } else {
        SemanticTagIdentity(
            namespaceUri = namespaceUri,
            localName = localName,
            syntaxRole = syntaxRole,
        )
    }

private fun SemanticNodeElFact.matchFingerprint(): String =
    buildString {
        append(carrierKind.name)
        append(":")
        append(attributeName ?: "<text>")
        append(":")
        append(ownerName ?: "<none>")
        append("=")
        append(
            normalizedTemplate?.renderForMatching(bindingReferences)
                ?: rawValue,
        )
    }

private fun NormalizedElTemplate.renderForMatching(bindingReferences: List<SemanticElBindingReference>): String {
    var rendered = render()
    bindingReferences
        .map(SemanticElBindingReference::canonicalId)
        .distinct()
        .sortedWith(compareByDescending<CanonicalBindingId> { it.value.length }.thenByDescending { it.value })
        .forEachIndexed { index, canonicalId ->
            rendered = rendered.replace(canonicalId.value, "binding#${index + 1}")
        }
    return rendered
}
