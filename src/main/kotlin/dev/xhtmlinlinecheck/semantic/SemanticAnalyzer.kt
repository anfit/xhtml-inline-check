package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.syntax.ParsedTrees

data class SemanticModels(
    val oldRoot: SemanticModel,
    val newRoot: SemanticModel,
)

fun interface SemanticAnalyzer {
    fun analyze(parsedTrees: ParsedTrees): SemanticModels

    companion object {
        fun scaffold(): SemanticAnalyzer =
            SemanticAnalyzer { parsedTrees ->
                SemanticModels(
                    oldRoot = SemanticModel(
                        document = parsedTrees.oldRoot.document,
                        provenance = parsedTrees.oldRoot.provenance,
                        sourceGraphFile = parsedTrees.oldRoot.sourceGraphFile,
                        rootNode = parsedTrees.oldRoot.rootNode,
                    ),
                    newRoot = SemanticModel(
                        document = parsedTrees.newRoot.document,
                        provenance = parsedTrees.newRoot.provenance,
                        sourceGraphFile = parsedTrees.newRoot.sourceGraphFile,
                        rootNode = parsedTrees.newRoot.rootNode,
                    ),
                )
            }
    }
}
