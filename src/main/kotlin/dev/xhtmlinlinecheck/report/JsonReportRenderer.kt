package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.IncludeProvenanceStep
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemLocation

class JsonReportRenderer {
    fun render(report: AnalysisReport): String {
        val problemEntries = report.problems.joinToString(",\n") { problem ->
            renderProblem(problem).prependIndent("    ")
        }

        return """
        {
          "result": "${report.result.name}",
          "summary": "${escape(report.summary)}",
          "problems": [
$problemEntries
          ],
          "stats": {
            "checkedFacts": ${report.stats.checkedFacts},
            "matchedFacts": ${report.stats.matchedFacts},
            "problemCount": ${report.stats.problemCount},
            "warningCount": ${report.stats.warningCount}
          }
        }
        """.trimIndent()
    }

    private fun renderProblem(problem: Problem): String =
        """
        {
          "id": "${escape(problem.id)}",
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
          "physicalLocation": "${escape(location.physicalLocation.render())}",
          "snippet": ${renderNullableString(location.snippet)},
          "includeStack": [
$includeStack
          ]
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

    private fun renderNullableString(value: String?): String =
        value?.let { "\"${escape(it)}\"" } ?: "null"

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
