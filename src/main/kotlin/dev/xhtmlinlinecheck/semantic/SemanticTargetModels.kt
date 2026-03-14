package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.SourceLocation

enum class ComponentTargetAttributeKind(
    val attributeName: String,
    val allowsMultipleReferences: Boolean,
) {
    FOR(attributeName = "for", allowsMultipleReferences = false),
    UPDATE(attributeName = "update", allowsMultipleReferences = true),
    RENDER(attributeName = "render", allowsMultipleReferences = true),
    PROCESS(attributeName = "process", allowsMultipleReferences = true),
    EXECUTE(attributeName = "execute", allowsMultipleReferences = true),
    ;

    companion object {
        fun fromAttributeName(attributeName: String): ComponentTargetAttributeKind? =
            entries.firstOrNull { it.attributeName == attributeName }
    }
}

enum class ComponentTargetReferenceKind {
    COMPONENT_ID,
    SEARCH_EXPRESSION,
}

data class ComponentTargetReference(
    val rawToken: String,
    val kind: ComponentTargetReferenceKind,
) {
    fun render(): String = rawToken
}

enum class ComponentTargetResolutionKind {
    COMPONENT,
    SOURCE_NODE,
    FORM,
    SEARCH_EXPRESSION,
    UNRESOLVED,
}

data class ResolvedComponentTargetNode(
    val nodeName: String,
    val explicitId: String? = null,
    val formAnchor: String? = null,
) {
    fun render(): String =
        buildString {
            append(nodeName)
            explicitId?.let { append("#").append(it) }
            append("@form:")
            append(formAnchor ?: "<none>")
        }
}

data class ResolvedComponentTargetReference(
    val reference: ComponentTargetReference,
    val kind: ComponentTargetResolutionKind,
    val target: ResolvedComponentTargetNode? = null,
    val detail: String? = null,
) {
    fun render(): String =
        when (kind) {
            ComponentTargetResolutionKind.COMPONENT ->
                "component:${reference.rawToken}->${requireNotNull(target).render()}"

            ComponentTargetResolutionKind.SOURCE_NODE ->
                "@this"

            ComponentTargetResolutionKind.FORM ->
                "@form->${requireNotNull(target).render()}"

            ComponentTargetResolutionKind.SEARCH_EXPRESSION ->
                "search:${reference.rawToken}"

            ComponentTargetResolutionKind.UNRESOLVED ->
                "unresolved:${reference.rawToken}"
        }
}

data class ComponentTargetAttribute(
    val kind: ComponentTargetAttributeKind,
    val attribute: SemanticNodeAttribute,
    val references: List<ComponentTargetReference>,
    val resolvedReferences: List<ResolvedComponentTargetReference> = emptyList(),
) {
    val attributeName: String
        get() = kind.attributeName

    val location: SourceLocation
        get() = attribute.location

    fun render(): String =
        buildString {
            append(attributeName)
            append("=")
            append(
                references.joinToString(separator = if (kind.allowsMultipleReferences) " " else "") {
                    it.render()
                },
            )
        }

    fun renderResolved(): String =
        buildString {
            append(attributeName)
            append("=")
            append(
                resolvedReferences
                    .ifEmpty { references.map { reference -> ResolvedComponentTargetReference(reference, ComponentTargetResolutionKind.UNRESOLVED) } }
                    .joinToString(separator = if (kind.allowsMultipleReferences) " " else "") { resolvedReference ->
                        resolvedReference.render()
                    },
            )
        }
}

internal object ComponentTargetReferenceParser {
    fun parse(attribute: SemanticNodeAttribute): ComponentTargetAttribute? {
        val kind = ComponentTargetAttributeKind.fromAttributeName(attribute.attributeName) ?: return null
        val references =
            tokenize(attribute.rawValue, allowsMultipleReferences = kind.allowsMultipleReferences)
                .map(::toReference)
        return ComponentTargetAttribute(
            kind = kind,
            attribute = attribute,
            references = references,
        )
    }

    private fun tokenize(
        rawValue: String,
        allowsMultipleReferences: Boolean,
    ): List<String> {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        if (!allowsMultipleReferences) {
            return listOf(trimmed)
        }
        return trimmed.split(Regex("\\s+"))
    }

    private fun toReference(rawToken: String): ComponentTargetReference =
        ComponentTargetReference(
            rawToken = rawToken,
            kind =
                if (rawToken.startsWith("@")) {
                    ComponentTargetReferenceKind.SEARCH_EXPRESSION
                } else {
                    ComponentTargetReferenceKind.COMPONENT_ID
                },
        )
}
