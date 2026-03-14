package dev.xhtmlinlinecheck.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ProblemModelsTest {
    @Test
    fun `problem locations expose logical and physical locations from provenance`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "fragment.xhtml"),
        )
        val problemLocation = ProblemLocation(
            provenance = Provenance.forRoot(document),
            snippet = "#{row.total}",
        )

        assertThat(problemLocation.logicalLocation.render()).isEqualTo("legacy/fragment.xhtml")
        assertThat(problemLocation.physicalLocation.render()).isEqualTo("legacy/fragment.xhtml")
        assertThat(problemLocation.render()).isEqualTo("legacy/fragment.xhtml")
        assertThat(problemLocation.snippet).isEqualTo("#{row.total}")
    }

    @Test
    fun `problem keeps side-specific locations grouped under a shared structure`() {
        val oldLocation = ProblemLocation(
            provenance = Provenance.forRoot(
                SourceDocument.fromPath(
                    side = AnalysisSide.OLD,
                    path = Path.of("legacy", "order.xhtml"),
                ),
            ),
            snippet = "#{row.label}",
        )
        val newLocation = ProblemLocation(
            provenance = Provenance.forRoot(
                SourceDocument.fromPath(
                    side = AnalysisSide.NEW,
                    path = Path.of("refactored", "order.xhtml"),
                ),
            ),
            snippet = "#{item.label}",
        )

        val problem = Problem(
            id = "P17",
            severity = Severity.ERROR,
            category = ProblemCategory.SCOPE,
            summary = "Local variable resolves to different binding",
            locations = ProblemLocations(
                old = oldLocation,
                new = newLocation,
            ),
            explanation = "The moved expression now resolves against a different iteration scope.",
            hint = "Preserve the original iteration scope when inlining the fragment.",
        )

        assertThat(problem.locations.old).isEqualTo(oldLocation)
        assertThat(problem.locations.new).isEqualTo(newLocation)
        assertThat(problem.locations.old?.snippet).isEqualTo("#{row.label}")
        assertThat(problem.locations.new?.snippet).isEqualTo("#{item.label}")
    }
}
