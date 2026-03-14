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
    val displayPath: String = absolutePath.invariantSeparatorsPathString,
) {
    companion object {
        fun fromPath(side: AnalysisSide, path: Path): SourceDocument {
            val normalizedPath = path.normalize()
            return SourceDocument(
                side = side,
                absolutePath = normalizedPath,
                displayPath = normalizedPath.invariantSeparatorsPathString,
            )
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
