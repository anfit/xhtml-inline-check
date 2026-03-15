package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.analyzer.AnalysisProfiler
import dev.xhtmlinlinecheck.syntax.ParsedTrees
import dev.xhtmlinlinecheck.rules.TagRuleRegistry

data class SemanticModels(
    val oldRoot: SemanticModel,
    val newRoot: SemanticModel,
)

fun interface SemanticAnalyzer {
    fun analyze(parsedTrees: ParsedTrees): SemanticModels

    companion object {
        fun scaffold(tagRules: TagRuleRegistry = TagRuleRegistry.builtIns()): SemanticAnalyzer =
            SemanticAnalyzer { parsedTrees ->
                val oldSemanticPass =
                    AnalysisProfiler.measure("semantic.old") {
                        analyzeTree(parsedTrees.oldRoot.syntaxTree, tagRules)
                    }
                val newSemanticPass =
                    AnalysisProfiler.measure("semantic.new") {
                        analyzeTree(parsedTrees.newRoot.syntaxTree, tagRules)
                    }
                SemanticModels(
                    oldRoot = SemanticModel(
                        document = parsedTrees.oldRoot.document,
                        provenance = parsedTrees.oldRoot.provenance,
                        syntaxTree = parsedTrees.oldRoot.syntaxTree,
                        tagRules = tagRules,
                        scopeModel = oldSemanticPass.scopeModel,
                        elOccurrences = oldSemanticPass.elOccurrences,
                        normalizedElOccurrences = oldSemanticPass.normalizedElOccurrences,
                        semanticNodes = oldSemanticPass.semanticNodes,
                    ),
                    newRoot = SemanticModel(
                        document = parsedTrees.newRoot.document,
                        provenance = parsedTrees.newRoot.provenance,
                        syntaxTree = parsedTrees.newRoot.syntaxTree,
                        tagRules = tagRules,
                        scopeModel = newSemanticPass.scopeModel,
                        elOccurrences = newSemanticPass.elOccurrences,
                        normalizedElOccurrences = newSemanticPass.normalizedElOccurrences,
                        semanticNodes = newSemanticPass.semanticNodes,
                    ),
                )
            }
    }
}

private data class SemanticTreeAnalysis(
    val scopeModel: ScopeStackModel,
    val elOccurrences: List<SemanticElOccurrence>,
    val normalizedElOccurrences: List<NormalizedSemanticElOccurrence>,
    val semanticNodes: List<SemanticNode>,
)

private fun analyzeTree(
    syntaxTree: dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree,
    tagRules: TagRuleRegistry,
): SemanticTreeAnalysis {
    val scopeModel = AnalysisProfiler.measure("scope") { ScopeStackModel.fromSyntaxTree(syntaxTree) }
    val extraction =
        AnalysisProfiler.measure("extract+normalize") {
            SemanticTreeExtractor.extract(
                syntaxTree = syntaxTree,
                scopeModel = scopeModel,
                tagRules = tagRules,
            )
        }
    val semanticNodes =
        AnalysisProfiler.measure("targets") {
            SemanticTargetResolver.resolve(extraction.semanticNodes)
        }

    return SemanticTreeAnalysis(
        scopeModel = scopeModel,
        elOccurrences = extraction.elOccurrences,
        normalizedElOccurrences = extraction.normalizedElOccurrences,
        semanticNodes = semanticNodes,
    )
}
