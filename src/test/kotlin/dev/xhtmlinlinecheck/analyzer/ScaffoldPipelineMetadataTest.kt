package dev.xhtmlinlinecheck.analyzer

import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.loader.SourceLoadException
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
import dev.xhtmlinlinecheck.semantic.SemanticAnalyzer
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ScaffoldPipelineMetadataTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `shared source documents and provenance survive scaffold pipeline stages`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val request = AnalysisRequest(
            oldRoot = tempDir.relativize(oldRoot),
            newRoot = tempDir.relativize(newRoot),
            baseOld = tempDir,
            baseNew = tempDir,
        )

        val loadedSources = SourceLoader.scaffold().load(request)
        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(loadedSources)
        val semanticModels = SemanticAnalyzer.scaffold().analyze(parsedTrees)

        assertThat(loadedSources.oldRoot.document.side).isEqualTo(AnalysisSide.OLD)
        assertThat(loadedSources.newRoot.document.side).isEqualTo(AnalysisSide.NEW)
        assertThat(parsedTrees.oldRoot.document).isEqualTo(loadedSources.oldRoot.document)
        assertThat(parsedTrees.newRoot.provenance).isEqualTo(loadedSources.newRoot.provenance)
        assertThat(parsedTrees.oldRoot.sourceGraphFile).isEqualTo(loadedSources.oldRoot.sourceGraphFile)
        assertThat(parsedTrees.oldRoot.syntaxTree.root).isNotNull()
        assertThat(semanticModels.newRoot.syntaxTree).isEqualTo(parsedTrees.newRoot.syntaxTree)
        assertThat(semanticModels.oldRoot.tagRules).isSameAs(TagRuleRegistry.builtIns())
        assertThat(loadedSources.oldRoot.contents).contains("<ui:composition")
        assertThat(loadedSources.newRoot.contents).contains("<ui:composition")
        assertThat(loadedSources.oldRoot.sourceGraphFile.stack.steps).isEmpty()
        assertThat(semanticModels.oldRoot.document.displayPath).isEqualTo("legacy/root.xhtml")
        assertThat(semanticModels.newRoot.provenance.logicalLocation.render()).isEqualTo("refactored/root.xhtml")
    }

    @Test
    fun `loader resolves roots against per-side base directories and preserves anchored display paths`() {
        val tree = TemporaryProjectTree(tempDir)
        tree.write(
            "workspace/legacy/views/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">legacy</ui:composition>
            """,
        )
        tree.write(
            "workspace/refactored/pages/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">refactored</ui:composition>
            """,
        )
        val oldBase = tree.path("workspace/legacy")
        val newBase = tree.path("workspace/refactored")
        val request = AnalysisRequest(
            oldRoot = Path.of("views", ".", "section", "..", "root.xhtml"),
            newRoot = Path.of("pages", ".", "root.xhtml"),
            baseOld = oldBase,
            baseNew = newBase,
        )

        val loadedSources = SourceLoader.scaffold().load(request)

        assertThat(loadedSources.oldRoot.document.absolutePath)
            .isEqualTo(oldBase.toAbsolutePath().normalize().resolve("views/root.xhtml"))
        assertThat(loadedSources.newRoot.document.absolutePath)
            .isEqualTo(newBase.toAbsolutePath().normalize().resolve("pages/root.xhtml"))
        assertThat(loadedSources.oldRoot.document.displayPath).isEqualTo("views/root.xhtml")
        assertThat(loadedSources.newRoot.document.displayPath).isEqualTo("pages/root.xhtml")
        assertThat(loadedSources.oldRoot.contents).contains("legacy")
        assertThat(loadedSources.newRoot.contents).contains("refactored")
        assertThat(loadedSources.oldRoot.provenance.logicalLocation.render()).isEqualTo("views/root.xhtml")
        assertThat(loadedSources.newRoot.provenance.physicalLocation.render()).isEqualTo("pages/root.xhtml")
        assertThat(loadedSources.oldRoot.sourceGraphFile.provenance).isEqualTo(loadedSources.oldRoot.provenance)
        assertThat(loadedSources.newRoot.sourceGraphFile.document.displayPath).isEqualTo("pages/root.xhtml")
    }

    @Test
    fun `loader reports missing files with normalized display paths`() {
        val tree = TemporaryProjectTree(tempDir)
        tree.write(
            "workspace/refactored/pages/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val oldBase = tree.path("workspace/legacy")
        val newBase = tree.path("workspace/refactored")

        assertThatThrownBy {
            SourceLoader.scaffold().load(
                AnalysisRequest(
                    oldRoot = Path.of("views", ".", "missing.xhtml"),
                    newRoot = Path.of("pages", "root.xhtml"),
                    baseOld = oldBase,
                    baseNew = newBase,
                ),
            )
        }
            .isInstanceOf(SourceLoadException::class.java)
            .hasMessageContaining("Missing source file for old: views/missing.xhtml")
            .hasMessageContaining(oldBase.toAbsolutePath().normalize().resolve("views/missing.xhtml").toString().replace("\\", "/"))
            .satisfies { exception ->
                val sourceLoadException = exception as SourceLoadException
                assertThat(sourceLoadException.document.displayPath).isEqualTo("views/missing.xhtml")
                assertThat(sourceLoadException.document.absolutePath)
                    .isEqualTo(oldBase.toAbsolutePath().normalize().resolve("views/missing.xhtml"))
            }
    }
}
