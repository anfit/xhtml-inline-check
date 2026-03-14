package dev.xhtmlinlinecheck.compare

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.AnalysisStats
import dev.xhtmlinlinecheck.domain.AnalysisSummary
import dev.xhtmlinlinecheck.domain.AggregateCounts
import dev.xhtmlinlinecheck.domain.AggregateCoverage
import dev.xhtmlinlinecheck.domain.BindingOrigin
import dev.xhtmlinlinecheck.domain.DiagnosticId
import dev.xhtmlinlinecheck.domain.Problem
import dev.xhtmlinlinecheck.domain.ProblemCategory
import dev.xhtmlinlinecheck.domain.ProblemIds
import dev.xhtmlinlinecheck.domain.ProblemLocation
import dev.xhtmlinlinecheck.domain.ProblemLocations
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.Severity
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailure
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailureKind
import dev.xhtmlinlinecheck.domain.WarningIds
import dev.xhtmlinlinecheck.domain.WarningTotals
import dev.xhtmlinlinecheck.rules.SyntaxRole
import dev.xhtmlinlinecheck.semantic.ComponentTargetAttribute
import dev.xhtmlinlinecheck.semantic.SemanticElCarrierKind
import dev.xhtmlinlinecheck.semantic.SemanticElOccurrence
import dev.xhtmlinlinecheck.semantic.SemanticModels
import dev.xhtmlinlinecheck.semantic.SemanticNode
import dev.xhtmlinlinecheck.semantic.SemanticNodeAncestor
import dev.xhtmlinlinecheck.semantic.SemanticNodeElFact
import dev.xhtmlinlinecheck.semantic.SemanticIterationAncestor
import dev.xhtmlinlinecheck.syntax.LogicalIncludeNode
import dev.xhtmlinlinecheck.syntax.LogicalName
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree
import dev.xhtmlinlinecheck.syntax.walkDepthFirst

fun interface EquivalenceComparator {
    fun compare(semanticModels: SemanticModels): AnalysisReport

    companion object {
        fun scaffold(): EquivalenceComparator =
            EquivalenceComparator { semanticModels ->
                val matchResult =
                    SemanticNodeMatcher.matchStructuralCandidates(
                        oldNodes = semanticModels.oldRoot.semanticNodes,
                        newNodes = semanticModels.newRoot.semanticNodes,
                    )
                val structuralAlignment = compareStructuralAlignment(semanticModels, matchResult)
                val matchedNodeComparison = compareMatchedNodes(semanticModels, matchResult)
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
                    structuralAlignment.problems +
                        matchedNodeComparison.problems +
                        includeProblems +
                        extractedElProblems +
                        scaffoldWarning
                val warningCount = problems.count { it.severity == Severity.WARNING }
                val warningTotals = WarningTotals(total = warningCount, blocking = warningCount)
                val counts =
                    AggregateCounts(
                        checked = structuralAlignment.checked + matchedNodeComparison.checked,
                        matched = structuralAlignment.matched + matchedNodeComparison.matched,
                        mismatched = structuralAlignment.mismatched + matchedNodeComparison.mismatched,
                    )

                AnalysisReport(
                    result = AnalysisResult.derive(
                        hasMismatch = counts.mismatched > 0,
                        blocksEquivalenceClaim = true,
                    ),
                    summary = AnalysisSummary(
                        headline = summaryHeadline(structuralAlignment, matchedNodeComparison),
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

private data class StructuralAlignmentResult(
    val checked: Int,
    val matched: Int,
    val mismatched: Int,
    val problems: List<Problem>,
)

private data class MatchedNodeComparisonResult(
    val checked: Int,
    val matched: Int,
    val mismatched: Int,
    val uncertain: Int,
    val problems: List<Problem>,
    val mismatchSignals: Set<String>,
)

private data class FactComparisonResult(
    val checked: Int = 0,
    val matched: Int = 0,
    val mismatched: Int = 0,
    val uncertain: Int = 0,
    val problems: List<Problem> = emptyList(),
)

private fun compareMatchedNodes(
    semanticModels: SemanticModels,
    matchResult: SemanticNodeMatchResult,
): MatchedNodeComparisonResult {
    val oldNodesById = semanticModels.oldRoot.semanticNodes.associateBy(SemanticNode::nodeId)
    val newNodesById = semanticModels.newRoot.semanticNodes.associateBy(SemanticNode::nodeId)
    var checked = 0
    var matched = 0
    var mismatched = 0
    var uncertain = 0
    val problems = mutableListOf<Problem>()
    val mismatchSignals = linkedSetOf<String>()

    matchResult.matches.forEach { match ->
        val oldNode = oldNodesById.getValue(match.oldNodeId)
        val newNode = newNodesById.getValue(match.newNodeId)

        compareNonRenderedElFacts(oldNode, newNode).also { result ->
            checked += result.checked
            matched += result.matched
            mismatched += result.mismatched
            uncertain += result.uncertain
            problems += result.problems
            if (result.mismatched > 0) {
                mismatchSignals += "local-binding EL mismatches"
            }
        }
        compareRenderedGuard(oldNode, newNode).also { result ->
            checked += result.checked
            matched += result.matched
            mismatched += result.mismatched
            uncertain += result.uncertain
            problems += result.problems
            if (result.mismatched > 0) {
                mismatchSignals += "rendered-guard mismatches"
            }
        }
        compareAncestry(
            oldNode = oldNode,
            newNode = newNode,
            kindLabel = "form ancestry",
            problemId = ProblemIds.STRUCTURE_FORM_ANCESTRY_CHANGED,
            oldAncestry = oldNode.formAncestry,
            newAncestry = newNode.formAncestry,
        ).also { result ->
            checked += result.checked
            matched += result.matched
            mismatched += result.mismatched
            problems += result.problems
            if (result.mismatched > 0) {
                mismatchSignals += "form-ancestry mismatches"
            }
        }
        compareAncestry(
            oldNode = oldNode,
            newNode = newNode,
            kindLabel = "naming-container ancestry",
            problemId = ProblemIds.STRUCTURE_NAMING_CONTAINER_ANCESTRY_CHANGED,
            oldAncestry = oldNode.namingContainerAncestry,
            newAncestry = newNode.namingContainerAncestry,
        ).also { result ->
            checked += result.checked
            matched += result.matched
            mismatched += result.mismatched
            problems += result.problems
            if (result.mismatched > 0) {
                mismatchSignals += "naming-container mismatches"
            }
        }
        compareIterationAncestry(oldNode, newNode).also { result ->
            checked += result.checked
            matched += result.matched
            mismatched += result.mismatched
            problems += result.problems
            if (result.mismatched > 0) {
                mismatchSignals += "iteration-ancestry mismatches"
            }
        }
        compareResolvedTargets(oldNode, newNode).also { result ->
            checked += result.checked
            matched += result.matched
            mismatched += result.mismatched
            problems += result.problems
            if (result.mismatched > 0) {
                mismatchSignals += "target-resolution mismatches"
            }
        }
    }

    return MatchedNodeComparisonResult(
        checked = checked,
        matched = matched,
        mismatched = mismatched,
        uncertain = uncertain,
        problems = problems,
        mismatchSignals = mismatchSignals,
    )
}

private fun compareResolvedTargets(
    oldNode: SemanticNode,
    newNode: SemanticNode,
): FactComparisonResult {
    val problems = mutableListOf<Problem>()
    var checked = 0
    var matched = 0
    var mismatched = 0

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

    return FactComparisonResult(
        checked = checked,
        matched = matched,
        mismatched = mismatched,
        problems = problems,
    )
}

private fun compareNonRenderedElFacts(
    oldNode: SemanticNode,
    newNode: SemanticNode,
): FactComparisonResult {
    val oldFacts = oldNode.elFacts.filter { it.attributeName != "rendered" && it.isSupported }.associateBy(::elFactKey)
    val newFacts = newNode.elFacts.filter { it.attributeName != "rendered" && it.isSupported }.associateBy(::elFactKey)
    val allKeys = (oldFacts.keys + newFacts.keys).toSortedSet()

    var checked = 0
    var matched = 0
    var mismatched = 0
    var uncertain = 0
    val problems = mutableListOf<Problem>()

    allKeys.forEach { key ->
        compareNormalizedElFact(
            oldFact = oldFacts[key],
            newFact = newFacts[key],
            oldNode = oldNode,
            newNode = newNode,
            mismatchProblem = ::bindingMismatchProblem,
        ).also { result ->
            checked += result.checked
            matched += result.matched
            mismatched += result.mismatched
            uncertain += result.uncertain
            problems += result.problems
        }
    }

    return FactComparisonResult(
        checked = checked,
        matched = matched,
        mismatched = mismatched,
        uncertain = uncertain,
        problems = problems,
    )
}

private fun compareRenderedGuard(
    oldNode: SemanticNode,
    newNode: SemanticNode,
): FactComparisonResult =
    compareNormalizedElFact(
        oldFact = oldNode.renderedAttribute,
        newFact = newNode.renderedAttribute,
        oldNode = oldNode,
        newNode = newNode,
        mismatchProblem = ::renderedGuardProblem,
        countSharedAbsenceAsMatch = true,
    )

private fun compareNormalizedElFact(
    oldFact: SemanticNodeElFact?,
    newFact: SemanticNodeElFact?,
    oldNode: SemanticNode,
    newNode: SemanticNode,
    mismatchProblem: (SemanticNodeElFact?, SemanticNodeElFact?, SemanticNode, SemanticNode) -> Problem,
    countSharedAbsenceAsMatch: Boolean = false,
): FactComparisonResult {
    if (oldFact == null && newFact == null) {
        return if (countSharedAbsenceAsMatch) {
            FactComparisonResult(checked = 1, matched = 1)
        } else {
            FactComparisonResult()
        }
    }
    if (oldFact == null || newFact == null) {
        return FactComparisonResult(
            checked = 1,
            mismatched = 1,
            problems = listOf(mismatchProblem(oldFact, newFact, oldNode, newNode)),
        )
    }
    if (oldFact.normalizedTemplate == null || newFact.normalizedTemplate == null) {
        return FactComparisonResult()
    }
    if (oldFact.bindingReferences.isEmpty() && newFact.bindingReferences.isEmpty()) {
        return if (countSharedAbsenceAsMatch) {
            if (oldFact.normalizedTemplate == newFact.normalizedTemplate &&
                oldFact.globalReferences == newFact.globalReferences
            ) {
                FactComparisonResult(checked = 1, matched = 1)
            } else if (oldFact.globalReferences.isNotEmpty() || newFact.globalReferences.isNotEmpty()) {
                FactComparisonResult(
                    uncertain = 1,
                    problems = listOf(unresolvedGlobalRootProblem(oldFact, newFact)),
                )
            } else {
                FactComparisonResult(
                    checked = 1,
                    mismatched = 1,
                    problems = listOf(mismatchProblem(oldFact, newFact, oldNode, newNode)),
                )
            }
        } else {
            FactComparisonResult()
        }
    }
    if (oldFact.globalReferences.isNotEmpty() || newFact.globalReferences.isNotEmpty()) {
        return FactComparisonResult(
            uncertain = 1,
            problems = listOf(unresolvedGlobalRootProblem(oldFact, newFact)),
        )
    }

    return if (oldFact.normalizedTemplate == newFact.normalizedTemplate) {
        FactComparisonResult(checked = 1, matched = 1)
    } else {
        FactComparisonResult(
            checked = 1,
            mismatched = 1,
            problems = listOf(mismatchProblem(oldFact, newFact, oldNode, newNode)),
        )
    }
}

private fun compareAncestry(
    oldNode: SemanticNode,
    newNode: SemanticNode,
    kindLabel: String,
    problemId: DiagnosticId,
    oldAncestry: List<SemanticNodeAncestor>,
    newAncestry: List<SemanticNodeAncestor>,
): FactComparisonResult {
    val oldRendered = oldAncestry.renderStructuralAncestry()
    val newRendered = newAncestry.renderStructuralAncestry()
    return if (oldRendered == newRendered) {
        FactComparisonResult(checked = 1, matched = 1)
    } else {
        FactComparisonResult(
            checked = 1,
            mismatched = 1,
            problems = listOf(ancestryProblem(problemId, kindLabel, oldNode, newNode, oldRendered, newRendered)),
        )
    }
}

private fun compareIterationAncestry(
    oldNode: SemanticNode,
    newNode: SemanticNode,
): FactComparisonResult {
    val oldRendered = oldNode.iterationAncestry.renderIterationAncestry()
    val newRendered = newNode.iterationAncestry.renderIterationAncestry()
    return if (oldRendered == newRendered) {
        FactComparisonResult(checked = 1, matched = 1)
    } else {
        FactComparisonResult(
            checked = 1,
            mismatched = 1,
            problems = listOf(iterationAncestryProblem(oldNode, newNode, oldRendered, newRendered)),
        )
    }
}

private fun compareStructuralAlignment(
    semanticModels: SemanticModels,
    matchResult: SemanticNodeMatchResult,
): StructuralAlignmentResult {
    val oldNodesById = semanticModels.oldRoot.semanticNodes.associateBy(SemanticNode::nodeId)
    val newNodesById = semanticModels.newRoot.semanticNodes.associateBy(SemanticNode::nodeId)
    val unmatchedOldProblems =
        matchResult.unmatchedOldNodeIds.map { nodeId ->
            val node = oldNodesById.getValue(nodeId)
            unmatchedStructuralNodeProblem(
                node = node,
                summary = "Legacy structural node has no trustworthy match in the refactored tree",
                explanationSide = "legacy",
                counterpartSide = "refactored",
                locations = ProblemLocations(old = ProblemLocation(node.provenance, node.describeForProblem())),
            )
        }
    val unmatchedNewProblems =
        matchResult.unmatchedNewNodeIds.map { nodeId ->
            val node = newNodesById.getValue(nodeId)
            unmatchedStructuralNodeProblem(
                node = node,
                summary = "Refactored structural node has no trustworthy match in the legacy tree",
                explanationSide = "refactored",
                counterpartSide = "legacy",
                locations = ProblemLocations(new = ProblemLocation(node.provenance, node.describeForProblem())),
            )
        }
    val mismatched = unmatchedOldProblems.size + unmatchedNewProblems.size
    val matched = matchResult.matches.size

    return StructuralAlignmentResult(
        checked = matched + mismatched,
        matched = matched,
        mismatched = mismatched,
        problems = unmatchedOldProblems + unmatchedNewProblems,
    )
}

private fun summaryHeadline(
    structuralAlignment: StructuralAlignmentResult,
    matchedNodeComparison: MatchedNodeComparisonResult,
): String =
    when {
        mismatchSignals(structuralAlignment, matchedNodeComparison).isNotEmpty() ->
            "Detected ${mismatchSignals(structuralAlignment, matchedNodeComparison).joinForHeadline()}; broader semantic comparison is still scaffolded."
        matchedNodeComparison.uncertain > 0 ->
            "Matched semantic nodes still depend on unresolved global roots, so bean-level equivalence remains uncertain."
        matchedNodeComparison.checked > 0 ->
            "Matched semantic-node checks agreed for structural alignment, EL normalization, ancestry, rendered guards, and target resolution; broader semantic comparison is still scaffolded."
        structuralAlignment.checked > 0 ->
            "Structural node alignment matched for all candidates; broader semantic comparison is still scaffolded."
        else ->
            "Scaffolded analyzer pipeline only; semantic comparison is not implemented yet."
    }

private fun mismatchSignals(
    structuralAlignment: StructuralAlignmentResult,
    matchedNodeComparison: MatchedNodeComparisonResult,
): List<String> =
    buildList {
        if (structuralAlignment.mismatched > 0) {
            add("unmatched structural nodes")
        }
        addAll(matchedNodeComparison.mismatchSignals)
    }

private fun List<String>.joinForHeadline(): String =
    when (size) {
        0 -> ""
        1 -> single()
        2 -> "${first()} and ${last()}"
        else -> dropLast(1).joinToString(", ") + ", and ${last()}"
    }

private fun bindingMismatchProblem(
    oldFact: SemanticNodeElFact?,
    newFact: SemanticNodeElFact?,
    oldNode: SemanticNode,
    newNode: SemanticNode,
): Problem =
    Problem(
        id = ProblemIds.SCOPE_BINDING_MISMATCH,
        severity = Severity.ERROR,
        category = ProblemCategory.SCOPE,
        summary = "Local variable resolves to different binding",
        locations =
            ProblemLocations(
                old = oldFact.toProblemLocation(oldNode),
                new = newFact.toProblemLocation(newNode),
            ),
        explanation =
            "A matched semantic node no longer carries equivalent normalized EL after resolving local bindings. " +
                "Old: ${oldFact.renderNormalizedOrMissing()} New: ${newFact.renderNormalizedOrMissing()}",
        hint = "Preserve the original local binding scope or keep the matched node's EL-bearing facts normalized to the same local origins.",
    )

private fun renderedGuardProblem(
    oldFact: SemanticNodeElFact?,
    newFact: SemanticNodeElFact?,
    oldNode: SemanticNode,
    newNode: SemanticNode,
): Problem =
    Problem(
        id = ProblemIds.STRUCTURE_RENDERED_GUARD_CHANGED,
        severity = Severity.ERROR,
        category = ProblemCategory.STRUCTURE,
        summary = "Matched node no longer has the same rendered guard",
        locations =
            ProblemLocations(
                old = oldFact.toProblemLocation(oldNode, fallbackSnippet = oldNode.describeForProblem()),
                new = newFact.toProblemLocation(newNode, fallbackSnippet = newNode.describeForProblem()),
            ),
        explanation =
            "The matched node's effective rendered guard changed after normalization. " +
                "Old: ${oldFact.renderNormalizedOrMissing()} New: ${newFact.renderNormalizedOrMissing()}",
        hint = "Keep the same rendered condition, or preserve equivalent local-binding capture if the guard moved.",
    )

private fun unresolvedGlobalRootProblem(
    oldFact: SemanticNodeElFact,
    newFact: SemanticNodeElFact,
): Problem {
    val oldBindingOrigin = oldFact.firstBindingOrigin()
    val newBindingOrigin = newFact.firstBindingOrigin()
    val oldGlobals = oldFact.globalReferences.joinToString(", ") { it.writtenName }
    val newGlobals = newFact.globalReferences.joinToString(", ") { it.writtenName }

    return Problem(
        id = WarningIds.UNSUPPORTED_UNRESOLVED_GLOBAL_ROOT,
        severity = Severity.WARNING,
        category = ProblemCategory.UNSUPPORTED,
        summary = "Unresolved global roots keep EL comparison uncertain",
        locations =
            ProblemLocations(
                old = ProblemLocation(oldFact.provenance, oldFact.rawValue, oldBindingOrigin),
                new = ProblemLocation(newFact.provenance, newFact.rawValue, newBindingOrigin),
            ),
        explanation =
            "The matched semantic-node fact still depends on symbolic global roots, so the EL layer can only compare local-binding shape here. " +
                "Old globals: [$oldGlobals] -> ${oldFact.normalizedTemplate!!.render()} " +
                "New globals: [$newGlobals] -> ${newFact.normalizedTemplate!!.render()}",
        hint = "Treat bean-level equivalence for these unresolved roots as unknown until broader EL or bean analysis exists.",
    )
}

private fun ancestryProblem(
    problemId: DiagnosticId,
    kindLabel: String,
    oldNode: SemanticNode,
    newNode: SemanticNode,
    oldRendered: String,
    newRendered: String,
): Problem =
    Problem(
        id = problemId,
        severity = Severity.ERROR,
        category = ProblemCategory.STRUCTURE,
        summary = "Matched node no longer has the same $kindLabel",
        locations =
            ProblemLocations(
                old = ProblemLocation(oldNode.provenance, oldNode.describeForProblem()),
                new = ProblemLocation(newNode.provenance, newNode.describeForProblem()),
            ),
        explanation =
            "The matched node's $kindLabel changed after the refactor. " +
                "Old: $oldRendered New: $newRendered",
        hint = "Keep the matched node inside the same behavioral container chain so $kindLabel stays stable.",
    )

private fun iterationAncestryProblem(
    oldNode: SemanticNode,
    newNode: SemanticNode,
    oldRendered: String,
    newRendered: String,
): Problem =
    Problem(
        id = ProblemIds.STRUCTURE_ITERATION_ANCESTRY_CHANGED,
        severity = Severity.ERROR,
        category = ProblemCategory.STRUCTURE,
        summary = "Matched node no longer has the same iteration ancestry",
        locations =
            ProblemLocations(
                old = ProblemLocation(oldNode.provenance, oldNode.describeForProblem()),
                new = ProblemLocation(newNode.provenance, newNode.describeForProblem()),
            ),
        explanation =
            "The matched node's enclosing iteration context changed after the refactor. " +
                "Old: $oldRendered New: $newRendered",
        hint = "Keep the matched node under the same repeat or forEach layers so local iteration semantics stay stable.",
    )

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

private fun unmatchedStructuralNodeProblem(
    node: SemanticNode,
    summary: String,
    explanationSide: String,
    counterpartSide: String,
    locations: ProblemLocations,
): Problem =
    Problem(
        id = ProblemIds.STRUCTURE_UNMATCHED_NODE,
        severity = Severity.ERROR,
        category = ProblemCategory.STRUCTURE,
        summary = summary,
        locations = locations,
        explanation =
            "The structural matcher could not align ${node.describeForProblem()} from the $explanationSide tree " +
                "with any node in the $counterpartSide tree after explicit id, explicit target-relationship, and semantic-signature matching.",
        hint = "Preserve a stable structural anchor such as explicit ids, resolved targets, or equivalent ancestry/context around this node.",
    )

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
        SemanticElCarrierKind.ELEMENT_ATTRIBUTE -> "$ownerTagName @$attributeName"
        SemanticElCarrierKind.TEXT_NODE -> "$ownerTagName text"
        SemanticElCarrierKind.INCLUDE_ATTRIBUTE -> "$ownerTagName @$attributeName"
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

private fun SemanticNode.describeForProblem(): String =
    buildString {
        append(nodeName)
        explicitIdAttribute?.rawValue?.let { explicitId ->
            append("#")
            append(explicitId)
        }
    }

private fun SemanticNodeElFact?.toProblemLocation(
    node: SemanticNode,
    fallbackSnippet: String? = null,
): ProblemLocation =
    if (this == null) {
        ProblemLocation(node.provenance, fallbackSnippet ?: node.describeForProblem())
    } else {
        ProblemLocation(provenance, rawValue, firstBindingOrigin())
    }

private fun SemanticNodeElFact.renderNormalizedOrMissing(): String =
    normalizedTemplate?.render() ?: rawValue

private fun SemanticNodeElFact?.renderNormalizedOrMissing(): String =
    this?.renderNormalizedOrMissing() ?: "<missing>"

private fun SemanticNodeElFact.firstBindingOrigin(): BindingOrigin? =
    bindingReferences.firstOrNull()?.binding?.origin

private fun elFactKey(fact: SemanticNodeElFact): String =
    buildString {
        append(fact.carrierKind.name)
        append("|")
        append(fact.attributeName ?: "<text>")
        append("|")
        append(fact.ownerName ?: "<none>")
    }

private fun List<SemanticNodeAncestor>.renderStructuralAncestry(): String =
    if (isEmpty()) {
        "<none>"
    } else {
        joinToString(" > ") { ancestor ->
            ancestor.semanticIdentity() + (ancestor.explicitId?.let { "#$it" } ?: "")
        }
    }

private fun List<SemanticIterationAncestor>.renderIterationAncestry(): String =
    if (isEmpty()) {
        "<none>"
    } else {
        joinToString(" > ") { ancestor ->
            ancestor.semanticIdentity() + ancestor.bindingKinds.joinToString(prefix = "[", postfix = "]") { it.name }
        }
    }

private fun SemanticNodeAncestor.semanticIdentity(): String =
    semanticIdentity(logicalName, nodeName, syntaxRole)

private fun SemanticIterationAncestor.semanticIdentity(): String =
    semanticIdentity(logicalName, nodeName, syntaxRole)

private fun semanticIdentity(
    logicalName: LogicalName?,
    nodeName: String,
    syntaxRole: SyntaxRole?,
): String =
    if (logicalName == null) {
        "${nodeName}@${syntaxRole?.name ?: "<none>"}"
    } else {
        "${logicalName.namespaceUri ?: "<none>"}:${logicalName.localName}@${syntaxRole?.name ?: "<none>"}"
    }
