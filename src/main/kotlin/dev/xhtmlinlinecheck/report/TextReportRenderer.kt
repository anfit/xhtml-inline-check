package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport

class TextReportRenderer {
    fun render(report: AnalysisReport): String {
        val lines = buildList {
            add(report.result.name)
            add(report.summary)
            if (report.problems.isNotEmpty()) {
                add("Problems:")
                report.problems.forEach { problem ->
                    add("${problem.id} ${problem.summary}")
                }
            }
        }

        return lines.joinToString(System.lineSeparator())
    }
}
