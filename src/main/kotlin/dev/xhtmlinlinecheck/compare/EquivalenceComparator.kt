package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisSummary
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.ProblemLocations
import dev.xhtmlinlinecheck.domain.Severity
import dev.xhtmlinlinecheck.domain.WarningIds
import dev.xhtmlinlinecheck.domain.WarningTotals
import dev.xhtmlinlinecheck.semantic.SemanticModels

fun interface EquivalenceComparator {
    fun compare(semanticModels: SemanticModels): AnalysisReport

    companion object {
        fun scaffold(): EquivalenceComparator =
            EquivalenceComparator {
                AnalysisReport(
                    result = AnalysisResult.derive(
                        hasMismatch = false,
                        blocksEquivalenceClaim = true,
                    ),
                    summary = AnalysisSummary(
                        headline = "Scaffolded analyzer pipeline only; semantic comparison is not implemented yet.",
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
                            id = WarningIds.UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD,
                            severity = Severity.WARNING,
                            category = ProblemCategory.UNSUPPORTED,
                            summary = "Analyzer pipeline scaffold is in place",
                            locations = ProblemLocations(
                                old = ProblemLocation(semanticModels.oldRoot.provenance),
                                new = ProblemLocation(semanticModels.newRoot.provenance),
                            ),
                            explanation = "The loader, syntax, semantic, and comparison stages exist as placeholders for later tasks.",
                            hint = "Implement the stage-specific logic before relying on comparison results.",
                        ),
                    ),
                    stats = AnalysisStats(
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
                )
            }
    }
}
