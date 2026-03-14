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

data class ComponentTargetAttribute(
    val kind: ComponentTargetAttributeKind,
    val attribute: SemanticNodeAttribute,
    val references: List<ComponentTargetReference>,
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
