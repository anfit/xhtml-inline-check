package dev.xhtmlinlinecheck.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AnalysisReportModelsTest {
    @Test
    fun `derives default coverage from counts for summary and stats`() {
        val counts = AggregateCounts(
            checked = 8,
            matched = 5,
            mismatched = 2,
        )

        val summary = AnalysisSummary(
            headline = "Comparison finished",
            counts = counts,
            warnings = WarningTotals(total = 1, blocking = 0),
        )
        val stats = AnalysisStats(
            counts = counts,
            warnings = WarningTotals(total = 1, blocking = 0),
        )

        assertThat(summary.coverage.covered).isEqualTo(7)
        assertThat(summary.coverage.total).isEqualTo(8)
        assertThat(summary.coverage.percent).isEqualTo(87.5)
        assertThat(stats.coverage).isEqualTo(summary.coverage)
    }

    @Test
    fun `report defaults stats from summary aggregates`() {
        val report = AnalysisReport(
            result = AnalysisResult.INCONCLUSIVE,
            summary = AnalysisSummary(
                headline = "Comparison finished",
                counts = AggregateCounts(
                    checked = 3,
                    matched = 2,
                    mismatched = 0,
                ),
                warnings = WarningTotals(
                    total = 1,
                    blocking = 1,
                ),
            ),
        )

        assertThat(report.stats.counts).isEqualTo(report.summary.counts)
        assertThat(report.stats.coverage).isEqualTo(report.summary.coverage)
        assertThat(report.stats.warnings).isEqualTo(report.summary.warnings)
    }

    @Test
    fun `report exposes deterministic diagnostic ordering and split error warning views`() {
        val oldDocument = SourceDocument.fromPath(AnalysisSide.OLD, Path.of("legacy.xhtml"))
        val newDocument = SourceDocument.fromPath(AnalysisSide.NEW, Path.of("refactored.xhtml"))
        val report =
            AnalysisReport(
                result = AnalysisResult.NOT_EQUIVALENT,
                summary = AnalysisSummary(
                    headline = "Comparison finished",
                    counts = AggregateCounts(
                        checked = 2,
                        matched = 1,
                        mismatched = 1,
                    ),
                    warnings = WarningTotals(total = 1, blocking = 0),
                ),
                problems = listOf(
                    Problem(
                        id = WarningIds.UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD,
                        severity = Severity.WARNING,
                        category = ProblemCategory.UNSUPPORTED,
                        summary = "Warning",
                        locations = ProblemLocations(new = ProblemLocation(Provenance.forRoot(newDocument))),
                        explanation = "warning",
                    ),
                    Problem(
                        id = ProblemIds.SCOPE_BINDING_MISMATCH,
                        severity = Severity.ERROR,
                        category = ProblemCategory.SCOPE,
                        summary = "Mismatch",
                        locations = ProblemLocations(old = ProblemLocation(Provenance.forRoot(oldDocument))),
                        explanation = "error",
                    ),
                ),
            )

        assertThat(report.orderedProblems.map { it.id.value }).containsExactly(
            ProblemIds.SCOPE_BINDING_MISMATCH.value,
            WarningIds.UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD.value,
        )
        assertThat(report.errors.map { it.id.value }).containsExactly(ProblemIds.SCOPE_BINDING_MISMATCH.value)
        assertThat(report.warnings.map { it.id.value }).containsExactly(WarningIds.UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD.value)
    }

    @Test
    fun `rejects impossible aggregate relationships`() {
        assertThatThrownBy {
            AggregateCounts(
                checked = 2,
                matched = 2,
                mismatched = 1,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WarningTotals(
                total = 1,
                blocking = 2,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            AnalysisSummary(
                headline = "Comparison finished",
                counts = AggregateCounts(
                    checked = 2,
                    matched = 1,
                    mismatched = 0,
                ),
                coverage = AggregateCoverage(
                    covered = 2,
                    total = 2,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
