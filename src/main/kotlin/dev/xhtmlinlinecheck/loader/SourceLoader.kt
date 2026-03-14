package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.SourceDocument

data class LoadedSources(
    val oldRoot: LoadedSource,
    val newRoot: LoadedSource,
)

fun interface SourceLoader {
    fun load(request: AnalysisRequest): LoadedSources

    companion object {
        fun scaffold(): SourceLoader =
            SourceLoader { request ->
                LoadedSources(
                    oldRoot = LoadedSource(
                        document = SourceDocument.fromPath(
                            side = AnalysisSide.OLD,
                            path = request.oldRoot,
                            rootDirectory = request.baseOld,
                        ),
                    ),
                    newRoot = LoadedSource(
                        document = SourceDocument.fromPath(
                            side = AnalysisSide.NEW,
                            path = request.newRoot,
                            rootDirectory = request.baseNew,
                        ),
                    ),
                )
            }
    }
}
