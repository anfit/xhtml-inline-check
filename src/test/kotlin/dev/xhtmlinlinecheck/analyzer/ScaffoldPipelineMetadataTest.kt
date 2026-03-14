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
}
