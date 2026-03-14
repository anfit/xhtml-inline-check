package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceGraphEdge
import dev.xhtmlinlinecheck.domain.SourceGraphFile
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.loader.LoadedSources
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
        fun scaffold(): XhtmlSyntaxParser =
            XhtmlSyntaxParser { loadedSources ->
                ParsedTrees(
                    oldRoot = loadedSources.oldRoot.toParsedSourceTree(),
                    newRoot = loadedSources.newRoot.toParsedSourceTree(),
                )
            }

        private fun dev.xhtmlinlinecheck.loader.LoadedSource.toParsedSourceTree(): ParsedSourceTree =
            ParsedSourceTree(
                document = document,
                provenance = provenance,
                sourceGraphFile = sourceGraphFile,
                syntaxTree = XhtmlSyntaxTree(root = LogicalTreeBuilder.parse(sourceGraphFile)),
            )
    }
}

private object LogicalTreeBuilder {
    private const val FACELETS_NAMESPACE = "http://xmlns.jcp.org/jsf/facelets"
    private const val INCLUDE_LOCAL_NAME = "include"

    fun parse(sourceGraphFile: SourceGraphFile): LogicalElementNode? {
        val reader = NamespaceAwareXml.newReader(sourceGraphFile.document.displayPath, sourceGraphFile.contents)
        reader.useAndClose {
            val includeEdges = ArrayDeque(sourceGraphFile.includeEdges)
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue
                }

                return readNode(reader, sourceGraphFile, includeEdges) as? LogicalElementNode
            }
            return null
        }
    }

    private fun readNode(
        reader: XMLStreamReader,
        sourceGraphFile: SourceGraphFile,
        includeEdges: ArrayDeque<SourceGraphEdge>,
    ): LogicalNode =
        if (reader.namespaceURI == FACELETS_NAMESPACE && reader.localName == INCLUDE_LOCAL_NAME) {
            readIncludeNode(reader, sourceGraphFile, includeEdges.removeFirst())
        } else {
            readElementNode(reader, sourceGraphFile, includeEdges)
        }

    private fun readElementNode(
        reader: XMLStreamReader,
        sourceGraphFile: SourceGraphFile,
        includeEdges: ArrayDeque<SourceGraphEdge>,
    ): LogicalElementNode {
        val location = reader.toSourceLocation(sourceGraphFile)
        val children = mutableListOf<LogicalNode>()
        val name = reader.toLogicalName()
        val attributes = buildAttributes(reader, sourceGraphFile)
        val namespaceBindings = buildNamespaceBindings(reader)

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> children += readNode(reader, sourceGraphFile, includeEdges)
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    reader.text.takeUnless(String::isBlank)?.let { text ->
                        children +=
                            LogicalTextNode(
                                text = text,
                                provenance = sourceGraphFile.provenanceAt(location),
                            )
                    }
                }

                XMLStreamConstants.END_ELEMENT -> return LogicalElementNode(
                    name = name,
                    attributes = attributes,
                    namespaceBindings = namespaceBindings,
                    children = children,
                    provenance = sourceGraphFile.provenanceAt(location),
                )
            }
        }

        return LogicalElementNode(
            name = name,
            attributes = attributes,
            namespaceBindings = namespaceBindings,
            children = children,
            provenance = sourceGraphFile.provenanceAt(location),
        )
    }

    private fun readIncludeNode(
        reader: XMLStreamReader,
        sourceGraphFile: SourceGraphFile,
        edge: SourceGraphEdge,
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
                    ?.let(::parse)
                    ?.let(::listOf)
                    ?: emptyList(),
            provenance = sourceGraphFile.provenanceAt(edge.includeSite),
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
                                namespaceUri = reader.getAttributeNamespace(index),
                                prefix = reader.getAttributePrefix(index),
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
            namespaceUri = namespaceURI,
            prefix = prefix,
        )

    private fun XMLStreamReader.toSourceLocation(
        sourceGraphFile: SourceGraphFile,
        attributeName: String? = null,
    ): SourceLocation = toSourceLocation(sourceGraphFile.document, attributeName)

    private fun SourceGraphFile.provenanceAt(location: SourceLocation): Provenance =
        Provenance(
            physicalLocation = location,
            logicalLocation = location,
            includeStack = stack.steps,
        )
}
