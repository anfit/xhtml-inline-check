package dev.xhtmlinlinecheck.semantic

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
                val oldScopeModel = ScopeStackModel.fromSyntaxTree(parsedTrees.oldRoot.syntaxTree)
                val oldElOccurrences = SemanticElExtractor.extract(parsedTrees.oldRoot.syntaxTree, tagRules)
                val oldNormalizedElOccurrences = SemanticElNormalizer.normalize(oldElOccurrences, oldScopeModel)
                val newScopeModel = ScopeStackModel.fromSyntaxTree(parsedTrees.newRoot.syntaxTree)
                val newElOccurrences = SemanticElExtractor.extract(parsedTrees.newRoot.syntaxTree, tagRules)
                val newNormalizedElOccurrences = SemanticElNormalizer.normalize(newElOccurrences, newScopeModel)
                SemanticModels(
                    oldRoot = SemanticModel(
                        document = parsedTrees.oldRoot.document,
                        provenance = parsedTrees.oldRoot.provenance,
                        syntaxTree = parsedTrees.oldRoot.syntaxTree,
                        tagRules = tagRules,
                        scopeModel = oldScopeModel,
                        elOccurrences = oldElOccurrences,
                        normalizedElOccurrences = oldNormalizedElOccurrences,
                        semanticNodes =
                            SemanticNodeExtractor.extract(
                                syntaxTree = parsedTrees.oldRoot.syntaxTree,
                                scopeModel = oldScopeModel,
                                elOccurrences = oldElOccurrences,
                                normalizedElOccurrences = oldNormalizedElOccurrences,
                            ),
                    ),
                    newRoot = SemanticModel(
                        document = parsedTrees.newRoot.document,
                        provenance = parsedTrees.newRoot.provenance,
                        syntaxTree = parsedTrees.newRoot.syntaxTree,
                        tagRules = tagRules,
                        scopeModel = newScopeModel,
                        elOccurrences = newElOccurrences,
                        normalizedElOccurrences = newNormalizedElOccurrences,
                        semanticNodes =
                            SemanticNodeExtractor.extract(
                                syntaxTree = parsedTrees.newRoot.syntaxTree,
                                scopeModel = newScopeModel,
                                elOccurrences = newElOccurrences,
                                normalizedElOccurrences = newNormalizedElOccurrences,
                            ),
                    ),
                )
            }
    }
}
