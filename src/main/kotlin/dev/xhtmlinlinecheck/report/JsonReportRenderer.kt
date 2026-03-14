package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.IncludeProvenanceStep
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.domain.WarningTotals
import java.util.Locale

class JsonReportRenderer {
    fun render(report: AnalysisReport): String {
        val problemEntries = report.problems.joinToString(",\n") { problem ->
            renderProblem(problem).prependIndent("    ")
        }

        return """
        {
          "result": "${report.result.name}",
          "summary": ${renderSummary(report)},
          "problems": [
$problemEntries
          ],
          "stats": ${renderStats(report)}
        }
        """.trimIndent()
    }

    private fun renderSummary(report: AnalysisReport): String =
        """
        {
          "headline": "${escape(report.summary.headline)}",
          "counts": ${renderCounts(report.summary.counts)},
          "coverage": ${renderCoverage(report.summary.coverage)},
          "warnings": ${renderWarnings(report.summary.warnings)}
        }
        """.trimIndent()

    private fun renderStats(report: AnalysisReport): String =
        """
        {
          "counts": ${renderCounts(report.stats.counts)},
          "coverage": ${renderCoverage(report.stats.coverage)},
          "warnings": ${renderWarnings(report.stats.warnings)}
        }
        """.trimIndent()

    private fun renderProblem(problem: Problem): String =
        """
        {
          "id": "${escape(problem.id.value)}",
          "severity": "${problem.severity.name}",
          "category": "${problem.category.name}",
          "summary": "${escape(problem.summary)}",
          "locations": {
            "old": ${renderLocation(problem.locations.old)},
            "new": ${renderLocation(problem.locations.new)}
          },
          "explanation": "${escape(problem.explanation)}",
          "hint": ${renderNullableString(problem.hint)}
        }
        """.trimIndent()

    private fun renderCounts(counts: AggregateCounts): String =
        """
        {
          "checked": ${counts.checked},
          "matched": ${counts.matched},
          "mismatched": ${counts.mismatched}
        }
        """.trimIndent()

    private fun renderCoverage(coverage: AggregateCoverage): String {
        val percent = coverage.percent?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "null"
        return """
        {
          "covered": ${coverage.covered},
          "total": ${coverage.total},
          "percent": $percent
        }
        """.trimIndent()
    }

    private fun renderWarnings(warnings: WarningTotals): String =
        """
        {
          "total": ${warnings.total},
          "blocking": ${warnings.blocking}
        }
        """.trimIndent()

    private fun renderLocation(location: ProblemLocation?): String {
        if (location == null) {
            return "null"
        }

        val includeStack = location.provenance.includeStack.joinToString(",\n") { step ->
            renderIncludeStep(step).prependIndent("        ")
        }

        return """
        {
          "logicalLocation": "${escape(location.logicalLocation.render())}",
          "logicalLocationDetails": ${renderSourceLocationDetails(location.logicalLocation)},
          "physicalLocation": "${escape(location.physicalLocation.render())}",
          "physicalLocationDetails": ${renderSourceLocationDetails(location.physicalLocation)},
          "snippet": ${renderNullableString(location.snippet)},
          "bindingOrigin": ${renderBindingOrigin(location.bindingOrigin)},
          "includeStack": [
$includeStack
          ]
        }
        """.trimIndent()
    }

    private fun renderSourceLocationDetails(location: SourceLocation): String {
        val start = location.span?.start
        return """
        {
          "document": "${escape(location.document.displayPath)}",
          "line": ${start?.line ?: "null"},
          "column": ${start?.column ?: "null"},
          "attributeName": ${renderNullableString(location.attributeName)},
          "attributeLocationPrecision": ${location.attributeLocationPrecision?.let { "\"${it.name}\"" } ?: "null"}
        }
        """.trimIndent()
    }

    private fun renderIncludeStep(step: IncludeProvenanceStep): String =
        """
        {
          "includeSite": "${escape(step.includeSite.render())}",
          "includedDocument": "${escape(step.includedDocument.displayPath)}",
          "parameterNames": [${step.parameterNames.joinToString(", ") { "\"${escape(it)}\"" }}]
        }
        """.trimIndent()

    private fun renderBindingOrigin(origin: dev.xhtmlinlinecheck.domain.BindingOrigin?): String {
        if (origin == null) {
            return "null"
        }
        return """
        {
          "descriptor": "${escape(origin.descriptor)}",
          "rendered": "${escape(origin.render())}",
          "location": ${origin.provenance?.logicalLocation?.let(::renderSourceLocationDetails) ?: "null"}
        }
        """.trimIndent()
    }

    private fun renderNullableString(value: String?): String =
        value?.let { "\"${escape(it)}\"" } ?: "null"

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
