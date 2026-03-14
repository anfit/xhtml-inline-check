package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceGraphEdge
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.domain.SourcePosition
import dev.xhtmlinlinecheck.domain.SourceSpan
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

internal object SourceGraphIncludeDiscovery {
    private const val FACELETS_NAMESPACE = "http://xmlns.jcp.org/jsf/facelets"
    private const val INCLUDE_LOCAL_NAME = "include"
    private const val SRC_ATTRIBUTE = "src"

    fun discover(document: SourceDocument, contents: String): List<SourceGraphEdge> {
        val reader = xmlInputFactory().createXMLStreamReader(document.displayPath, StringReader(contents))

        reader.use {
            val edges = mutableListOf<SourceGraphEdge>()
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue
                }
                if (reader.namespaceURI != FACELETS_NAMESPACE || reader.localName != INCLUDE_LOCAL_NAME) {
                    continue
                }

                val sourcePath = reader.readAttributeValue(SRC_ATTRIBUTE)
                edges += SourceGraphEdge.discovered(
                    includeSite = reader.toSourceLocation(document, if (sourcePath != null) SRC_ATTRIBUTE else null),
                    sourcePath = sourcePath,
                )
            }
            return edges
        }
    }

    private fun xmlInputFactory(): XMLInputFactory =
        XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
        }

    private fun javax.xml.stream.XMLStreamReader.readAttributeValue(attributeName: String): String? {
        for (index in 0 until attributeCount) {
            if (getAttributeLocalName(index) == attributeName) {
                return getAttributeValue(index)
            }
        }
        return null
    }

    private fun javax.xml.stream.XMLStreamReader.toSourceLocation(
        document: SourceDocument,
        attributeName: String?,
    ): SourceLocation {
        val line = location.lineNumber.coerceAtLeast(1)
        val column = location.columnNumber.coerceAtLeast(1)
        return SourceLocation(
            document = document,
            span = SourceSpan(SourcePosition(line = line, column = column)),
            attributeName = attributeName,
        )
    }
}

private inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R =
    try {
        block(this)
    } finally {
        this?.close()
    }
