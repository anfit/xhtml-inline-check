package dev.xhtmlinlinecheck.cli

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.analyzer.FaceletsAnalyzer
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.DiagnosticCatalog
import dev.xhtmlinlinecheck.domain.DiagnosticId
import dev.xhtmlinlinecheck.report.JsonDiagnosticExplanationRenderer
import dev.xhtmlinlinecheck.report.JsonReportRenderer
import dev.xhtmlinlinecheck.report.ReportRenderOptions
import dev.xhtmlinlinecheck.report.TextDiagnosticExplanationRenderer
import dev.xhtmlinlinecheck.report.TextReportRenderer
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
import java.nio.file.Path

enum class OutputFormat {
    TEXT,
    JSON,
}

sealed interface CliInvocation {
    val format: OutputFormat
}

data class AnalysisInvocation(
    val oldRoot: Path,
    val newRoot: Path,
    val baseOld: Path?,
    val baseNew: Path?,
    override val format: OutputFormat,
    val maxProblems: Int?,
    val failOnWarning: Boolean,
) : CliInvocation {
    fun toAnalysisRequest(): AnalysisRequest =
        AnalysisRequest(
            oldRoot = oldRoot.normalize(),
            newRoot = newRoot.normalize(),
            baseOld = baseOld?.toAbsolutePath()?.normalize(),
            baseNew = baseNew?.toAbsolutePath()?.normalize(),
        )
}

data class ExplainInvocation(
    val explainId: String,
    override val format: OutputFormat,
) : CliInvocation

class FaceletsVerifyCli(
    private val analyzer: FaceletsAnalyzer =
        FaceletsAnalyzer.scaffold(
            TagRuleRegistry.forExecutionRoot(Path.of("").toAbsolutePath().normalize()),
        ),
    private val textRenderer: TextReportRenderer = TextReportRenderer(),
    private val jsonRenderer: JsonReportRenderer = JsonReportRenderer(),
    private val textExplanationRenderer: TextDiagnosticExplanationRenderer = TextDiagnosticExplanationRenderer(),
    private val jsonExplanationRenderer: JsonDiagnosticExplanationRenderer = JsonDiagnosticExplanationRenderer(),
) {
    private val jsonMapper =
        jacksonObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .disable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

    fun run(args: List<String>, output: Appendable = System.out): Int {
        val parsed = parseArgs(args)
        if (parsed == null) {
            writeLine(output, usage())
            return 64
        }

        return when (parsed) {
            is ExplainInvocation -> renderExplanation(parsed, output)
            is AnalysisInvocation -> runAnalysis(parsed, output)
        }
    }

    private fun runAnalysis(
        invocation: AnalysisInvocation,
        output: Appendable,
    ): Int {
        return try {
            val report = analyzer.analyze(invocation.toAnalysisRequest())
            val renderOptions = ReportRenderOptions(maxProblems = invocation.maxProblems)
            val rendered =
                when (invocation.format) {
                    OutputFormat.TEXT -> textRenderer.render(report, renderOptions)
                    OutputFormat.JSON -> jsonRenderer.render(report, renderOptions)
                }

            writeLine(output, rendered)
            exitCode(report.result, report.warnings.isNotEmpty(), invocation.failOnWarning)
        } catch (exception: Exception) {
            writeLine(output, renderAnalysisFailure(invocation.format, exception))
            AnalysisResult.INCONCLUSIVE.exitCode
        }
    }

    private fun parseArgs(args: List<String>): CliInvocation? {
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
        var verbose = false
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

                "--verbose" -> {
                    verbose = true
                    index += 1
                }

                "--explain" -> {
                    explainId = args.getOrNull(index + 1) ?: return null
                    index += 2
                }

                else -> {
                    if (args[index].startsWith("--")) {
                        return null
                    }
                    roots += args[index]
                    index += 1
                }
            }
        }

        if (explainId != null) {
            if (roots.isNotEmpty() || baseOld != null || baseNew != null || maxProblems != null || failOnWarning || verbose) {
                return null
            }
            return ExplainInvocation(
                explainId = explainId,
                format = format,
            )
        }

        if (roots.size != 2) {
            return null
        }

        return AnalysisInvocation(
            oldRoot = Path.of(roots[0]),
            newRoot = Path.of(roots[1]),
            baseOld = baseOld,
            baseNew = baseNew,
            format = format,
            maxProblems = maxProblems,
            failOnWarning = failOnWarning,
        )
    }

    private fun renderExplanation(parsed: ExplainInvocation, output: Appendable): Int {
        val diagnosticId = DiagnosticId.parse(parsed.explainId)
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

    private fun renderAnalysisFailure(
        format: OutputFormat,
        exception: Exception,
    ): String {
        val message = exception.message ?: "Analysis failed"
        return when (format) {
            OutputFormat.TEXT ->
                buildString {
                    append("ANALYSIS_FAILED")
                    append(System.lineSeparator())
                    append(message)
                }

            OutputFormat.JSON ->
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    linkedMapOf(
                        "error" to
                            linkedMapOf(
                                "type" to "ANALYSIS_FAILED",
                                "message" to message,
                            ),
                    ),
                )
        }
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
        "Usage: facelets-verify <oldRoot.xhtml> <newRoot.xhtml> [--base-old <dir>] [--base-new <dir>] [--format text|json] [--max-problems <n>] [--fail-on-warning] [--verbose] | facelets-verify --explain <problem-id> [--format text|json]"

    private fun writeLine(output: Appendable, value: String) {
        output.append(value).append(System.lineSeparator())
    }
}
