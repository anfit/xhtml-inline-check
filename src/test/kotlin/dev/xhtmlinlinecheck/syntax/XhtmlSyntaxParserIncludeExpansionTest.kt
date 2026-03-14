package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.rules.FACELETS_NAMESPACE
import dev.xhtmlinlinecheck.rules.StaticTagRule
import dev.xhtmlinlinecheck.rules.SyntaxRole
import dev.xhtmlinlinecheck.rules.TagRule
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
import dev.xhtmlinlinecheck.semantic.SemanticAnalyzer
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class XhtmlSyntaxParserIncludeExpansionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `parser expands resolved includes into logical include nodes with included subtree provenance`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/table.xhtml">
                <ui:param name="row" value="#{bean.row}" />
              </ui:include>
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/table.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <h:outputText xmlns:h="http://xmlns.jcp.org/jsf/html" value="#{row.label}" />
            </ui:fragment>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(
            SourceLoader.scaffold().load(
                AnalysisRequest(
                    oldRoot = tempDir.relativize(oldRoot),
                    newRoot = tempDir.relativize(newRoot),
                    baseOld = tempDir,
                    baseNew = tempDir,
                ),
            ),
        )

        val includeNode = parsedTrees.oldRoot.syntaxTree.root!!.children.single() as LogicalIncludeNode
        val expandedRoot = includeNode.children.single() as LogicalElementNode
        val output = expandedRoot.children.single() as LogicalElementNode

        assertThat(includeNode.sourcePath).isEqualTo("/fragments/table.xhtml")
        assertThat(includeNode.parameters.map { it.name }).containsExactly("row")
        assertThat(includeNode.parameters.single().valueExpression).isEqualTo("#{bean.row}")
        assertThat(includeNode.location.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(includeNode.provenance.logicalLocation.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(expandedRoot.name.localName).isEqualTo("fragment")
        assertThat(expandedRoot.tagRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(expandedRoot.tagRule.isTransparentStructureWrapper).isTrue()
        assertThat(expandedRoot.location.render()).startsWith("fragments/table.xhtml:1:")
        assertThat(expandedRoot.provenance.physicalLocation.render()).startsWith("fragments/table.xhtml:1:")
        assertThat(expandedRoot.provenance.logicalLocation.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(expandedRoot.provenance.includeStack.single().includeSite.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(expandedRoot.provenance.includeStack.single().includedDocument.displayPath).isEqualTo("fragments/table.xhtml")
        assertThat(output.name.localName).isEqualTo("outputText")
        assertThat(output.tagRule.targetAttributeNames).contains("for", "update", "render", "process", "execute")
        assertThat(output.attributes.single { it.name.localName == "value" }.value).isEqualTo("#{row.label}")
    }

    @Test
    fun `parser keeps unresolved includes as explicit logical boundaries without expanded children`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="#{bean.fragmentPath}" />
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(
            SourceLoader.scaffold().load(
                AnalysisRequest(
                    oldRoot = tempDir.relativize(oldRoot),
                    newRoot = tempDir.relativize(newRoot),
                    baseOld = tempDir,
                    baseNew = tempDir,
                ),
            ),
        )

        val includeNode = parsedTrees.oldRoot.syntaxTree.root!!.children.single() as LogicalIncludeNode

        assertThat(includeNode.sourcePath).isEqualTo("#{bean.fragmentPath}")
        assertThat(includeNode.expandedFile).isNull()
        assertThat(includeNode.children).isEmpty()
        assertThat(includeNode.parameters).isEmpty()
        assertThat(includeNode.includeFailure).isNotNull()
        assertThat(includeNode.includeFailure!!.kind).isEqualTo(dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailureKind.DYNAMIC_PATH)
        assertThat(includeNode.includeFailure!!.dynamicSourcePath).isEqualTo("#{bean.fragmentPath}")
    }

    @Test
    fun `parser preserves nested include stacks across recursive expansion`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/outer.xhtml" />
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/outer.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/inner.xhtml" />
            </ui:fragment>
            """,
        )
        tree.write(
            "fragments/inner.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <h:panelGroup xmlns:h="http://xmlns.jcp.org/jsf/html" id="target" />
            </ui:fragment>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(
            SourceLoader.scaffold().load(
                AnalysisRequest(
                    oldRoot = tempDir.relativize(oldRoot),
                    newRoot = tempDir.relativize(newRoot),
                    baseOld = tempDir,
                    baseNew = tempDir,
                ),
            ),
        )

        val outerInclude = parsedTrees.oldRoot.syntaxTree.root!!.children.single() as LogicalIncludeNode
        val outerRoot = outerInclude.children.single() as LogicalElementNode
        val innerInclude = outerRoot.children.single() as LogicalIncludeNode
        val innerRoot = innerInclude.children.single() as LogicalElementNode
        val panel = innerRoot.children.single() as LogicalElementNode

        assertThat(panel.name.localName).isEqualTo("panelGroup")
        assertThat(panel.provenance.includeStack).hasSize(2)
        assertThat(panel.provenance.physicalLocation.render()).startsWith("fragments/inner.xhtml:2:")
        assertThat(panel.provenance.logicalLocation.render()).startsWith("fragments/outer.xhtml:2:")
        assertThat(panel.provenance.includeStack.map { it.includedDocument.displayPath })
            .containsExactly("fragments/outer.xhtml", "fragments/inner.xhtml")
    }

    @Test
    fun `parser keeps include cycle failures on logical boundaries without expanding the recursive branch`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/outer.xhtml" />
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/outer.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/legacy/root.xhtml" />
            </ui:fragment>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(
            SourceLoader.scaffold().load(
                AnalysisRequest(
                    oldRoot = tempDir.relativize(oldRoot),
                    newRoot = tempDir.relativize(newRoot),
                    baseOld = tempDir,
                    baseNew = tempDir,
                ),
            ),
        )

        val outerInclude = parsedTrees.oldRoot.syntaxTree.root!!.children.single() as LogicalIncludeNode
        val outerRoot = outerInclude.children.single() as LogicalElementNode
        val recursiveInclude = outerRoot.children.single() as LogicalIncludeNode

        assertThat(recursiveInclude.sourcePath).isEqualTo("/legacy/root.xhtml")
        assertThat(recursiveInclude.expandedFile).isNull()
        assertThat(recursiveInclude.children).isEmpty()
        assertThat(recursiveInclude.includeFailure).isNotNull()
        assertThat(recursiveInclude.includeFailure!!.cycleDocuments.map { it.displayPath })
            .containsExactly("legacy/root.xhtml", "fragments/outer.xhtml", "legacy/root.xhtml")
    }

    @Test
    fun `parser keeps missing include failures on logical boundaries without expanded children`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/missing.xhtml" />
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(
            SourceLoader.scaffold().load(
                AnalysisRequest(
                    oldRoot = tempDir.relativize(oldRoot),
                    newRoot = tempDir.relativize(newRoot),
                    baseOld = tempDir,
                    baseNew = tempDir,
                ),
            ),
        )

        val includeNode = parsedTrees.oldRoot.syntaxTree.root!!.children.single() as LogicalIncludeNode

        assertThat(includeNode.sourcePath).isEqualTo("/fragments/missing.xhtml")
        assertThat(includeNode.expandedFile).isNull()
        assertThat(includeNode.children).isEmpty()
        assertThat(includeNode.includeFailure).isNotNull()
        assertThat(includeNode.includeFailure!!.missingDocument!!.displayPath).isEqualTo("fragments/missing.xhtml")
    }

    @Test
    fun `normalized structural roots flatten include boundaries and transparent facelets wrappers`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup id="before" />
              <ui:include src="/fragments/content.xhtml" />
              <h:panelGroup id="after" />
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/content.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets" xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:outputText id="included" value="included" />
            </ui:fragment>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(
            SourceLoader.scaffold().load(
                AnalysisRequest(
                    oldRoot = tempDir.relativize(oldRoot),
                    newRoot = tempDir.relativize(newRoot),
                    baseOld = tempDir,
                    baseNew = tempDir,
                ),
            ),
        )

        val normalizedRoots = parsedTrees.oldRoot.syntaxTree.normalizedStructureRoots()
        val normalizedNames =
            normalizedRoots.map { node ->
                (node as LogicalElementNode).attributes.single { it.name.localName == "id" }.value
            }

        assertThat(normalizedNames).containsExactly("before", "included", "after")
        assertThat(normalizedRoots).allMatch { it is LogicalElementNode && !it.isTransparentStructureWrapper }
    }

    @Test
    fun `custom registry drives include expansion and semantic handoff consistently`() {
        val customFaceletsNamespace = "urn:test:facelets"
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <legacy:composition xmlns:legacy="$customFaceletsNamespace" xmlns:h="http://xmlns.jcp.org/jsf/html">
              <legacy:include src="/fragments/content.xhtml">
                <legacy:param name="label" value="#{bean.label}" />
              </legacy:include>
            </legacy:composition>
            """,
        )
        tree.write(
            "fragments/content.xhtml",
            """
            <legacy:fragment xmlns:legacy="$customFaceletsNamespace" xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:outputText value="#{label}" />
            </legacy:fragment>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="$FACELETS_NAMESPACE" />
            """,
        )
        val customRegistry = registryWithCustomFaceletsNamespace(customFaceletsNamespace)

        val loadedSources = SourceLoader.scaffold(customRegistry).load(
            AnalysisRequest(
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )
        val parsedTrees = XhtmlSyntaxParser.scaffold(customRegistry).parse(loadedSources)
        val semanticModels = SemanticAnalyzer.scaffold(customRegistry).analyze(parsedTrees)

        val includeEdge = loadedSources.oldRoot.sourceGraphFile.includeEdges.single()
        val includeNode = parsedTrees.oldRoot.syntaxTree.root!!.children.single() as LogicalIncludeNode
        val expandedRoot = includeNode.children.single() as LogicalElementNode

        assertThat(includeEdge.parameters.map { it.name }).containsExactly("label")
        assertThat(includeNode.parameters.map { it.name }).containsExactly("label")
        assertThat(expandedRoot.tagRule.isTransparentStructureWrapper).isTrue()
        assertThat((expandedRoot.children.single() as LogicalElementNode).attributes.single().value).isEqualTo("#{label}")
        assertThat(semanticModels.oldRoot.tagRules).isSameAs(customRegistry)
    }

    private fun registryWithCustomFaceletsNamespace(customNamespace: String): TagRuleRegistry {
        val builtIns = TagRuleRegistry.builtIns()
        val customRules =
            mapOf(
                "composition" to StaticTagRule(isTransparentStructureWrapper = true),
                "fragment" to StaticTagRule(isTransparentStructureWrapper = true),
                "include" to StaticTagRule(syntaxRole = SyntaxRole.INCLUDE, isTransparentStructureWrapper = true),
                "param" to StaticTagRule(syntaxRole = SyntaxRole.INCLUDE_PARAMETER),
            )

        return CustomFaceletsNamespaceRegistry(customNamespace, customRules, builtIns)
    }
}

private class CustomFaceletsNamespaceRegistry(
    private val customNamespace: String,
    private val customRules: Map<String, TagRule>,
    private val builtIns: TagRuleRegistry,
) : TagRuleRegistry {
    override fun ruleFor(name: LogicalName): TagRule? =
        if (name.namespaceUri == customNamespace) {
            customRules[name.localName]
        } else {
            builtIns.ruleFor(name)
        }

    override fun resolve(name: LogicalName): TagRule = ruleFor(name) ?: builtIns.resolve(name)
}
