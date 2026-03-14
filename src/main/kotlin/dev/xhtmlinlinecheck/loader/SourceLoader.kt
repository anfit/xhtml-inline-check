package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceGraphEdge
import dev.xhtmlinlinecheck.domain.SourceGraphFile
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailure
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

data class LoadedSources(
    val oldRoot: LoadedSource,
    val newRoot: LoadedSource,
)

class SourceLoadException(
    val document: SourceDocument,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

fun interface SourceLoader {
    fun load(request: AnalysisRequest): LoadedSources

    companion object {
        fun scaffold(): SourceLoader =
            SourceLoader { request ->
                LoadedSources(
                    oldRoot = loadRoot(
                        side = AnalysisSide.OLD,
                        path = request.oldRoot,
                        rootDirectory = request.baseOld,
                    ),
                    newRoot = loadRoot(
                        side = AnalysisSide.NEW,
                        path = request.newRoot,
                        rootDirectory = request.baseNew,
                    ),
                )
            }

        private fun loadRoot(
            side: AnalysisSide,
            path: Path,
            rootDirectory: Path?,
        ): LoadedSource {
            val document = SourceDocument.fromPath(
                side = side,
                path = path,
                rootDirectory = rootDirectory,
            )
            val sourceGraphFile = loadGraph(document)
            return LoadedSource(
                document = document,
                contents = sourceGraphFile.contents,
                sourceGraphFile = sourceGraphFile,
            )
        }

        private fun loadGraph(
            document: SourceDocument,
            parentEdge: SourceGraphEdge? = null,
            ancestry: List<SourceDocument> = listOf(document),
        ): SourceGraphFile {
            val contents = readContents(document)
            val graphFile =
                parentEdge?.let { SourceGraphFile.included(document = document, edge = it, contents = contents) }
                    ?: SourceGraphFile.root(document).copy(contents = contents)

            val includeEdges =
                SourceGraphIncludeDiscovery.discover(
                    document = document,
                    contents = contents,
                    stackBefore = graphFile.stack,
                ).map { discoveredEdge ->
                    val includedDocument = discoveredEdge.sourcePath?.let(document::resolveIncludeSource)
                    val includeFailure =
                        includedDocument
                            ?.takeIf { included -> ancestry.any { it.absolutePath == included.absolutePath } }
                            ?.let { included ->
                                SourceGraphIncludeFailure.includeCycle(
                                    ancestry
                                        .dropWhile { it.absolutePath != included.absolutePath } + included,
                                )
                            }
                    val includedFile =
                        includedDocument
                            ?.takeIf { Files.exists(it.absolutePath) && includeFailure == null }
                            ?.let {
                                loadGraph(
                                    document = it,
                                    parentEdge = discoveredEdge.copy(includedDocument = it),
                                    ancestry = ancestry + it,
                                )
                            }
                    discoveredEdge.copy(
                        includedDocument = includedDocument,
                        includedFile = includedFile,
                        includeFailure = includeFailure,
                    )
                }
            return graphFile.copy(includeEdges = includeEdges)
        }

        private fun readContents(document: SourceDocument): String =
            try {
                Files.readString(document.absolutePath)
            } catch (exception: NoSuchFileException) {
                throw SourceLoadException(
                    document = document,
                    message =
                        "Missing source file for ${document.side.name.lowercase()}: " +
                            "${document.displayPath} (${document.absolutePath.invariantSeparatorsPathString})",
                    cause = exception,
                )
            } catch (exception: IOException) {
                throw SourceLoadException(
                    document = document,
                    message =
                        "Failed to read source file for ${document.side.name.lowercase()}: " +
                            "${document.displayPath} (${document.absolutePath.invariantSeparatorsPathString})",
                    cause = exception,
                )
            }
    }
}
