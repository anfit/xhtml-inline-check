package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.el.ElTemplate
import dev.xhtmlinlinecheck.syntax.LogicalNodePath

enum class SemanticElCarrierKind {
    ELEMENT_ATTRIBUTE,
    TEXT_NODE,
    INCLUDE_ATTRIBUTE,
    INCLUDE_PARAMETER,
}

data class SemanticElParseFailure(
    val message: String,
    val offset: Int,
)

data class SemanticElOccurrence(
    val carrierKind: SemanticElCarrierKind,
    val nodePath: LogicalNodePath,
    val ownerTagName: String,
    val ownerName: String? = null,
    val attributeName: String? = null,
    val rawValue: String,
    val location: SourceLocation,
    val provenance: Provenance,
    val template: ElTemplate? = null,
    val parseFailure: SemanticElParseFailure? = null,
) {
    init {
        require((template == null) != (parseFailure == null)) {
            "semantic EL occurrences must carry either a parsed template or a parse failure"
        }
    }

    val isSupported: Boolean
        get() = parseFailure == null
}
