package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.semantic.SemanticNode
import dev.xhtmlinlinecheck.semantic.SemanticNodeId

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

        val oldBySignature = unmatchedOld.groupBy(::structuralSignatureFor)
        val newBySignature = unmatchedNew.groupBy(::structuralSignatureFor)
        oldBySignature.keys
            .intersect(newBySignature.keys)
            .sortedBy(StructuralSignature::render)
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

private data class StructuralSignature(
    val nodeName: String,
    val rendered: String?,
    val targetAttributes: List<Pair<String, String>>,
    val formAncestry: List<String>,
    val namingContainerAncestry: List<String>,
    val iterationAncestry: List<String>,
) {
    fun render(): String =
        buildString {
            append(nodeName)
            append("|rendered=")
            append(rendered ?: "<none>")
            append("|targets=")
            append(targetAttributes.joinToString(",") { (name, value) -> "$name=$value" })
            append("|forms=")
            append(formAncestry.joinToString(">"))
            append("|naming=")
            append(namingContainerAncestry.joinToString(">"))
            append("|iteration=")
            append(iterationAncestry.joinToString(">"))
        }
}

private fun List<SemanticNode>.groupByExplicitId(): Map<String, List<SemanticNode>> =
    filter { it.explicitIdAttribute != null }.groupBy { it.explicitIdAttribute!!.rawValue }

private fun structuralSignatureFor(node: SemanticNode): StructuralSignature =
    StructuralSignature(
        nodeName = node.nodeName,
        rendered = node.renderedAttribute?.normalizedTemplate?.render() ?: node.renderedAttribute?.rawValue,
        targetAttributes = node.targetAttributes.map { it.attributeName to it.rawValue },
        formAncestry = node.formAncestry.map { it.nodeName },
        namingContainerAncestry = node.namingContainerAncestry.map { it.nodeName },
        iterationAncestry = node.iterationAncestry.map { it.nodeName },
    )
