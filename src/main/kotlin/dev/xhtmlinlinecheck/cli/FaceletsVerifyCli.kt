package dev.xhtmlinlinecheck.cli

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.analyzer.FaceletsAnalyzer
import dev.xhtmlinlinecheck.report.JsonReportRenderer
import dev.xhtmlinlinecheck.report.TextReportRenderer
import java.nio.file.Path

enum class OutputFormat {
    TEXT,
    JSON,
}

data class CliArguments(
    val oldRoot: Path,
    val newRoot: Path,
    val format: OutputFormat,
)

class FaceletsVerifyCli(
    private val analyzer: FaceletsAnalyzer = FaceletsAnalyzer.scaffold(),
    private val textRenderer: TextReportRenderer = TextReportRenderer(),
    private val jsonRenderer: JsonReportRenderer = JsonReportRenderer(),
) {
    fun run(args: List<String>, output: Appendable = System.out): Int {
        val parsed = parseArgs(args)
        if (parsed == null) {
            writeLine(output, usage())
            return 64
        }

        val report = analyzer.analyze(
            AnalysisRequest(
                oldRoot = parsed.oldRoot,
                newRoot = parsed.newRoot,
            ),
        )

        val rendered = when (parsed.format) {
            OutputFormat.TEXT -> textRenderer.render(report)
            OutputFormat.JSON -> jsonRenderer.render(report)
        }

        writeLine(output, rendered)
        return report.result.exitCode
    }

    private fun parseArgs(args: List<String>): CliArguments? {
        if (args.size < 2) {
            return null
        }

        val roots = mutableListOf<String>()
        var format = OutputFormat.TEXT
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

                else -> {
                    roots += current
                    index += 1
                }
            }
        }

        if (roots.size != 2) {
            return null
        }

        return CliArguments(
            oldRoot = Path.of(roots[0]),
            newRoot = Path.of(roots[1]),
            format = format,
        )
    }

    private fun usage(): String =
        "Usage: facelets-verify <oldRoot.xhtml> <newRoot.xhtml> [--format text|json]"

    private fun writeLine(output: Appendable, value: String) {
        output.append(value).append(System.lineSeparator())
    }
}
