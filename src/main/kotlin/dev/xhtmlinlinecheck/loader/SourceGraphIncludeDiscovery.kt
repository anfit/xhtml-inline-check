package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceGraphEdge
import dev.xhtmlinlinecheck.domain.SourceGraphParameter
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceGraphStack
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
import dev.xhtmlinlinecheck.rules.isIncludeParameterTag
import dev.xhtmlinlinecheck.rules.isIncludeTag
import dev.xhtmlinlinecheck.syntax.LogicalName
import dev.xhtmlinlinecheck.xml.NamespaceAwareXml
import dev.xhtmlinlinecheck.xml.readAttributeValue
import dev.xhtmlinlinecheck.xml.toSourceLocation
import dev.xhtmlinlinecheck.xml.useAndClose
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

internal object SourceGraphIncludeDiscovery {
    private const val NAME_ATTRIBUTE = "name"
    private const val SRC_ATTRIBUTE = "src"
    private const val VALUE_ATTRIBUTE = "value"

    fun discover(
        document: SourceDocument,
        contents: String,
        stackBefore: SourceGraphStack = SourceGraphStack.root(),
        tagRules: TagRuleRegistry = TagRuleRegistry.builtIns(),
    ): List<SourceGraphEdge> {
        val xmlReader = NamespaceAwareXml.newReader(document.displayPath, contents)

        try {
            val edges = mutableListOf<SourceGraphEdge>()
            while (xmlReader.hasNext()) {
                if (xmlReader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue
                }
                if (!tagRules.resolve(xmlReader.toLogicalName()).isIncludeTag) {
                    continue
                }

                val sourcePath = xmlReader.readAttributeValue(SRC_ATTRIBUTE)
                edges += SourceGraphEdge.discovered(
                    includeSite = xmlReader.toSourceLocation(document, if (sourcePath != null) SRC_ATTRIBUTE else null),
                    sourcePath = sourcePath,
                    parameters = xmlReader.readIncludeParameters(document, tagRules),
                    stackBefore = stackBefore,
                )
            }
            return edges
        } finally {
            xmlReader.close()
        }
    }

    private fun XMLStreamReader.readIncludeParameters(
        document: SourceDocument,
        tagRules: TagRuleRegistry,
    ): List<SourceGraphParameter> {
        val parameters = mutableListOf<SourceGraphParameter>()
        var depth = 1
        while (hasNext() && depth > 0) {
            when (next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (depth == 1 && tagRules.resolve(toLogicalName()).isIncludeParameterTag) {
                        toIncludeParameter(document)?.let(parameters::add)
                    }
                    depth += 1
                }

                XMLStreamConstants.END_ELEMENT -> depth -= 1
            }
        }

        return parameters
    }

    private fun XMLStreamReader.toIncludeParameter(document: SourceDocument): SourceGraphParameter? {
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

    private fun XMLStreamReader.toLogicalName(): LogicalName =
        LogicalName(
            localName = localName,
            namespaceUri = namespaceURI,
            prefix = prefix,
        )
}
