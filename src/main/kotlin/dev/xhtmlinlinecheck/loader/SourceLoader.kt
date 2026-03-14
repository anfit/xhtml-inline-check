package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import java.nio.file.Path

data class LoadedSources(
    val oldRoot: Path,
    val newRoot: Path,
)

fun interface SourceLoader {
    fun load(request: AnalysisRequest): LoadedSources

    companion object {
        fun scaffold(): SourceLoader =
            SourceLoader { request ->
                LoadedSources(
                    oldRoot = request.oldRoot.normalize(),
                    newRoot = request.newRoot.normalize(),
                )
            }
    }
}
