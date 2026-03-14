package dev.xhtmlinlinecheck.xml

import dev.xhtmlinlinecheck.domain.AttributeLocationPrecision
import com.ctc.wstx.stax.WstxInputFactory
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.domain.SourcePosition
import dev.xhtmlinlinecheck.domain.SourceSpan
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader

internal object NamespaceAwareXml {
    private val inputFactory: XMLInputFactory =
        WstxInputFactory().apply {
            setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
            setProperty(XMLInputFactory.IS_COALESCING, true)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }

    fun newReader(systemId: String, contents: String): XMLStreamReader =
        inputFactory.createXMLStreamReader(systemId, StringReader(contents))
}

internal fun XMLStreamReader.readAttributeValue(attributeName: String): String? {
    for (index in 0 until attributeCount) {
        if (getAttributeLocalName(index) == attributeName) {
            return getAttributeValue(index)
        }
    }
    return null
}

internal fun XMLStreamReader.toSourceLocation(
    document: SourceDocument,
    attributeName: String? = null,
    attributeLocationPrecision: AttributeLocationPrecision? =
        attributeName?.let { AttributeLocationPrecision.ELEMENT_FALLBACK },
): SourceLocation =
    SourceLocation(
        document = document,
        span =
            SourceSpan(
                start =
                    SourcePosition(
                        line = location.lineNumber.coerceAtLeast(1),
                        column = location.columnNumber.coerceAtLeast(1),
                    ),
            ),
        attributeName = attributeName,
        attributeLocationPrecision = attributeLocationPrecision,
    )

internal inline fun <T : AutoCloseable?, R> T.useAndClose(block: (T) -> R): R =
    try {
        block(this)
    } finally {
        this?.close()
    }
