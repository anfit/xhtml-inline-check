package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.AnalysisSummary
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemIds
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.ProblemLocations
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.Severity
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailure
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailureKind
import dev.xhtmlinlinecheck.domain.WarningIds
import dev.xhtmlinlinecheck.domain.WarningTotals
import dev.xhtmlinlinecheck.semantic.NormalizedSemanticElOccurrence
import dev.xhtmlinlinecheck.semantic.SemanticElCarrierKind
import dev.xhtmlinlinecheck.semantic.SemanticElOccurrence
import dev.xhtmlinlinecheck.semantic.SemanticModels
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree
import dev.xhtmlinlinecheck.syntax.walkDepthFirst

fun interface EquivalenceComparator {
    fun compare(semanticModels: SemanticModels): AnalysisReport

    companion object {
        fun scaffold(): EquivalenceComparator =
            EquivalenceComparator { semanticModels ->
                val elComparison = compareNormalizedEl(semanticModels)
                val includeProblems =
                    buildList {
                        addAll(semanticModels.oldRoot.syntaxTree.collectIncludeProblems())
                        addAll(semanticModels.newRoot.syntaxTree.collectIncludeProblems())
                    }
                val extractedElProblems =
                    semanticModels.oldRoot.elOccurrences.collectUnsupportedElProblems() +
                        semanticModels.newRoot.elOccurrences.collectUnsupportedElProblems()
                val scaffoldWarning =
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
                val problems =
                    elComparison.problems +
                        includeProblems +
                        extractedElProblems +
                        scaffoldWarning
                val warningCount = problems.count { it.severity == Severity.WARNING }
                val warningTotals = WarningTotals(total = warningCount, blocking = warningCount)
                val counts = AggregateCounts(checked = elComparison.checked, matched = elComparison.matched, mismatched = elComparison.mismatched)

                AnalysisReport(
                    result = AnalysisResult.derive(
                        hasMismatch = elComparison.mismatched > 0,
                        blocksEquivalenceClaim = true,
                    ),
                    summary = AnalysisSummary(
                        headline = summaryHeadline(elComparison),
                        counts = counts,
                        coverage = AggregateCoverage.from(counts),
                        warnings = warningTotals,
                    ),
                    problems = problems,
                    stats = AnalysisStats(
                        counts = counts,
                        coverage = AggregateCoverage.from(counts),
                        warnings = warningTotals,
                    ),
                )
            }
    }
}

private data class ElComparisonResult(
    val checked: Int,
    val matched: Int,
    val mismatched: Int,
    val problems: List<Problem>,
)

private fun compareNormalizedEl(semanticModels: SemanticModels): ElComparisonResult {
    val oldOccurrences = semanticModels.oldRoot.normalizedElOccurrences
    val newOccurrences = semanticModels.newRoot.normalizedElOccurrences
    if (oldOccurrences.size != newOccurrences.size) {
        return ElComparisonResult(checked = 0, matched = 0, mismatched = 0, problems = emptyList())
    }

    val problems = mutableListOf<Problem>()
    var checked = 0
    var matched = 0

    oldOccurrences.zip(newOccurrences).forEach { (oldOccurrence, newOccurrence) ->
        if (!oldOccurrence.hasComparableShapeTo(newOccurrence)) {
            return@forEach
        }
        if (oldOccurrence.bindingReferences.isEmpty() && newOccurrence.bindingReferences.isEmpty()) {
            return@forEach
        }

        checked++
        if (oldOccurrence.normalizedTemplate == newOccurrence.normalizedTemplate) {
            matched++
        } else {
            problems += bindingMismatchProblem(oldOccurrence, newOccurrence)
        }
    }

    return ElComparisonResult(
        checked = checked,
        matched = matched,
        mismatched = problems.size,
        problems = problems,
    )
}

private fun NormalizedSemanticElOccurrence.hasComparableShapeTo(other: NormalizedSemanticElOccurrence): Boolean =
    occurrence.carrierKind == other.occurrence.carrierKind &&
        occurrence.ownerTagName == other.occurrence.ownerTagName &&
        occurrence.ownerName == other.occurrence.ownerName &&
        occurrence.attributeName == other.occurrence.attributeName

private fun bindingMismatchProblem(
    oldOccurrence: NormalizedSemanticElOccurrence,
    newOccurrence: NormalizedSemanticElOccurrence,
): Problem {
    val oldBindingOrigin = oldOccurrence.firstBindingOrigin()
    val newBindingOrigin = newOccurrence.firstBindingOrigin()

    return Problem(
        id = ProblemIds.SCOPE_BINDING_MISMATCH,
        severity = Severity.ERROR,
        category = ProblemCategory.SCOPE,
        summary = "Local variable resolves to different binding",
        locations =
            ProblemLocations(
                old = ProblemLocation(oldOccurrence.occurrence.provenance, oldOccurrence.occurrence.rawValue, oldBindingOrigin),
                new = ProblemLocation(newOccurrence.occurrence.provenance, newOccurrence.occurrence.rawValue, newBindingOrigin),
            ),
        explanation =
            "The normalized EL differs after resolving local bindings against the active scope stack. " +
                "Old: ${oldOccurrence.normalizedTemplate.render()} New: ${newOccurrence.normalizedTemplate.render()}",
        hint = "Preserve the original local binding scope or rename the refactored binding so it resolves to the same origin.",
    )
}

private fun NormalizedSemanticElOccurrence.firstBindingOrigin() = bindingReferences.firstOrNull()?.binding?.origin

private fun summaryHeadline(elComparison: ElComparisonResult): String =
    when {
        elComparison.mismatched > 0 ->
            "Detected local-binding EL mismatches; broader semantic comparison is still scaffolded."
        elComparison.checked > 0 ->
            "Local-binding EL normalization matched for comparable occurrences; broader semantic comparison is still scaffolded."
        else ->
            "Scaffolded analyzer pipeline only; semantic comparison is not implemented yet."
    }

private fun List<SemanticElOccurrence>.collectUnsupportedElProblems(): List<Problem> =
    filter { !it.isSupported }.map { occurrence ->
        val locations = locationsFor(occurrence.provenance, occurrence.rawValue)
        val carrierDescription = occurrence.describeCarrier()
        val failure = requireNotNull(occurrence.parseFailure) {
            "unsupported semantic EL occurrences must carry a parse failure"
        }

        Problem(
            id = WarningIds.UNSUPPORTED_EXTRACTED_EL,
            severity = Severity.WARNING,
            category = ProblemCategory.UNSUPPORTED,
            summary = "Extracted EL falls outside the MVP subset",
            locations = locations,
            explanation =
                "$carrierDescription uses extracted EL that the MVP parser does not support yet: ${failure.message}. " +
                    "The affected semantic fact is treated as unknown, so equivalence remains inconclusive.",
            hint = "Rewrite the EL into the documented MVP subset or keep the result inconclusive until support is added.",
        )
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

private fun SemanticElOccurrence.describeCarrier(): String =
    when (carrierKind) {
        SemanticElCarrierKind.ELEMENT_ATTRIBUTE ->
            "$ownerTagName @$attributeName"

        SemanticElCarrierKind.TEXT_NODE ->
            "$ownerTagName text"

        SemanticElCarrierKind.INCLUDE_ATTRIBUTE ->
            "$ownerTagName @$attributeName"

        SemanticElCarrierKind.INCLUDE_PARAMETER -> {
            val parameterNameSuffix = ownerName?.let { " name=$it" }.orEmpty()
            "$ownerTagName$parameterNameSuffix @$attributeName"
        }
    }
