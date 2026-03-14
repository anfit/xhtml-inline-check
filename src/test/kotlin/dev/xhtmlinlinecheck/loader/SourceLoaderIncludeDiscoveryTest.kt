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
        assertThat(edge.isResolved).isFalse()
        assertThat(edge.includeSite.document).isEqualTo(loadedSources.oldRoot.document)
        assertThat(edge.includeSite.attributeName).isEqualTo("src")
        assertThat(edge.includeSite.span?.start?.line).isEqualTo(2)
        assertThat(edge.includeSite.span?.start?.column).isGreaterThan(0)
        assertThat(loadedSources.newRoot.sourceGraphFile.includeEdges).isEmpty()
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
    }
}
