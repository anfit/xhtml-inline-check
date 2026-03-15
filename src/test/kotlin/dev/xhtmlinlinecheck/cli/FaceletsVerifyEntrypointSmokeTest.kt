package dev.xhtmlinlinecheck.cli

import dev.xhtmlinlinecheck.testing.FixtureScenarios
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class FaceletsVerifyEntrypointSmokeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `starts via main entrypoint and returns equivalent exit code for a representative equivalent fixture`() {
        val scenario = FixtureScenarios.scenario("equivalent/safe-include-inline")

        val invocation =
            invokeMain(
                scenario.oldRoot.toString(),
                scenario.newRoot.toString(),
                "--base-old",
                FixtureScenarios.repositoryRoot.toString(),
                "--base-new",
                FixtureScenarios.repositoryRoot.toString(),
            )

        assertThat(invocation.exitCode).isEqualTo(0)
        assertThat(invocation.output).contains("EQUIVALENT")
        assertThat(invocation.output).contains("W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD")
    }

    @Test
    fun `starts via main entrypoint and returns mismatch exit code for a representative not-equivalent fixture`() {
        val scenario = FixtureScenarios.scenario("not-equivalent/changed-ajax-target")

        val invocation = invokeMain(scenario.oldRoot.toString(), scenario.newRoot.toString())

        assertThat(invocation.exitCode).isEqualTo(1)
        assertThat(invocation.output).contains("NOT EQUIVALENT")
        assertThat(invocation.output).contains("P-TARGET-RESOLUTION_CHANGED")
    }

    @Test
    fun `starts via main entrypoint and returns usage error for invalid input`() {
        val invocation = invokeMain("--unknown-flag")

        assertThat(invocation.exitCode).isEqualTo(64)
        assertThat(invocation.output).contains("Usage: facelets-verify")
    }

    @Test
    fun `starts via main entrypoint and returns analysis failed output for malformed xhtml`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "old/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <h:panelGroup xmlns:h="http://xmlns.jcp.org/jsf/html">
            </ui:composition>
            """,
        )
        val newRoot = tree.write(
            "new/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )

        val invocation = invokeMain(oldRoot.toString(), newRoot.toString())

        assertThat(invocation.exitCode).isEqualTo(2)
        assertThat(invocation.output).contains("ANALYSIS_FAILED")
        assertThat(invocation.output).contains("root.xhtml")
    }

    private fun invokeMain(vararg args: String): CliProcessResult {
        val process = ProcessBuilder(
            javaExecutable().toString(),
            "-cp",
            System.getProperty("java.class.path"),
            "dev.xhtmlinlinecheck.cli.MainKt",
            *args,
        )
            .redirectErrorStream(true)
            .start()

        val output = ByteArrayOutputStream()
        process.inputStream.copyTo(output)
        return CliProcessResult(
            exitCode = process.waitFor(),
            output = output.toString(StandardCharsets.UTF_8),
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

    private data class CliProcessResult(
        val exitCode: Int,
        val output: String,
    )
}
