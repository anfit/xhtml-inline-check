package dev.xhtmlinlinecheck.report

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.AnalysisSummary
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.AttributeLocationPrecision
import dev.xhtmlinlinecheck.domain.BindingOrigin
import dev.xhtmlinlinecheck.domain.IncludeProvenanceStep
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemIds
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
import java.nio.file.Path

internal object GoldenReportSamples {
    fun equivalentReport(): AnalysisReport {
        val newDocument = document(AnalysisSide.NEW, "refactored", "order.xhtml")
        return AnalysisReport(
            result = AnalysisResult.EQUIVALENT,
            summary = AnalysisSummary(
                headline = "All checked facts matched",
                counts = AggregateCounts(checked = 4, matched = 4, mismatched = 0),
                coverage = AggregateCoverage(covered = 4, total = 4),
                warnings = WarningTotals(total = 1, blocking = 0),
            ),
            problems = listOf(scaffoldWarning(newDocument)),
            stats = AnalysisStats(
                counts = AggregateCounts(checked = 4, matched = 4, mismatched = 0),
                coverage = AggregateCoverage(covered = 4, total = 4),
                warnings = WarningTotals(total = 1, blocking = 0),
            ),
        )
    }

    fun notEquivalentReport(): AnalysisReport {
        val oldRoot = document(AnalysisSide.OLD, "legacy", "order.xhtml")
        val oldFragment = document(AnalysisSide.OLD, "legacy", "fragments", "table.xhtml")
        val newRoot = document(AnalysisSide.NEW, "refactored", "order.xhtml")
        val oldLogical =
            SourceLocation(
                document = oldRoot,
                span = SourceSpan(SourcePosition(line = 12, column = 17)),
                attributeName = "value",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            )
        val oldPhysical =
            SourceLocation(
                document = oldFragment,
                span = SourceSpan(SourcePosition(line = 4, column = 9)),
            )
        val oldIncludeSite =
            SourceLocation(
                document = oldRoot,
                span = SourceSpan(SourcePosition(line = 8, column = 5)),
                attributeName = "src",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            )
        val newValueLocation =
            SourceLocation(
                document = newRoot,
                span = SourceSpan(SourcePosition(line = 15, column = 19)),
                attributeName = "value",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            )

        return AnalysisReport(
            result = AnalysisResult.NOT_EQUIVALENT,
            summary = AnalysisSummary(
                headline = "Comparison found mismatches",
                counts = AggregateCounts(checked = 5, matched = 4, mismatched = 1),
                coverage = AggregateCoverage(covered = 5, total = 5),
                warnings = WarningTotals(total = 1, blocking = 0),
            ),
            problems = listOf(
                scaffoldWarning(newRoot),
                Problem(
                    id = ProblemIds.SCOPE_BINDING_MISMATCH,
                    severity = Severity.ERROR,
                    category = ProblemCategory.SCOPE,
                    summary = "Scope binding changed",
                    locations = ProblemLocations(
                        old =
                            ProblemLocation(
                                provenance =
                                    Provenance(
                                        physicalLocation = oldPhysical,
                                        logicalLocation = oldLogical,
                                        includeStack =
                                            listOf(
                                                IncludeProvenanceStep(
                                                    includeSite = oldIncludeSite,
                                                    includedDocument = oldFragment,
                                                    parameterNames = listOf("rows"),
                                                ),
                                            ),
                                    ),
                                snippet = "#{row.label}",
                                bindingOrigin =
                                    BindingOrigin(
                                        descriptor = "ui:repeat var=row",
                                        provenance = Provenance.forRoot(oldFragment),
                                    ),
                            ),
                        new =
                            ProblemLocation(
                                provenance = Provenance(newValueLocation, newValueLocation),
                                snippet = "#{item.label}",
                                bindingOrigin =
                                    BindingOrigin(
                                        descriptor = "ui:repeat var=item",
                                        provenance = Provenance.forRoot(newRoot),
                                    ),
                            ),
                    ),
                    explanation = "The same expression now resolves against a different local binding.",
                    hint = "Restore the original repeat scope.",
                ),
            ),
            stats = AnalysisStats(
                counts = AggregateCounts(checked = 5, matched = 4, mismatched = 1),
                coverage = AggregateCoverage(covered = 5, total = 5),
                warnings = WarningTotals(total = 1, blocking = 0),
            ),
        )
    }

    fun inconclusiveReport(): AnalysisReport {
        val newDocument = document(AnalysisSide.NEW, "refactored", "order.xhtml")
        val includeLocation =
            SourceLocation(
                document = newDocument,
                span = SourceSpan(SourcePosition(line = 6, column = 9)),
                attributeName = "src",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            )

        return AnalysisReport(
            result = AnalysisResult.INCONCLUSIVE,
            summary = AnalysisSummary(
                headline = "Dynamic include prevents a trustworthy equivalence claim",
                counts = AggregateCounts(checked = 0, matched = 0, mismatched = 0),
                coverage = AggregateCoverage(covered = 0, total = 0),
                warnings = WarningTotals(total = 1, blocking = 1),
            ),
            problems = listOf(
                Problem(
                    id = WarningIds.UNSUPPORTED_DYNAMIC_INCLUDE,
                    severity = Severity.WARNING,
                    category = ProblemCategory.UNSUPPORTED,
                    summary = "Dynamic include path is not statically resolvable",
                    locations = ProblemLocations(
                        new =
                            ProblemLocation(
                                provenance = Provenance(includeLocation, includeLocation),
                                snippet = "src=#{bean.fragmentPath}",
                            ),
                    ),
                    explanation = "The include src uses a dynamic expression (#{bean.fragmentPath}), so comparison beneath this node is not trustworthy.",
                    hint = "Replace the dynamic include with a static path or keep the result inconclusive.",
                ),
            ),
            stats = AnalysisStats(
                counts = AggregateCounts(checked = 0, matched = 0, mismatched = 0),
                coverage = AggregateCoverage(covered = 0, total = 0),
                warnings = WarningTotals(total = 1, blocking = 1),
            ),
        )
    }

    private fun scaffoldWarning(newDocument: SourceDocument): Problem =
        Problem(
            id = WarningIds.UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD,
            severity = Severity.WARNING,
            category = ProblemCategory.UNSUPPORTED,
            summary = "Analyzer pipeline is still scaffolded",
            locations = ProblemLocations(
                new = ProblemLocation(provenance = Provenance.forRoot(newDocument)),
            ),
            explanation = "Current analysis stages still return a scaffold warning.",
        )

    private fun document(side: AnalysisSide, vararg segments: String): SourceDocument =
        SourceDocument.fromPath(side = side, path = Path.of(segments.first(), *segments.drop(1).toTypedArray()))
}
