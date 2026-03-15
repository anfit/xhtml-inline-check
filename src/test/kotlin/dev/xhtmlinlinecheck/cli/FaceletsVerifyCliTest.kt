package dev.xhtmlinlinecheck.cli

import dev.xhtmlinlinecheck.testing.FixtureScenarios
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
    fun `fail on warning upgrades clean-but-warned runs to a failing exit code`() {
        val args = smokeArgs() + "--fail-on-warning"
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(args, output)

        assertThat(exitCode).isEqualTo(2)
        assertThat(output.toString()).contains("EQUIVALENT")
        assertThat(output.toString()).contains("W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD")
    }

    @Test
    fun `accepts verbose flag without changing the semantic outcome`() {
        val args = smokeArgs() + "--verbose"
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(args, output)

        assertThat(exitCode).isEqualTo(0)
        assertThat(output.toString()).contains("EQUIVALENT")
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
        assertThat(output.toString()).doesNotContain(tree.path("workspace/legacy").toString())
        assertThat(output.toString()).doesNotContain(tree.path("workspace/refactored").toString())
    }

    @Test
    fun `keeps repo relative fixture roots valid when repository bases are also provided`() {
        val scenario = FixtureScenarios.scenario("equivalent/safe-include-inline")
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf(
                relativeToRepositoryRoot(scenario.oldRoot),
                relativeToRepositoryRoot(scenario.newRoot),
                "--base-old",
                relativeToRepositoryRoot(FixtureScenarios.repositoryRoot),
                "--base-new",
                relativeToRepositoryRoot(FixtureScenarios.repositoryRoot),
            ),
            output,
        )

        assertThat(exitCode).isEqualTo(0)
        assertThat(output.toString()).contains("EQUIVALENT")
        assertThat(output.toString()).doesNotContain("ANALYSIS_FAILED")
    }

    @Test
    fun `returns inconclusive exit code and text failure output when analysis fails`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf(
                "missing-old.xhtml",
                "missing-new.xhtml",
            ),
            output,
        )

        assertThat(exitCode).isEqualTo(2)
        assertThat(output.toString()).contains("ANALYSIS_FAILED")
        assertThat(output.toString()).contains("Missing source file for old: missing-old.xhtml")
    }

    @Test
    fun `renders failure payload as json when analysis fails in json mode`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf(
                "missing-old.xhtml",
                "missing-new.xhtml",
                "--format",
                "json",
            ),
            output,
        )

        assertThat(exitCode).isEqualTo(2)
        assertThat(output.toString()).contains("\"type\" : \"ANALYSIS_FAILED\"")
        assertThat(output.toString()).contains("\"message\" : \"Missing source file for old: missing-old.xhtml")
    }

    @Test
    fun `max problems caps displayed diagnostics after stable ordering`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf(
                "fixtures/not-equivalent/changed-ajax-target/old/root.xhtml",
                "fixtures/not-equivalent/changed-ajax-target/new/root.xhtml",
                "--max-problems",
                "1",
            ),
            output,
        )

        assertThat(exitCode).isEqualTo(1)
        assertThat(output.toString()).contains("Displayed diagnostics: 1/2 (1 omitted by --max-problems=1)")
        assertThat(output.toString()).contains("P-TARGET-RESOLUTION_CHANGED")
        assertThat(output.toString()).doesNotContain("W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD")
    }

    @Test
    fun `explain renders stable diagnostic help in text mode`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf("--explain", "P-TARGET-RESOLUTION_CHANGED"),
            output,
        )

        assertThat(exitCode).isEqualTo(0)
        assertThat(output.toString()).contains("P-TARGET-RESOLUTION_CHANGED")
        assertThat(output.toString()).contains("Severity: ERROR")
        assertThat(output.toString()).contains("Category: TARGET")
        assertThat(output.toString()).contains("Summary: Component target no longer resolves the same way")
    }

    @Test
    fun `explain renders stable diagnostic help in json mode`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf("--format", "json", "--explain", "W-UNSUPPORTED-DYNAMIC_INCLUDE"),
            output,
        )

        assertThat(exitCode).isEqualTo(0)
        assertThat(output.toString()).contains("\"id\" : \"W-UNSUPPORTED-DYNAMIC_INCLUDE\"")
        assertThat(output.toString()).contains("\"severity\" : \"WARNING\"")
        assertThat(output.toString()).contains("\"blocking\" : true")
    }

    @Test
    fun `explain rejects unknown diagnostic ids`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf("--explain", "P-NOT-REAL"),
            output,
        )

        assertThat(exitCode).isEqualTo(64)
        assertThat(output.toString()).contains("Unknown diagnostic id: P-NOT-REAL")
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

        private fun relativeToRepositoryRoot(path: Path): String =
            FixtureScenarios.repositoryRoot.relativize(path.toAbsolutePath().normalize()).toString()
    }
}
