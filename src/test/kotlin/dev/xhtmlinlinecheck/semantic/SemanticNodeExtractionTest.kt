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
        assertThat(includeNode.participatesInStructuralMatching).isFalse()
        assertThat(includeNode.elFacts)
            .extracting("carrierKind", "attributeName", "ownerName", "rawValue")
            .containsExactly(
                Tuple.tuple(SemanticElCarrierKind.INCLUDE_ATTRIBUTE, "src", null, "/fragments/body.xhtml"),
                Tuple.tuple(SemanticElCarrierKind.INCLUDE_PARAMETER, "value", "label", "#{bean.label}"),
            )

        assertThat(fragmentNode.isTransparentStructureWrapper).isTrue()
        assertThat(fragmentNode.participatesInStructuralMatching).isFalse()
        assertThat(fragmentNode.provenance.physicalLocation.document.displayPath).isEqualTo("fragments/body.xhtml")
        assertThat(fragmentNode.provenance.logicalLocation.document.displayPath).isEqualTo("legacy/root.xhtml")

        assertThat(panelNode.nodePath.segments).containsExactly(0, 0, 0, 0, 0)
        assertThat(panelNode.participatesInStructuralMatching).isTrue()
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
        assertThat(panelNode.structuralContext.formAncestry.map { it.nodeName }).containsExactly("h:form")
        assertThat(panelNode.structuralContext.namingContainerAncestry.map { it.nodeName }).containsExactly("h:form")
        assertThat(panelNode.structuralContext.iterationAncestry)
            .extracting("nodeName")
            .containsExactly("ui:repeat")
        assertThat(panelNode.structuralContext.iterationAncestry.single().bindingOrigins.map { it.descriptor })
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
        assertThat(textNode.structuralContext.formAncestry).isEmpty()
        assertThat(textNode.structuralContext.namingContainerAncestry).isEmpty()
        assertThat(textNode.structuralContext.iterationAncestry).isEmpty()
    }

    @Test
    fun `semantic nodes deterministically join path ids EL facts and target attributes into one canonical model`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:form id="mainForm">
                <ui:include src="/fragments/actions.xhtml">
                  <ui:param name="item" value="#{bean.selected}" />
                </ui:include>
              </h:form>
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/actions.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                         xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup id="details">
                Summary #{item.name}
                <h:outputLabel id="nameLabel" for="nameInput" rendered="#{item.visible}" />
                <h:commandButton id="saveButton" update="msgs details" process="@form" rendered="#{item.editable}" />
              </h:panelGroup>
            </ui:fragment>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val semanticModel = semanticModelsFor(oldRoot, newRoot, tempDir).oldRoot
        val semanticNodes = semanticModel.semanticNodes

        assertThat(semanticNodes)
            .extracting("nodePath.segments", "nodeId.value", "nodeName")
            .containsExactly(
                Tuple.tuple(emptyList<Int>(), "node:/", "ui:composition"),
                Tuple.tuple(listOf(0), "node:/0", "h:form"),
                Tuple.tuple(listOf(0, 0), "node:/0/0", "ui:include"),
                Tuple.tuple(listOf(0, 0, 0), "node:/0/0/0", "ui:fragment"),
                Tuple.tuple(listOf(0, 0, 0, 0), "node:/0/0/0/0", "h:panelGroup"),
                Tuple.tuple(listOf(0, 0, 0, 0, 0), "node:/0/0/0/0/0", "#text"),
                Tuple.tuple(listOf(0, 0, 0, 0, 1), "node:/0/0/0/0/1", "h:outputLabel"),
                Tuple.tuple(listOf(0, 0, 0, 0, 2), "node:/0/0/0/0/2", "h:commandButton"),
            )

        assertThat(
            semanticNodes.flatMap { node ->
                node.elFacts.map { fact ->
                    Tuple.tuple(
                        node.nodeId.value,
                        fact.carrierKind,
                        fact.attributeName,
                        fact.ownerName,
                        fact.rawValue,
                        fact.normalizedTemplate?.render(),
                    )
                }
            },
        ).containsExactly(
            Tuple.tuple(
                "node:/0/0",
                SemanticElCarrierKind.INCLUDE_ATTRIBUTE,
                "src",
                null,
                "/fragments/actions.xhtml",
                "/fragments/actions.xhtml",
            ),
            Tuple.tuple(
                "node:/0/0",
                SemanticElCarrierKind.INCLUDE_PARAMETER,
                "value",
                "item",
                "#{bean.selected}",
                "#{global(bean).selected}",
            ),
            Tuple.tuple(
                "node:/0/0/0/0/0",
                SemanticElCarrierKind.TEXT_NODE,
                null,
                null,
                "\n    Summary #{item.name}\n    ",
                "\n    Summary #{binding#1.name}\n    ",
            ),
            Tuple.tuple(
                "node:/0/0/0/0/1",
                SemanticElCarrierKind.ELEMENT_ATTRIBUTE,
                "rendered",
                null,
                "#{item.visible}",
                "#{binding#1.visible}",
            ),
            Tuple.tuple(
                "node:/0/0/0/0/2",
                SemanticElCarrierKind.ELEMENT_ATTRIBUTE,
                "rendered",
                null,
                "#{item.editable}",
                "#{binding#1.editable}",
            ),
        )

        assertThat(semanticNodes.flatMap { node -> node.elFacts })
            .extracting("carrierKind", "attributeName", "ownerName", "rawValue")
            .containsExactlyElementsOf(
                semanticModel.elOccurrences.map { occurrence ->
                    Tuple.tuple(
                        occurrence.carrierKind,
                        occurrence.attributeName,
                        occurrence.ownerName,
                        occurrence.rawValue,
                    )
                },
            )

        assertThat(
            semanticNodes.flatMap { node ->
                node.targetAttributes.map { attribute ->
                    Tuple.tuple(node.nodeId.value, attribute.attributeName, attribute.rawValue)
                }
            },
        ).containsExactly(
            Tuple.tuple("node:/0/0/0/0/1", "for", "nameInput"),
            Tuple.tuple("node:/0/0/0/0/2", "update", "msgs details"),
            Tuple.tuple("node:/0/0/0/0/2", "process", "@form"),
        )
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
        val semanticNodes = semanticModels.oldRoot.semanticNodes
        val repeatNode = semanticNodes.single { it.nodeName == "ui:repeat" }
        val forEachNode = semanticNodes.single { it.nodeName == "c:forEach" }
        val deepOutputNode = semanticModels.oldRoot.semanticNodes.single { it.explicitIdAttribute?.rawValue == "deepOutput" }

        assertThat(
            listOf(repeatNode, forEachNode, deepOutputNode).map { node ->
                Tuple.tuple(
                    node.nodeName,
                    node.structuralContext.formAncestry.map { it.nodeName },
                    node.structuralContext.namingContainerAncestry.map { it.nodeName },
                    node.structuralContext.iterationAncestry.map { it.nodeName },
                )
            },
        ).containsExactly(
            Tuple.tuple("ui:repeat", listOf("h:form"), listOf("h:form"), emptyList<String>()),
            Tuple.tuple("c:forEach", listOf("h:form"), listOf("h:form"), listOf("ui:repeat")),
            Tuple.tuple("h:outputText", listOf("h:form"), listOf("h:form"), listOf("ui:repeat", "c:forEach")),
        )
        assertThat(deepOutputNode.structuralContext.formAncestry.map { it.nodeName }).containsExactly("h:form")
        assertThat(deepOutputNode.structuralContext.namingContainerAncestry.map { it.nodeName }).containsExactly("h:form")
        assertThat(deepOutputNode.structuralContext.iterationAncestry.map { it.nodeName }).containsExactly("ui:repeat", "c:forEach")
        assertThat(deepOutputNode.structuralContext.iterationAncestry.flatMap { it.bindingOrigins }.map { it.descriptor })
            .containsExactly(
                "ui:repeat var=row",
                "ui:repeat varStatus=rowStatus",
                "c:forEach var=child",
                "c:forEach varStatus=childStatus",
            )
        assertThat(
            deepOutputNode.structuralContext.formAncestry +
                deepOutputNode.structuralContext.namingContainerAncestry,
        )
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
