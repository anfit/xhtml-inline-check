package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisSummary
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.AttributeLocationPrecision
import dev.xhtmlinlinecheck.domain.BindingOrigin
import dev.xhtmlinlinecheck.domain.ProblemIds
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.ProblemLocations
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.Severity
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.domain.SourcePosition
import dev.xhtmlinlinecheck.domain.SourceSpan
import dev.xhtmlinlinecheck.domain.WarningIds
import dev.xhtmlinlinecheck.domain.WarningTotals
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ReportRenderersBaselineTest {
    @Test
    fun `renderers surface attribute fallback metadata when only element coordinates are available`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "order.xhtml"),
        )
        val location =
            SourceLocation(
                document = document,
                span = SourceSpan(SourcePosition(line = 12, column = 7)),
                attributeName = "rendered",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            )
        val report =
            AnalysisReport(
                result = AnalysisResult.INCONCLUSIVE,
                summary = AnalysisSummary(
                    headline = "Fallback metadata check",
                    counts = AggregateCounts(checked = 0, matched = 0, mismatched = 0),
                    coverage = AggregateCoverage(covered = 0, total = 0),
                    warnings = WarningTotals(total = 1, blocking = 1),
                ),
                problems = listOf(
                    Problem(
                        id = WarningIds.UNSUPPORTED_EXTRACTED_EL,
                        severity = Severity.WARNING,
                        category = ProblemCategory.UNSUPPORTED,
                        summary = "Attribute location stays explicit",
                        locations = ProblemLocations(old = ProblemLocation(Provenance(location, location), "#{bean.flag}")),
                        explanation = "Reporter should preserve fallback metadata.",
                    ),
                ),
                stats = AnalysisStats(
                    counts = AggregateCounts(checked = 0, matched = 0, mismatched = 0),
                    coverage = AggregateCoverage(covered = 0, total = 0),
                    warnings = WarningTotals(total = 1, blocking = 1),
                ),
            )

        assertThat(TextReportRenderer().render(report)).contains("@rendered (element fallback)")
        assertThat(JsonReportRenderer().render(report)).contains("\"attributeLocationPrecision\" : \"ELEMENT_FALLBACK\"")
    }

    @Test
    fun `renderers surface binding origins when a comparison diagnostic explains a resolved binding`() {
        val oldDocument = SourceDocument.fromPath(AnalysisSide.OLD, Path.of("legacy", "order.xhtml"))
        val newDocument = SourceDocument.fromPath(AnalysisSide.NEW, Path.of("refactored", "order.xhtml"))
        val report =
            AnalysisReport(
                result = AnalysisResult.NOT_EQUIVALENT,
                summary = AnalysisSummary(
                    headline = "Binding mismatch",
                    counts = AggregateCounts(checked = 1, matched = 0, mismatched = 1),
                    coverage = AggregateCoverage(covered = 1, total = 1),
                    warnings = WarningTotals(total = 0, blocking = 0),
                ),
                problems = listOf(
                    Problem(
                        id = ProblemIds.SCOPE_BINDING_MISMATCH,
                        severity = Severity.ERROR,
                        category = ProblemCategory.SCOPE,
                        summary = "Local variable resolves to different binding",
                        locations = ProblemLocations(
                            old =
                                ProblemLocation(
                                    provenance = Provenance.forRoot(oldDocument),
                                    snippet = "#{row.label}",
                                    bindingOrigin =
                                        BindingOrigin(
                                            descriptor = "ui:repeat var=row",
                                            provenance = Provenance.forRoot(oldDocument),
                                        ),
                                ),
                            new =
                                ProblemLocation(
                                    provenance = Provenance.forRoot(newDocument),
                                    snippet = "#{item.label}",
                                    bindingOrigin =
                                        BindingOrigin(
                                            descriptor = "ui:repeat var=item",
                                            provenance = Provenance.forRoot(newDocument),
                                        ),
                                ),
                        ),
                        explanation = "The expression now resolves against a different iterator binding.",
                    ),
                ),
                stats = AnalysisStats(
                    counts = AggregateCounts(checked = 1, matched = 0, mismatched = 1),
                    coverage = AggregateCoverage(covered = 1, total = 1),
                    warnings = WarningTotals(total = 0, blocking = 0),
                ),
            )

        assertThat(TextReportRenderer().render(report)).contains("[binding: ui:repeat var=row from legacy/order.xhtml]")
        assertThat(JsonReportRenderer().render(report)).contains("\"bindingOrigin\"")
        assertThat(JsonReportRenderer().render(report)).contains("\"descriptor\": \"ui:repeat var=item\"")
        assertThat(JsonReportRenderer().render(report)).contains("\"rendered\": \"ui:repeat var=item from refactored/order.xhtml\"")
    }

    @Test
    fun `equivalent text output stays concise while keeping warnings visible`() {
        val rendered = TextReportRenderer().render(equivalentReport())

        assertThat(rendered).contains("EQUIVALENT")
        assertThat(rendered).contains("Checked: 4  Matched: 4  Mismatched: 0")
        assertThat(rendered).contains("Coverage: 4/4 (100.0%)")
        assertThat(rendered).contains("Warnings: 1 total (0 blocking)")
        assertThat(rendered).contains("Warnings:")
        assertThat(rendered).contains("W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD Analyzer pipeline is still scaffolded at refactored/order.xhtml")
        assertThat(rendered).doesNotContain("Problems:")
        assertThat(rendered).doesNotContain("Hint:")
    }

    @Test
    fun `mismatch text output stays detailed and separates warnings`() {
        val rendered = TextReportRenderer().render(notEquivalentReport())

        assertThat(rendered).contains("NOT_EQUIVALENT")
        assertThat(rendered).contains("Problems: 1")
        assertThat(rendered).contains("P-STRUCTURE-FORM_ANCESTRY_CHANGED Component moved outside form")
        assertThat(rendered).contains("old: legacy/order.xhtml -> <h:form><p:commandButton id=\"saveBtn\" /></h:form>")
        assertThat(rendered).contains("new: refactored/order.xhtml -> <p:commandButton id=\"saveBtn\" />")
        assertThat(rendered).contains("The component no longer has the same form ancestry.")
        assertThat(rendered).contains("Hint: Restore the original form wrapper around the command button.")
        assertThat(rendered).contains("Warnings: 1")
        assertThat(rendered).contains("W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD Analyzer pipeline is still scaffolded")
    }

    @Test
    fun `inconclusive text output keeps warnings detailed and visible`() {
        val rendered = TextReportRenderer().render(inconclusiveReport())

        assertThat(rendered).contains("INCONCLUSIVE")
        assertThat(rendered).contains("Warnings: 1 total (1 blocking)")
        assertThat(rendered).contains("Warnings:")
        assertThat(rendered).contains("W-UNSUPPORTED-DYNAMIC_INCLUDE Dynamic include path is not statically resolvable")
        assertThat(rendered).contains("new: refactored/order.xhtml -> src=#{bean.fragmentPath}")
        assertThat(rendered).contains("The include src uses a dynamic expression")
        assertThat(rendered).doesNotContain("Problems:")
    }

    @Test
    fun `json output separates errors and warnings with shared aggregates`() {
        val rendered = JsonReportRenderer().render(notEquivalentReport())

        assertThat(rendered).contains("\"result\" : \"NOT_EQUIVALENT\"")
        assertThat(rendered).contains("\"summary\"")
        assertThat(rendered).contains("\"headline\" : \"Found one mismatch\"")
        assertThat(rendered).contains("\"problems\"")
        assertThat(rendered).contains("\"warnings\"")
        assertThat(rendered).contains("\"id\" : \"P-STRUCTURE-FORM_ANCESTRY_CHANGED\"")
        assertThat(rendered).contains("\"id\" : \"W-UNSUPPORTED-ANALYZER_PIPELINE_SCAFFOLD\"")
        assertThat(rendered).contains("\"mismatched\" : 1")
        assertThat(rendered).contains("\"blocking\" : 0")
        assertThat(rendered).contains("\"stats\"")
    }

    private fun equivalentReport(): AnalysisReport =
        AnalysisReport(
            result = AnalysisResult.EQUIVALENT,
            summary = AnalysisSummary(
                headline = "All checked facts matched",
                counts = AggregateCounts(
                    checked = 4,
                    matched = 4,
                    mismatched = 0,
                ),
                coverage = AggregateCoverage(
                    covered = 4,
                    total = 4,
                ),
                warnings = WarningTotals(
                    total = 1,
                    blocking = 0,
                ),
            ),
            problems = listOf(scaffoldWarning()),
            stats = AnalysisStats(
                counts = AggregateCounts(
                    checked = 4,
                    matched = 4,
                    mismatched = 0,
                ),
                coverage = AggregateCoverage(
                    covered = 4,
                    total = 4,
                ),
                warnings = WarningTotals(
                    total = 1,
                    blocking = 0,
                ),
            ),
        )

    private fun notEquivalentReport(): AnalysisReport =
        AnalysisReport(
            result = AnalysisResult.NOT_EQUIVALENT,
            summary = AnalysisSummary(
                headline = "Found one mismatch",
                counts = AggregateCounts(
                    checked = 4,
                    matched = 3,
                    mismatched = 1,
                ),
                coverage = AggregateCoverage(
                    covered = 4,
                    total = 4,
                ),
                warnings = WarningTotals(
                    total = 1,
                    blocking = 0,
                ),
            ),
            problems = listOf(
                scaffoldWarning(),
                Problem(
                    id = ProblemIds.STRUCTURE_FORM_ANCESTRY_CHANGED,
                    severity = Severity.ERROR,
                    category = ProblemCategory.STRUCTURE,
                    summary = "Component moved outside form",
                    locations = ProblemLocations(
                        old = ProblemLocation(
                            provenance = Provenance.forRoot(
                                SourceDocument.fromPath(
                                    side = AnalysisSide.OLD,
                                    path = Path.of("legacy", "order.xhtml"),
                                ),
                            ),
                            snippet = "<h:form><p:commandButton id=\"saveBtn\" /></h:form>",
                        ),
                        new = ProblemLocation(
                            provenance = Provenance.forRoot(
                                SourceDocument.fromPath(
                                    side = AnalysisSide.NEW,
                                    path = Path.of("refactored", "order.xhtml"),
                                ),
                            ),
                            snippet = "<p:commandButton id=\"saveBtn\" />",
                        ),
                    ),
                    explanation = "The component no longer has the same form ancestry.",
                    hint = "Restore the original form wrapper around the command button.",
                ),
            ),
            stats = AnalysisStats(
                counts = AggregateCounts(
                    checked = 4,
                    matched = 3,
                    mismatched = 1,
                ),
                coverage = AggregateCoverage(
                    covered = 4,
                    total = 4,
                ),
                warnings = WarningTotals(
                    total = 1,
                    blocking = 0,
                ),
            ),
        )

    private fun inconclusiveReport(): AnalysisReport =
        AnalysisReport(
            result = AnalysisResult.INCONCLUSIVE,
            summary = AnalysisSummary(
                headline = "Dynamic include prevents a trustworthy equivalence claim",
                counts = AggregateCounts(
                    checked = 0,
                    matched = 0,
                    mismatched = 0,
                ),
                coverage = AggregateCoverage(
                    covered = 0,
                    total = 0,
                ),
                warnings = WarningTotals(
                    total = 1,
                    blocking = 1,
                ),
            ),
            problems = listOf(
                Problem(
                    id = WarningIds.UNSUPPORTED_DYNAMIC_INCLUDE,
                    severity = Severity.WARNING,
                    category = ProblemCategory.UNSUPPORTED,
                    summary = "Dynamic include path is not statically resolvable",
                    locations = ProblemLocations(
                        new = ProblemLocation(
                            provenance = Provenance.forRoot(
                                SourceDocument.fromPath(
                                    side = AnalysisSide.NEW,
                                    path = Path.of("refactored", "order.xhtml"),
                                ),
                            ),
                            snippet = "src=#{bean.fragmentPath}",
                        ),
                    ),
                    explanation = "The include src uses a dynamic expression (#{bean.fragmentPath}), so comparison beneath this node is not trustworthy.",
                    hint = "Replace the dynamic include with a static path or keep the result inconclusive.",
                ),
            ),
        )

    private fun scaffoldWarning(): Problem =
        Problem(
            id = WarningIds.UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD,
            severity = Severity.WARNING,
            category = ProblemCategory.UNSUPPORTED,
            summary = "Analyzer pipeline is still scaffolded",
            locations = ProblemLocations(
                new = ProblemLocation(
                    provenance = Provenance.forRoot(
                        SourceDocument.fromPath(
                            side = AnalysisSide.NEW,
                            path = Path.of("refactored", "order.xhtml"),
                        ),
                    ),
                ),
            ),
            explanation = "Current analysis stages still return a scaffold warning.",
        )
    }
}
