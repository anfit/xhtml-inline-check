package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceGraphEdge
import dev.xhtmlinlinecheck.domain.SourceGraphParameter
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceGraphStack
import dev.xhtmlinlinecheck.xml.NamespaceAwareXml
import dev.xhtmlinlinecheck.xml.readAttributeValue
import dev.xhtmlinlinecheck.xml.toSourceLocation
import dev.xhtmlinlinecheck.xml.useAndClose
import javax.xml.stream.XMLStreamConstants

internal object SourceGraphIncludeDiscovery {
    private const val FACELETS_NAMESPACE = "http://xmlns.jcp.org/jsf/facelets"
    private const val INCLUDE_LOCAL_NAME = "include"
    private const val PARAM_LOCAL_NAME = "param"
    private const val NAME_ATTRIBUTE = "name"
    private const val SRC_ATTRIBUTE = "src"
    private const val VALUE_ATTRIBUTE = "value"

    fun discover(
        document: SourceDocument,
        contents: String,
        stackBefore: SourceGraphStack = SourceGraphStack.root(),
    ): List<SourceGraphEdge> {
        val reader = NamespaceAwareXml.newReader(document.displayPath, contents)

        reader.useAndClose {
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
                    stackBefore = stackBefore,
                )
            }
            return edges
        }
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
}
