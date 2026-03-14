package dev.xhtmlinlinecheck.semantic

object SemanticTargetResolver {
    fun resolve(nodes: List<SemanticNode>): List<SemanticNode> {
        val nodesByExplicitId =
            nodes
                .filter { it.kind == SemanticNodeKind.ELEMENT }
                .mapNotNull { node ->
                    node.explicitIdAttribute
                        ?.takeIf { it.isStaticLiteral }
                        ?.rawValue
                        ?.let { explicitId -> explicitId to node }
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )

        return nodes.map { node ->
            if (node.componentTargetAttributes.isEmpty()) {
                node
            } else {
                node.copy(
                    componentTargetAttributes =
                        node.componentTargetAttributes.map { attribute ->
                            attribute.copy(
                                resolvedReferences =
                                    attribute.references.map { reference ->
                                        resolveReference(reference, sourceNode = node, nodesByExplicitId = nodesByExplicitId)
                                    },
                            )
                        },
                )
            }
        }
    }
}

private fun resolveReference(
    reference: ComponentTargetReference,
    sourceNode: SemanticNode,
    nodesByExplicitId: Map<String, List<SemanticNode>>,
): ResolvedComponentTargetReference =
    when (reference.kind) {
        ComponentTargetReferenceKind.SEARCH_EXPRESSION ->
            resolveSearchExpression(reference, sourceNode)

        ComponentTargetReferenceKind.COMPONENT_ID ->
            resolveComponentReference(reference, sourceNode, nodesByExplicitId)
    }

private fun resolveSearchExpression(
    reference: ComponentTargetReference,
    sourceNode: SemanticNode,
): ResolvedComponentTargetReference =
    when (reference.rawToken) {
        "@this" ->
            ResolvedComponentTargetReference(
                reference = reference,
                kind = ComponentTargetResolutionKind.SOURCE_NODE,
                target = sourceNode.toResolvedTargetNode(),
            )

        "@form" ->
            sourceNode.effectiveFormAncestor()?.let { formNode ->
                ResolvedComponentTargetReference(
                    reference = reference,
                    kind = ComponentTargetResolutionKind.FORM,
                    target = formNode.toResolvedTargetNode(),
                )
            } ?: ResolvedComponentTargetReference(
                reference = reference,
                kind = ComponentTargetResolutionKind.UNRESOLVED,
                detail = "No enclosing form is visible from the source node.",
            )

        else ->
            ResolvedComponentTargetReference(
                reference = reference,
                kind = ComponentTargetResolutionKind.SEARCH_EXPRESSION,
            )
    }

private fun resolveComponentReference(
    reference: ComponentTargetReference,
    sourceNode: SemanticNode,
    nodesByExplicitId: Map<String, List<SemanticNode>>,
): ResolvedComponentTargetReference {
    val candidates = nodesByExplicitId[reference.rawToken].orEmpty()
    val sourceFormNodeId = sourceNode.effectiveFormAncestor()?.nodeId
    val sameFormCandidates =
        candidates.filter { candidate ->
            candidate.effectiveFormAncestor()?.nodeId == sourceFormNodeId
        }

    val resolvedCandidate =
        when {
            sameFormCandidates.size == 1 -> sameFormCandidates.single()
            sameFormCandidates.size > 1 -> null
            sourceFormNodeId == null && candidates.size == 1 -> candidates.single()
            else -> null
        }

    return if (resolvedCandidate != null) {
        ResolvedComponentTargetReference(
            reference = reference,
            kind = ComponentTargetResolutionKind.COMPONENT,
            target = resolvedCandidate.toResolvedTargetNode(),
        )
    } else {
        ResolvedComponentTargetReference(
            reference = reference,
            kind = ComponentTargetResolutionKind.UNRESOLVED,
            detail =
                when {
                    candidates.isEmpty() ->
                        "No component with id='${reference.rawToken}' is visible to the source node."

                    sameFormCandidates.size > 1 ->
                        "Multiple same-form components share id='${reference.rawToken}', so resolution is ambiguous."

                    sourceFormNodeId != null ->
                        "No same-form component with id='${reference.rawToken}' is visible from the source node."

                    else ->
                        "Multiple components share id='${reference.rawToken}', so resolution is ambiguous."
                },
        )
    }
}

private fun SemanticNode.toResolvedTargetNode(): ResolvedComponentTargetNode =
    ResolvedComponentTargetNode(
        nodeName = nodeName,
        explicitId = explicitIdAttribute?.takeIf { it.isStaticLiteral }?.rawValue,
        formAnchor = effectiveFormAnchor(),
    )

private fun SemanticNodeAncestor.toResolvedTargetNode(): ResolvedComponentTargetNode =
    ResolvedComponentTargetNode(
        nodeName = nodeName,
        explicitId = explicitId,
        formAnchor = explicitId ?: nodeName,
    )

private fun SemanticNode.effectiveFormAncestor(): SemanticNodeAncestor? =
    if (isForm) {
        asAncestor()
    } else {
        formAncestry.lastOrNull()
    }

private fun SemanticNode.effectiveFormAnchor(): String? =
    effectiveFormAncestor()?.let { formAncestor ->
        formAncestor.explicitId ?: formAncestor.nodeName
    }

private fun SemanticNode.asAncestor(): SemanticNodeAncestor =
    SemanticNodeAncestor(
        nodeId = nodeId,
        nodeName = nodeName,
        explicitId = explicitIdAttribute?.takeIf { it.isStaticLiteral }?.rawValue,
        location = location,
        provenance = provenance,
    )
