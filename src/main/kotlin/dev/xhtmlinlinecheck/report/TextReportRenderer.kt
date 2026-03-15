package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.Problem

class TextReportRenderer {
    fun render(
        report: AnalysisReport,
        options: ReportRenderOptions = ReportRenderOptions(),
    ): String {
        val sections = report.toReportSections(options)

        val lines = buildList {
            add(renderResult(report.result))
            add(sections.headline)
            add(renderAggregateLines(sections.summary))
            renderDisplayLimit(sections.display)?.let(::add)

            when (report.result) {
                AnalysisResult.EQUIVALENT -> {
                    if (sections.warnings.isNotEmpty()) {
                        add("Warnings:")
                        sections.warnings.forEach { warning ->
                            add(renderConciseDiagnostic(warning))
                        }
                    }
                }

                AnalysisResult.INCONCLUSIVE -> {
                    if (sections.warnings.isNotEmpty()) {
                        add("Warnings:")
                        sections.warnings.forEach { warning ->
                            add(renderDetailedDiagnostic(warning))
                        }
                    }
                }

                AnalysisResult.NOT_EQUIVALENT -> {
                    add("Problems: ${sections.errors.size}")
                    sections.errors.forEach { problem ->
                        add(renderDetailedDiagnostic(problem))
                    }
                    if (sections.warnings.isNotEmpty()) {
                        add("Warnings: ${sections.warnings.size}")
                        sections.warnings.forEach { warning ->
                            add(renderConciseDiagnostic(warning))
                        }
                    }
                }
            }
        }

        return lines.joinToString(System.lineSeparator())
    }

    private fun renderDisplayLimit(display: ReportDisplayView): String? {
        if (display.maxProblems == null) {
            return null
        }
        return buildString {
            append("Displayed diagnostics: ")
            append(display.displayedDiagnostics)
            append("/")
            append(display.totalDiagnostics)
            append(" (")
            append(display.omittedDiagnostics)
            append(" omitted by --max-problems=")
            append(display.maxProblems)
            append(")")
        }
    }

    private fun renderAggregateLines(aggregate: ReportAggregateView): String =
        buildString {
            append("Checked: ")
            append(aggregate.checked)
            append("  Matched: ")
            append(aggregate.matched)
            append("  Mismatched: ")
            append(aggregate.mismatched)
            append(System.lineSeparator())
            append("Coverage: ")
            append(aggregate.covered)
            append("/")
            append(aggregate.total)
            append(" (")
            append(aggregate.percentText)
            append(")")
            append(System.lineSeparator())
            append("Warnings: ")
            append(aggregate.warningTotal)
            append(" total")
            append(" (")
            append(aggregate.warningBlocking)
            append(" blocking)")
        }

    private fun renderConciseDiagnostic(problem: Problem): String = buildString {
        append(problem.id.value)
        append(" ")
        append(problem.summary)
        firstLocation(problem)?.let { location ->
            append(" at ")
            append(location.render())
        }
    }

    private fun renderDetailedDiagnostic(problem: Problem): String =
        buildString {
            append(problem.id.value)
            append(" ")
            append(problem.summary)
            append(System.lineSeparator())
            problem.locations.old?.let { location ->
                append("old: ")
                append(location.renderWithContext())
                append(System.lineSeparator())
            }
            problem.locations.new?.let { location ->
                append("new: ")
                append(location.renderWithContext())
                append(System.lineSeparator())
            }
            append(problem.explanation)
            problem.hint?.let { hint ->
                append(System.lineSeparator())
                append("Hint: ")
                append(hint)
            }
        }

    private fun renderResult(result: AnalysisResult): String = result.name.replace('_', ' ')

    private fun firstLocation(problem: Problem) = problem.locations.old ?: problem.locations.new
}
