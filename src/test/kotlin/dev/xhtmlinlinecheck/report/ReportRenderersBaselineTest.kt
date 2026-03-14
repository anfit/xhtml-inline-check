package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisSummary
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.AttributeLocationPrecision
import dev.xhtmlinlinecheck.domain.ProblemIds
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.ProblemLocations
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.Severity
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.domain.SourcePosition
import dev.xhtmlinlinecheck.domain.SourceSpan
import dev.xhtmlinlinecheck.domain.WarningTotals
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

class ReportRenderersBaselineTest {
    @Test
    fun `renderers surface attribute fallback metadata when only element coordinates are available`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "order.xhtml"),
        )
        val location =
            SourceLocation(
                document = document,
                span = SourceSpan(SourcePosition(line = 12, column = 7)),
                attributeName = "rendered",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            )
        val report =
            AnalysisReport(
                result = AnalysisResult.INCONCLUSIVE,
                summary = AnalysisSummary(
                    headline = "Fallback metadata check",
                    counts = AggregateCounts(checked = 0, matched = 0, mismatched = 0),
                    coverage = AggregateCoverage(covered = 0, total = 0),
                    warnings = WarningTotals(total = 1, blocking = 1),
                ),
                problems = listOf(
                    Problem(
                        id = ProblemIds.SCOPE_BINDING_MISMATCH,
                        severity = Severity.ERROR,
                        category = ProblemCategory.SCOPE,
                        summary = "Attribute location stays explicit",
                        locations = ProblemLocations(old = ProblemLocation(Provenance(location, location), "#{bean.flag}")),
                        explanation = "Reporter should preserve fallback metadata.",
                    ),
                ),
                stats = AnalysisStats(
                    counts = AggregateCounts(checked = 0, matched = 0, mismatched = 0),
                    coverage = AggregateCoverage(covered = 0, total = 0),
                    warnings = WarningTotals(total = 1, blocking = 1),
                ),
            )

        assertThat(TextReportRenderer().render(report)).contains("@rendered (element fallback)")
        assertThat(JsonReportRenderer().render(report)).contains("\"attributeLocationPrecision\": \"ELEMENT_FALLBACK\"")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("renderers")
    fun `renders baseline report fields deterministically`(
        rendererName: String,
    ) {
        val report = AnalysisReport(
            result = AnalysisResult.NOT_EQUIVALENT,
            summary = AnalysisSummary(
                headline = "Found one mismatch",
                counts = AggregateCounts(
                    checked = 4,
                    matched = 3,
                    mismatched = 1,
                ),
                coverage = AggregateCoverage(
                    covered = 4,
                    total = 4,
                ),
                warnings = WarningTotals(
                    total = 0,
                    blocking = 0,
                ),
            ),
            problems = listOf(
                Problem(
                    id = ProblemIds.STRUCTURE_FORM_ANCESTRY_CHANGED,
                    severity = Severity.ERROR,
                    category = ProblemCategory.STRUCTURE,
                    summary = "Component moved outside form",
                    locations = ProblemLocations(
                        old = ProblemLocation(
                            provenance = Provenance.forRoot(
                                SourceDocument.fromPath(
                                    side = AnalysisSide.OLD,
                                    path = Path.of("legacy", "order.xhtml"),
                                ),
                            ),
                            snippet = "<h:form><p:commandButton id=\"saveBtn\" /></h:form>",
                        ),
                        new = ProblemLocation(
                            provenance = Provenance.forRoot(
                                SourceDocument.fromPath(
                                    side = AnalysisSide.NEW,
                                    path = Path.of("refactored", "order.xhtml"),
                                ),
                            ),
                            snippet = "<p:commandButton id=\"saveBtn\" />",
                        ),
                    ),
                    explanation = "The component no longer has the same form ancestry.",
                    hint = "Restore the original form wrapper around the command button.",
                ),
            ),
            stats = AnalysisStats(
                counts = AggregateCounts(
                    checked = 4,
                    matched = 3,
                    mismatched = 1,
                ),
                coverage = AggregateCoverage(
                    covered = 4,
                    total = 4,
                ),
                warnings = WarningTotals(
                    total = 0,
                    blocking = 0,
                ),
            ),
        )

        val rendered = when (rendererName) {
            "text" -> TextReportRenderer().render(report)
            "json" -> JsonReportRenderer().render(report)
            else -> error("Unsupported renderer: $rendererName")
        }

        assertThat(rendered).contains("NOT_EQUIVALENT")
        assertThat(rendered).contains("Found one mismatch")
        assertThat(rendered).contains("P-STRUCTURE-FORM_ANCESTRY_CHANGED")
        assertThat(rendered).contains("legacy/order.xhtml")
        assertThat(rendered).contains("refactored/order.xhtml")
        assertThat(rendered).contains("Component moved outside form")
        when (rendererName) {
            "text" -> {
                assertThat(rendered).contains("Counts: checked=4, matched=3, mismatched=1")
                assertThat(rendered).contains("Warnings: total=0, blocking=0")
            }

            "json" -> {
                assertThat(rendered).contains("\"counts\"")
                assertThat(rendered).contains("\"warnings\"")
                assertThat(rendered).contains("\"mismatched\": 1")
            }
        }
    }

    companion object {
        @JvmStatic
        fun renderers(): Stream<String> = Stream.of("text", "json")
    }
}
