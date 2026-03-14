package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.SourceGraphParameter
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.el.ElParseException
import dev.xhtmlinlinecheck.el.ElParser
import dev.xhtmlinlinecheck.rules.FACELETS_NAMESPACE
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
import dev.xhtmlinlinecheck.syntax.LogicalElementNode
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.LogicalName
import dev.xhtmlinlinecheck.syntax.LogicalNodePath
import dev.xhtmlinlinecheck.syntax.LogicalTextNode
import dev.xhtmlinlinecheck.syntax.walkDepthFirstWithPath
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree

internal object SemanticElExtractor {
    private val includeName = LogicalName(localName = "include", namespaceUri = FACELETS_NAMESPACE, prefix = "ui")
    private val includeParameterName = LogicalName(localName = "param", namespaceUri = FACELETS_NAMESPACE, prefix = "ui")

    fun extract(
        syntaxTree: XhtmlSyntaxTree,
        tagRules: TagRuleRegistry,
    ): List<SemanticElOccurrence> {
        val occurrences = mutableListOf<SemanticElOccurrence>()
        val includeRule = tagRules.resolve(includeName)
        val includeParameterRule = tagRules.resolve(includeParameterName)
        val ownerTagNamesByPath = mutableMapOf<LogicalNodePath, String>()

        syntaxTree.walkDepthFirstWithPath { path, node ->
            when (node) {
                is LogicalElementNode -> {
                    ownerTagNamesByPath[path] = node.renderedTagName()
                    occurrences += extractElementOccurrences(path, node)
                }
                is LogicalTextNode -> {
                    occurrences += extractTextOccurrences(path, node, ownerTagName = ownerTagNamesByPath[path.parent()])
                }
                is LogicalIncludeNode -> {
                    occurrences += extractIncludeOccurrences(path, node, includeRule.elAttributeNames)
                    occurrences += extractIncludeParameterOccurrences(path, node, includeParameterRule.elAttributeNames)
                }
                else -> Unit
            }
        }

        return occurrences
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
        ownerTagName: String?,
    ): List<SemanticElOccurrence> {
        if (!node.text.containsElContainer()) {
            return emptyList()
        }

        return listOf(
            occurrence(
                carrierKind = SemanticElCarrierKind.TEXT_NODE,
                nodePath = path,
                ownerTagName = ownerTagName ?: "#text",
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

    private fun LogicalNodePath.parent(): LogicalNodePath? =
        if (segments.isEmpty()) {
            null
        } else {
            LogicalNodePath(segments.dropLast(1))
        }

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
