package dev.xhtmlinlinecheck.cli

import dev.xhtmlinlinecheck.testing.FixtureScenarios
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.stream.Stream

class FaceletsVerifyDeterministicOutputTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("analysisCases")
    fun `analysis mode stays byte-stable across repeated executions`(testCase: CliDeterminismCase) {
        val invocations = runRepeatedly(testCase)
        val first = invocations.first()

        assertThat(invocations.map { it.exitCode }).containsOnly(testCase.expectedExitCode)
        assertThat(invocations.map { it.stdout }).containsOnly(first.stdout)
        assertThat(invocations.map { it.stderr }).containsOnly(first.stderr)
        assertThat(first.stderr).isBlank()
        assertThat(first.stdout).isNotBlank()
        assertThat(first.stdout).contains(testCase.requiredMarker)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("explainCases")
    fun `explain mode stays byte-stable across repeated executions`(testCase: CliDeterminismCase) {
        val invocations = runRepeatedly(testCase)
        val first = invocations.first()

        assertThat(invocations.map { it.exitCode }).containsOnly(0)
        assertThat(invocations.map { it.stdout }).containsOnly(first.stdout)
        assertThat(invocations.map { it.stderr }).containsOnly(first.stderr)
        assertThat(first.stderr).isBlank()
        assertThat(first.stdout).contains(testCase.requiredMarker)
    }

    private fun runRepeatedly(testCase: CliDeterminismCase): List<CliProcessResult> =
        (1..3).map { invokeMain(testCase.args) }

    private fun invokeMain(args: List<String>): CliProcessResult {
        val process =
            ProcessBuilder(
                javaExecutable().toString(),
                "-cp",
                System.getProperty("java.class.path"),
                "dev.xhtmlinlinecheck.cli.MainKt",
                *args.toTypedArray(),
            )
                .directory(FixtureScenarios.repositoryRoot.toFile())
                .start()

        process.outputStream.close()

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        process.inputStream.copyTo(stdout)
        process.errorStream.copyTo(stderr)
        return CliProcessResult(
            exitCode = process.waitFor(),
            stdout = stdout.toString(StandardCharsets.UTF_8),
            stderr = stderr.toString(StandardCharsets.UTF_8),
        )
    }

    private fun javaExecutable(): Path =
        Path.of(System.getProperty("java.home"), "bin", executableName("java"))

    private fun executableName(command: String): String =
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "$command.exe"
        } else {
            command
        }

    companion object {
        @JvmStatic
        fun analysisCases(): Stream<CliDeterminismCase> =
            Stream.of(
                analysisCase(
                    name = "equivalent text fixture",
                    scenarioName = "equivalent/safe-include-inline",
                    requiredMarker = "EQUIVALENT",
                ),
                analysisCase(
                    name = "equivalent json fixture",
                    scenarioName = "equivalent/safe-include-inline",
                    format = "json",
                    requiredMarker = "\"result\" : \"EQUIVALENT\"",
                ),
                analysisCase(
                    name = "mismatch text fixture",
                    scenarioName = "not-equivalent/changed-ajax-target",
                    requiredMarker = "P-TARGET-RESOLUTION_CHANGED",
                    expectedExitCode = 1,
                ),
                analysisCase(
                    name = "mismatch json fixture with max problems",
                    scenarioName = "not-equivalent/changed-ajax-target",
                    format = "json",
                    extraArgs = listOf("--max-problems", "1"),
                    requiredMarker = "\"display\" : {",
                    expectedExitCode = 1,
                ),
                analysisCase(
                    name = "inconclusive text fixture",
                    scenarioName = "inconclusive/dynamic-include",
                    requiredMarker = "W-UNSUPPORTED-DYNAMIC_INCLUDE",
                    expectedExitCode = 2,
                ),
                analysisCase(
                    name = "inconclusive json fixture",
                    scenarioName = "inconclusive/dynamic-include",
                    format = "json",
                    requiredMarker = "\"result\" : \"INCONCLUSIVE\"",
                    expectedExitCode = 2,
                ),
            )

        @JvmStatic
        fun explainCases(): Stream<CliDeterminismCase> =
            Stream.of(
                CliDeterminismCase(
                    name = "explain text output",
                    args = listOf("--explain", "P-TARGET-RESOLUTION_CHANGED"),
                    expectedExitCode = 0,
                    requiredMarker = "Summary: Component target no longer resolves the same way",
                ),
                CliDeterminismCase(
                    name = "explain json output",
                    args = listOf("--format", "json", "--explain", "W-UNSUPPORTED-DYNAMIC_INCLUDE"),
                    expectedExitCode = 0,
                    requiredMarker = "\"id\" : \"W-UNSUPPORTED-DYNAMIC_INCLUDE\"",
                ),
            )

        private fun analysisCase(
            name: String,
            scenarioName: String,
            requiredMarker: String,
            expectedExitCode: Int = 0,
            format: String? = null,
            extraArgs: List<String> = emptyList(),
        ): CliDeterminismCase {
            val scenario = FixtureScenarios.scenario(scenarioName)
            val args =
                buildList {
                    add(relativeToRepositoryRoot(scenario.oldRoot))
                    add(relativeToRepositoryRoot(scenario.newRoot))
                    add("--base-old")
                    add(relativeToRepositoryRoot(FixtureScenarios.repositoryRoot))
                    add("--base-new")
                    add(relativeToRepositoryRoot(FixtureScenarios.repositoryRoot))
                    if (format != null) {
                        add("--format")
                        add(format)
                    }
                    addAll(extraArgs)
                }
            return CliDeterminismCase(
                name = name,
                args = args,
                expectedExitCode = expectedExitCode,
                requiredMarker = requiredMarker,
            )
        }

        private fun relativeToRepositoryRoot(path: Path): String =
            FixtureScenarios.repositoryRoot.relativize(path.toAbsolutePath().normalize()).toString()
    }

    private data class CliProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    data class CliDeterminismCase(
        val name: String,
        val args: List<String>,
        val expectedExitCode: Int,
        val requiredMarker: String,
    ) {
        override fun toString(): String = name
    }
}
