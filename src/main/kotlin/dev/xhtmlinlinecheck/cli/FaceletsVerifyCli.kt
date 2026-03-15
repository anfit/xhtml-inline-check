package dev.xhtmlinlinecheck.cli

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.analyzer.FaceletsAnalyzer
import dev.xhtmlinlinecheck.domain.DiagnosticCatalog
import dev.xhtmlinlinecheck.domain.DiagnosticId
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.report.JsonDiagnosticExplanationRenderer
import dev.xhtmlinlinecheck.report.JsonReportRenderer
import dev.xhtmlinlinecheck.report.ReportRenderOptions
import dev.xhtmlinlinecheck.report.TextDiagnosticExplanationRenderer
import dev.xhtmlinlinecheck.report.TextReportRenderer
import java.nio.file.Path

enum class OutputFormat {
    TEXT,
    JSON,
}

data class CliArguments(
    val oldRoot: Path?,
    val newRoot: Path?,
    val baseOld: Path?,
    val baseNew: Path?,
    val format: OutputFormat,
    val maxProblems: Int?,
    val failOnWarning: Boolean,
    val explainId: String?,
)

class FaceletsVerifyCli(
    private val analyzer: FaceletsAnalyzer = FaceletsAnalyzer.scaffold(),
    private val textRenderer: TextReportRenderer = TextReportRenderer(),
    private val jsonRenderer: JsonReportRenderer = JsonReportRenderer(),
    private val textExplanationRenderer: TextDiagnosticExplanationRenderer = TextDiagnosticExplanationRenderer(),
    private val jsonExplanationRenderer: JsonDiagnosticExplanationRenderer = JsonDiagnosticExplanationRenderer(),
) {
    fun run(args: List<String>, output: Appendable = System.out): Int {
        val parsed = parseArgs(args)
        if (parsed == null) {
            writeLine(output, usage())
            return 64
        }

        if (parsed.explainId != null) {
            return renderExplanation(parsed, output)
        }

        val report = analyzer.analyze(
            AnalysisRequest(
                oldRoot = requireNotNull(parsed.oldRoot),
                newRoot = requireNotNull(parsed.newRoot),
                baseOld = parsed.baseOld,
                baseNew = parsed.baseNew,
            ),
        )
        val renderOptions = ReportRenderOptions(maxProblems = parsed.maxProblems)

        val rendered = when (parsed.format) {
            OutputFormat.TEXT -> textRenderer.render(report, renderOptions)
            OutputFormat.JSON -> jsonRenderer.render(report, renderOptions)
        }

        writeLine(output, rendered)
        return exitCode(report.result, report.warnings.isNotEmpty(), parsed.failOnWarning)
    }

    private fun parseArgs(args: List<String>): CliArguments? {
        if (args.isEmpty()) {
            return null
        }

        val roots = mutableListOf<String>()
        var format = OutputFormat.TEXT
        var baseOld: Path? = null
        var baseNew: Path? = null
        var maxProblems: Int? = null
        var failOnWarning = false
        var explainId: String? = null
        var index = 0
        while (index < args.size) {
            when (args[index]) {
                "--format" -> {
                    val value = args.getOrNull(index + 1) ?: return null
                    format = when (value.lowercase()) {
                        "text" -> OutputFormat.TEXT
                        "json" -> OutputFormat.JSON
                        else -> return null
                    }
                    index += 2
                }

                "--base-old" -> {
                    val value = args.getOrNull(index + 1) ?: return null
                    baseOld = Path.of(value)
                    index += 2
                }

                "--base-new" -> {
                    val value = args.getOrNull(index + 1) ?: return null
                    baseNew = Path.of(value)
                    index += 2
                }

                "--max-problems" -> {
                    val value = args.getOrNull(index + 1)?.toIntOrNull() ?: return null
                    if (value < 0) {
                        return null
                    }
                    maxProblems = value
                    index += 2
                }

                "--fail-on-warning" -> {
                    failOnWarning = true
                    index += 1
                }

                "--explain" -> {
                    explainId = args.getOrNull(index + 1) ?: return null
                    index += 2
                }

                else -> {
                    roots += args[index]
                    index += 1
                }
            }
        }

        if (explainId != null) {
            if (roots.isNotEmpty() || baseOld != null || baseNew != null) {
                return null
            }
            return CliArguments(
                oldRoot = null,
                newRoot = null,
                baseOld = null,
                baseNew = null,
                format = format,
                maxProblems = maxProblems,
                failOnWarning = failOnWarning,
                explainId = explainId,
            )
        }

        if (roots.size != 2) {
            return null
        }

        return CliArguments(
            oldRoot = Path.of(roots[0]),
            newRoot = Path.of(roots[1]),
            baseOld = baseOld,
            baseNew = baseNew,
            format = format,
            maxProblems = maxProblems,
            failOnWarning = failOnWarning,
            explainId = null,
        )
    }

    private fun renderExplanation(parsed: CliArguments, output: Appendable): Int {
        val diagnosticId = DiagnosticId.parse(requireNotNull(parsed.explainId))
        val definition =
            diagnosticId?.let(DiagnosticCatalog::definitionFor)
        if (definition == null) {
            writeLine(output, "Unknown diagnostic id: ${parsed.explainId}")
            return 64
        }

        val rendered = when (parsed.format) {
            OutputFormat.TEXT -> textExplanationRenderer.render(definition)
            OutputFormat.JSON -> jsonExplanationRenderer.render(definition)
        }
        writeLine(output, rendered)
        return 0
    }

    private fun exitCode(
        result: AnalysisResult,
        hasWarnings: Boolean,
        failOnWarning: Boolean,
    ): Int {
        if (!failOnWarning || !hasWarnings) {
            return result.exitCode
        }
        return when (result) {
            AnalysisResult.NOT_EQUIVALENT -> AnalysisResult.NOT_EQUIVALENT.exitCode
            AnalysisResult.EQUIVALENT,
            AnalysisResult.INCONCLUSIVE,
            -> AnalysisResult.INCONCLUSIVE.exitCode
        }
    }

    private fun usage(): String =
        "Usage: facelets-verify <oldRoot.xhtml> <newRoot.xhtml> [--base-old <dir>] [--base-new <dir>] [--format text|json] [--max-problems <n>] [--fail-on-warning] | facelets-verify --explain <problem-id> [--format text|json]"

    private fun writeLine(output: Appendable, value: String) {
        output.append(value).append(System.lineSeparator())
    }
}
