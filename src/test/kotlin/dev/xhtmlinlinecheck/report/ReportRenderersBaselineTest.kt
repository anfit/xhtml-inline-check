package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
                    explanation = "The component no longer has the same form ancestry.",
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
    }

    companion object {
        @JvmStatic
        fun renderers(): Stream<String> = Stream.of("text", "json")
    }
}
