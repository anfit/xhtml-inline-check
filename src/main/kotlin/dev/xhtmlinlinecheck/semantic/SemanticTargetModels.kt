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
    val rawValue: String,
    val location: SourceLocation,
    val references: List<ComponentTargetReference>,
) {
    val attributeName: String
        get() = kind.attributeName

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
    fun parse(
        attributeName: String,
        rawValue: String,
        location: SourceLocation,
    ): ComponentTargetAttribute? {
        val kind = ComponentTargetAttributeKind.fromAttributeName(attributeName) ?: return null
        val references =
            tokenize(rawValue, allowsMultipleReferences = kind.allowsMultipleReferences)
                .map(::toReference)
        return ComponentTargetAttribute(
            kind = kind,
            rawValue = rawValue,
            location = location,
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
