package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.AnalysisSummary
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.ProblemLocations
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.Severity
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailure
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailureKind
import dev.xhtmlinlinecheck.domain.WarningIds
import dev.xhtmlinlinecheck.domain.WarningTotals
import dev.xhtmlinlinecheck.semantic.SemanticModels
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree
import dev.xhtmlinlinecheck.syntax.walkDepthFirst

fun interface EquivalenceComparator {
    fun compare(semanticModels: SemanticModels): AnalysisReport

    companion object {
        fun scaffold(): EquivalenceComparator =
            EquivalenceComparator { semanticModels ->
                val includeProblems =
                    buildList {
                        addAll(semanticModels.oldRoot.syntaxTree.collectIncludeProblems())
                        addAll(semanticModels.newRoot.syntaxTree.collectIncludeProblems())
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

private fun XhtmlSyntaxTree.collectIncludeProblems(): List<Problem> {
    val problems = mutableListOf<Problem>()
    walkDepthFirst { node ->
        if (node is LogicalIncludeNode) {
            node.includeFailure?.let { includeFailure ->
                problems += includeProblemFor(node, includeFailure)
            }
        }
    }
    return problems
}

private fun includeProblemFor(
    node: LogicalIncludeNode,
    includeFailure: SourceGraphIncludeFailure,
): Problem {
    val locations = locationsFor(node.provenance, node.sourcePath)

    return when (includeFailure.kind) {
        SourceGraphIncludeFailureKind.DYNAMIC_PATH -> {
            val requestedPath = includeFailure.dynamicSourcePath ?: node.sourcePath ?: "dynamic src"
            Problem(
                id = WarningIds.UNSUPPORTED_DYNAMIC_INCLUDE,
                severity = Severity.WARNING,
                category = ProblemCategory.UNSUPPORTED,
                summary = "Dynamic include path is not statically resolvable",
                locations = locations,
                explanation =
                    "The include src uses a dynamic expression ($requestedPath), so comparison beneath this node is not trustworthy.",
                hint = "Replace the dynamic include with a static path or treat the result as inconclusive.",
            )
        }

        SourceGraphIncludeFailureKind.INCLUDE_CYCLE -> {
            val cyclePath = includeFailure.cycleDocuments.joinToString(" -> ") { it.displayPath }
            Problem(
                id = WarningIds.UNSUPPORTED_INCLUDE_CYCLE,
                severity = Severity.WARNING,
                category = ProblemCategory.UNSUPPORTED,
                summary = "Recursive include cycle detected",
                locations = locations,
                explanation = "The include graph loops back on itself: $cyclePath",
                hint = "Break the recursive include chain before relying on static equivalence analysis.",
            )
        }

        SourceGraphIncludeFailureKind.MISSING_FILE -> {
            val missingDocument = requireNotNull(includeFailure.missingDocument) {
                "missing include diagnostics require the unresolved target document"
            }
            val requestedPath = node.sourcePath ?: missingDocument.displayPath
            Problem(
                id = WarningIds.UNSUPPORTED_MISSING_INCLUDE,
                severity = Severity.WARNING,
                category = ProblemCategory.UNSUPPORTED,
                summary = "Included file could not be found",
                locations = locations,
                explanation =
                    "Static include $requestedPath resolved to ${missingDocument.displayPath}, but that file does not exist.",
                hint = "Fix the include path or restore the missing file before relying on static equivalence analysis.",
            )
        }
    }
}

private fun locationsFor(
    provenance: Provenance,
    snippet: String?,
): ProblemLocations =
    when (provenance.logicalLocation.document.side) {
        AnalysisSide.OLD -> ProblemLocations(old = ProblemLocation(provenance, snippet))
        AnalysisSide.NEW -> ProblemLocations(new = ProblemLocation(provenance, snippet))
    }
