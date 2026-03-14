package dev.xhtmlinlinecheck.xml

import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.AttributeLocationPrecision
import dev.xhtmlinlinecheck.domain.SourceDocument
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.xml.stream.XMLStreamConstants

class NamespaceAwareXmlTest {
    private val document =
        SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = java.nio.file.Path.of("legacy", "root.xhtml"),
        )

    @Test
    fun `captures start-element source line and column from the XML reader`() {
        val reader =
            NamespaceAwareXml.newReader(
                document.displayPath,
                """
                <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" xmlns:h="http://xmlns.jcp.org/jsf/html">
                  <h:outputText value="#{bean.message}" />
                </ui:composition>
                """.trimIndent(),
            )

        try {
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue
                }

                val rootLocation = reader.toSourceLocation(document)
                assertThat(rootLocation.document).isEqualTo(document)
                assertThat(rootLocation.span?.start?.line).isEqualTo(1)
                assertThat(rootLocation.span?.start?.column).isGreaterThan(0)
                assertThat(rootLocation.attributeName).isNull()
                assertThat(rootLocation.attributeLocationPrecision).isNull()

                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                        continue
                    }

                    val childLocation = reader.toSourceLocation(document)
                    assertThat(childLocation.document).isEqualTo(document)
                    assertThat(childLocation.span?.start?.line).isEqualTo(2)
                    assertThat(childLocation.span?.start?.column).isGreaterThan(1)
                    assertThat(childLocation.render()).startsWith("legacy/root.xhtml:2:")
                    return
                }
            }
        } finally {
            reader.close()
        }

        error("expected to encounter a child start element")
    }

    @Test
    fun `captures attribute context as an explicit element-fallback location`() {
        val reader =
            NamespaceAwareXml.newReader(
                document.displayPath,
                """
                <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" xmlns:h="http://xmlns.jcp.org/jsf/html">
                  <h:outputText
                      id="message"
                      value="#{bean.message}" />
                </ui:composition>
                """.trimIndent(),
            )

        try {
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT || reader.localName != "outputText") {
                    continue
                }

                val elementLocation = reader.toSourceLocation(document)
                val valueLocation = reader.toSourceLocation(document, "value")

                assertThat(reader.readAttributeValue("value")).isEqualTo("#{bean.message}")
                assertThat(valueLocation.document).isEqualTo(document)
                assertThat(valueLocation.span).isEqualTo(elementLocation.span)
                assertThat(valueLocation.attributeName).isEqualTo("value")
                assertThat(valueLocation.attributeLocationPrecision)
                    .isEqualTo(AttributeLocationPrecision.ELEMENT_FALLBACK)
                assertThat(valueLocation.render()).contains("@value (element fallback)")
                return
            }
        } finally {
            reader.close()
        }

        error("expected to encounter h:outputText")
    }
}
