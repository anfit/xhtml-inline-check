package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.syntax.ParsedTrees
import java.nio.file.Path

data class SemanticModels(
    val oldRoot: Path,
    val newRoot: Path,
)

fun interface SemanticAnalyzer {
    fun analyze(parsedTrees: ParsedTrees): SemanticModels

    companion object {
        fun scaffold(): SemanticAnalyzer =
            SemanticAnalyzer { parsedTrees ->
                SemanticModels(
                    oldRoot = parsedTrees.oldRoot,
                    newRoot = parsedTrees.newRoot,
                )
            }
    }
}
