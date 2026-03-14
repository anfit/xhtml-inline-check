package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceDocument

data class LoadedSource(
    val document: SourceDocument,
    val contents: String,
    val provenance: Provenance = Provenance.forRoot(document),
)
