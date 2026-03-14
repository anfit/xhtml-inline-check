package dev.xhtmlinlinecheck.loader

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceGraphFile

data class LoadedSource(
    val document: SourceDocument,
    val contents: String,
    val sourceGraphFile: SourceGraphFile = SourceGraphFile.root(document),
    val provenance: Provenance = sourceGraphFile.provenance,
) {
    init {
        require(sourceGraphFile.document == document) {
            "loaded source graph file must match loaded document"
        }
        require(provenance == sourceGraphFile.provenance) {
            "loaded source provenance must come from the source graph file"
        }
    }
}
