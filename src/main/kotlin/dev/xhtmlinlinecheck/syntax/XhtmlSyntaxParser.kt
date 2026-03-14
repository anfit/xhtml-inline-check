package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.loader.LoadedSources

data class ParsedTrees(
    val oldRoot: ParsedSourceTree,
    val newRoot: ParsedSourceTree,
)

fun interface XhtmlSyntaxParser {
    fun parse(loadedSources: LoadedSources): ParsedTrees

    companion object {
        fun scaffold(): XhtmlSyntaxParser =
            XhtmlSyntaxParser { loadedSources ->
                ParsedTrees(
                    oldRoot = ParsedSourceTree(
                        document = loadedSources.oldRoot.document,
                        provenance = loadedSources.oldRoot.provenance,
                    ),
                    newRoot = ParsedSourceTree(
                        document = loadedSources.newRoot.document,
                        provenance = loadedSources.newRoot.provenance,
                    ),
                )
            }
    }
}
