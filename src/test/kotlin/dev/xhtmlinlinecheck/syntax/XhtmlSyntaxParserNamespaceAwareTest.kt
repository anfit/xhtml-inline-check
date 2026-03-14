package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class XhtmlSyntaxParserNamespaceAwareTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `parser preserves namespace declarations and qualified names for later rule work`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition
                xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                xmlns:h="http://xmlns.jcp.org/jsf/html"
                xmlns:p="http://primefaces.org/ui">
              <h:panelGroup id="panel" p:widgetVar="ordersWidget" />
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(
            SourceLoader.scaffold().load(
                AnalysisRequest(
                    oldRoot = tempDir.relativize(oldRoot),
                    newRoot = tempDir.relativize(newRoot),
                    baseOld = tempDir,
                    baseNew = tempDir,
                ),
            ),
        )

        val root = parsedTrees.oldRoot.rootNode!!
        val panel = root.children.single() as LogicalElementNode

        assertThat(root.name.prefix).isEqualTo("ui")
        assertThat(root.name.namespaceUri).isEqualTo("http://xmlns.jcp.org/jsf/facelets")
        assertThat(root.namespaceBindings)
            .containsExactlyInAnyOrder(
                LogicalNamespaceBinding(prefix = "ui", namespaceUri = "http://xmlns.jcp.org/jsf/facelets"),
                LogicalNamespaceBinding(prefix = "h", namespaceUri = "http://xmlns.jcp.org/jsf/html"),
                LogicalNamespaceBinding(prefix = "p", namespaceUri = "http://primefaces.org/ui"),
            )

        assertThat(panel.name.localName).isEqualTo("panelGroup")
        assertThat(panel.name.prefix).isEqualTo("h")
        assertThat(panel.name.namespaceUri).isEqualTo("http://xmlns.jcp.org/jsf/html")
        assertThat(panel.attributes.map { it.name })
            .containsExactlyInAnyOrder(
                LogicalName(localName = "id"),
                LogicalName(localName = "widgetVar", namespaceUri = "http://primefaces.org/ui", prefix = "p"),
            )
    }

    @Test
    fun `parser keeps attribute extraction and source locations on namespaced elements`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:outputText id="message" value="#{bean.message}" />
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(
            SourceLoader.scaffold().load(
                AnalysisRequest(
                    oldRoot = tempDir.relativize(oldRoot),
                    newRoot = tempDir.relativize(newRoot),
                    baseOld = tempDir,
                    baseNew = tempDir,
                ),
            ),
        )

        val output = parsedTrees.oldRoot.rootNode!!.children.single() as LogicalElementNode
        val valueAttribute = output.attributes.single { it.name.localName == "value" }

        assertThat(output.provenance.logicalLocation.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(valueAttribute.value).isEqualTo("#{bean.message}")
        assertThat(valueAttribute.location.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(valueAttribute.location.attributeName).isEqualTo("value")
    }
}
