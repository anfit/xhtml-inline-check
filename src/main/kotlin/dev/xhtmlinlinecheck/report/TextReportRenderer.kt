package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.ProblemLocation

class TextReportRenderer {
    fun render(report: AnalysisReport): String {
        val lines = buildList {
            add(report.result.name)
            add(report.summary)
            if (report.problems.isNotEmpty()) {
                add("Problems:")
                report.problems.forEach { problem ->
                    add("${problem.id} [${problem.severity.name.lowercase()}/${problem.category.name.lowercase()}] ${problem.summary}")
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

    private fun renderLocation(location: ProblemLocation): String = buildString {
        append(location.render())
        location.snippet?.let { snippet ->
            append(" -> ").append(snippet)
        }
    }
}
