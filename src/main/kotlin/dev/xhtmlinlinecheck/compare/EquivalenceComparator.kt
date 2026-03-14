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
import dev.xhtmlinlinecheck.syntax.LogicalElementNode
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.LogicalNode

fun interface EquivalenceComparator {
    fun compare(semanticModels: SemanticModels): AnalysisReport

    companion object {
        fun scaffold(): EquivalenceComparator =
            EquivalenceComparator { semanticModels ->
                val includeProblems =
                    buildList {
                        addAll(semanticModels.oldRoot.rootNode.collectIncludeCycleProblems())
                        addAll(semanticModels.newRoot.rootNode.collectIncludeCycleProblems())
                    }
                val problems =
                    includeProblems +
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
                        )
                val warningTotals = WarningTotals(total = problems.size, blocking = problems.size)

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
                        warnings = warningTotals,
                    ),
                    problems = problems,
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
                        warnings = warningTotals,
                    ),
                )
            }
    }
}

private fun LogicalElementNode?.collectIncludeCycleProblems(): List<Problem> {
    if (this == null) {
        return emptyList()
    }

    val problems = mutableListOf<Problem>()

    fun visit(node: LogicalNode) {
        when (node) {
            is LogicalElementNode -> node.children.forEach(::visit)
            is LogicalIncludeNode -> {
                node.includeFailure?.let { includeFailure ->
                    val cyclePath =
                        includeFailure.cycleDocuments
                            .joinToString(" -> ") { it.displayPath }
                    problems +=
                        Problem(
                            id = WarningIds.UNSUPPORTED_INCLUDE_CYCLE,
                            severity = Severity.WARNING,
                            category = ProblemCategory.UNSUPPORTED,
                            summary = "Recursive include cycle detected",
                            locations =
                                when (node.provenance.logicalLocation.document.side) {
                                    dev.xhtmlinlinecheck.domain.AnalysisSide.OLD ->
                                        ProblemLocations(old = ProblemLocation(node.provenance))
                                    dev.xhtmlinlinecheck.domain.AnalysisSide.NEW ->
                                        ProblemLocations(new = ProblemLocation(node.provenance))
                                },
                            explanation = "The include graph loops back on itself: $cyclePath",
                            hint = "Break the recursive include chain before relying on static equivalence analysis.",
                        )
                }
                node.children.forEach(::visit)
            }
            else -> Unit
        }
    }

    visit(this)
    return problems
}
