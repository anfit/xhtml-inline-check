package dev.xhtmlinlinecheck.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ProblemModelsTest {
    @Test
    fun `diagnostic ids use stable kind-category-slug format`() {
        assertThat(ProblemIds.STRUCTURE_UNMATCHED_NODE.value)
            .isEqualTo("P-STRUCTURE-UNMATCHED_NODE")
        assertThat(ProblemIds.STRUCTURE_FORM_ANCESTRY_CHANGED.value)
            .isEqualTo("P-STRUCTURE-FORM_ANCESTRY_CHANGED")
        assertThat(WarningIds.UNSUPPORTED_EXTRACTED_EL.value)
            .isEqualTo("W-UNSUPPORTED-EXTRACTED_EL")
        assertThat(WarningIds.UNSUPPORTED_DYNAMIC_INCLUDE.value)
            .isEqualTo("W-UNSUPPORTED-DYNAMIC_INCLUDE")
        assertThat(WarningIds.UNSUPPORTED_INCLUDE_CYCLE.value)
            .isEqualTo("W-UNSUPPORTED-INCLUDE_CYCLE")
        assertThat(WarningIds.UNSUPPORTED_MISSING_INCLUDE.value)
            .isEqualTo("W-UNSUPPORTED-MISSING_INCLUDE")
        assertThat(WarningIds.UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD.value)
            .isEqualTo("W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD")
        assertThat(DiagnosticId.parse("P-TARGET-RESOLUTION_CHANGED"))
            .isEqualTo(ProblemIds.TARGET_RESOLUTION_CHANGED)
    }

    @Test
    fun `problem locations expose logical and physical locations from provenance`() {
        val rootDocument = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "root.xhtml"),
        )
        val fragmentDocument = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "fragment.xhtml"),
        )
        val problemLocation = ProblemLocation(
            provenance =
                Provenance(
                    physicalLocation = SourceLocation(document = fragmentDocument),
                    logicalLocation =
                        SourceLocation(
                            document = rootDocument,
                            span = SourceSpan(SourcePosition(line = 4, column = 7)),
                            attributeName = "src",
                            attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
                        ),
                ),
            snippet = "#{row.total}",
        )

        assertThat(problemLocation.logicalLocation.render()).isEqualTo("legacy/root.xhtml:4:7 @src (element fallback)")
        assertThat(problemLocation.physicalLocation.render()).isEqualTo("legacy/fragment.xhtml")
        assertThat(problemLocation.render()).isEqualTo("legacy/root.xhtml:4:7 @src (element fallback)")
        assertThat(problemLocation.snippet).isEqualTo("#{row.total}")
        assertThat(problemLocation.bindingOrigin).isNull()
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
            id = ProblemIds.SCOPE_BINDING_MISMATCH,
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

        assertThat(problem.id.value).isEqualTo("P-SCOPE-BINDING_MISMATCH")
        assertThat(problem.locations.old).isEqualTo(oldLocation)
        assertThat(problem.locations.new).isEqualTo(newLocation)
        assertThat(problem.locations.old?.snippet).isEqualTo("#{row.label}")
        assertThat(problem.locations.new?.snippet).isEqualTo("#{item.label}")
    }

    @Test
    fun `problem locations can carry binding origins with provenance-backed descriptors`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "table.xhtml"),
        )
        val bindingOrigin = BindingOrigin(
            descriptor = "ui:repeat var=row",
            provenance = Provenance.forRoot(document),
        )
        val problemLocation = ProblemLocation(
            provenance = Provenance.forRoot(document),
            snippet = "#{row.label}",
            bindingOrigin = bindingOrigin,
        )

        assertThat(problemLocation.bindingOrigin).isEqualTo(bindingOrigin)
        assertThat(problemLocation.bindingOrigin?.render()).isEqualTo("ui:repeat var=row from legacy/table.xhtml")
    }
}
