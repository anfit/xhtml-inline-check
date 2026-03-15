package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.el.ElParseException
import dev.xhtmlinlinecheck.el.ElParser
import dev.xhtmlinlinecheck.rules.FACELETS_NAMESPACE
import dev.xhtmlinlinecheck.rules.SyntaxRole
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
import dev.xhtmlinlinecheck.syntax.LogicalAttribute
import dev.xhtmlinlinecheck.syntax.LogicalElementNode
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.LogicalName
import dev.xhtmlinlinecheck.syntax.LogicalNode
import dev.xhtmlinlinecheck.syntax.LogicalNodePath
import dev.xhtmlinlinecheck.syntax.LogicalTextNode
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree

internal data class SemanticTreeExtraction(
    val elOccurrences: List<SemanticElOccurrence>,
    val normalizedElOccurrences: List<NormalizedSemanticElOccurrence>,
    val semanticNodes: List<SemanticNode>,
)

internal object SemanticTreeExtractor {
    private val includeName = LogicalName(localName = "include", namespaceUri = FACELETS_NAMESPACE, prefix = "ui")
    private val includeParameterName = LogicalName(localName = "param", namespaceUri = FACELETS_NAMESPACE, prefix = "ui")

    fun extract(
        syntaxTree: XhtmlSyntaxTree,
        scopeModel: ScopeStackModel,
        tagRules: TagRuleRegistry,
    ): SemanticTreeExtraction {
        val includeRule = tagRules.resolve(includeName)
        val includeParameterRule = tagRules.resolve(includeParameterName)
        val occurrences = mutableListOf<SemanticElOccurrence>()
        val baseNodes = mutableListOf<SemanticNode>()

        fun visit(
            node: LogicalNode,
            path: LogicalNodePath,
            structuralContext: SemanticStructuralContext,
            parentElementTagName: String? = null,
        ) {
            val currentOccurrences =
                when (node) {
                    is LogicalElementNode -> extractElementOccurrences(path, node)
                    is LogicalIncludeNode ->
                        extractIncludeOccurrences(path, node, includeRule.elAttributeNames) +
                            extractIncludeParameterOccurrences(path, node, includeParameterRule.elAttributeNames)

                    is LogicalTextNode -> extractTextOccurrences(path, node, parentElementTagName)
                }
            occurrences += currentOccurrences

            val semanticNode =
                when (node) {
                    is LogicalElementNode ->
                        node.toSemanticNode(
                            nodeId = semanticNodeIdFor(path),
                            nodePath = path,
                            elFacts = emptyList(),
                            structuralContext = structuralContext,
                        )

                    is LogicalIncludeNode ->
                        node.toSemanticNode(
                            nodeId = semanticNodeIdFor(path),
                            nodePath = path,
                            elFacts = emptyList(),
                            structuralContext = structuralContext,
                        )

                    is LogicalTextNode ->
                        node.toSemanticNode(
                            nodeId = semanticNodeIdFor(path),
                            nodePath = path,
                            elFacts = emptyList(),
                            structuralContext = structuralContext,
                        )
                }
            baseNodes += semanticNode

            val nextStructuralContext = structuralContext.extend(semanticNode, scopeModel, path)
            val nextParentElementTagName =
                when (node) {
                    is LogicalElementNode -> node.renderedTagName()
                    else -> parentElementTagName
                }

            when (node) {
                is LogicalElementNode ->
                    node.children.forEachIndexed { index, child ->
                        visit(
                            node = child,
                            path = path.child(index),
                            structuralContext = nextStructuralContext,
                            parentElementTagName = nextParentElementTagName,
                        )
                    }

                is LogicalIncludeNode ->
                    node.children.forEachIndexed { index, child ->
                        visit(
                            node = child,
                            path = path.child(index),
                            structuralContext = nextStructuralContext,
                            parentElementTagName = nextParentElementTagName,
                        )
                    }

                is LogicalTextNode -> Unit
            }
        }

        syntaxTree.root?.let { visit(it, LogicalNodePath.root(), SemanticStructuralContext()) }

        val normalizedElOccurrences = SemanticElNormalizer.normalize(occurrences, scopeModel)
        val normalizedByOccurrence = normalizedElOccurrences.associateBy(NormalizedSemanticElOccurrence::occurrence)
        val elFactsByPath =
            occurrences
                .map { occurrence ->
                    occurrence.nodePath to occurrence.toSemanticFact(normalizedByOccurrence[occurrence])
                }.groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )
        val semanticNodes =
            baseNodes.map { node ->
                val elFacts = elFactsByPath[node.nodePath].orEmpty()
                node.copy(
                    renderedAttribute = elFacts.firstOrNull { it.attributeName == "rendered" },
                    elFacts = elFacts,
                )
            }

        return SemanticTreeExtraction(
            elOccurrences = occurrences,
            normalizedElOccurrences = normalizedElOccurrences,
            semanticNodes = semanticNodes,
        )
    }

    private fun extractElementOccurrences(
        path: LogicalNodePath,
        node: LogicalElementNode,
    ): List<SemanticElOccurrence> =
        node.attributes
            .filter { it.name.localName in node.tagRule.elAttributeNames }
            .map { attribute ->
                occurrence(
                    carrierKind = SemanticElCarrierKind.ELEMENT_ATTRIBUTE,
                    nodePath = path,
                    ownerTagName = node.renderedTagName(),
                    attributeName = attribute.name.localName,
                    rawValue = attribute.value,
                    location = attribute.location,
                    provenance = node.provenance.atLocation(attribute.location),
                )
            }

    private fun extractTextOccurrences(
        path: LogicalNodePath,
        node: LogicalTextNode,
        parentElementTagName: String?,
    ): List<SemanticElOccurrence> {
        if (!node.text.containsElContainer()) {
            return emptyList()
        }

        return listOf(
            occurrence(
                carrierKind = SemanticElCarrierKind.TEXT_NODE,
                nodePath = path,
                ownerTagName = parentElementTagName ?: "#text",
                rawValue = node.text,
                location = node.location,
                provenance = node.provenance.atLocation(node.location),
            ),
        )
    }

    private fun extractIncludeOccurrences(
        path: LogicalNodePath,
        node: LogicalIncludeNode,
        elAttributeNames: Set<String>,
    ): List<SemanticElOccurrence> {
        if ("src" !in elAttributeNames || node.sourcePath == null) {
            return emptyList()
        }

        return listOf(
            occurrence(
                carrierKind = SemanticElCarrierKind.INCLUDE_ATTRIBUTE,
                nodePath = path,
                ownerTagName = "ui:include",
                attributeName = "src",
                rawValue = node.sourcePath,
                location = node.includeSite,
                provenance = node.provenance.atLocation(node.includeSite),
            ),
        )
    }

    private fun extractIncludeParameterOccurrences(
        path: LogicalNodePath,
        node: LogicalIncludeNode,
        elAttributeNames: Set<String>,
    ): List<SemanticElOccurrence> {
        if ("value" !in elAttributeNames) {
            return emptyList()
        }

        return node.parameters
            .filter { it.valueExpression != null }
            .map { parameter ->
                occurrence(
                    carrierKind = SemanticElCarrierKind.INCLUDE_PARAMETER,
                    nodePath = path,
                    ownerTagName = "ui:param",
                    ownerName = parameter.name,
                    attributeName = "value",
                    rawValue = requireNotNull(parameter.valueExpression),
                    location = parameter.provenance.physicalLocation,
                    provenance = parameter.provenance,
                )
            }
    }

    private fun occurrence(
        carrierKind: SemanticElCarrierKind,
        nodePath: LogicalNodePath,
        ownerTagName: String,
        rawValue: String,
        location: SourceLocation,
        provenance: Provenance,
        attributeName: String? = null,
        ownerName: String? = null,
    ): SemanticElOccurrence =
        try {
            SemanticElOccurrence(
                carrierKind = carrierKind,
                nodePath = nodePath,
                ownerTagName = ownerTagName,
                ownerName = ownerName,
                attributeName = attributeName,
                rawValue = rawValue,
                location = location,
                provenance = provenance,
                template = ElParser.parseTemplate(rawValue),
            )
        } catch (error: ElParseException) {
            SemanticElOccurrence(
                carrierKind = carrierKind,
                nodePath = nodePath,
                ownerTagName = ownerTagName,
                ownerName = ownerName,
                attributeName = attributeName,
                rawValue = rawValue,
                location = location,
                provenance = provenance,
                parseFailure = SemanticElParseFailure(message = error.message ?: "EL parse failed", offset = error.offset),
            )
        }

    private fun LogicalElementNode.renderedTagName(): String =
        name.prefix?.let { "$it:${name.localName}" } ?: name.localName

    private fun String.containsElContainer(): Boolean = contains("#{") || contains("\${")

    private fun Provenance.atLocation(location: SourceLocation): Provenance =
        if (includeStack.isEmpty()) {
            copy(
                physicalLocation = location,
                logicalLocation = location,
            )
        } else {
            copy(physicalLocation = location)
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
            .map { bindingById(it) }
            .filter { it.kind == dev.xhtmlinlinecheck.domain.BindingKind.ITERATION_VAR || it.kind == dev.xhtmlinlinecheck.domain.BindingKind.VAR_STATUS }
    if (iterationBindings.isEmpty()) {
        return null
    }

    return SemanticIterationAncestor(
        nodeId = semanticNode.nodeId,
        nodeName = semanticNode.nodeName,
        logicalName = semanticNode.logicalName,
        syntaxRole = semanticNode.syntaxRole,
        location = semanticNode.location,
        provenance = semanticNode.provenance,
        bindingKinds = iterationBindings.map(ScopeBinding::kind),
        bindingIds = iterationBindings.map { it.id },
        bindingOrigins = iterationBindings.map(ScopeBinding::origin),
    )
}

private fun SemanticNode.asAncestor(): SemanticNodeAncestor =
    SemanticNodeAncestor(
        nodeId = nodeId,
        nodeName = nodeName,
        logicalName = logicalName,
        syntaxRole = syntaxRole,
        explicitId = explicitIdAttribute?.takeIf { it.isStaticLiteral }?.rawValue,
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
