package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.semantic.SemanticAnalyzer
import dev.xhtmlinlinecheck.semantic.SemanticModels
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
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

    @Test
    fun `structural signatures use normalized component target references instead of raw attribute spacing`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:commandButton update="msgs   panel" process="@this   @form" />
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:commandButton update="msgs panel" process="@this @form" />
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val result = SemanticNodeMatcher.matchStructuralCandidates(
            oldNodes = semanticModels.oldRoot.semanticNodes,
            newNodes = semanticModels.newRoot.semanticNodes,
        )

        val oldButton = semanticModels.oldRoot.semanticNodes.single { it.nodeName == "h:commandButton" }
        val newButton = semanticModels.newRoot.semanticNodes.single { it.nodeName == "h:commandButton" }

        assertThat(oldButton.componentTargetAttributes.map { it.render() })
            .containsExactly("update=msgs panel", "process=@this @form")
        assertThat(newButton.componentTargetAttributes.map { it.render() })
            .containsExactly("update=msgs panel", "process=@this @form")
        assertThat(oldButton.componentTargetAttributes.map { it.attribute.rawValue })
            .containsExactly("msgs   panel", "@this   @form")
        assertThat(newButton.componentTargetAttributes.map { it.attribute.rawValue })
            .containsExactly("msgs panel", "@this @form")
        assertThat(result.matches)
            .extracting("reason", "oldNodeId.value", "newNodeId.value")
            .containsExactly(
                Tuple.tuple(SemanticNodeMatchReason.STRUCTURAL_SIGNATURE, "node:/0", "node:/0"),
            )
        assertThat(result.unmatchedOldNodeIds).isEmpty()
        assertThat(result.unmatchedNewNodeIds).isEmpty()
    }

    @Test
    fun `structural matching relies on the combined structural context contract`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:form id="mainForm">
                <ui:repeat var="row" value="#{bean.rows}">
                  <h:outputText value="#{row.label}" />
                </ui:repeat>
              </h:form>
              <c:forEach var="row" items="#{bean.rows}">
                <h:outputText value="#{row.label}" />
              </c:forEach>
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <c:forEach var="row" items="#{bean.rows}">
                <h:outputText value="#{row.label}" />
              </c:forEach>
              <h:form id="mainForm">
                <ui:repeat var="row" value="#{bean.rows}">
                  <h:outputText value="#{row.label}" />
                </ui:repeat>
              </h:form>
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val oldOutputs = semanticModels.oldRoot.semanticNodes.filter { it.nodeName == "h:outputText" }
        val newOutputs = semanticModels.newRoot.semanticNodes.filter { it.nodeName == "h:outputText" }

        assertThat(
            oldOutputs.map { output ->
                Tuple.tuple(
                    output.structuralContext.formAncestry.map { it.nodeName },
                    output.structuralContext.namingContainerAncestry.map { it.nodeName },
                    output.structuralContext.iterationAncestry.map { it.nodeName },
                )
            },
        )
            .containsExactly(
                Tuple.tuple(
                    listOf("h:form"),
                    listOf("h:form"),
                    listOf("ui:repeat"),
                ),
                Tuple.tuple(
                    emptyList<String>(),
                    emptyList<String>(),
                    listOf("c:forEach"),
                ),
            )
        assertThat(
            newOutputs.map { output ->
                Tuple.tuple(
                    output.structuralContext.formAncestry.map { it.nodeName },
                    output.structuralContext.namingContainerAncestry.map { it.nodeName },
                    output.structuralContext.iterationAncestry.map { it.nodeName },
                )
            },
        )
            .containsExactly(
                Tuple.tuple(
                    emptyList<String>(),
                    emptyList<String>(),
                    listOf("c:forEach"),
                ),
                Tuple.tuple(
                    listOf("h:form"),
                    listOf("h:form"),
                    listOf("ui:repeat"),
                ),
            )

        val result = SemanticNodeMatcher.matchStructuralCandidates(
            oldNodes = semanticModels.oldRoot.semanticNodes,
            newNodes = semanticModels.newRoot.semanticNodes,
        )

        assertThat(result.matches)
            .extracting("reason", "oldNodeId.value", "newNodeId.value")
            .containsExactlyInAnyOrder(
                Tuple.tuple(SemanticNodeMatchReason.EXPLICIT_ID, "node:/0", "node:/1"),
                Tuple.tuple(SemanticNodeMatchReason.STRUCTURAL_SIGNATURE, "node:/1", "node:/0"),
                Tuple.tuple(SemanticNodeMatchReason.STRUCTURAL_SIGNATURE, "node:/1/0", "node:/0/0"),
                Tuple.tuple(SemanticNodeMatchReason.STRUCTURAL_SIGNATURE, "node:/0/0", "node:/1/0"),
                Tuple.tuple(SemanticNodeMatchReason.STRUCTURAL_SIGNATURE, "node:/0/0/0", "node:/1/0/0"),
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
