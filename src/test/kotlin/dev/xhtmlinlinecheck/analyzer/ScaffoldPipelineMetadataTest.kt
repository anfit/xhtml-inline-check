package dev.xhtmlinlinecheck.analyzer

import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.semantic.SemanticAnalyzer
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ScaffoldPipelineMetadataTest {
    @Test
    fun `shared source documents and provenance survive scaffold pipeline stages`() {
        val request = AnalysisRequest(
            oldRoot = Path.of("legacy", ".", "root.xhtml"),
            newRoot = Path.of("refactored", ".", "root.xhtml"),
        )

        val loadedSources = SourceLoader.scaffold().load(request)
        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(loadedSources)
        val semanticModels = SemanticAnalyzer.scaffold().analyze(parsedTrees)

        assertThat(loadedSources.oldRoot.document.side).isEqualTo(AnalysisSide.OLD)
        assertThat(loadedSources.newRoot.document.side).isEqualTo(AnalysisSide.NEW)
        assertThat(parsedTrees.oldRoot.document).isEqualTo(loadedSources.oldRoot.document)
        assertThat(parsedTrees.newRoot.provenance).isEqualTo(loadedSources.newRoot.provenance)
        assertThat(semanticModels.oldRoot.document.displayPath).isEqualTo("legacy/root.xhtml")
        assertThat(semanticModels.newRoot.provenance.logicalLocation.render()).isEqualTo("refactored/root.xhtml")
    }

    @Test
    fun `loader resolves roots against per-side base directories and preserves anchored display paths`() {
        val request = AnalysisRequest(
            oldRoot = Path.of("views", "root.xhtml"),
            newRoot = Path.of("pages", "root.xhtml"),
            baseOld = Path.of("fixtures", "legacy"),
            baseNew = Path.of("fixtures", "refactored"),
        )

        val loadedSources = SourceLoader.scaffold().load(request)

        assertThat(loadedSources.oldRoot.document.absolutePath)
            .isEqualTo(Path.of("fixtures", "legacy").toAbsolutePath().normalize().resolve("views/root.xhtml"))
        assertThat(loadedSources.newRoot.document.absolutePath)
            .isEqualTo(Path.of("fixtures", "refactored").toAbsolutePath().normalize().resolve("pages/root.xhtml"))
        assertThat(loadedSources.oldRoot.document.displayPath).isEqualTo("views/root.xhtml")
        assertThat(loadedSources.newRoot.document.displayPath).isEqualTo("pages/root.xhtml")
        assertThat(loadedSources.oldRoot.provenance.logicalLocation.render()).isEqualTo("views/root.xhtml")
        assertThat(loadedSources.newRoot.provenance.physicalLocation.render()).isEqualTo("pages/root.xhtml")
    }
}
