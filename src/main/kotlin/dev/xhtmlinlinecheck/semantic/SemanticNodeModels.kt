package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.BindingId
import dev.xhtmlinlinecheck.domain.BindingOrigin
import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.rules.SyntaxRole
import dev.xhtmlinlinecheck.syntax.LogicalName
import dev.xhtmlinlinecheck.syntax.LogicalNodePath

@JvmInline
value class SemanticNodeId(
    val value: String,
) {
    override fun toString(): String = value
}

enum class SemanticNodeKind {
    ELEMENT,
    INCLUDE,
    TEXT,
}

data class SemanticNodeAttribute(
    val attributeName: String,
    val rawValue: String,
    val location: SourceLocation,
)

data class SemanticNodeAncestor(
    val nodeId: SemanticNodeId,
    val nodeName: String,
    val location: SourceLocation,
    val provenance: Provenance,
)

data class SemanticIterationAncestor(
    val nodeId: SemanticNodeId,
    val nodeName: String,
    val location: SourceLocation,
    val provenance: Provenance,
    val bindingIds: List<BindingId>,
    val bindingOrigins: List<BindingOrigin>,
)

data class SemanticNodeElFact(
    val carrierKind: SemanticElCarrierKind,
    val ownerName: String? = null,
    val attributeName: String? = null,
    val rawValue: String,
    val location: SourceLocation,
    val provenance: Provenance,
    val normalizedTemplate: NormalizedElTemplate? = null,
    val bindingReferences: List<SemanticElBindingReference> = emptyList(),
    val globalReferences: List<SemanticElGlobalReference> = emptyList(),
    val parseFailure: SemanticElParseFailure? = null,
) {
    val isSupported: Boolean
        get() = parseFailure == null
}

data class SemanticNode(
    val nodeId: SemanticNodeId,
    val nodePath: LogicalNodePath,
    val kind: SemanticNodeKind,
    val nodeName: String,
    val logicalName: LogicalName? = null,
    val syntaxRole: SyntaxRole? = null,
    val location: SourceLocation,
    val provenance: Provenance,
    val isTransparentStructureWrapper: Boolean,
    val isForm: Boolean,
    val isNamingContainer: Boolean,
    val explicitIdAttribute: SemanticNodeAttribute? = null,
    val renderedAttribute: SemanticNodeElFact? = null,
    val targetAttributes: List<SemanticNodeAttribute> = emptyList(),
    val elFacts: List<SemanticNodeElFact> = emptyList(),
    val formAncestry: List<SemanticNodeAncestor> = emptyList(),
    val namingContainerAncestry: List<SemanticNodeAncestor> = emptyList(),
    val iterationAncestry: List<SemanticIterationAncestor> = emptyList(),
)
