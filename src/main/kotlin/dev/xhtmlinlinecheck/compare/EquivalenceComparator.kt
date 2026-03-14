package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.ProblemLocations
import dev.xhtmlinlinecheck.domain.Severity
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
                    summary = "Scaffolded analyzer pipeline only; semantic comparison is not implemented yet.",
                    problems = listOf(
                        Problem(
                            id = "W00",
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
                        checkedFacts = 0,
                        matchedFacts = 0,
                        problemCount = 0,
                        warningCount = 1,
                    ),
                )
            }
    }
}
