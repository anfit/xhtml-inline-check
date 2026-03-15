package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.BindingOrigin
import dev.xhtmlinlinecheck.domain.IncludeProvenanceStep
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.Severity
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.domain.WarningTotals
import java.util.Locale

data class ReportRenderOptions(
    val maxProblems: Int? = null,
) {
    init {
        require(maxProblems == null || maxProblems >= 0) {
            "maxProblems must be non-negative"
        }
    }
}

internal data class ReportDisplayView(
    val maxProblems: Int?,
    val displayedDiagnostics: Int,
    val totalDiagnostics: Int,
    val omittedDiagnostics: Int,
)

internal data class ReportAggregateView(
    val checked: Int,
    val matched: Int,
    val mismatched: Int,
    val covered: Int,
    val total: Int,
    val percentText: String,
    val percentValue: Double?,
    val warningTotal: Int,
    val warningBlocking: Int,
)

internal data class ReportSections(
    val result: String,
    val headline: String,
    val display: ReportDisplayView,
    val summary: ReportAggregateView,
    val stats: ReportAggregateView,
    val errors: List<Problem>,
    val warnings: List<Problem>,
)

internal fun AnalysisReport.toReportSections(options: ReportRenderOptions = ReportRenderOptions()): ReportSections {
    val displayedProblems =
        if (options.maxProblems == null) {
            orderedProblems
        } else {
            orderedProblems.take(options.maxProblems)
        }

    return ReportSections(
        result = result.name,
        headline = summary.headline,
        display =
            ReportDisplayView(
                maxProblems = options.maxProblems,
                displayedDiagnostics = displayedProblems.size,
                totalDiagnostics = orderedProblems.size,
                omittedDiagnostics = orderedProblems.size - displayedProblems.size,
            ),
        summary = summaryView(summary.counts, summary.coverage, summary.warnings),
        stats = summaryView(stats.counts, stats.coverage, stats.warnings),
        errors = displayedProblems.filter { it.severity == Severity.ERROR },
        warnings = displayedProblems.filter { it.severity == Severity.WARNING },
    )
}

private fun summaryView(
    counts: AggregateCounts,
    coverage: AggregateCoverage,
    warnings: WarningTotals,
): ReportAggregateView =
    ReportAggregateView(
        checked = counts.checked,
        matched = counts.matched,
        mismatched = counts.mismatched,
        covered = coverage.covered,
        total = coverage.total,
        percentText = coverage.percent?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: "n/a",
        percentValue = coverage.percent,
        warningTotal = warnings.total,
        warningBlocking = warnings.blocking,
    )

internal fun ProblemLocation.renderWithContext(): String = buildString {
    append(render())
    snippet?.let { renderedSnippet ->
        append(" -> ").append(renderedSnippet)
    }
    bindingOrigin?.let { origin ->
        append(" [binding: ").append(origin.render()).append("]")
    }
}

internal fun SourceLocation.renderDetails(): Map<String, Any?> =
    linkedMapOf(
        "document" to document.displayPath,
        "line" to span?.start?.line,
        "column" to span?.start?.column,
        "attributeName" to attributeName,
        "attributeLocationPrecision" to attributeLocationPrecision?.name,
    )

internal fun BindingOrigin.renderDetails(): Map<String, Any?> =
    linkedMapOf(
        "descriptor" to descriptor,
        "rendered" to render(),
        "location" to provenance?.logicalLocation?.renderDetails(),
    )

internal fun IncludeProvenanceStep.renderDetails(): Map<String, Any?> =
    linkedMapOf(
        "includeSite" to includeSite.render(),
        "includedDocument" to includedDocument.displayPath,
        "parameterNames" to parameterNames,
    )

internal fun ProblemLocation.renderDetails(): Map<String, Any?> =
    linkedMapOf(
        "logicalLocation" to logicalLocation.render(),
        "logicalLocationDetails" to logicalLocation.renderDetails(),
        "physicalLocation" to physicalLocation.render(),
        "physicalLocationDetails" to physicalLocation.renderDetails(),
        "snippet" to snippet,
        "bindingOrigin" to bindingOrigin?.renderDetails(),
        "includeStack" to provenance.includeStack.map { it.renderDetails() },
    )
