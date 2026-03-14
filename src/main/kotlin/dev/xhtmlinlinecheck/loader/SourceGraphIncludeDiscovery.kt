package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceGraphEdge
import dev.xhtmlinlinecheck.domain.SourceGraphParameter
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.domain.SourcePosition
import dev.xhtmlinlinecheck.domain.SourceSpan
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

internal object SourceGraphIncludeDiscovery {
    private const val FACELETS_NAMESPACE = "http://xmlns.jcp.org/jsf/facelets"
    private const val INCLUDE_LOCAL_NAME = "include"
    private const val PARAM_LOCAL_NAME = "param"
    private const val NAME_ATTRIBUTE = "name"
    private const val SRC_ATTRIBUTE = "src"
    private const val VALUE_ATTRIBUTE = "value"

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
                    parameters = reader.readIncludeParameters(document),
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

    private fun javax.xml.stream.XMLStreamReader.readIncludeParameters(document: SourceDocument): List<SourceGraphParameter> {
        val parameters = mutableListOf<SourceGraphParameter>()
        var depth = 1
        while (hasNext() && depth > 0) {
            when (next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (depth == 1 && namespaceURI == FACELETS_NAMESPACE && localName == PARAM_LOCAL_NAME) {
                        toIncludeParameter(document)?.let(parameters::add)
                    }
                    depth += 1
                }

                XMLStreamConstants.END_ELEMENT -> depth -= 1
            }
        }

        return parameters
    }

    private fun javax.xml.stream.XMLStreamReader.toIncludeParameter(document: SourceDocument): SourceGraphParameter? {
        val name = readAttributeValue(NAME_ATTRIBUTE)?.takeUnless(String::isBlank) ?: return null
        val valueExpression = readAttributeValue(VALUE_ATTRIBUTE)
        val locationAttribute =
            when {
                valueExpression != null -> VALUE_ATTRIBUTE
                readAttributeValue(NAME_ATTRIBUTE) != null -> NAME_ATTRIBUTE
                else -> null
            }
        val location = toSourceLocation(document, locationAttribute)
        return SourceGraphParameter(
            name = name,
            valueExpression = valueExpression,
            provenance = Provenance(physicalLocation = location, logicalLocation = location),
        )
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
