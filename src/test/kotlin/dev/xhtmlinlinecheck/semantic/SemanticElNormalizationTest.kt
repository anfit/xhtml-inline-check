package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.syntax.LogicalElementNode
import dev.xhtmlinlinecheck.syntax.LogicalNodePath
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import dev.xhtmlinlinecheck.syntax.walkDepthFirstWithPath
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
    fun `safe alpha-renamed nested bindings normalize the same inside composite EL`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:repeat var="row" value="#{bean.items}">
                <c:forEach var="child" items="#{row.children}">
                  <h:outputText
                      id="value"
                      value="#{row.children[child.code].visible ? row.children[child.code].label : child.fallback}" />
                </c:forEach>
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
              <ui:repeat var="item" value="#{bean.items}">
                <c:forEach var="entry" items="#{item.children}">
                  <h:outputText
                      id="value"
                      value="#{item.children[entry.code].visible ? item.children[entry.code].label : entry.fallback}" />
                </c:forEach>
              </ui:repeat>
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val oldNormalized = normalizedValueOccurrence(semanticModels.oldRoot)
        val newNormalized = normalizedValueOccurrence(semanticModels.newRoot)

        assertThat(oldNormalized.normalizedTemplate).isEqualTo(newNormalized.normalizedTemplate)
        assertThat(oldNormalized.normalizedTemplate.render())
            .isEqualTo("#{binding#1.children[binding#2.code].visible ? binding#1.children[binding#2.code].label : binding#2.fallback}")
        assertThat(newNormalized.normalizedTemplate.render())
            .isEqualTo("#{binding#1.children[binding#2.code].visible ? binding#1.children[binding#2.code].label : binding#2.fallback}")
        assertThat(oldNormalized.bindingReferences.map { it.binding.origin.descriptor })
            .containsExactly("ui:repeat var=row", "c:forEach var=child")
        assertThat(newNormalized.bindingReferences.map { it.binding.origin.descriptor })
            .containsExactly("ui:repeat var=item", "c:forEach var=entry")
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
    fun `preserved inner shadowing stays equivalent when only the outer shadowed binding is renamed`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <c:set var="row" value="#{bean.outer}" />
              <ui:repeat var="row" value="#{bean.items}">
                <h:outputText id="capturedValue" value="#{row.label}" />
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
              <c:set var="outerRow" value="#{bean.outer}" />
              <ui:repeat var="row" value="#{bean.items}">
                <h:outputText id="capturedValue" value="#{row.label}" />
              </ui:repeat>
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val oldNormalized = normalizedValueOccurrence(semanticModels.oldRoot, "capturedValue")
        val newNormalized = normalizedValueOccurrence(semanticModels.newRoot, "capturedValue")

        assertThat(oldNormalized.normalizedTemplate).isEqualTo(newNormalized.normalizedTemplate)
        assertThat(oldNormalized.normalizedTemplate.render()).isEqualTo("#{binding#2.label}")
        assertThat(newNormalized.normalizedTemplate.render()).isEqualTo("#{binding#2.label}")
        assertThat(oldNormalized.bindingReferences.single().binding.origin.descriptor).isEqualTo("ui:repeat var=row")
        assertThat(newNormalized.bindingReferences.single().binding.origin.descriptor).isEqualTo("ui:repeat var=row")
    }

    @Test
    fun `removing inner iterator shadowing changes which binding identical EL text captures`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:repeat var="row" value="#{bean.rows}">
                <ui:repeat var="row" value="#{row.children}">
                  <h:outputText id="capturedValue" value="#{row.label}" />
                </ui:repeat>
              </ui:repeat>
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:repeat var="row" value="#{bean.rows}">
                <h:outputText id="capturedValue" value="#{row.label}" />
              </ui:repeat>
            </ui:composition>
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val oldNormalized = normalizedValueOccurrence(semanticModels.oldRoot, "capturedValue")
        val newNormalized = normalizedValueOccurrence(semanticModels.newRoot, "capturedValue")

        assertThat(oldNormalized.normalizedTemplate).isNotEqualTo(newNormalized.normalizedTemplate)
        assertThat(oldNormalized.normalizedTemplate.render()).isEqualTo("#{binding#2.label}")
        assertThat(newNormalized.normalizedTemplate.render()).isEqualTo("#{binding#1.label}")
        assertThat(oldNormalized.bindingReferences.single().binding.origin.descriptor).isEqualTo("ui:repeat var=row")
        assertThat(newNormalized.bindingReferences.single().binding.origin.descriptor).isEqualTo("ui:repeat var=row")
        assertThat(oldNormalized.bindingReferences.single().binding.id).isNotEqualTo(newNormalized.bindingReferences.single().binding.id)
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
        assertThat(oldNormalized.bindingReferences).hasSize(1)
        assertThat(newNormalized.bindingReferences).hasSize(1)
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

    private fun normalizedValueOccurrence(
        semanticModel: SemanticModel,
        id: String = "value",
    ): NormalizedSemanticElOccurrence {
        val path = findPathById(semanticModel.syntaxTree, id)
        return semanticModel.normalizedElOccurrences.single { occurrence ->
            occurrence.occurrence.nodePath == path &&
                occurrence.occurrence.ownerTagName == "h:outputText" &&
                occurrence.occurrence.attributeName == "value"
        }
    }

    private fun findPathById(
        syntaxTree: dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree,
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
}
