package dev.xhtmlinlinecheck.domain

import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

enum class AnalysisSide {
    OLD,
    NEW,
}

data class SourceDocument(
    val side: AnalysisSide,
    val absolutePath: Path,
    val rootDirectory: Path,
    val displayPath: String = absolutePath.invariantSeparatorsPathString,
) {
    fun resolveIncludeSource(sourcePath: String): SourceDocument? {
        val includePath = parseIncludePath(sourcePath) ?: return null
        val normalizedPath =
            normalizeAbsolute(
                if (sourcePath.startsWith("/")) {
                    rootDirectory.resolve(includePath)
                } else {
                    absolutePath.parent.resolve(includePath)
                },
            )
        return fromAbsolutePath(
            side = side,
            absolutePath = normalizedPath,
            rootDirectory = rootDirectory,
        )
    }

    companion object {
        fun fromPath(side: AnalysisSide, path: Path, rootDirectory: Path? = null): SourceDocument {
            val normalizedRootDirectory = normalizeAbsolute(rootDirectory ?: defaultRootDirectory(path))
            val normalizedPath = resolveAbsolute(path, normalizedRootDirectory)
            return fromAbsolutePath(
                side = side,
                absolutePath = normalizedPath,
                rootDirectory = normalizedRootDirectory,
            )
        }

        private fun defaultRootDirectory(path: Path): Path =
            if (path.isAbsolute) {
                path.parent ?: path.root ?: path
            } else {
                Path.of("").toAbsolutePath()
            }

        private fun resolveAbsolute(path: Path, rootDirectory: Path): Path =
            normalizeAbsolute(
                if (path.isAbsolute) {
                    path
                } else {
                    rootDirectory.resolve(path)
                },
            )

        private fun fromAbsolutePath(side: AnalysisSide, absolutePath: Path, rootDirectory: Path): SourceDocument {
            val normalizedRootDirectory = normalizeAbsolute(rootDirectory)
            val normalizedPath = normalizeAbsolute(absolutePath)
            val displayPath = normalizedPath.relativeTo(normalizedRootDirectory)
            return SourceDocument(
                side = side,
                absolutePath = normalizedPath,
                rootDirectory = normalizedRootDirectory,
                displayPath = displayPath,
            )
        }

        private fun normalizeAbsolute(path: Path): Path = path.toAbsolutePath().normalize()

        private fun parseIncludePath(sourcePath: String): Path? {
            if (sourcePath.isBlank()) {
                return null
            }
            if (sourcePath.contains("#{") || sourcePath.contains("\${")) {
                return null
            }

            val normalized = sourcePath.removePrefix("/").replace('\\', '/')
            if (normalized.isBlank()) {
                return null
            }

            return Path.of(normalized).normalize()
        }

        private fun Path.relativeTo(rootDirectory: Path): String =
            if (startsWith(rootDirectory)) {
                rootDirectory.relativize(this).invariantSeparatorsPathString
            } else {
                invariantSeparatorsPathString
            }
    }
}

data class SourcePosition(
    val line: Int,
    val column: Int,
) {
    init {
        require(line > 0) { "line must be positive" }
        require(column > 0) { "column must be positive" }
    }
}

data class SourceSpan(
    val start: SourcePosition,
    val end: SourcePosition? = null,
)

data class SourceLocation(
    val document: SourceDocument,
    val span: SourceSpan? = null,
    val attributeName: String? = null,
) {
    fun render(): String = buildString {
        append(document.displayPath)
        span?.let { append(":${it.start.line}:${it.start.column}") }
        attributeName?.let { append(" @").append(it) }
    }
}

data class IncludeProvenanceStep(
    val includeSite: SourceLocation,
    val includedDocument: SourceDocument,
    val parameterNames: List<String> = emptyList(),
)

data class Provenance(
    val physicalLocation: SourceLocation,
    val logicalLocation: SourceLocation = physicalLocation,
    val includeStack: List<IncludeProvenanceStep> = emptyList(),
) {
    companion object {
        fun forRoot(document: SourceDocument): Provenance {
            val rootLocation = SourceLocation(document = document)
            return Provenance(
                physicalLocation = rootLocation,
                logicalLocation = rootLocation,
            )
        }
    }
}
