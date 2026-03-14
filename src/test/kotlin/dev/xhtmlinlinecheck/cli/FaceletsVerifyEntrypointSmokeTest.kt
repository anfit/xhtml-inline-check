package dev.xhtmlinlinecheck.cli

import dev.xhtmlinlinecheck.testing.FixtureScenarios
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class FaceletsVerifyEntrypointSmokeTest {
    @Test
    fun `starts via main entrypoint and returns scaffold exit code`() {
        val scenario = FixtureScenarios.scenario("support/smoke")
        val process = ProcessBuilder(
            javaExecutable().toString(),
            "-cp",
            System.getProperty("java.class.path"),
            "dev.xhtmlinlinecheck.cli.MainKt",
            scenario.oldRoot.toString(),
            scenario.newRoot.toString(),
        )
            .redirectErrorStream(true)
            .start()

        val output = ByteArrayOutputStream()
        process.inputStream.copyTo(output)
        val exitCode = process.waitFor()
        val rendered = output.toString(StandardCharsets.UTF_8)

        assertThat(exitCode).isEqualTo(2)
        assertThat(rendered).contains("INCONCLUSIVE")
        assertThat(rendered).contains("W00")
    }

    private fun javaExecutable(): Path =
        Path.of(System.getProperty("java.home"), "bin", "java.exe")
}
