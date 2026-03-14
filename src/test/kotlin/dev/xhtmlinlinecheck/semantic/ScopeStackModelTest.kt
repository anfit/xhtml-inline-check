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

class ScopeStackModelTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scope stack resolves bindings relative to node position and inner shadowing`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <c:set var="outer" value="#{bean.outer}" />
              <ui:repeat var="row" varStatus="status" value="#{bean.rows}">
                <h:outputText id="repeatValue" value="#{row.label}" />
                <c:forEach var="row" varStatus="loop" items="#{row.children}">
                  <h:outputText id="nestedValue" value="#{row.label}" />
                </c:forEach>
                <h:outputText id="afterNestedValue" value="#{row.label}" />
              </ui:repeat>
              <h:outputText id="outsideValue" value="#{outer}" />
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
        val scopeModel = semanticModels.oldRoot.scopeModel
        val root = semanticModels.oldRoot.syntaxTree.root!!
        val repeatPath = findPathById(semanticModels.oldRoot.syntaxTree, "repeatValue").parent()
        val repeatValuePath = findPathById(semanticModels.oldRoot.syntaxTree, "repeatValue")
        val nestedValuePath = findPathById(semanticModels.oldRoot.syntaxTree, "nestedValue")
        val afterNestedValuePath = findPathById(semanticModels.oldRoot.syntaxTree, "afterNestedValue")
        val outsideValuePath = findPathById(semanticModels.oldRoot.syntaxTree, "outsideValue")

        assertThat(scopeModel.snapshotAt(LogicalNodePath.root()).nodeScopeId).isEqualTo(scopeModel.rootScopeId)
        assertThat(scopeModel.resolve("outer", repeatValuePath))
            .extracting("kind", "writtenName")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.C_SET, "outer")
        assertThat(scopeModel.resolve("row", repeatPath, ScopeLookupPosition.NODE)).isNull()
        assertThat(scopeModel.resolve("row", repeatPath, ScopeLookupPosition.DESCENDANT))
            .extracting("kind", "origin.descriptor")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.ITERATION_VAR, "ui:repeat var=row")
        assertThat(scopeModel.resolve("status", repeatValuePath))
            .extracting("kind", "origin.descriptor")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.VAR_STATUS, "ui:repeat varStatus=status")
        assertThat(scopeModel.resolve("row", nestedValuePath))
            .extracting("kind", "origin.descriptor")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.C_FOR_EACH, "c:forEach var=row")
        assertThat(scopeModel.resolve("loop", nestedValuePath))
            .extracting("kind", "writtenName")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.VAR_STATUS, "loop")
        assertThat(scopeModel.resolve("row", afterNestedValuePath))
            .extracting("kind", "origin.descriptor")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.ITERATION_VAR, "ui:repeat var=row")
        assertThat(scopeModel.resolve("loop", afterNestedValuePath)).isNull()
        assertThat(scopeModel.resolve("row", outsideValuePath)).isNull()
        assertThat(scopeModel.visibleBindingsAt(nestedValuePath).map { it.writtenName })
            .containsExactly("loop", "row", "status", "row", "outer")
        assertThat(root.name.localName).isEqualTo("composition")
    }

    @Test
    fun `scope stack injects include parameters only into expanded include descendants`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:include src="/fragments/panel.xhtml">
                <ui:param name="label" value="#{bean.label}" />
              </ui:include>
              <h:outputText id="outsideOutput" value="#{bean.label}" />
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/panel.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets" xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:outputText id="includedOutput" value="#{label}" />
            </ui:fragment>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val semanticModels = semanticModelsFor(oldRoot, newRoot, tempDir)
        val oldScope = semanticModels.oldRoot.scopeModel
        val outputPath = findPathById(semanticModels.oldRoot.syntaxTree, "includedOutput")
        val siblingPath = findPathById(semanticModels.oldRoot.syntaxTree, "outsideOutput")
        val includePath = outputPath.parent().parent()

        assertThat(oldScope.resolve("label", includePath, ScopeLookupPosition.NODE)).isNull()
        assertThat(oldScope.resolve("label", includePath, ScopeLookupPosition.DESCENDANT))
            .extracting("kind", "origin.descriptor", "valueExpression")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.UI_PARAM, "ui:param name=label", "#{bean.label}")
        assertThat(oldScope.resolve("label", outputPath))
            .extracting("kind", "provenance.logicalLocation.document.displayPath")
            .containsExactly(
                dev.xhtmlinlinecheck.domain.BindingKind.UI_PARAM,
                "legacy/root.xhtml",
            )
        assertThat(oldScope.resolve("label", siblingPath)).isNull()
    }

    @Test
    fun `c set binding persists into later siblings within the same enclosing scope`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup id="beforeSet">
                <h:outputText id="beforeOutput" value="#{outer}" />
              </h:panelGroup>
              <c:set var="outer" value="#{bean.outer}" />
              <h:panelGroup id="afterSet">
                <h:outputText id="afterOutput" value="#{outer}" />
              </h:panelGroup>
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
        val scopeModel = semanticModels.oldRoot.scopeModel
        val setPath = findPathByLocalName(semanticModels.oldRoot.syntaxTree, "set")
        val beforeOutputPath = findPathById(semanticModels.oldRoot.syntaxTree, "beforeOutput")
        val afterOutputPath = findPathById(semanticModels.oldRoot.syntaxTree, "afterOutput")

        assertThat(scopeModel.resolve("outer", setPath, ScopeLookupPosition.NODE)).isNull()
        assertThat(scopeModel.resolve("outer", setPath, ScopeLookupPosition.DESCENDANT))
            .extracting("kind", "origin.descriptor", "valueExpression")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.C_SET, "c:set var=outer", "#{bean.outer}")
        assertThat(scopeModel.resolve("outer", beforeOutputPath)).isNull()
        assertThat(scopeModel.resolve("outer", afterOutputPath))
            .extracting("kind", "origin.descriptor")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.C_SET, "c:set var=outer")
    }

    @Test
    fun `nested c set shadows outer binding only inside its enclosing branch`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <c:set var="item" value="#{bean.outer}" />
              <h:panelGroup id="branch">
                <c:set var="item" value="#{bean.inner}" />
                <h:outputText id="insideBranch" value="#{item}" />
              </h:panelGroup>
              <h:outputText id="afterBranch" value="#{item}" />
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
        val scopeModel = semanticModels.oldRoot.scopeModel
        val insideBranchPath = findPathById(semanticModels.oldRoot.syntaxTree, "insideBranch")
        val afterBranchPath = findPathById(semanticModels.oldRoot.syntaxTree, "afterBranch")

        assertThat(scopeModel.resolve("item", insideBranchPath))
            .extracting("kind", "valueExpression")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.C_SET, "#{bean.inner}")
        assertThat(scopeModel.visibleBindingsAt(insideBranchPath).map { it.valueExpression })
            .containsExactly("#{bean.inner}", "#{bean.outer}")
        assertThat(scopeModel.resolve("item", afterBranchPath))
            .extracting("kind", "valueExpression")
            .containsExactly(dev.xhtmlinlinecheck.domain.BindingKind.C_SET, "#{bean.outer}")
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

    private fun findPathByLocalName(
        syntaxTree: dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree,
        localName: String,
    ): LogicalNodePath {
        var foundPath: LogicalNodePath? = null
        syntaxTree.walkDepthFirstWithPath { path, node ->
            if (node is LogicalElementNode && node.name.localName == localName) {
                foundPath = path
            }
        }
        return requireNotNull(foundPath) { "missing node with localName=$localName" }
    }

    private fun LogicalNodePath.parent(): LogicalNodePath =
        LogicalNodePath(segments.dropLast(1))
}
