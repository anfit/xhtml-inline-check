package dev.xhtmlinlinecheck.analyzer

import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.testing.FixtureExpectations
import dev.xhtmlinlinecheck.testing.FixtureScenarios
import dev.xhtmlinlinecheck.testing.assertThatReport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CanonicalFixtureComparisonTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("canonicalScenarios")
    fun `canonical fixtures keep result derivation and diagnostic ids stable`(scenarioName: String) {
        val scenario = FixtureScenarios.scenario(scenarioName)
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

        when (report.result) {
            AnalysisResult.EQUIVALENT -> assertThat(report.stats.warnings.blocking).isZero()
            AnalysisResult.NOT_EQUIVALENT -> assertThat(report.summary.counts.mismatched).isPositive()
            AnalysisResult.INCONCLUSIVE -> assertThat(report.stats.warnings.blocking).isPositive()
        }
    }

    companion object {
        @JvmStatic
        fun canonicalScenarios(): Stream<String> =
            Stream.of(
                "equivalent/safe-include-inline",
                "equivalent/safe-alpha-renaming",
                "not-equivalent/lost-ui-param",
                "not-equivalent/variable-capture-regression",
                "not-equivalent/form-ancestry-drift",
                "not-equivalent/naming-container-ancestry-drift",
                "not-equivalent/changed-for-target",
                "not-equivalent/changed-ajax-target",
                "inconclusive/dynamic-include",
                "inconclusive/inconclusive-but-not-proven-wrong",
            )
    }
}
