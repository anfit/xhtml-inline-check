package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.ProblemLocation
import java.util.Locale

class TextReportRenderer {
    fun render(report: AnalysisReport): String {
        val lines = buildList {
            add(report.result.name)
            add(report.summary.headline)
            add(renderCounts(report.stats.counts.checked, report.stats.counts.matched, report.stats.counts.mismatched))
            add(renderCoverage(report.stats.coverage))
            add(renderWarnings(report.stats.warnings.total, report.stats.warnings.blocking))
            if (report.problems.isNotEmpty()) {
                add("Problems:")
                report.problems.forEach { problem ->
                    add("${problem.id.value} [${problem.severity.name.lowercase()}/${problem.category.name.lowercase()}] ${problem.summary}")
                    problem.locations.old?.let { location ->
                        add("  old: ${renderLocation(location)}")
                    }
                    problem.locations.new?.let { location ->
                        add("  new: ${renderLocation(location)}")
                    }
                    add("  explanation: ${problem.explanation}")
                    problem.hint?.let { hint ->
                        add("  hint: $hint")
                    }
                }
            }
        }

        return lines.joinToString(System.lineSeparator())
    }

    private fun renderCounts(checked: Int, matched: Int, mismatched: Int): String =
        "Counts: checked=$checked, matched=$matched, mismatched=$mismatched"

    private fun renderCoverage(coverage: AggregateCoverage): String {
        val percent = coverage.percent?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: "n/a"
        return "Coverage: covered=${coverage.covered}/${coverage.total} ($percent)"
    }

    private fun renderWarnings(total: Int, blocking: Int): String =
        "Warnings: total=$total, blocking=$blocking"

    private fun renderLocation(location: ProblemLocation): String = buildString {
        append(location.render())
        location.snippet?.let { snippet ->
            append(" -> ").append(snippet)
        }
    }
}
