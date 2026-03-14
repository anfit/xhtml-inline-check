package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.ProblemLocations
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.Severity
import dev.xhtmlinlinecheck.domain.SourceDocument
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

class ReportRenderersBaselineTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("renderers")
    fun `renders baseline report fields deterministically`(
        rendererName: String,
    ) {
        val report = AnalysisReport(
            result = AnalysisResult.NOT_EQUIVALENT,
            summary = "Found one mismatch",
            problems = listOf(
                Problem(
                    id = "P01",
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
                checkedFacts = 4,
                matchedFacts = 3,
                problemCount = 1,
                warningCount = 0,
            ),
        )

        val rendered = when (rendererName) {
            "text" -> TextReportRenderer().render(report)
            "json" -> JsonReportRenderer().render(report)
            else -> error("Unsupported renderer: $rendererName")
        }

        assertThat(rendered).contains("NOT_EQUIVALENT")
        assertThat(rendered).contains("Found one mismatch")
        assertThat(rendered).contains("P01")
        assertThat(rendered).contains("legacy/order.xhtml")
        assertThat(rendered).contains("refactored/order.xhtml")
        assertThat(rendered).contains("Component moved outside form")
    }

    companion object {
        @JvmStatic
        fun renderers(): Stream<String> = Stream.of("text", "json")
    }
}
