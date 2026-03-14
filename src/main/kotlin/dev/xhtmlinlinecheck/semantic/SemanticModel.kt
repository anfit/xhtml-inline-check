package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceGraphFile

data class SemanticModel(
    val document: SourceDocument,
    val provenance: Provenance,
    val sourceGraphFile: SourceGraphFile,
) {
    init {
        require(sourceGraphFile.document == document) {
            "semantic source graph file must match semantic document"
        }
        require(provenance == sourceGraphFile.provenance) {
            "semantic provenance must come from the source graph file"
        }
    }
}
