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
                SemanticModels(
                    oldRoot = SemanticModel(
                        document = parsedTrees.oldRoot.document,
                        provenance = parsedTrees.oldRoot.provenance,
                        syntaxTree = parsedTrees.oldRoot.syntaxTree,
                        tagRules = tagRules,
                        scopeModel = ScopeStackModel.fromSyntaxTree(parsedTrees.oldRoot.syntaxTree),
                        elOccurrences = SemanticElExtractor.extract(parsedTrees.oldRoot.syntaxTree, tagRules),
                    ),
                    newRoot = SemanticModel(
                        document = parsedTrees.newRoot.document,
                        provenance = parsedTrees.newRoot.provenance,
                        syntaxTree = parsedTrees.newRoot.syntaxTree,
                        tagRules = tagRules,
                        scopeModel = ScopeStackModel.fromSyntaxTree(parsedTrees.newRoot.syntaxTree),
                        elOccurrences = SemanticElExtractor.extract(parsedTrees.newRoot.syntaxTree, tagRules),
                    ),
                )
            }
    }
}
