package dev.xhtmlinlinecheck.testing

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import dev.xhtmlinlinecheck.syntax.walkDepthFirstWithPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DummySampleFixtureSmokeTest {
    @Test
    fun `dummy report sample remains loadable and parseable as a realistic reference tree`() {
        val repositoryRoot = FixtureScenarios.repositoryRoot
        val oldRoot = repositoryRoot.resolve("dummy/old/report.xhtml")
        val newRoot = repositoryRoot.resolve("dummy/new/report-flattened.xhtml")

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = repositoryRoot.relativize(oldRoot),
                newRoot = repositoryRoot.relativize(newRoot),
                baseOld = repositoryRoot,
                baseNew = repositoryRoot,
            ),
        )

        assertThat(loadedSources.oldRoot.document.displayPath).isEqualTo("dummy/old/report.xhtml")
        assertThat(loadedSources.newRoot.document.displayPath).isEqualTo("dummy/new/report-flattened.xhtml")
        assertThat(loadedSources.oldRoot.contents).contains("Garden Report")
        assertThat(loadedSources.newRoot.contents).contains("Garden Report")
        assertThat(loadedSources.oldRoot.sourceGraphFile.includeEdges).allMatch { it.includeFailure == null }

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(loadedSources)
        var sawInclude = false
        parsedTrees.oldRoot.syntaxTree.walkDepthFirstWithPath { _, node ->
            if (node is LogicalIncludeNode) {
                sawInclude = true
            }
        }

        assertThat(parsedTrees.oldRoot.syntaxTree.root).isNotNull()
        assertThat(parsedTrees.newRoot.syntaxTree.root).isNotNull()
        assertThat(sawInclude).isTrue()
    }
}
