package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceDocument

data class ParsedSourceTree(
    val document: SourceDocument,
    val provenance: Provenance,
)
