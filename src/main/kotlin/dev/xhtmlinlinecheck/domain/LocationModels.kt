package dev.xhtmlinlinecheck.domain

data class SourcePosition(
    val line: Int,
    val column: Int,
)

data class SourceLocation(
    val path: String,
    val position: SourcePosition? = null,
    val attributeName: String? = null,
)

data class Provenance(
    val physicalLocation: SourceLocation,
    val includeChain: List<String> = emptyList(),
)
