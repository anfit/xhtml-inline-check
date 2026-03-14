package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.domain.AttributeLocationPrecision
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

        val root = parsedTrees.oldRoot.syntaxTree.root!!
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

        val output = parsedTrees.oldRoot.syntaxTree.root!!.children.single() as LogicalElementNode
        val valueAttribute = output.attributes.single { it.name.localName == "value" }

        assertThat(output.location.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(output.provenance.logicalLocation.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(valueAttribute.value).isEqualTo("#{bean.message}")
        assertThat(valueAttribute.location.render()).contains("@value (element fallback)")
        assertThat(valueAttribute.location.attributeName).isEqualTo("value")
        assertThat(valueAttribute.location.attributeLocationPrecision).isEqualTo(AttributeLocationPrecision.ELEMENT_FALLBACK)
    }

    @Test
    fun `parser preserves explicit syntax-node locations for element text and include nodes`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup id="wrapper">hello<ui:include src="/fragments/content.xhtml" /></h:panelGroup>
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/content.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <h:outputText xmlns:h="http://xmlns.jcp.org/jsf/html" value="included" />
            </ui:fragment>
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

        val wrapper = parsedTrees.oldRoot.syntaxTree.root!!.children.single() as LogicalElementNode
        val text = wrapper.children[0] as LogicalTextNode
        val include = wrapper.children[1] as LogicalIncludeNode

        assertThat(wrapper.location.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(text.location.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(include.location.render()).isEqualTo(include.includeSite.render())
        assertThat((include.children.single() as LogicalElementNode).location.render()).startsWith("fragments/content.xhtml:1:")
    }

    @Test
    fun `parser preserves element names namespaces attributes and child order for downstream semantic work`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition
                xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                xmlns:h="http://xmlns.jcp.org/jsf/html"
                xmlns:p="http://primefaces.org/ui">
              <h:panelGroup id="wrapper" p:widgetVar="ordersWidget">
                <h:outputText value="first" />
                <ui:fragment>
                  <h:outputText value="second" />
                </ui:fragment>
                <h:outputText value="third" />
              </h:panelGroup>
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

        val wrapper = parsedTrees.oldRoot.syntaxTree.root!!.children.single() as LogicalElementNode
        val fragment = wrapper.children[1] as LogicalElementNode

        assertThat(wrapper.name)
            .isEqualTo(
                LogicalName(
                    localName = "panelGroup",
                    namespaceUri = "http://xmlns.jcp.org/jsf/html",
                    prefix = "h",
                ),
            )
        assertThat(wrapper.attributes.map { it.name to it.value })
            .containsExactly(
                LogicalName(localName = "id") to "wrapper",
                LogicalName(localName = "widgetVar", namespaceUri = "http://primefaces.org/ui", prefix = "p") to "ordersWidget",
            )
        assertThat(wrapper.children.map { node ->
            when (node) {
                is LogicalElementNode -> node.name.localName
                is LogicalIncludeNode -> "include"
                is LogicalTextNode -> "#text"
            }
        }).containsExactly("outputText", "fragment", "outputText")
        assertThat(fragment.name)
            .isEqualTo(
                LogicalName(
                    localName = "fragment",
                    namespaceUri = "http://xmlns.jcp.org/jsf/facelets",
                    prefix = "ui",
                ),
            )
        assertThat((fragment.children.single() as LogicalElementNode).attributes.single().value).isEqualTo("second")
    }
}
