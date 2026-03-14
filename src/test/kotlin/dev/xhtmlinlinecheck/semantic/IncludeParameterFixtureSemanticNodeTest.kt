package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import dev.xhtmlinlinecheck.testing.FixtureScenarios
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IncludeParameterFixtureSemanticNodeTest {
    @Test
    fun `support fixture keeps include param visible on expanded semantic nodes but not after inline rewrite`() {
        val semanticModels = semanticModelsFor("support/include-param-scope")
        val oldPanelNode = semanticModels.oldRoot.semanticNodes.single { it.explicitIdAttribute?.rawValue == "panel" }
        val newPanelNode = semanticModels.newRoot.semanticNodes.single { it.explicitIdAttribute?.rawValue == "panel" }

        assertThat(oldPanelNode.renderedAttribute)
            .extracting("rawValue", "normalizedTemplate")
            .containsExactly("#{label}", oldPanelNode.renderedAttribute!!.normalizedTemplate)
        assertThat(oldPanelNode.renderedAttribute!!.normalizedTemplate!!.render()).isEqualTo("#{binding#1}")
        assertThat(oldPanelNode.renderedAttribute!!.bindingReferences.map { it.binding.origin.descriptor })
            .containsExactly("ui:param name=label")
        assertThat(oldPanelNode.provenance.physicalLocation.document.displayPath)
            .isEqualTo("fixtures/support/include-param-scope/old/fragments/panel.xhtml")
        assertThat(oldPanelNode.provenance.logicalLocation.document.displayPath)
            .isEqualTo("fixtures/support/include-param-scope/old/root.xhtml")

        assertThat(newPanelNode.renderedAttribute)
            .extracting("rawValue", "normalizedTemplate")
            .containsExactly("#{label}", newPanelNode.renderedAttribute!!.normalizedTemplate)
        assertThat(newPanelNode.renderedAttribute!!.normalizedTemplate!!.render()).isEqualTo("#{global(label)}")
        assertThat(newPanelNode.renderedAttribute!!.bindingReferences).isEmpty()
        assertThat(newPanelNode.renderedAttribute!!.globalReferences.map { it.writtenName }).containsExactly("label")
        assertThat(newPanelNode.provenance.logicalLocation.document.displayPath)
            .isEqualTo("fixtures/support/include-param-scope/new/root.xhtml")
    }

    private fun semanticModelsFor(relativePath: String): SemanticModels {
        val scenario = FixtureScenarios.scenario(relativePath)
        return SemanticAnalyzer.scaffold().analyze(
            XhtmlSyntaxParser.scaffold().parse(
                SourceLoader.scaffold().load(
                    AnalysisRequest(
                        oldRoot = FixtureScenarios.repositoryRoot.relativize(scenario.oldRoot),
                        newRoot = FixtureScenarios.repositoryRoot.relativize(scenario.newRoot),
                        baseOld = FixtureScenarios.repositoryRoot,
                        baseNew = FixtureScenarios.repositoryRoot,
                    ),
                ),
            ),
        )
    }
}
