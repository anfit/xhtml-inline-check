package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.AnalysisSummary
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemIds
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.ProblemLocations
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.Severity
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.WarningIds
import dev.xhtmlinlinecheck.domain.WarningTotals
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class JsonReportRendererOrderingTest {
    @Test
    fun `renders top-level summary and problem fields in stable order`() {
        val rendered = JsonReportRenderer().render(reportWithProblems())

        assertThat(rendered).contains("\"result\": \"NOT_EQUIVALENT\"")
        assertThat(rendered).contains("\"id\": \"P-SCOPE-BINDING_MISMATCH\"")
        assertThat(rendered).contains("\"id\": \"W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD\"")

        assertContainsInOrder(
            rendered,
            "\"result\"",
            "\"summary\"",
            "\"problems\"",
            "\"stats\"",
        )
        assertContainsInOrder(
            rendered,
            "\"headline\"",
            "\"counts\"",
            "\"coverage\"",
            "\"warnings\"",
        )
        assertContainsInOrder(
            rendered,
            "\"id\"",
            "\"severity\"",
            "\"category\"",
            "\"summary\"",
            "\"locations\"",
            "\"explanation\"",
            "\"hint\"",
        )
    }

    @Test
    fun `preserves caller supplied problem order deterministically`() {
        val rendered = JsonReportRenderer().render(reportWithProblems())

        val firstProblemId = rendered.indexOf("\"id\": \"P-SCOPE-BINDING_MISMATCH\"")
        val secondProblemId = rendered.indexOf("\"id\": \"W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD\"")

        assertThat(firstProblemId).isGreaterThanOrEqualTo(0)
        assertThat(secondProblemId).isGreaterThan(firstProblemId)
    }

    private fun reportWithProblems(): AnalysisReport =
        AnalysisReport(
            result = AnalysisResult.NOT_EQUIVALENT,
            summary = AnalysisSummary(
                headline = "Comparison found mismatches",
                counts = AggregateCounts(
                    checked = 5,
                    matched = 3,
                    mismatched = 2,
                ),
                coverage = AggregateCoverage(
                    covered = 5,
                    total = 5,
                ),
                warnings = WarningTotals(
                    total = 1,
                    blocking = 1,
                ),
            ),
            problems = listOf(
                Problem(
                    id = ProblemIds.SCOPE_BINDING_MISMATCH,
                    severity = Severity.ERROR,
                    category = ProblemCategory.SCOPE,
                    summary = "Scope binding changed",
                    locations = ProblemLocations(
                        old = location(
                            side = AnalysisSide.OLD,
                            path = Path.of("legacy", "order.xhtml"),
                            snippet = "#{row.label}",
                        ),
                        new = location(
                            side = AnalysisSide.NEW,
                            path = Path.of("refactored", "order.xhtml"),
                            snippet = "#{item.label}",
                        ),
                    ),
                    explanation = "The same expression now resolves against a different local binding.",
                    hint = "Restore the original repeat scope.",
                ),
                Problem(
                    id = WarningIds.UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD,
                    severity = Severity.WARNING,
                    category = ProblemCategory.UNSUPPORTED,
                    summary = "Analyzer pipeline is still scaffolded",
                    locations = ProblemLocations(
                        new = location(
                            side = AnalysisSide.NEW,
                            path = Path.of("refactored", "order.xhtml"),
                            snippet = "<ui:composition />",
                        ),
                    ),
                    explanation = "Current analysis stages still return a scaffold warning.",
                    hint = null,
                ),
            ),
        )

    private fun location(
        side: AnalysisSide,
        path: Path,
        snippet: String,
    ): ProblemLocation =
        ProblemLocation(
            provenance = Provenance.forRoot(
                SourceDocument.fromPath(
                    side = side,
                    path = path,
                ),
            ),
            snippet = snippet,
        )

    private fun assertContainsInOrder(
        actual: String,
        vararg fragments: String,
    ) {
        var previousIndex = -1
        fragments.forEach { fragment ->
            val currentIndex = actual.indexOf(fragment, previousIndex + 1)
            assertThat(currentIndex)
                .describedAs("Expected fragment %s after index %s", fragment, previousIndex)
                .isGreaterThan(previousIndex)
            previousIndex = currentIndex
        }
    }
}
