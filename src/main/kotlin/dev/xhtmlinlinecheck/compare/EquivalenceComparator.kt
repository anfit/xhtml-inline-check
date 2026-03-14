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
import dev.xhtmlinlinecheck.semantic.ComponentTargetAttribute
import dev.xhtmlinlinecheck.semantic.NormalizedSemanticElOccurrence
import dev.xhtmlinlinecheck.semantic.SemanticElCarrierKind
import dev.xhtmlinlinecheck.semantic.SemanticElOccurrence
import dev.xhtmlinlinecheck.semantic.SemanticModels
import dev.xhtmlinlinecheck.semantic.SemanticNode
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree
import dev.xhtmlinlinecheck.syntax.walkDepthFirst

fun interface EquivalenceComparator {
    fun compare(semanticModels: SemanticModels): AnalysisReport

    companion object {
        fun scaffold(): EquivalenceComparator =
            EquivalenceComparator { semanticModels ->
                val elComparison = compareNormalizedEl(semanticModels)
                val matchResult =
                    SemanticNodeMatcher.matchStructuralCandidates(
                        oldNodes = semanticModels.oldRoot.semanticNodes,
                        newNodes = semanticModels.newRoot.semanticNodes,
                    )
                val targetComparison = compareResolvedTargets(semanticModels, matchResult)
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
                        targetComparison.problems +
                        includeProblems +
                        extractedElProblems +
                        scaffoldWarning
                val warningCount = problems.count { it.severity == Severity.WARNING }
                val warningTotals = WarningTotals(total = warningCount, blocking = warningCount)
                val counts =
                    AggregateCounts(
                        checked = elComparison.checked + targetComparison.checked,
                        matched = elComparison.matched + targetComparison.matched,
                        mismatched = elComparison.mismatched + targetComparison.mismatched,
                    )

                AnalysisReport(
                    result = AnalysisResult.derive(
                        hasMismatch = counts.mismatched > 0,
                        blocksEquivalenceClaim = true,
                    ),
                    summary = AnalysisSummary(
                        headline = summaryHeadline(elComparison, targetComparison),
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
    val uncertain: Int,
    val problems: List<Problem>,
)

private data class TargetComparisonResult(
    val checked: Int,
    val matched: Int,
    val mismatched: Int,
    val problems: List<Problem>,
)

private fun compareNormalizedEl(semanticModels: SemanticModels): ElComparisonResult {
    val oldOccurrences = semanticModels.oldRoot.normalizedElOccurrences
    val newOccurrences = semanticModels.newRoot.normalizedElOccurrences
    if (oldOccurrences.size != newOccurrences.size) {
        return ElComparisonResult(checked = 0, matched = 0, mismatched = 0, uncertain = 0, problems = emptyList())
    }

    val problems = mutableListOf<Problem>()
    var checked = 0
    var matched = 0
    var mismatched = 0
    var uncertain = 0

    oldOccurrences.zip(newOccurrences).forEach { (oldOccurrence, newOccurrence) ->
        if (!oldOccurrence.hasComparableShapeTo(newOccurrence)) {
            return@forEach
        }
        if (oldOccurrence.bindingReferences.isEmpty() && newOccurrence.bindingReferences.isEmpty()) {
            return@forEach
        }
        if (oldOccurrence.globalReferences.isNotEmpty() || newOccurrence.globalReferences.isNotEmpty()) {
            uncertain++
            problems += unresolvedGlobalRootProblem(oldOccurrence, newOccurrence)
            return@forEach
        }

        checked++
        if (oldOccurrence.normalizedTemplate == newOccurrence.normalizedTemplate) {
            matched++
        } else {
            mismatched++
            problems += bindingMismatchProblem(oldOccurrence, newOccurrence)
        }
    }

    return ElComparisonResult(
        checked = checked,
        matched = matched,
        mismatched = mismatched,
        uncertain = uncertain,
        problems = problems,
    )
}

private fun compareResolvedTargets(
    semanticModels: SemanticModels,
    matchResult: SemanticNodeMatchResult,
): TargetComparisonResult {
    val oldNodesById = semanticModels.oldRoot.semanticNodes.associateBy(SemanticNode::nodeId)
    val newNodesById = semanticModels.newRoot.semanticNodes.associateBy(SemanticNode::nodeId)
    val problems = mutableListOf<Problem>()
    var checked = 0
    var matched = 0
    var mismatched = 0

    matchResult.matches.forEach { match ->
        val oldNode = oldNodesById.getValue(match.oldNodeId)
        val newNode = newNodesById.getValue(match.newNodeId)
        val allKinds =
            (oldNode.componentTargetAttributes.map(ComponentTargetAttribute::kind) +
                newNode.componentTargetAttributes.map(ComponentTargetAttribute::kind))
                .distinct()

        allKinds.forEach { kind ->
            checked++
            val oldAttributes = oldNode.componentTargetAttributes.filter { it.kind == kind }
            val newAttributes = newNode.componentTargetAttributes.filter { it.kind == kind }
            val oldResolved = oldAttributes.map(ComponentTargetAttribute::renderResolved)
            val newResolved = newAttributes.map(ComponentTargetAttribute::renderResolved)

            if (oldResolved == newResolved) {
                matched++
            } else {
                mismatched++
                problems += targetResolutionProblem(oldNode, oldAttributes, newNode, newAttributes)
            }
        }
    }

    return TargetComparisonResult(
        checked = checked,
        matched = matched,
        mismatched = mismatched,
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

private fun unresolvedGlobalRootProblem(
    oldOccurrence: NormalizedSemanticElOccurrence,
    newOccurrence: NormalizedSemanticElOccurrence,
): Problem {
    val oldBindingOrigin = oldOccurrence.firstBindingOrigin()
    val newBindingOrigin = newOccurrence.firstBindingOrigin()
    val oldGlobals = oldOccurrence.globalReferences.joinToString(", ") { it.writtenName }
    val newGlobals = newOccurrence.globalReferences.joinToString(", ") { it.writtenName }

    return Problem(
        id = WarningIds.UNSUPPORTED_UNRESOLVED_GLOBAL_ROOT,
        severity = Severity.WARNING,
        category = ProblemCategory.UNSUPPORTED,
        summary = "Unresolved global roots keep EL comparison uncertain",
        locations =
            ProblemLocations(
                old = ProblemLocation(oldOccurrence.occurrence.provenance, oldOccurrence.occurrence.rawValue, oldBindingOrigin),
                new = ProblemLocation(newOccurrence.occurrence.provenance, newOccurrence.occurrence.rawValue, newBindingOrigin),
            ),
        explanation =
            "The occurrence still depends on symbolic global roots, so the EL layer can only compare local-binding shape here. " +
                "Old globals: [$oldGlobals] -> ${oldOccurrence.normalizedTemplate.render()} " +
                "New globals: [$newGlobals] -> ${newOccurrence.normalizedTemplate.render()}",
        hint = "Treat bean-level equivalence for these unresolved roots as unknown until broader EL or bean analysis exists.",
    )
}

private fun NormalizedSemanticElOccurrence.firstBindingOrigin() = bindingReferences.firstOrNull()?.binding?.origin

private fun summaryHeadline(
    elComparison: ElComparisonResult,
    targetComparison: TargetComparisonResult,
): String =
    when {
        targetComparison.mismatched > 0 && elComparison.mismatched > 0 ->
            "Detected target-resolution and local-binding EL mismatches; broader semantic comparison is still scaffolded."
        targetComparison.mismatched > 0 ->
            "Detected target-resolution mismatches; broader semantic comparison is still scaffolded."
        elComparison.mismatched > 0 ->
            "Detected local-binding EL mismatches; broader semantic comparison is still scaffolded."
        elComparison.uncertain > 0 ->
            "Local-binding EL comparison encountered unresolved global roots, so bean-level equivalence remains uncertain."
        targetComparison.checked > 0 ->
            "Resolved component targets matched for structurally paired nodes; broader semantic comparison is still scaffolded."
        elComparison.checked > 0 ->
            "Local-binding EL normalization matched for comparable occurrences; broader semantic comparison is still scaffolded."
        else ->
            "Scaffolded analyzer pipeline only; semantic comparison is not implemented yet."
    }

private fun targetResolutionProblem(
    oldNode: SemanticNode,
    oldAttributes: List<ComponentTargetAttribute>,
    newNode: SemanticNode,
    newAttributes: List<ComponentTargetAttribute>,
): Problem {
    val oldAttribute = oldAttributes.firstOrNull()
    val newAttribute = newAttributes.firstOrNull()
    val oldSnippet = oldAttributes.joinToString(separator = ", ") { attribute -> attribute.render() }.takeIf { it.isNotBlank() }
    val newSnippet = newAttributes.joinToString(separator = ", ") { attribute -> attribute.render() }.takeIf { it.isNotBlank() }
    val oldResolved = oldAttributes.joinToString(separator = ", ") { attribute -> attribute.renderResolved() }.ifBlank { "<missing>" }
    val newResolved = newAttributes.joinToString(separator = ", ") { attribute -> attribute.renderResolved() }.ifBlank { "<missing>" }

    return Problem(
        id = ProblemIds.TARGET_RESOLUTION_CHANGED,
        severity = Severity.ERROR,
        category = ProblemCategory.TARGET,
        summary = "Component target resolves differently after refactor",
        locations =
            ProblemLocations(
                old =
                    ProblemLocation(
                        provenance = oldNode.attributeProvenance(oldAttribute),
                        snippet = oldSnippet,
                    ),
                new =
                    ProblemLocation(
                        provenance = newNode.attributeProvenance(newAttribute),
                        snippet = newSnippet,
                    ),
            ),
        explanation =
            "The matched node's target-bearing attributes no longer resolve to the same semantic targets. " +
                "Old: $oldResolved New: $newResolved",
        hint = "Keep the component in the same form or preserve the target component ids so the resolved target meaning stays the same.",
    )
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

private fun SemanticNode.attributeProvenance(attribute: ComponentTargetAttribute?): Provenance {
    val targetLocation = attribute?.location
    if (targetLocation == null) {
        return provenance
    }

    return Provenance(
        physicalLocation =
            provenance.physicalLocation.copy(
                span = targetLocation.span,
                attributeName = targetLocation.attributeName,
                attributeLocationPrecision = targetLocation.attributeLocationPrecision,
            ),
        logicalLocation =
            provenance.logicalLocation.copy(
                span = targetLocation.span,
                attributeName = targetLocation.attributeName,
                attributeLocationPrecision = targetLocation.attributeLocationPrecision,
            ),
        includeStack = provenance.includeStack,
    )
}
