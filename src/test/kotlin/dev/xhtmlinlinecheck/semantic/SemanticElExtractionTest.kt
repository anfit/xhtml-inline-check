package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SemanticElExtractionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `registry-declared element attributes feed the EL pipeline and non-registry attributes stay out`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:repeat var="row" value="#{bean.rows}" rendered="#{bean.visible}">
                <c:if test="#{row.visible}">
                  <h:panelGroup id="panel" rendered="#{row.enabled}" title="#{bean.tooltip}" />
                </c:if>
              </ui:repeat>
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)

        assertThat(semanticModels.oldRoot.elOccurrences)
            .extracting("ownerTagName", "attributeName", "rawValue")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("ui:repeat", "value", "#{bean.rows}"),
                org.assertj.core.groups.Tuple.tuple("ui:repeat", "rendered", "#{bean.visible}"),
                org.assertj.core.groups.Tuple.tuple("c:if", "test", "#{row.visible}"),
                org.assertj.core.groups.Tuple.tuple("h:panelGroup", "rendered", "#{row.enabled}"),
            )
        assertThat(semanticModels.oldRoot.elOccurrences.map { it.attributeName }).doesNotContain("title")
        assertThat(semanticModels.oldRoot.elOccurrences).allSatisfy { occurrence ->
            assertThat(occurrence.isSupported).isTrue()
        }
    }

    @Test
    fun `include src and ui param value are extracted through the same semantic EL model`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/panel.xhtml">
                <ui:param name="label" value="#{bean.label}" />
              </ui:include>
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/panel.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)

        assertThat(semanticModels.oldRoot.elOccurrences)
            .extracting("carrierKind", "ownerTagName", "ownerName", "attributeName", "rawValue")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                    SemanticElCarrierKind.INCLUDE_ATTRIBUTE,
                    "ui:include",
                    null,
                    "src",
                    "/fragments/panel.xhtml",
                ),
                org.assertj.core.groups.Tuple.tuple(
                    SemanticElCarrierKind.INCLUDE_PARAMETER,
                    "ui:param",
                    "label",
                    "value",
                    "#{bean.label}",
                ),
            )
        assertThat(semanticModels.oldRoot.elOccurrences.first().isSupported).isTrue()
        assertThat(semanticModels.oldRoot.elOccurrences.last().provenance.logicalLocation.attributeName).isEqualTo("value")
    }

    @Test
    fun `unsupported EL in a registry-declared attribute is captured as a semantic parse failure`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup id="panel" rendered="#{fn:length(bean.items)}" />
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val occurrence = semanticModels.oldRoot.elOccurrences.single()

        assertThat(occurrence.ownerTagName).isEqualTo("h:panelGroup")
        assertThat(occurrence.attributeName).isEqualTo("rendered")
        assertThat(occurrence.rawValue).isEqualTo("#{fn:length(bean.items)}")
        assertThat(occurrence.isSupported).isFalse()
        assertThat(occurrence.template).isNull()
        assertThat(occurrence.parseFailure!!.message).contains("Unexpected token ':'")
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
