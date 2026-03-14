package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceDocument

data class SemanticModel(
    val document: SourceDocument,
    val provenance: Provenance,
)
