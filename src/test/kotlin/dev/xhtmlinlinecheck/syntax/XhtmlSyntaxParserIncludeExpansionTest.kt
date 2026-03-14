package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class XhtmlSyntaxParserIncludeExpansionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `parser expands resolved includes into logical include nodes with included subtree provenance`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/table.xhtml">
                <ui:param name="row" value="#{bean.row}" />
              </ui:include>
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/table.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <h:outputText xmlns:h="http://xmlns.jcp.org/jsf/html" value="#{row.label}" />
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

        val includeNode = parsedTrees.oldRoot.rootNode!!.children.single() as LogicalIncludeNode
        val expandedRoot = includeNode.children.single() as LogicalElementNode
        val output = expandedRoot.children.single() as LogicalElementNode

        assertThat(includeNode.sourcePath).isEqualTo("/fragments/table.xhtml")
        assertThat(includeNode.parameters.map { it.name }).containsExactly("row")
        assertThat(includeNode.parameters.single().valueExpression).isEqualTo("#{bean.row}")
        assertThat(includeNode.provenance.logicalLocation.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(expandedRoot.name.localName).isEqualTo("fragment")
        assertThat(expandedRoot.provenance.logicalLocation.render()).startsWith("fragments/table.xhtml:1:")
        assertThat(expandedRoot.provenance.includeStack.single().includeSite.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(expandedRoot.provenance.includeStack.single().includedDocument.displayPath).isEqualTo("fragments/table.xhtml")
        assertThat(output.name.localName).isEqualTo("outputText")
        assertThat(output.attributes.single { it.name.localName == "value" }.value).isEqualTo("#{row.label}")
    }

    @Test
    fun `parser keeps unresolved includes as explicit logical boundaries without expanded children`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="#{bean.fragmentPath}" />
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

        val includeNode = parsedTrees.oldRoot.rootNode!!.children.single() as LogicalIncludeNode

        assertThat(includeNode.sourcePath).isEqualTo("#{bean.fragmentPath}")
        assertThat(includeNode.expandedFile).isNull()
        assertThat(includeNode.children).isEmpty()
        assertThat(includeNode.parameters).isEmpty()
    }

    @Test
    fun `parser preserves nested include stacks across recursive expansion`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/outer.xhtml" />
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/outer.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/inner.xhtml" />
            </ui:fragment>
            """,
        )
        tree.write(
            "fragments/inner.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <h:panelGroup xmlns:h="http://xmlns.jcp.org/jsf/html" id="target" />
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

        val outerInclude = parsedTrees.oldRoot.rootNode!!.children.single() as LogicalIncludeNode
        val outerRoot = outerInclude.children.single() as LogicalElementNode
        val innerInclude = outerRoot.children.single() as LogicalIncludeNode
        val innerRoot = innerInclude.children.single() as LogicalElementNode
        val panel = innerRoot.children.single() as LogicalElementNode

        assertThat(panel.name.localName).isEqualTo("panelGroup")
        assertThat(panel.provenance.includeStack).hasSize(2)
        assertThat(panel.provenance.includeStack.map { it.includedDocument.displayPath })
            .containsExactly("fragments/outer.xhtml", "fragments/inner.xhtml")
    }
}
