package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceGraphEdge
import dev.xhtmlinlinecheck.domain.SourceGraphFile
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.loader.LoadedSources
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
import dev.xhtmlinlinecheck.rules.isIncludeTag
import dev.xhtmlinlinecheck.xml.NamespaceAwareXml
import dev.xhtmlinlinecheck.xml.toSourceLocation
import dev.xhtmlinlinecheck.xml.useAndClose
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

data class ParsedTrees(
    val oldRoot: ParsedSourceTree,
    val newRoot: ParsedSourceTree,
)

fun interface XhtmlSyntaxParser {
    fun parse(loadedSources: LoadedSources): ParsedTrees

    companion object {
        fun scaffold(tagRules: TagRuleRegistry = TagRuleRegistry.builtIns()): XhtmlSyntaxParser =
            XhtmlSyntaxParser { loadedSources ->
                ParsedTrees(
                    oldRoot = loadedSources.oldRoot.toParsedSourceTree(tagRules),
                    newRoot = loadedSources.newRoot.toParsedSourceTree(tagRules),
                )
            }

        private fun dev.xhtmlinlinecheck.loader.LoadedSource.toParsedSourceTree(
            tagRules: TagRuleRegistry,
        ): ParsedSourceTree =
            ParsedSourceTree(
                document = document,
                provenance = provenance,
                sourceGraphFile = sourceGraphFile,
                syntaxTree = XhtmlSyntaxTree(root = LogicalTreeBuilder.parse(sourceGraphFile, tagRules)),
            )
    }
}

private object LogicalTreeBuilder {
    fun parse(
        sourceGraphFile: SourceGraphFile,
        tagRules: TagRuleRegistry,
    ): LogicalElementNode? {
        val xmlReader = NamespaceAwareXml.newReader(
            sourceGraphFile.document.displayPath,
            sourceGraphFile.contents,
        )

        try {
            val includeEdges = ArrayDeque(sourceGraphFile.includeEdges)
            while (xmlReader.hasNext()) {
                if (xmlReader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue
                }

                return readNode(xmlReader, sourceGraphFile, includeEdges, tagRules) as? LogicalElementNode
            }
            return null
        } finally {
            xmlReader.close()
        }
    }

    private fun readNode(
        reader: XMLStreamReader,
        sourceGraphFile: SourceGraphFile,
        includeEdges: ArrayDeque<SourceGraphEdge>,
        tagRules: TagRuleRegistry,
    ): LogicalNode =
        if (tagRules.resolve(reader.toLogicalName()).isIncludeTag) {
            readIncludeNode(reader, sourceGraphFile, includeEdges.removeFirst(), tagRules)
        } else {
            readElementNode(reader, sourceGraphFile, includeEdges, tagRules)
        }

    private fun readElementNode(
        reader: XMLStreamReader,
        sourceGraphFile: SourceGraphFile,
        includeEdges: ArrayDeque<SourceGraphEdge>,
        tagRules: TagRuleRegistry,
    ): LogicalElementNode {
        val location = reader.toSourceLocation(sourceGraphFile)
        val children = mutableListOf<LogicalNode>()
        val name = reader.toLogicalName()
        val tagRule = tagRules.resolve(name)
        val attributes = buildAttributes(reader, sourceGraphFile)
        val namespaceBindings = buildNamespaceBindings(reader)

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> children += readNode(
                    reader,
                    sourceGraphFile,
                    includeEdges,
                    tagRules
                )

                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    reader.text.takeUnless(String::isBlank)?.let { text ->
                        children +=
                            LogicalTextNode(
                                text = text,
                                location = location,
                                provenance = sourceGraphFile.provenanceAt(location),
                            )
                    }
                }

                XMLStreamConstants.END_ELEMENT -> return LogicalElementNode(
                    name = name,
                    tagRule = tagRule,
                    attributes = attributes,
                    namespaceBindings = namespaceBindings,
                    children = children,
                    location = location,
                    provenance = sourceGraphFile.provenanceAt(location),
                )
            }
        }

        return LogicalElementNode(
            name = name,
            tagRule = tagRule,
            attributes = attributes,
            namespaceBindings = namespaceBindings,
            children = children,
            location = location,
            provenance = sourceGraphFile.provenanceAt(location),
        )
    }

    private fun readIncludeNode(
        reader: XMLStreamReader,
        sourceGraphFile: SourceGraphFile,
        edge: SourceGraphEdge,
        tagRules: TagRuleRegistry,
    ): LogicalIncludeNode {
        skipCurrentElement(reader)
        return LogicalIncludeNode(
            includeSite = edge.includeSite,
            sourcePath = edge.sourcePath,
            parameters = edge.parameters,
            expandedFile = edge.includedFile,
            includeFailure = edge.includeFailure,
            children =
                edge.includedFile
                    ?.let { parse(it, tagRules) }
                    ?.let(::listOf)
                    ?: emptyList(),
            location = edge.includeSite,
            provenance = sourceGraphFile.includeBoundaryProvenanceAt(edge.includeSite),
        )
    }

    private fun skipCurrentElement(reader: XMLStreamReader) {
        var depth = 1
        while (reader.hasNext() && depth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> depth += 1
                XMLStreamConstants.END_ELEMENT -> depth -= 1
            }
        }
    }

    private fun buildAttributes(reader: XMLStreamReader, sourceGraphFile: SourceGraphFile): List<LogicalAttribute> =
        buildList {
            for (index in 0 until reader.attributeCount) {
                add(
                    LogicalAttribute(
                        name =
                            LogicalName(
                                localName = reader.getAttributeLocalName(index),
                                namespaceUri = reader.getAttributeNamespace(index).nullIfBlank(),
                                prefix = reader.getAttributePrefix(index).nullIfBlank(),
                            ),
                        value = reader.getAttributeValue(index),
                        location = reader.toSourceLocation(sourceGraphFile, reader.getAttributeLocalName(index)),
                    ),
                )
            }
        }

    private fun buildNamespaceBindings(reader: XMLStreamReader): List<LogicalNamespaceBinding> =
        buildList {
            for (index in 0 until reader.namespaceCount) {
                val namespaceUri = reader.getNamespaceURI(index) ?: continue
                add(
                    LogicalNamespaceBinding(
                        prefix = reader.getNamespacePrefix(index),
                        namespaceUri = namespaceUri,
                    ),
                )
            }
        }

    private fun XMLStreamReader.toLogicalName(): LogicalName =
        LogicalName(
            localName = localName,
            namespaceUri = namespaceURI.nullIfBlank(),
            prefix = prefix,
        )

    private fun String?.nullIfBlank(): String? = this?.takeUnless(String::isBlank)

    private fun XMLStreamReader.toSourceLocation(
        sourceGraphFile: SourceGraphFile,
        attributeName: String? = null,
    ): SourceLocation = toSourceLocation(sourceGraphFile.document, attributeName)

    private fun SourceGraphFile.provenanceAt(location: SourceLocation): Provenance =
        Provenance(
            physicalLocation = location,
            logicalLocation =
                if (stack.steps.isEmpty()) {
                    location
                } else {
                    provenance.logicalLocation
                },
            includeStack = stack.steps,
        )

    private fun SourceGraphFile.includeBoundaryProvenanceAt(location: SourceLocation): Provenance =
        Provenance(
            physicalLocation = location,
            logicalLocation = location,
            includeStack = stack.steps,
        )
}
