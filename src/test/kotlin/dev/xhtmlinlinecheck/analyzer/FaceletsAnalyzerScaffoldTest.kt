package dev.xhtmlinlinecheck.analyzer

import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.testing.TemporaryProjectTree
import dev.xhtmlinlinecheck.testing.assertThatReport
import org.assertj.core.api.Assertions.assertThat
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
            .hasProblemIds("W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD")
        assertThat(report.problems.single().locations.old?.logicalLocation?.render()).isEqualTo("old/root.xhtml")
        assertThat(report.problems.single().locations.new?.logicalLocation?.render()).isEqualTo("new/root.xhtml")
    }

    @Test
    fun `scaffold analyzer emits a dedicated warning for include cycles before the generic scaffold warning`() {
        val tree = TemporaryProjectTree(tempDir)
        val oldRoot = tree.write(
            "legacy/root.xhtml",
            """
            <ui:composition xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/fragments/outer.xhtml" />
            </ui:composition>
            """,
        )
        tree.write(
            "fragments/outer.xhtml",
            """
            <ui:fragment xmlns:ui="http://xmlns.jcp.org/jsf/facelets">
              <ui:include src="/legacy/root.xhtml" />
            </ui:fragment>
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
                oldRoot = tempDir.relativize(oldRoot),
                newRoot = tempDir.relativize(newRoot),
                baseOld = tempDir,
                baseNew = tempDir,
            ),
        )

        assertThatReport(report)
            .hasResult(AnalysisResult.INCONCLUSIVE)
            .hasProblemCount(2)
            .hasWarningCount(2)
            .hasProblemIds(
                "W-UNSUPPORTED-INCLUDE_CYCLE",
                "W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD",
            )
        assertThat(report.problems.first().summary).isEqualTo("Recursive include cycle detected")
        assertThat(report.problems.first().locations.old?.logicalLocation?.render()).startsWith("fragments/outer.xhtml:2:")
        assertThat(report.problems.first().explanation)
            .contains("legacy/root.xhtml -> fragments/outer.xhtml -> legacy/root.xhtml")
    }
}
