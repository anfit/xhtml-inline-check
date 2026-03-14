package dev.xhtmlinlinecheck.testing

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.syntax.LogicalElementNode
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SimpleIncludeFixturePipelineTest {
    @Test
    fun `simple include fixture covers loader and parser expansion together`() {
        val scenario = FixtureScenarios.scenario("support/include-expansion")

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = FixtureScenarios.repositoryRoot.relativize(scenario.oldRoot),
                newRoot = FixtureScenarios.repositoryRoot.relativize(scenario.newRoot),
                baseOld = FixtureScenarios.repositoryRoot,
                baseNew = FixtureScenarios.repositoryRoot,
            ),
        )

        assertThat(loadedSources.oldRoot.document.displayPath).isEqualTo("fixtures/support/include-expansion/old/root.xhtml")
        assertThat(loadedSources.oldRoot.document.rootDirectory).isEqualTo(FixtureScenarios.repositoryRoot)
        assertThat(loadedSources.oldRoot.contents).contains("<ui:include src=\"/fragments/panel.xhtml\">")

        val includeEdge = loadedSources.oldRoot.sourceGraphFile.includeEdges.single()

        assertThat(includeEdge.includeSite.render()).startsWith("fixtures/support/include-expansion/old/root.xhtml:2:")
        assertThat(includeEdge.sourcePath).isEqualTo("/fragments/panel.xhtml")
        assertThat(includeEdge.includedDocument).isNotNull()
        assertThat(includeEdge.includedDocument!!.displayPath)
            .isEqualTo("fixtures/support/include-expansion/old/fragments/panel.xhtml")
        assertThat(includeEdge.includedFile).isNotNull()
        assertThat(includeEdge.includedFile!!.contents).contains("<h:outputText")
        assertThat(includeEdge.parameters.map { it.name }).containsExactly("label")
        assertThat(includeEdge.parameters.single().valueExpression).isEqualTo("#{bean.label}")

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(loadedSources)
        val includeNode = parsedTrees.oldRoot.rootNode!!.children.single() as LogicalIncludeNode
        val expandedRoot = includeNode.children.single() as LogicalElementNode
        val output = expandedRoot.children.single() as LogicalElementNode

        assertThat(includeNode.sourcePath).isEqualTo("/fragments/panel.xhtml")
        assertThat(includeNode.parameters.map { it.name }).containsExactly("label")
        assertThat(includeNode.provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-expansion/old/root.xhtml:2:")
        assertThat(expandedRoot.name.localName).isEqualTo("fragment")
        assertThat(expandedRoot.provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-expansion/old/fragments/panel.xhtml:1:")
        assertThat(expandedRoot.provenance.includeStack.single().includeSite.render())
            .startsWith("fixtures/support/include-expansion/old/root.xhtml:2:")
        assertThat(output.name.localName).isEqualTo("outputText")
        assertThat(output.attributes.single { it.name.localName == "value" }.value).isEqualTo("#{label}")
    }

    @Test
    fun `nested include fixture preserves parameter flow and provenance through deeper expansion`() {
        val scenario = FixtureScenarios.scenario("support/include-expansion-nested")

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = FixtureScenarios.repositoryRoot.relativize(scenario.oldRoot),
                newRoot = FixtureScenarios.repositoryRoot.relativize(scenario.newRoot),
                baseOld = FixtureScenarios.repositoryRoot,
                baseNew = FixtureScenarios.repositoryRoot,
            ),
        )

        val outerIncludeEdge = loadedSources.oldRoot.sourceGraphFile.includeEdges.single()
        val layoutFile = outerIncludeEdge.includedFile!!
        val innerIncludeEdge = layoutFile.includeEdges.single()

        assertThat(outerIncludeEdge.sourcePath).isEqualTo("/fragments/layout.xhtml")
        assertThat(outerIncludeEdge.parameters.map { it.name }).containsExactly("sectionLabel")
        assertThat(outerIncludeEdge.parameters.single().valueExpression).isEqualTo("#{bean.sectionLabel}")
        assertThat(outerIncludeEdge.parameters.single().provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-expansion-nested/old/root.xhtml:3:")

        assertThat(innerIncludeEdge.sourcePath).isEqualTo("/fragments/content.xhtml")
        assertThat(innerIncludeEdge.parameters.map { it.name }).containsExactly("resolvedLabel", "wrapperClass")
        assertThat(innerIncludeEdge.parameters[0].valueExpression).isEqualTo("#{sectionLabel}")
        assertThat(innerIncludeEdge.parameters[0].provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-expansion-nested/old/fragments/layout.xhtml:4:")
        assertThat(innerIncludeEdge.parameters[1].valueExpression).isEqualTo("hero")
        assertThat(innerIncludeEdge.parameters[1].provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-expansion-nested/old/fragments/layout.xhtml:5:")

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(loadedSources)
        val outerInclude = parsedTrees.oldRoot.rootNode!!.children.single() as LogicalIncludeNode
        val layoutRoot = outerInclude.children.single() as LogicalElementNode
        val layoutPanel = layoutRoot.children.single() as LogicalElementNode
        val innerInclude = layoutPanel.children.single() as LogicalIncludeNode
        val contentRoot = innerInclude.children.single() as LogicalElementNode
        val output = contentRoot.children.single() as LogicalElementNode

        assertThat(outerInclude.parameters.map { it.name }).containsExactly("sectionLabel")
        assertThat(innerInclude.parameters.map { it.name }).containsExactly("resolvedLabel", "wrapperClass")
        assertThat(output.provenance.includeStack).hasSize(2)
        assertThat(output.provenance.includeStack.map { it.includedDocument.displayPath })
            .containsExactly(
                "fixtures/support/include-expansion-nested/old/fragments/layout.xhtml",
                "fixtures/support/include-expansion-nested/old/fragments/content.xhtml",
            )
        assertThat(output.provenance.includeStack.map { it.parameterNames })
            .containsExactly(
                listOf("sectionLabel"),
                listOf("resolvedLabel", "wrapperClass"),
            )
        assertThat(output.attributes.single { it.name.localName == "styleClass" }.value).isEqualTo("#{wrapperClass}")
        assertThat(output.attributes.single { it.name.localName == "value" }.value).isEqualTo("#{resolvedLabel}")
    }

    @Test
    fun `missing include fixture preserves precise provenance without expanded content`() {
        val scenario = FixtureScenarios.scenario("support/missing-include")

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = FixtureScenarios.repositoryRoot.relativize(scenario.oldRoot),
                newRoot = FixtureScenarios.repositoryRoot.relativize(scenario.newRoot),
                baseOld = FixtureScenarios.repositoryRoot,
                baseNew = FixtureScenarios.repositoryRoot,
            ),
        )

        val includeEdge = loadedSources.oldRoot.sourceGraphFile.includeEdges.single()

        assertThat(includeEdge.sourcePath).isEqualTo("/fragments/missing.xhtml")
        assertThat(includeEdge.includeSite.render())
            .startsWith("fixtures/support/missing-include/old/root.xhtml:2:")
        assertThat(includeEdge.includeSite.attributeName).isEqualTo("src")
        assertThat(includeEdge.includedDocument).isNotNull()
        assertThat(includeEdge.includedDocument!!.displayPath)
            .isEqualTo("fixtures/support/missing-include/old/fragments/missing.xhtml")
        assertThat(includeEdge.includedFile).isNull()
        assertThat(includeEdge.includeFailure).isNotNull()
        assertThat(includeEdge.includeFailure!!.missingDocument).isEqualTo(includeEdge.includedDocument)
        assertThat(includeEdge.parameters.map { it.name }).containsExactly("label")
        assertThat(includeEdge.parameters.single().provenance.logicalLocation.render())
            .startsWith("fixtures/support/missing-include/old/root.xhtml:3:")
        assertThat(includeEdge.parameters.single().provenance.logicalLocation.attributeName).isEqualTo("value")

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(loadedSources)
        val includeNode = parsedTrees.oldRoot.rootNode!!.children.single() as LogicalIncludeNode

        assertThat(includeNode.sourcePath).isEqualTo("/fragments/missing.xhtml")
        assertThat(includeNode.children).isEmpty()
        assertThat(includeNode.provenance.logicalLocation.render())
            .startsWith("fixtures/support/missing-include/old/root.xhtml:2:")
        assertThat(includeNode.provenance.logicalLocation.attributeName).isEqualTo("src")
        assertThat(includeNode.includeFailure).isNotNull()
        assertThat(includeNode.includeFailure!!.missingDocument!!.displayPath)
            .isEqualTo("fixtures/support/missing-include/old/fragments/missing.xhtml")
        assertThat(includeNode.parameters.map { it.name }).containsExactly("label")
    }

    @Test
    fun `include cycle fixture records recursive provenance on the source graph and logical boundary`() {
        val scenario = FixtureScenarios.scenario("support/include-cycle")

        val loadedSources = SourceLoader.scaffold().load(
            AnalysisRequest(
                oldRoot = FixtureScenarios.repositoryRoot.relativize(scenario.oldRoot),
                newRoot = FixtureScenarios.repositoryRoot.relativize(scenario.newRoot),
                baseOld = FixtureScenarios.repositoryRoot,
                baseNew = FixtureScenarios.repositoryRoot,
            ),
        )

        val outerIncludeEdge = loadedSources.oldRoot.sourceGraphFile.includeEdges.single()
        val outerFile = outerIncludeEdge.includedFile!!
        val recursiveEdge = outerFile.includeEdges.single()

        assertThat(outerIncludeEdge.sourcePath).isEqualTo("fragments/outer.xhtml")
        assertThat(outerIncludeEdge.parameters.map { it.name }).containsExactly("rootLabel")
        assertThat(outerIncludeEdge.parameters.single().provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-cycle/old/root.xhtml:3:")

        assertThat(outerFile.provenance.includeStack).hasSize(1)
        assertThat(outerFile.provenance.includeStack.single().includedDocument.displayPath)
            .isEqualTo("fixtures/support/include-cycle/old/fragments/outer.xhtml")
        assertThat(outerFile.provenance.includeStack.single().parameterNames).containsExactly("rootLabel")

        assertThat(recursiveEdge.sourcePath).isEqualTo("../root.xhtml")
        assertThat(recursiveEdge.includedDocument).isNotNull()
        assertThat(recursiveEdge.includedDocument!!.displayPath)
            .isEqualTo("fixtures/support/include-cycle/old/root.xhtml")
        assertThat(recursiveEdge.includedFile).isNull()
        assertThat(recursiveEdge.stackBefore.steps).hasSize(1)
        assertThat(recursiveEdge.stackBefore.steps.single().includedDocument.displayPath)
            .isEqualTo("fixtures/support/include-cycle/old/fragments/outer.xhtml")
        assertThat(recursiveEdge.stackBefore.steps.single().parameterNames).containsExactly("rootLabel")
        assertThat(recursiveEdge.parameters.map { it.name }).containsExactly("cycleLabel")
        assertThat(recursiveEdge.parameters.single().valueExpression).isEqualTo("#{rootLabel}")
        assertThat(recursiveEdge.includeFailure).isNotNull()
        assertThat(recursiveEdge.includeFailure!!.cycleDocuments.map { it.displayPath })
            .containsExactly(
                "fixtures/support/include-cycle/old/root.xhtml",
                "fixtures/support/include-cycle/old/fragments/outer.xhtml",
                "fixtures/support/include-cycle/old/root.xhtml",
            )

        val parsedTrees = XhtmlSyntaxParser.scaffold().parse(loadedSources)
        val outerInclude = parsedTrees.oldRoot.rootNode!!.children.single() as LogicalIncludeNode
        val outerRoot = outerInclude.children.single() as LogicalElementNode
        val recursiveInclude = outerRoot.children.single() as LogicalIncludeNode

        assertThat(recursiveInclude.sourcePath).isEqualTo("../root.xhtml")
        assertThat(recursiveInclude.parameters.map { it.name }).containsExactly("cycleLabel")
        assertThat(recursiveInclude.provenance.logicalLocation.render())
            .startsWith("fixtures/support/include-cycle/old/fragments/outer.xhtml:2:")
        assertThat(recursiveInclude.provenance.logicalLocation.attributeName).isEqualTo("src")
        assertThat(recursiveInclude.provenance.includeStack).hasSize(1)
        assertThat(recursiveInclude.provenance.includeStack.single().includeSite.render())
            .startsWith("fixtures/support/include-cycle/old/root.xhtml:2:")
        assertThat(recursiveInclude.provenance.includeStack.single().includedDocument.displayPath)
            .isEqualTo("fixtures/support/include-cycle/old/fragments/outer.xhtml")
        assertThat(recursiveInclude.provenance.includeStack.single().parameterNames)
            .containsExactly("rootLabel")
        assertThat(recursiveInclude.children).isEmpty()
        assertThat(recursiveInclude.includeFailure).isNotNull()
        assertThat(recursiveInclude.includeFailure!!.cycleDocuments.map { it.displayPath })
            .containsExactly(
                "fixtures/support/include-cycle/old/root.xhtml",
                "fixtures/support/include-cycle/old/fragments/outer.xhtml",
                "fixtures/support/include-cycle/old/root.xhtml",
            )
    }
}
