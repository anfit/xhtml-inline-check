package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SourceLoaderIncludeDiscoveryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `loader discovers static facelets includes in the source graph`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" xmlns:other="urn:not-facelets">
              <ui:include src="/fragments/table.xhtml" />
              <other:include src="/ignored.xhtml" />
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )

        val edge = loadedSources.oldRoot.sourceGraphFile.includeEdges.single()

        assertThat(edge.sourcePath).isEqualTo("/fragments/table.xhtml")
        assertThat(edge.isResolved).isTrue()
        assertThat(edge.includedDocument).isNotNull()
        assertThat(edge.includedDocument!!.displayPath).isEqualTo("fragments/table.xhtml")
        assertThat(edge.includedFile).isNotNull()
        assertThat(edge.includedFile!!.contents).contains("<ui:fragment")
        assertThat(edge.includeSite.document).isEqualTo(loadedSources.oldRoot.document)
        assertThat(edge.includeSite.attributeName).isEqualTo("src")
        assertThat(edge.includeSite.span?.start?.line).isEqualTo(2)
        assertThat(edge.includeSite.span?.start?.column).isGreaterThan(0)
        assertThat(edge.parameters).isEmpty()
        assertThat(loadedSources.newRoot.sourceGraphFile.includeEdges).isEmpty()
    }

    @Test
    fun `loader resolves relative include src against the including file and preserves the shared base directory`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/views/orders/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="../fragments/table.xhtml" />
            </ui:composition>
            """,
        )
        tree.write(
            "legacy/views/fragments/table.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )

        val includedDocument = loadedSources.oldRoot.sourceGraphFile.includeEdges.single().includedDocument

        assertThat(includedDocument).isNotNull()
        assertThat(includedDocument!!.displayPath).isEqualTo("legacy/views/fragments/table.xhtml")
        assertThat(includedDocument.rootDirectory).isEqualTo(loadedSources.oldRoot.document.rootDirectory)
        assertThat(loadedSources.oldRoot.sourceGraphFile.includeEdges.single().includedFile).isNotNull()
    }

    @Test
    fun `loader preserves dynamic include src expressions for later resolution tasks`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <facelets:view xmlns:facelets="http://xmlns.jcp.org/jsf/facelets">
              <facelets:include src="#{bean.fragmentPath}" />
            </facelets:view>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )

        val edge = loadedSources.oldRoot.sourceGraphFile.includeEdges.single()

        assertThat(edge.sourcePath).isEqualTo("#{bean.fragmentPath}")
        assertThat(edge.includeSite.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(edge.includeSite.render()).endsWith(" @src")
        assertThat(edge.includedFile).isNull()
    }

    @Test
    fun `loader extracts direct ui params from include sites with value provenance`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/table.xhtml">
                <ui:param name="row" value="#{bean.row}" />
                <ui:param name="mode" value="compact" />
              </ui:include>
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/table.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )

        val parameters = loadedSources.oldRoot.sourceGraphFile.includeEdges.single().parameters

        assertThat(parameters).hasSize(2)
        assertThat(parameters.map { it.name }).containsExactly("row", "mode")
        assertThat(parameters.map { it.valueExpression }).containsExactly("#{bean.row}", "compact")
        assertThat(parameters.map { it.provenance.physicalLocation.render() }.all { it.startsWith("legacy/root.xhtml:") })
            .isTrue()
        assertThat(parameters.map { it.provenance.physicalLocation.attributeName })
            .containsOnly("value")
    }

    @Test
    fun `loader ignores nested or malformed ui params outside direct include parameter slots`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/table.xhtml">
                <ui:fragment>
                  <ui:param name="nested" value="#{bean.nested}" />
                </ui:fragment>
                <ui:param value="#{bean.missingName}" />
                <ui:param name="direct" />
              </ui:include>
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/table.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )

        val parameter = loadedSources.oldRoot.sourceGraphFile.includeEdges.single().parameters.single()

        assertThat(parameter.name).isEqualTo("direct")
        assertThat(parameter.valueExpression).isNull()
        assertThat(parameter.provenance.physicalLocation.render()).startsWith("legacy/root.xhtml:")
        assertThat(parameter.provenance.physicalLocation.attributeName).isEqualTo("name")
    }
}
