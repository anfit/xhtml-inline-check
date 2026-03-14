package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceDocument
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxTree

data class SemanticModel(
    val document: SourceDocument,
    val provenance: Provenance,
    val syntaxTree: XhtmlSyntaxTree,
    val tagRules: TagRuleRegistry,
    val scopeModel: ScopeStackModel,
) {
    init {
        require(provenance.physicalLocation.document == document) {
            "semantic provenance physical location must point at the semantic document"
        }
    }
}
