package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.domain.BindingKind
import dev.xhtmlinlinecheck.rules.SyntaxRole
import dev.xhtmlinlinecheck.semantic.CanonicalBindingId
import dev.xhtmlinlinecheck.semantic.ComponentTargetAttribute
import dev.xhtmlinlinecheck.semantic.ComponentTargetResolutionKind
import dev.xhtmlinlinecheck.semantic.NormalizedElTemplate
import dev.xhtmlinlinecheck.semantic.ResolvedComponentTargetReference
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
    EXPLICIT_TARGET_RELATIONSHIP,
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

internal data class SemanticTargetRelationshipSignature(
    val tagIdentity: SemanticTagIdentity,
    val relationships: List<String>,
) {
    fun render(): String =
        buildString {
            append(tagIdentity.render())
            append("|relationships=")
            append(relationships.joinToString(","))
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

        matchByExplicitIds(unmatchedOld, unmatchedNew, matches)
        matchByExplicitTargetRelationships(unmatchedOld, unmatchedNew, matches)
        matchBySemanticSignatureWithAncestryConstraints(unmatchedOld, unmatchedNew, matches)

        return SemanticNodeMatchResult(
            matches = matches,
            unmatchedOldNodeIds = unmatchedOld.map(SemanticNode::nodeId),
            unmatchedNewNodeIds = unmatchedNew.map(SemanticNode::nodeId),
        )
    }
}

private fun matchByExplicitIds(
    unmatchedOld: MutableList<SemanticNode>,
    unmatchedNew: MutableList<SemanticNode>,
    matches: MutableList<SemanticNodeMatch>,
) {
    val oldByExplicitId = unmatchedOld.groupByExplicitId()
    val newByExplicitId = unmatchedNew.groupByExplicitId()
    oldByExplicitId.keys
        .intersect(newByExplicitId.keys)
        .sorted()
        .forEach { explicitId ->
            val oldMatches = oldByExplicitId.getValue(explicitId)
            val newMatches = newByExplicitId.getValue(explicitId)
            if (oldMatches.size == 1 && newMatches.size == 1) {
                recordMatch(
                    oldNode = oldMatches.single(),
                    newNode = newMatches.single(),
                    reason = SemanticNodeMatchReason.EXPLICIT_ID,
                    unmatchedOld = unmatchedOld,
                    unmatchedNew = unmatchedNew,
                    matches = matches,
                )
            }
        }
}

private fun matchByExplicitTargetRelationships(
    unmatchedOld: MutableList<SemanticNode>,
    unmatchedNew: MutableList<SemanticNode>,
    matches: MutableList<SemanticNodeMatch>,
) {
    while (true) {
        val oldRelationshipSignatures =
            unmatchedOld.associateWith { node ->
                targetRelationshipSignatureFor(node).takeIf { it.relationships.isNotEmpty() }
            }
        val newRelationshipSignatures =
            unmatchedNew.associateWith { node ->
                targetRelationshipSignatureFor(node).takeIf { it.relationships.isNotEmpty() }
            }
        val proposedMatches =
            mutuallyUniqueMatches(
                oldNodes = unmatchedOld,
                newNodes = unmatchedNew,
                oldSignatureFor = { oldRelationshipSignatures.getValue(it) },
                newSignatureFor = { newRelationshipSignatures.getValue(it) },
                matches = matches,
            )
        if (proposedMatches.isEmpty()) {
            break
        }
        proposedMatches.forEach { (oldNode, newNode) ->
            recordMatch(
                oldNode = oldNode,
                newNode = newNode,
                reason = SemanticNodeMatchReason.EXPLICIT_TARGET_RELATIONSHIP,
                unmatchedOld = unmatchedOld,
                unmatchedNew = unmatchedNew,
                matches = matches,
            )
        }
    }
}

private fun matchBySemanticSignatureWithAncestryConstraints(
    unmatchedOld: MutableList<SemanticNode>,
    unmatchedNew: MutableList<SemanticNode>,
    matches: MutableList<SemanticNodeMatch>,
) {
    while (true) {
        val proposedMatches =
            mutuallyUniqueMatches(
                oldNodes = unmatchedOld,
                newNodes = unmatchedNew,
                oldSignatureFor = ::semanticSignatureFor,
                newSignatureFor = ::semanticSignatureFor,
                matches = matches,
            )
        if (proposedMatches.isEmpty()) {
            break
        }
        proposedMatches.forEach { (oldNode, newNode) ->
            recordMatch(
                oldNode = oldNode,
                newNode = newNode,
                reason = SemanticNodeMatchReason.STRUCTURAL_SIGNATURE,
                unmatchedOld = unmatchedOld,
                unmatchedNew = unmatchedNew,
                matches = matches,
            )
        }
    }
}

private fun <T : Any> mutuallyUniqueMatches(
    oldNodes: List<SemanticNode>,
    newNodes: List<SemanticNode>,
    oldSignatureFor: (SemanticNode) -> T?,
    newSignatureFor: (SemanticNode) -> T?,
    matches: List<SemanticNodeMatch>,
): List<Pair<SemanticNode, SemanticNode>> {
    val oldCandidates =
        oldNodes.associateWith { oldNode ->
            val oldSignature = oldSignatureFor(oldNode) ?: return@associateWith emptyList()
            newNodes.filter { newNode ->
                newSignatureFor(newNode) == oldSignature && satisfiesAncestryConstraint(oldNode, newNode, matches)
            }
        }
    val newCandidates =
        newNodes.associateWith { newNode ->
            val newSignature = newSignatureFor(newNode) ?: return@associateWith emptyList()
            oldNodes.filter { oldNode ->
                oldSignatureFor(oldNode) == newSignature && satisfiesAncestryConstraint(oldNode, newNode, matches)
            }
        }

    return oldNodes.mapNotNull { oldNode ->
        val newCandidatesForOld = oldCandidates.getValue(oldNode)
        if (newCandidatesForOld.size != 1) {
            return@mapNotNull null
        }
        val newNode = newCandidatesForOld.single()
        if (newCandidates.getValue(newNode).singleOrNull() != oldNode) {
            return@mapNotNull null
        }
        oldNode to newNode
    }
}

private fun satisfiesAncestryConstraint(
    oldNode: SemanticNode,
    newNode: SemanticNode,
    matches: List<SemanticNodeMatch>,
): Boolean {
    val oldMatchedAncestorId = nearestMatchedStructuralAncestorId(oldNode, matches.map(SemanticNodeMatch::oldNodeId).toSet())
    val newMatchedAncestorId = nearestMatchedStructuralAncestorId(newNode, matches.map(SemanticNodeMatch::newNodeId).toSet())
    if (oldMatchedAncestorId == null || newMatchedAncestorId == null) {
        return oldMatchedAncestorId == null && newMatchedAncestorId == null
    }
    return matches.any { match ->
        match.oldNodeId == oldMatchedAncestorId && match.newNodeId == newMatchedAncestorId
    }
}

private fun nearestMatchedStructuralAncestorId(
    node: SemanticNode,
    matchedNodeIds: Set<SemanticNodeId>,
): SemanticNodeId? =
    node.nodePath.ancestorPaths()
        .asSequence()
        .map(::semanticNodeIdFor)
        .firstOrNull { it in matchedNodeIds }

private fun targetRelationshipSignatureFor(
    node: SemanticNode,
): SemanticTargetRelationshipSignature {
    val relationships =
        node.componentTargetAttributes.flatMap { attribute ->
            attribute.resolvedReferences.mapNotNull { reference ->
                reference.explicitTargetRelationship(attribute.kind.attributeName)
            }
        }
    return SemanticTargetRelationshipSignature(
        tagIdentity = node.semanticTagIdentity(),
        relationships = relationships,
    )
}

private fun List<SemanticNode>.groupByExplicitId(): Map<String, List<SemanticNode>> =
    filter { it.explicitIdAttribute?.isStaticLiteral == true }.groupBy { it.explicitIdAttribute!!.rawValue }

private fun recordMatch(
    oldNode: SemanticNode,
    newNode: SemanticNode,
    reason: SemanticNodeMatchReason,
    unmatchedOld: MutableList<SemanticNode>,
    unmatchedNew: MutableList<SemanticNode>,
    matches: MutableList<SemanticNodeMatch>,
) {
    matches +=
        SemanticNodeMatch(
            oldNodeId = oldNode.nodeId,
            newNodeId = newNode.nodeId,
            reason = reason,
        )
    unmatchedOld.remove(oldNode)
    unmatchedNew.remove(newNode)
}

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

private fun ResolvedComponentTargetReference.explicitTargetRelationship(
    attributeName: String,
): String? {
    val resolvedTarget = target ?: return null
    val explicitTarget = resolvedTarget.explicitId ?: return null
    val stableTargetAnchor =
        when (kind) {
            ComponentTargetResolutionKind.COMPONENT ->
                "component:${resolvedTarget.nodeName}#$explicitTarget@form:${resolvedTarget.formAnchor ?: "<none>"}"

            ComponentTargetResolutionKind.FORM ->
                "form:${resolvedTarget.nodeName}#$explicitTarget"

            else -> null
        } ?: return null

    return "$attributeName->$stableTargetAnchor"
}

private fun dev.xhtmlinlinecheck.syntax.LogicalNodePath.ancestorPaths(): List<dev.xhtmlinlinecheck.syntax.LogicalNodePath> =
    segments.indices.reversed().map { depth ->
        dev.xhtmlinlinecheck.syntax.LogicalNodePath(segments.take(depth))
    }

private fun semanticNodeIdFor(path: dev.xhtmlinlinecheck.syntax.LogicalNodePath): SemanticNodeId =
    SemanticNodeId(
        buildString {
            append("node:/")
            append(path.segments.joinToString("/"))
        },
    )
