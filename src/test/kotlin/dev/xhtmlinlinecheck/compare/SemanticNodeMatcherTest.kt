package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.semantic.SemanticAnalyzer
import dev.xhtmlinlinecheck.semantic.SemanticModels
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SemanticNodeMatcherTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `include inlining does not distort explicit id matching when wrapper nodes differ`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:include src="/fragments/content.xhtml" />
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/content.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                         xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup id="panel">
                <h:outputText id="value" value="#{bean.label}" />
              </h:panelGroup>
            </ui:fragment>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup id="panel">
                <h:outputText id="value" value="#{bean.label}" />
              </h:panelGroup>
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val result = SemanticNodeMatcher.matchStructuralCandidates(
            oldNodes = semanticModels.oldRoot.semanticNodes,
            newNodes = semanticModels.newRoot.semanticNodes,
        )

        assertThat(semanticModels.oldRoot.semanticNodes.filter { it.isTransparentStructureWrapper })
            .extracting("nodeName")
            .containsExactly("ui:composition", "ui:include", "ui:fragment")
        assertThat(semanticModels.newRoot.semanticNodes.filter { it.isTransparentStructureWrapper })
            .extracting("nodeName")
            .containsExactly("ui:composition")
        assertThat(result.matches)
            .extracting("reason", "oldNodeId.value", "newNodeId.value")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(SemanticNodeMatchReason.EXPLICIT_ID, "node:/0/0/0/0", "node:/0"),
                org.assertj.core.groups.Tuple.tuple(SemanticNodeMatchReason.EXPLICIT_ID, "node:/0/0/0/0/0", "node:/0/0"),
            )
        assertThat(result.unmatchedOldNodeIds).isEmpty()
        assertThat(result.unmatchedNewNodeIds).isEmpty()
    }

    @Test
    fun `wrapper only structure changes fall back to semantic signatures instead of creating false drift`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:fragment>
                <h:panelGroup>
                  <h:outputText value="#{bean.label}" />
                </h:panelGroup>
              </ui:fragment>
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup>
                <h:outputText value="#{bean.label}" />
              </h:panelGroup>
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val result = SemanticNodeMatcher.matchStructuralCandidates(
            oldNodes = semanticModels.oldRoot.semanticNodes,
            newNodes = semanticModels.newRoot.semanticNodes,
        )

        assertThat(result.matches)
            .extracting("reason", "oldNodeId.value", "newNodeId.value")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(SemanticNodeMatchReason.STRUCTURAL_SIGNATURE, "node:/0/0", "node:/0"),
                org.assertj.core.groups.Tuple.tuple(SemanticNodeMatchReason.STRUCTURAL_SIGNATURE, "node:/0/0/0", "node:/0/0"),
            )
        assertThat(result.unmatchedOldNodeIds).isEmpty()
        assertThat(result.unmatchedNewNodeIds).isEmpty()
    }

    private fun semanticModelsFor(oldRoot: Path, newRoot: Path, baseDir: Path): SemanticModels =
        SemanticAnalyzer.scaffold().analyze(
            XhtmlSyntaxParser.scaffold().parse(
                SourceLoader.scaffold().load(
                    AnalysisRequest(
                        oldRoot = baseDir.relativize(oldRoot),
                        newRoot = baseDir.relativize(newRoot),
                        baseOld = baseDir,
                        baseNew = baseDir,
                    ),
                ),
            ),
        )
}
