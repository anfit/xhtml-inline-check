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
                val newScopeModel = ScopeStackModel.fromSyntaxTree(parsedTrees.newRoot.syntaxTree)
                val newElOccurrences = SemanticElExtractor.extract(parsedTrees.newRoot.syntaxTree, tagRules)
                SemanticModels(
                    oldRoot = SemanticModel(
                        document = parsedTrees.oldRoot.document,
                        provenance = parsedTrees.oldRoot.provenance,
                        syntaxTree = parsedTrees.oldRoot.syntaxTree,
                        tagRules = tagRules,
                        scopeModel = oldScopeModel,
                        elOccurrences = oldElOccurrences,
                        normalizedElOccurrences = SemanticElNormalizer.normalize(oldElOccurrences, oldScopeModel),
                    ),
                    newRoot = SemanticModel(
                        document = parsedTrees.newRoot.document,
                        provenance = parsedTrees.newRoot.provenance,
                        syntaxTree = parsedTrees.newRoot.syntaxTree,
                        tagRules = tagRules,
                        scopeModel = newScopeModel,
                        elOccurrences = newElOccurrences,
                        normalizedElOccurrences = SemanticElNormalizer.normalize(newElOccurrences, newScopeModel),
                    ),
                )
            }
    }
}
