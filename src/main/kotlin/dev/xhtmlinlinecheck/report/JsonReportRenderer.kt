package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport

class JsonReportRenderer {
    fun render(report: AnalysisReport): String =
        """
        {
          "result": "${report.result.name}",
          "summary": "${escape(report.summary)}",
          "problemCount": ${report.problems.size},
          "warningCount": ${report.stats.warningCount}
        }
        """.trimIndent()

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
