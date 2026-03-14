package dev.xhtmlinlinecheck.cli

import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

class FaceletsVerifyCliTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `returns inconclusive exit code for scaffolded pipeline`() {
        val args = smokeArgs()
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(args, output)

        assertThat(exitCode).isEqualTo(2)
        assertThat(output.toString()).contains("INCONCLUSIVE")
        assertThat(output.toString()).contains("W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD")
    }

    @Test
    fun `prints usage when roots are missing`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(emptyList(), output)

        assertThat(exitCode).isEqualTo(64)
        assertThat(output.toString()).contains("Usage: facelets-verify")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonArgumentOrders")
    fun `renders json when requested regardless of option ordering`(
        order: String,
    ) {
        val roots = smokeArgs()
        val output = StringBuilder()
        val args = when (order) {
            "roots-first" -> roots + listOf("--format", "json")
            "format-first" -> listOf("--format", "json") + roots
            else -> error("Unsupported argument order: $order")
        }

        val exitCode = FaceletsVerifyCli().run(args, output)

        assertThat(exitCode).isEqualTo(2)
        assertThat(output.toString()).contains("\"result\": \"INCONCLUSIVE\"")
    }

    private fun smokeArgs(): List<String> {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "old/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val newRoot = tree.write(
            "new/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        return listOf(oldRoot.toString(), newRoot.toString())
    }

    companion object {
        @JvmStatic
        fun jsonArgumentOrders(): Stream<String> = Stream.of("roots-first", "format-first")
    }
}
