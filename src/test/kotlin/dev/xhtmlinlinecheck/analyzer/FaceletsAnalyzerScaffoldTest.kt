package dev.xhtmlinlinecheck.analyzer

import dev.xhtmlinlinecheck.domain.AttributeLocationPrecision
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.testing.FixtureExpectations
import dev.xhtmlinlinecheck.testing.FixtureScenarios
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import dev.xhtmlinlinecheck.testing.assertThatReport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FaceletsAnalyzerScaffoldTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scaffold analyzer returns stable inconclusive report for a minimal pair`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "old/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val newRoot = tree.write(
            "new/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = oldRoot,
                newRoot = newRoot,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasSummaryContaining("Scaffold")
            .hasProblemCount(1)
            .hasWarningCount(1)
            .hasProblemIds("W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD")
        assertThat(report.problems.single().locations.old?.logicalLocation?.render()).isEqualTo("old/root.xhtml")
        assertThat(report.problems.single().locations.new?.logicalLocation?.render()).isEqualTo("new/root.xhtml")
    }

    @Test
    fun `scaffold analyzer emits a dedicated warning for include cycles before the generic scaffold warning`() {
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
            "new/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasProblemCount(2)
            .hasWarningCount(2)
            .hasProblemIds(
                "W-UNSUPPORTED-INCLUDE_CYCLE",
                "W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD",
            )
        assertThat(report.problems.first().summary).isEqualTo("Recursive include cycle detected")
        assertThat(report.problems.first().locations.old?.logicalLocation?.render()).startsWith("fragments/outer.xhtml:2:")
        assertThat(report.problems.first().explanation)
            .contains("legacy/root.xhtml -> fragments/outer.xhtml -> legacy/root.xhtml")
    }

    @Test
    fun `scaffold analyzer emits a dedicated warning for dynamic include paths before the generic scaffold warning`() {
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
            "new/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasProblemCount(2)
            .hasWarningCount(2)
            .hasProblemIds(
                "W-UNSUPPORTED-DYNAMIC_INCLUDE",
                "W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD",
            )
        assertThat(report.problems.first().summary).isEqualTo("Dynamic include path is not statically resolvable")
        assertThat(report.problems.first().locations.old?.logicalLocation?.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(report.problems.first().locations.old?.snippet).isEqualTo("#{bean.fragmentPath}")
        assertThat(report.problems.first().explanation).contains("#{bean.fragmentPath}")
    }

    @Test
    fun `dynamic include fixture keeps dedicated warning ids and derived inconclusive result stable`() {
        val scenario = FixtureScenarios.scenario("inconclusive/dynamic-include")
        val expectation = FixtureExpectations.read(scenario)

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = FixtureScenarios.repositoryRoot.relativize(scenario.oldRoot),
                newRoot = FixtureScenarios.repositoryRoot.relativize(scenario.newRoot),
                baseOld = FixtureScenarios.repositoryRoot,
                baseNew = FixtureScenarios.repositoryRoot,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.valueOf(expectation.result))
            .hasProblemCount(expectation.problemIds.size + expectation.warningIds.size)
            .hasWarningCount(expectation.warningIds.size)
            .hasProblemIds(*(expectation.problemIds + expectation.warningIds).toTypedArray())
        assertThat(report.problems.first().summary).isEqualTo("Dynamic include path is not statically resolvable")
        assertThat(report.problems.first().locations.old?.logicalLocation?.render())
            .startsWith("fixtures/inconclusive/dynamic-include/old/root.xhtml:2:")
        assertThat(report.problems.first().locations.old?.physicalLocation?.render())
            .startsWith("fixtures/inconclusive/dynamic-include/old/root.xhtml:2:")
        assertThat(report.problems.first().locations.old?.logicalLocation?.attributeName).isEqualTo("src")
        assertThat(report.problems.first().locations.old?.physicalLocation?.attributeName).isEqualTo("src")
        assertThat(report.problems.first().locations.old?.logicalLocation?.attributeLocationPrecision)
            .isEqualTo(AttributeLocationPrecision.ELEMENT_FALLBACK)
        assertThat(report.problems.first().locations.old?.physicalLocation?.attributeLocationPrecision)
            .isEqualTo(AttributeLocationPrecision.ELEMENT_FALLBACK)
        assertThat(report.problems.first().locations.old?.snippet).isEqualTo("#{bean.fragmentPath}")
        assertThat(report.problems.first().explanation).contains("comparison beneath this node is not trustworthy")
        assertThat(report.summary.headline).contains("Scaffolded analyzer pipeline only")
    }

    @Test
    fun `scaffold analyzer emits explicit unsupported diagnostics for extracted attribute EL outside the MVP subset`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup rendered="#{fn:length(bean.items)}" />
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = oldRoot,
                newRoot = newRoot,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasProblemCount(2)
            .hasWarningCount(2)
            .hasProblemIds(
                "W-UNSUPPORTED-EXTRACTED_EL",
                "W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD",
            )
        assertThat(report.problems.first().summary).isEqualTo("Extracted EL falls outside the MVP subset")
        assertThat(report.problems.first().locations.old?.logicalLocation?.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(report.problems.first().locations.old?.snippet).isEqualTo("#{fn:length(bean.items)}")
        assertThat(report.problems.first().explanation).contains("h:panelGroup @rendered")
        assertThat(report.problems.first().explanation).contains("Unexpected token ':'")
        assertThat(report.problems.first().explanation).contains("treated as unknown")
    }

    @Test
    fun `scaffold analyzer emits explicit unsupported diagnostics for extracted text EL outside the MVP subset`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets"
                            xmlns:h="http://xmlns.jcp.org/jsf/html">
              <h:panelGroup>#{fn:length(bean.items)}</h:panelGroup>
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "refactored/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = oldRoot,
                newRoot = newRoot,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasProblemCount(2)
            .hasWarningCount(2)
            .hasProblemIds(
                "W-UNSUPPORTED-EXTRACTED_EL",
                "W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD",
            )
        assertThat(report.problems.first().summary).isEqualTo("Extracted EL falls outside the MVP subset")
        assertThat(report.problems.first().locations.old?.logicalLocation?.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(report.problems.first().locations.old?.snippet).isEqualTo("#{fn:length(bean.items)}")
        assertThat(report.problems.first().explanation).contains("h:panelGroup text")
        assertThat(report.problems.first().explanation).contains("Unexpected token ':'")
    }

    @Test
    fun `scaffold analyzer emits a dedicated warning for missing include files before the generic scaffold warning`() {
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
            "new/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasProblemCount(2)
            .hasWarningCount(2)
            .hasProblemIds(
                "W-UNSUPPORTED-MISSING_INCLUDE",
                "W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD",
            )
        assertThat(report.problems.first().summary).isEqualTo("Included file could not be found")
        assertThat(report.problems.first().locations.old?.logicalLocation?.render()).startsWith("legacy/root.xhtml:2:")
        assertThat(report.problems.first().locations.old?.snippet).isEqualTo("/fragments/missing.xhtml")
        assertThat(report.problems.first().explanation)
            .contains("Static include /fragments/missing.xhtml resolved to fragments/missing.xhtml")
    }

    @Test
    fun `missing include fixture keeps dedicated diagnostic provenance stable`() {
        val scenario = FixtureScenarios.scenario("support/missing-include")

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = FixtureScenarios.repositoryRoot.relativize(scenario.oldRoot),
                newRoot = FixtureScenarios.repositoryRoot.relativize(scenario.newRoot),
                baseOld = FixtureScenarios.repositoryRoot,
                baseNew = FixtureScenarios.repositoryRoot,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasProblemCount(2)
            .hasWarningCount(2)
            .hasProblemIds(
                "W-UNSUPPORTED-MISSING_INCLUDE",
                "W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD",
            )
        assertThat(report.problems.first().locations.old?.logicalLocation?.render())
            .startsWith("fixtures/support/missing-include/old/root.xhtml:2:")
        assertThat(report.problems.first().locations.old?.physicalLocation?.render())
            .startsWith("fixtures/support/missing-include/old/root.xhtml:2:")
        assertThat(report.problems.first().locations.old?.logicalLocation?.attributeName).isEqualTo("src")
        assertThat(report.problems.first().locations.old?.physicalLocation?.attributeName).isEqualTo("src")
        assertThat(report.problems.first().locations.old?.logicalLocation?.attributeLocationPrecision)
            .isEqualTo(AttributeLocationPrecision.ELEMENT_FALLBACK)
        assertThat(report.problems.first().locations.old?.physicalLocation?.attributeLocationPrecision)
            .isEqualTo(AttributeLocationPrecision.ELEMENT_FALLBACK)
        assertThat(report.problems.first().locations.old?.snippet).isEqualTo("/fragments/missing.xhtml")
        assertThat(report.problems.first().explanation)
            .contains(
                "Static include /fragments/missing.xhtml resolved to " +
                    "fixtures/support/missing-include/old/fragments/missing.xhtml",
            )
    }

    @Test
    fun `include cycle fixture keeps dedicated diagnostic provenance stable`() {
        val scenario = FixtureScenarios.scenario("support/include-cycle")

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = FixtureScenarios.repositoryRoot.relativize(scenario.oldRoot),
                newRoot = FixtureScenarios.repositoryRoot.relativize(scenario.newRoot),
                baseOld = FixtureScenarios.repositoryRoot,
                baseNew = FixtureScenarios.repositoryRoot,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasProblemCount(2)
            .hasWarningCount(2)
            .hasProblemIds(
                "W-UNSUPPORTED-INCLUDE_CYCLE",
                "W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD",
            )
        assertThat(report.problems.first().summary).isEqualTo("Recursive include cycle detected")
        assertThat(report.problems.first().locations.old?.logicalLocation?.render())
            .startsWith("fixtures/support/include-cycle/old/fragments/outer.xhtml:2:")
        assertThat(report.problems.first().locations.old?.physicalLocation?.render())
            .startsWith("fixtures/support/include-cycle/old/fragments/outer.xhtml:2:")
        assertThat(report.problems.first().locations.old?.logicalLocation?.attributeName).isEqualTo("src")
        assertThat(report.problems.first().locations.old?.physicalLocation?.attributeName).isEqualTo("src")
        assertThat(report.problems.first().locations.old?.logicalLocation?.attributeLocationPrecision)
            .isEqualTo(AttributeLocationPrecision.ELEMENT_FALLBACK)
        assertThat(report.problems.first().locations.old?.physicalLocation?.attributeLocationPrecision)
            .isEqualTo(AttributeLocationPrecision.ELEMENT_FALLBACK)
        assertThat(report.problems.first().locations.old?.snippet).isEqualTo("../root.xhtml")
        assertThat(report.problems.first().explanation)
            .contains(
                "fixtures/support/include-cycle/old/root.xhtml -> " +
                    "fixtures/support/include-cycle/old/fragments/outer.xhtml -> " +
                    "fixtures/support/include-cycle/old/root.xhtml",
            )
    }
}
