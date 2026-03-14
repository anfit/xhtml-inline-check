package dev.xhtmlinlinecheck.testing

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.syntax.LogicalElementNode
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SimpleIncludeFixturePipelineTest {
    @Test
    fun `simple include fixture covers loader and parser expansion together`() {
        val scenario = FixtureScenarios.scenario("support/include-expansion")

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = FixtureScenarios.repositoryRoot.relativize(scenario.oldRoot),
                newRoot = FixtureScenarios.repositoryRoot.relativize(scenario.newRoot),
                baseOld = FixtureScenarios.repositoryRoot,
                baseNew = FixtureScenarios.repositoryRoot,
            ),
        )

        assertThat(loadedSources.oldRoot.document.displayPath).isEqualTo("fixtures/support/include-expansion/old/root.xhtml")
        assertThat(loadedSources.oldRoot.document.rootDirectory).isEqualTo(FixtureScenarios.repositoryRoot)
        assertThat(loadedSources.oldRoot.contents).contains("<ui:include src=\"/fragments/panel.xhtml\">")

        val includeEdge = loadedSources.oldRoot.sourceGraphFile.includeEdges.single()

        assertThat(includeEdge.includeSite.render()).startsWith("fixtures/support/include-expansion/old/root.xhtml:2:")
        assertThat(includeEdge.sourcePath).isEqualTo("/fragments/panel.xhtml")
        assertThat(includeEdge.includedDocument).isNotNull()
        assertThat(includeEdge.includedDocument!!.displayPath)
            .isEqualTo("fixtures/support/include-expansion/old/fragments/panel.xhtml")
        assertThat(includeEdge.includedFile).isNotNull()
        assertThat(includeEdge.includedFile!!.contents).contains("<h:outputText")
        assertThat(includeEdge.parameters.map { it.name }).containsExactly("label")
        assertThat(includeEdge.parameters.single().valueExpression).isEqualTo("#{bean.label}")

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(loadedSources)
        val includeNode = parsedTrees.oldRoot.rootNode!!.children.single() as LogicalIncludeNode
        val expandedRoot = includeNode.children.single() as LogicalElementNode
        val output = expandedRoot.children.single() as LogicalElementNode

        assertThat(includeNode.sourcePath).isEqualTo("/fragments/panel.xhtml")
        assertThat(includeNode.parameters.map { it.name }).containsExactly("label")
        assertThat(includeNode.provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-expansion/old/root.xhtml:2:")
        assertThat(expandedRoot.name.localName).isEqualTo("fragment")
        assertThat(expandedRoot.provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-expansion/old/fragments/panel.xhtml:1:")
        assertThat(expandedRoot.provenance.includeStack.single().includeSite.render())
            .startsWith("fixtures/support/include-expansion/old/root.xhtml:2:")
        assertThat(output.name.localName).isEqualTo("outputText")
        assertThat(output.attributes.single { it.name.localName == "value" }.value).isEqualTo("#{label}")
    }
}
