package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SemanticElNormalizationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `safe alpha-renamed local bindings normalize to the same canonical id`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:repeat var="row" value="#{bean.items}">
                <h:outputText id="value" value="#{row.label}" />
              </ui:repeat>
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:repeat var="item" value="#{bean.items}">
                <h:outputText id="value" value="#{item.label}" />
              </ui:repeat>
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val oldNormalized = normalizedValueOccurrence(semanticModels.oldRoot)
        val newNormalized = normalizedValueOccurrence(semanticModels.newRoot)

        assertThat(oldNormalized.normalizedTemplate).isEqualTo(newNormalized.normalizedTemplate)
        assertThat(oldNormalized.normalizedTemplate.render()).isEqualTo("#{binding#1.label}")
        assertThat(newNormalized.normalizedTemplate.render()).isEqualTo("#{binding#1.label}")
        assertThat(oldNormalized.bindingReferences.single().binding.origin.descriptor).isEqualTo("ui:repeat var=row")
        assertThat(newNormalized.bindingReferences.single().binding.origin.descriptor).isEqualTo("ui:repeat var=item")
    }

    @Test
    fun `shadowing changes produce different canonical local binding references`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <c:set var="row" value="#{bean.outer}" />
              <ui:repeat var="row" value="#{bean.items}">
                <h:outputText id="value" value="#{row.label}" />
              </ui:repeat>
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <c:set var="row" value="#{bean.outer}" />
              <h:outputText id="value" value="#{row.label}" />
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val oldNormalized = normalizedValueOccurrence(semanticModels.oldRoot)
        val newNormalized = normalizedValueOccurrence(semanticModels.newRoot)

        assertThat(oldNormalized.normalizedTemplate).isNotEqualTo(newNormalized.normalizedTemplate)
        assertThat(oldNormalized.normalizedTemplate.render()).isEqualTo("#{binding#2.label}")
        assertThat(newNormalized.normalizedTemplate.render()).isEqualTo("#{binding#1.label}")
        assertThat(oldNormalized.bindingReferences.single().binding.origin.descriptor).isEqualTo("ui:repeat var=row")
        assertThat(newNormalized.bindingReferences.single().binding.origin.descriptor).isEqualTo("c:set var=row")
    }

    @Test
    fun `normalization keeps unresolved global roots distinct from resolved local bindings`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:repeat var="row" value="#{bean.items}">
                <h:outputText id="value" value="#{row.label eq bean.selectedLabel}" />
              </ui:repeat>
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:repeat var="item" value="#{bean.items}">
                <h:outputText id="value" value="#{item.label eq bean.selectedLabel}" />
              </ui:repeat>
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val oldNormalized = normalizedValueOccurrence(semanticModels.oldRoot)
        val newNormalized = normalizedValueOccurrence(semanticModels.newRoot)

        assertThat(oldNormalized.normalizedTemplate.render()).isEqualTo("#{binding#1.label == global(bean).selectedLabel}")
        assertThat(newNormalized.normalizedTemplate.render()).isEqualTo("#{binding#1.label == global(bean).selectedLabel}")
        assertThat(oldNormalized.bindingReferences).single()
        assertThat(newNormalized.bindingReferences).single()
        assertThat(oldNormalized.globalReferences.map { it.writtenName }).containsExactly("bean")
        assertThat(newNormalized.globalReferences.map { it.writtenName }).containsExactly("bean")
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

    private fun normalizedValueOccurrence(semanticModel: SemanticModel): NormalizedSemanticElOccurrence =
        semanticModel.normalizedElOccurrences.single { occurrence ->
            occurrence.occurrence.ownerTagName == "h:outputText" && occurrence.occurrence.attributeName == "value"
        }
}
