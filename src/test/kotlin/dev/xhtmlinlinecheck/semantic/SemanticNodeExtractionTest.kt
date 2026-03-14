package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SemanticNodeExtractionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `semantic nodes carry ids rendered targets transparency ancestry and expanded provenance`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:form id="mainForm">
                <ui:include src="/fragments/body.xhtml">
                  <ui:param name="label" value="#{bean.label}" />
                </ui:include>
              </h:form>
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/body.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                         xmlns:h="http://xmlns.jcp.org/jsf/html">
              <ui:repeat var="row" varStatus="status" value="#{bean.rows}">
                <h:panelGroup id="panel" rendered="#{row.visible}" update="msgs panel" process="@this" />
                <h:outputText id="labelOutput" value="#{label}" />
              </ui:repeat>
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
        val oldNodes = semanticModels.oldRoot.semanticNodes
        val includeNode = oldNodes.single { it.kind == SemanticNodeKind.INCLUDE }
        val fragmentNode = oldNodes.single { it.nodeName == "ui:fragment" }
        val panelNode = oldNodes.single { it.explicitIdAttribute?.rawValue == "panel" }

        assertThat(includeNode.nodeId.value).isEqualTo("node:/0/0")
        assertThat(includeNode.isTransparentStructureWrapper).isTrue()
        assertThat(includeNode.elFacts)
            .extracting("carrierKind", "attributeName", "ownerName", "rawValue")
            .containsExactly(
                Tuple.tuple(SemanticElCarrierKind.INCLUDE_ATTRIBUTE, "src", null, "/fragments/body.xhtml"),
                Tuple.tuple(SemanticElCarrierKind.INCLUDE_PARAMETER, "value", "label", "#{bean.label}"),
            )

        assertThat(fragmentNode.isTransparentStructureWrapper).isTrue()
        assertThat(fragmentNode.provenance.physicalLocation.document.displayPath).isEqualTo("fragments/body.xhtml")
        assertThat(fragmentNode.provenance.logicalLocation.document.displayPath).isEqualTo("legacy/root.xhtml")

        assertThat(panelNode.nodePath.segments).containsExactly(0, 0, 0, 0, 0)
        assertThat(panelNode.explicitIdAttribute)
            .extracting("attributeName", "rawValue")
            .containsExactly("id", "panel")
        assertThat(panelNode.renderedAttribute)
            .extracting("attributeName", "rawValue", "normalizedTemplate")
            .containsExactly("rendered", "#{row.visible}", panelNode.renderedAttribute!!.normalizedTemplate)
        assertThat(panelNode.renderedAttribute!!.normalizedTemplate!!.render()).isEqualTo("#{binding#2.visible}")
        assertThat(panelNode.renderedAttribute!!.bindingReferences.map { it.binding.origin.descriptor })
            .containsExactly("ui:repeat var=row")
        assertThat(panelNode.targetAttributes)
            .extracting("attributeName", "rawValue")
            .containsExactly(
                Tuple.tuple("update", "msgs panel"),
                Tuple.tuple("process", "@this"),
            )
        assertThat(panelNode.formAncestry.map { it.nodeName }).containsExactly("h:form")
        assertThat(panelNode.namingContainerAncestry.map { it.nodeName }).containsExactly("h:form")
        assertThat(panelNode.iterationAncestry)
            .extracting("nodeName")
            .containsExactly("ui:repeat")
        assertThat(panelNode.iterationAncestry.single().bindingOrigins.map { it.descriptor })
            .containsExactly("ui:repeat var=row", "ui:repeat varStatus=status")
        assertThat(panelNode.provenance.physicalLocation.document.displayPath).isEqualTo("fragments/body.xhtml")
        assertThat(panelNode.provenance.logicalLocation.document.displayPath).isEqualTo("legacy/root.xhtml")
    }

    @Test
    fun `semantic nodes attach normalized EL to text nodes and include metadata on the same path-based model`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup id="wrapper">Hello #{bean.user}</h:panelGroup>
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
        val textNode = semanticModels.oldRoot.semanticNodes.single { it.kind == SemanticNodeKind.TEXT }

        assertThat(textNode.nodeId.value).isEqualTo("node:/0/0")
        assertThat(textNode.nodeName).isEqualTo("#text")
        assertThat(textNode.elFacts)
            .extracting("carrierKind", "rawValue")
            .containsExactly(Tuple.tuple(SemanticElCarrierKind.TEXT_NODE, "Hello #{bean.user}"))
        assertThat(textNode.elFacts.single().normalizedTemplate!!.render()).isEqualTo("Hello #{global(bean).user}")
        assertThat(textNode.elFacts.single().globalReferences.map { it.writtenName }).containsExactly("bean")
        assertThat(textNode.formAncestry).isEmpty()
        assertThat(textNode.namingContainerAncestry).isEmpty()
        assertThat(textNode.iterationAncestry).isEmpty()
    }

    @Test
    fun `semantic nodes accumulate combined form naming container and iteration ancestry without wrapper noise`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:c="http://java.sun.com/jsp/jstl/core"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:form id="outerForm">
                <ui:fragment>
                  <ui:repeat var="row" varStatus="rowStatus" value="#{bean.rows}">
                    <c:forEach var="child" varStatus="childStatus" items="#{row.children}">
                      <h:outputText id="deepOutput" value="#{child.label}" />
                    </c:forEach>
                  </ui:repeat>
                </ui:fragment>
              </h:form>
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
        val deepOutputNode = semanticModels.oldRoot.semanticNodes.single { it.explicitIdAttribute?.rawValue == "deepOutput" }

        assertThat(deepOutputNode.formAncestry.map { it.nodeName }).containsExactly("h:form")
        assertThat(deepOutputNode.namingContainerAncestry.map { it.nodeName }).containsExactly("h:form")
        assertThat(deepOutputNode.iterationAncestry.map { it.nodeName }).containsExactly("ui:repeat", "c:forEach")
        assertThat(deepOutputNode.iterationAncestry.flatMap { it.bindingOrigins }.map { it.descriptor })
            .containsExactly(
                "ui:repeat var=row",
                "ui:repeat varStatus=rowStatus",
                "c:forEach var=child",
                "c:forEach varStatus=childStatus",
            )
        assertThat(deepOutputNode.formAncestry + deepOutputNode.namingContainerAncestry)
            .allSatisfy { ancestor ->
                assertThat(ancestor.nodeName).isNotEqualTo("ui:composition")
                assertThat(ancestor.nodeName).isNotEqualTo("ui:fragment")
            }
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
