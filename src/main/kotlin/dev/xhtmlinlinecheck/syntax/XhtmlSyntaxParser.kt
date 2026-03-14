package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.loader.LoadedSources
import java.nio.file.Path

data class ParsedTrees(
    val oldRoot: Path,
    val newRoot: Path,
)

fun interface XhtmlSyntaxParser {
    fun parse(loadedSources: LoadedSources): ParsedTrees

    companion object {
        fun scaffold(): XhtmlSyntaxParser =
            XhtmlSyntaxParser { loadedSources ->
                ParsedTrees(
                    oldRoot = loadedSources.oldRoot,
                    newRoot = loadedSources.newRoot,
                )
            }
    }
}
