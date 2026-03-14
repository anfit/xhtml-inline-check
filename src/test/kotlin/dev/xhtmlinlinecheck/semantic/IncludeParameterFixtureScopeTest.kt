package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.domain.BindingKind
import dev.xhtmlinlinecheck.syntax.LogicalElementNode
import dev.xhtmlinlinecheck.syntax.LogicalNodePath
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree
import dev.xhtmlinlinecheck.syntax.walkDepthFirstWithPath
import dev.xhtmlinlinecheck.testing.FixtureScenarios
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IncludeParameterFixtureScopeTest {
    @Test
    fun `support fixture keeps ui param visible to expanded descendants with include site provenance`() {
        val semanticModels = semanticModelsFor("support/include-param-scope")
        val oldScope = semanticModels.oldRoot.scopeModel
        val scopedOutputPath = findPathById(semanticModels.oldRoot.syntaxTree, "scopedOutput")
        val panelPath = findPathById(semanticModels.oldRoot.syntaxTree, "panel")
        val outsideOutputPath = findPathById(semanticModels.oldRoot.syntaxTree, "outsideOutput")

        val bindingAtDescendant = oldScope.resolve("label", scopedOutputPath)
        val bindingAtPanel = oldScope.resolve("label", panelPath, ScopeLookupPosition.DESCENDANT)

        assertThat(bindingAtDescendant)
            .extracting("kind", "origin.descriptor", "valueExpression")
            .containsExactly(BindingKind.UI_PARAM, "ui:param name=label", "#{bean.label}")
        assertThat(bindingAtPanel)
            .extracting("kind", "origin.descriptor", "valueExpression")
            .containsExactly(BindingKind.UI_PARAM, "ui:param name=label", "#{bean.label}")
        assertThat(bindingAtDescendant!!.provenance.logicalLocation.document.displayPath)
            .isEqualTo("fixtures/support/include-param-scope/old/root.xhtml")
        assertThat(bindingAtDescendant.provenance.logicalLocation.attributeName).isEqualTo("value")
        assertThat(bindingAtDescendant.provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-param-scope/old/root.xhtml:3:")
        assertThat(oldScope.resolve("label", outsideOutputPath)).isNull()
    }

    @Test
    fun `support fixture preserves regression where inline rewrite looks equivalent but loses include param scope`() {
        val semanticModels = semanticModelsFor("support/include-param-scope")
        val oldScopedOutputPath = findPathById(semanticModels.oldRoot.syntaxTree, "scopedOutput")
        val newScopedOutputPath = findPathById(semanticModels.newRoot.syntaxTree, "scopedOutput")
        val oldScopedOutput = nodeAt(semanticModels.oldRoot.syntaxTree, oldScopedOutputPath)
        val newScopedOutput = nodeAt(semanticModels.newRoot.syntaxTree, newScopedOutputPath)

        assertThat(oldScopedOutput.attributes.single { it.name.localName == "value" }.value).isEqualTo("#{label}")
        assertThat(newScopedOutput.attributes.single { it.name.localName == "value" }.value).isEqualTo("#{label}")
        assertThat(semanticModels.oldRoot.scopeModel.resolve("label", oldScopedOutputPath))
            .extracting("kind", "origin.descriptor")
            .containsExactly(BindingKind.UI_PARAM, "ui:param name=label")
        assertThat(semanticModels.newRoot.scopeModel.resolve("label", newScopedOutputPath)).isNull()
        assertThat(semanticModels.oldRoot.scopeModel.visibleBindingsAt(oldScopedOutputPath).map { it.origin.descriptor })
            .contains("ui:param name=label")
        assertThat(semanticModels.newRoot.scopeModel.visibleBindingsAt(newScopedOutputPath).map { it.origin.descriptor })
            .doesNotContain("ui:param name=label")
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

    private fun findPathById(
        syntaxTree: XhtmlSyntaxTree,
        id: String,
    ): LogicalNodePath {
        var foundPath: LogicalNodePath? = null
        syntaxTree.walkDepthFirstWithPath { path, node ->
            if (node is LogicalElementNode) {
                val idAttribute = node.attributes.firstOrNull { it.name.localName == "id" }
                if (idAttribute?.value == id) {
                    foundPath = path
                }
            }
        }
        return requireNotNull(foundPath) { "missing node with id=$id" }
    }

    private fun nodeAt(
        syntaxTree: XhtmlSyntaxTree,
        path: LogicalNodePath,
    ): LogicalElementNode {
        var foundNode: LogicalElementNode? = null
        syntaxTree.walkDepthFirstWithPath { candidatePath, node ->
            if (candidatePath == path && node is LogicalElementNode) {
                foundNode = node
            }
        }
        return requireNotNull(foundNode) { "missing element at path=${path.segments}" }
    }
}
