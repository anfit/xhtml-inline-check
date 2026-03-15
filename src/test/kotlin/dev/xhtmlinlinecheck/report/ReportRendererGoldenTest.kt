package dev.xhtmlinlinecheck.report

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class ReportRendererGoldenTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    fun `text reporter matches checked-in golden output`(goldenCase: ReporterGoldenCase) {
        val rendered = TextReportRenderer().render(goldenCase.report)

        assertThat(normalize(rendered)).isEqualTo(normalize(Files.readString(goldenCase.textGoldenPath)))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    fun `json reporter matches checked-in golden output`(goldenCase: ReporterGoldenCase) {
        val rendered = JsonReportRenderer().render(goldenCase.report)

        assertThat(normalize(rendered)).isEqualTo(normalize(Files.readString(goldenCase.jsonGoldenPath)))
    }

    companion object {
        @JvmStatic
        fun goldenCases(): Stream<ReporterGoldenCase> =
            Stream.of(
                ReporterGoldenCase(
                    name = "equivalent",
                    report = GoldenReportSamples.equivalentReport(),
                    textGoldenPath = goldenPath("equivalent.txt"),
                    jsonGoldenPath = goldenPath("equivalent.json"),
                ),
                ReporterGoldenCase(
                    name = "not-equivalent",
                    report = GoldenReportSamples.notEquivalentReport(),
                    textGoldenPath = goldenPath("not-equivalent.txt"),
                    jsonGoldenPath = goldenPath("not-equivalent.json"),
                ),
                ReporterGoldenCase(
                    name = "inconclusive",
                    report = GoldenReportSamples.inconclusiveReport(),
                    textGoldenPath = goldenPath("inconclusive.txt"),
                    jsonGoldenPath = goldenPath("inconclusive.json"),
                ),
                ReporterGoldenCase(
                    name = "ordered-diagnostics",
                    report = GoldenReportSamples.orderedDiagnosticsReport(),
                    textGoldenPath = goldenPath("ordered-diagnostics.txt"),
                    jsonGoldenPath = goldenPath("ordered-diagnostics.json"),
                ),
            )

        private fun goldenPath(fileName: String): Path =
            Path.of("src", "test", "resources", "dev", "xhtmlinlinecheck", "report", "golden", fileName)
    }
}

data class ReporterGoldenCase(
    val name: String,
    val report: dev.xhtmlinlinecheck.domain.AnalysisReport,
    val textGoldenPath: Path,
    val jsonGoldenPath: Path,
) {
    override fun toString(): String = name
}

private fun normalize(value: String): String =
    value.replace("\r\n", "\n").trimEnd('\n', '\r')
