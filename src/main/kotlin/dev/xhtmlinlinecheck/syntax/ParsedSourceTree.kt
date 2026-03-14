package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.domain.SourceGraphFile

data class ParsedSourceTree(
    val document: SourceDocument,
    val provenance: Provenance,
    val sourceGraphFile: SourceGraphFile,
) {
    init {
        require(sourceGraphFile.document == document) {
            "parsed source graph file must match parsed document"
        }
        require(provenance == sourceGraphFile.provenance) {
            "parsed provenance must come from the source graph file"
        }
    }
}
