package dev.xhtmlinlinecheck.report

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.Problem

class JsonReportRenderer {
    private val mapper =
        jacksonObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .disable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

    fun render(
        report: AnalysisReport,
        options: ReportRenderOptions = ReportRenderOptions(),
    ): String {
        val sections = report.toReportSections(options)
        val payload = linkedMapOf<String, Any?>()
        payload["result"] = sections.result
        payload["summary"] = renderAggregatePayload(sections.headline, sections.summary)
        if (sections.display.maxProblems != null) {
            payload["display"] = renderDisplayPayload(sections.display)
        }
        payload["problems"] = sections.errors.map(::renderProblem)
        payload["warnings"] = sections.warnings.map(::renderProblem)
        payload["stats"] = renderAggregatePayload(sections.headline, sections.stats)

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
    }

    private fun renderDisplayPayload(display: ReportDisplayView): Map<String, Any?> =
        linkedMapOf(
            "maxProblems" to display.maxProblems,
            "displayedDiagnostics" to display.displayedDiagnostics,
            "totalDiagnostics" to display.totalDiagnostics,
            "omittedDiagnostics" to display.omittedDiagnostics,
        )

    private fun renderAggregatePayload(
        headline: String,
        aggregate: ReportAggregateView,
    ): Map<String, Any?> =
        linkedMapOf(
            "headline" to headline,
            "counts" to
                linkedMapOf(
                    "checked" to aggregate.checked,
                    "matched" to aggregate.matched,
                    "mismatched" to aggregate.mismatched,
                ),
            "coverage" to
                linkedMapOf(
                    "covered" to aggregate.covered,
                    "total" to aggregate.total,
                    "percent" to aggregate.percentValue,
                ),
            "warnings" to
                linkedMapOf(
                    "total" to aggregate.warningTotal,
                    "blocking" to aggregate.warningBlocking,
                ),
        )

    private fun renderProblem(problem: Problem): Map<String, Any?> =
        linkedMapOf(
            "id" to problem.id.value,
            "severity" to problem.severity.name,
            "category" to problem.category.name,
            "summary" to problem.summary,
            "locations" to
                linkedMapOf(
                    "old" to problem.locations.old?.renderDetails(),
                    "new" to problem.locations.new?.renderDetails(),
                ),
            "explanation" to problem.explanation,
            "hint" to problem.hint,
        )
}
