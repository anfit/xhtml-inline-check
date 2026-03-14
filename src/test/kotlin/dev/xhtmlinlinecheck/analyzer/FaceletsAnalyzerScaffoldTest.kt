package dev.xhtmlinlinecheck.analyzer

import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import dev.xhtmlinlinecheck.testing.assertThatReport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class FaceletsAnalyzerScaffoldTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scaffold analyzer returns stable inconclusive report for a minimal pair`() {
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

        val report = FaceletsAnalyzer.scaffold().analyze(
            AnalysisRequest(
                oldRoot = oldRoot,
                newRoot = newRoot,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasSummaryContaining("Scaffold")
            .hasProblemCount(1)
            .hasWarningCount(1)
            .hasProblemIds("W00")
    }
}
