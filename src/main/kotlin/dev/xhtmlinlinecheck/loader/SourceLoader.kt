package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.analyzer.AnalysisRequest
import dev.xhtmlinlinecheck.domain.AnalysisSide
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceGraphEdge
import dev.xhtmlinlinecheck.domain.SourceGraphFile
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailure
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
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
        fun scaffold(tagRules: TagRuleRegistry = TagRuleRegistry.builtIns()): SourceLoader =
            SourceLoader { request ->
                LoadedSources(
                    oldRoot = loadRoot(
                        side = AnalysisSide.OLD,
                        path = request.oldRoot,
                        rootDirectory = request.baseOld,
                        tagRules = tagRules,
                    ),
                    newRoot = loadRoot(
                        side = AnalysisSide.NEW,
                        path = request.newRoot,
                        rootDirectory = request.baseNew,
                        tagRules = tagRules,
                    ),
                )
            }

        private fun loadRoot(
            side: AnalysisSide,
            path: Path,
            rootDirectory: Path?,
            tagRules: TagRuleRegistry,
        ): LoadedSource {
            val document = SourceDocument.fromPath(
                side = side,
                path = path,
                rootDirectory = rootDirectory,
            )
            val sourceGraphFile =
                loadGraph(
                    document = document,
                    analysisRootAnchor = document.absolutePath.parent ?: document.rootDirectory,
                    tagRules = tagRules,
                )
            return LoadedSource(
                document = document,
                contents = sourceGraphFile.contents,
                sourceGraphFile = sourceGraphFile,
            )
        }

        private fun loadGraph(
            document: SourceDocument,
            analysisRootAnchor: Path,
            parentEdge: SourceGraphEdge? = null,
            ancestry: List<SourceDocument> = listOf(document),
            tagRules: TagRuleRegistry,
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
                    tagRules = tagRules,
                ).map { discoveredEdge ->
                    val includedDocument =
                        discoveredEdge.sourcePath?.let { sourcePath ->
                            resolveIncludedDocument(
                                document = document,
                                sourcePath = sourcePath,
                                analysisRootAnchor = analysisRootAnchor,
                            )
                        }
                    val includeFailure =
                        discoveredEdge.sourcePath
                            ?.takeIf(::isDynamicIncludePath)
                            ?.let(SourceGraphIncludeFailure::dynamicPath)
                            ?: includedDocument
                                ?.takeIf { included -> ancestry.any { it.absolutePath == included.absolutePath } }
                                ?.let { included ->
                                    SourceGraphIncludeFailure.includeCycle(
                                        ancestry.dropWhile { it.absolutePath != included.absolutePath } + included,
                                    )
                                }
                            ?: includedDocument
                                ?.takeIf { included -> !Files.exists(included.absolutePath) }
                                ?.let(SourceGraphIncludeFailure::missingFile)

                    val includedFile =
                        includedDocument
                            ?.takeIf { Files.exists(it.absolutePath) && includeFailure == null }
                            ?.let {
                                loadGraph(
                                    document = it,
                                    analysisRootAnchor = analysisRootAnchor,
                                    parentEdge = discoveredEdge.copy(includedDocument = it),
                                    ancestry = ancestry + it,
                                    tagRules = tagRules,
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

        private fun isDynamicIncludePath(sourcePath: String): Boolean =
            sourcePath.contains("#{") || sourcePath.contains("\${")

        private fun resolveIncludedDocument(
            document: SourceDocument,
            sourcePath: String,
            analysisRootAnchor: Path,
        ): SourceDocument? {
            val primary = document.resolveIncludeSource(sourcePath) ?: return null
            if (!sourcePath.startsWith("/") || Files.exists(primary.absolutePath)) {
                return primary
            }

            val includePath = Path.of(sourcePath.removePrefix("/").replace('\\', '/')).normalize()
            val fallbackPath = analysisRootAnchor.resolve(includePath).toAbsolutePath().normalize()
            if (!Files.exists(fallbackPath)) {
                return primary
            }

            val displayPath =
                if (fallbackPath.startsWith(primary.rootDirectory)) {
                    primary.rootDirectory.relativize(fallbackPath).invariantSeparatorsPathString
                } else {
                    fallbackPath.invariantSeparatorsPathString
                }

            return primary.copy(
                absolutePath = fallbackPath,
                displayPath = displayPath,
            )
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
