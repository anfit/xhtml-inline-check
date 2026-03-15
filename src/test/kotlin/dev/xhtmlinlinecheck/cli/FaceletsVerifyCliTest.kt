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
    fun `returns equivalent exit code when only the informational scaffold warning remains`() {
        val args = smokeArgs()
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(args, output)

        assertThat(exitCode).isEqualTo(0)
        assertThat(output.toString()).contains("EQUIVALENT")
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

        assertThat(exitCode).isEqualTo(0)
        assertThat(output.toString()).contains("\"result\" : \"EQUIVALENT\"")
    }

    @Test
    fun `accepts base directory options and anchors reported root paths to those bases`() {
        val tree = TemporaryProjectTree(tempDir)
        tree.write(
            "workspace/legacy/views/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        tree.write(
            "workspace/refactored/pages/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets" />
            """,
        )
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf(
                "views/root.xhtml",
                "pages/root.xhtml",
                "--base-old",
                tree.path("workspace/legacy").toString(),
                "--base-new",
                tree.path("workspace/refactored").toString(),
            ),
            output,
        )

        assertThat(exitCode).isEqualTo(0)
        assertThat(output.toString()).contains("views/root.xhtml")
        assertThat(output.toString()).contains("pages/root.xhtml")
        assertThat(output.toString()).doesNotContain(tree.path("workspace/legacy").toString())
        assertThat(output.toString()).doesNotContain(tree.path("workspace/refactored").toString())
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
