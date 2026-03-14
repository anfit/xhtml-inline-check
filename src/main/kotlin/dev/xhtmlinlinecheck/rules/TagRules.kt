package dev.xhtmlinlinecheck.rules

import dev.xhtmlinlinecheck.syntax.LogicalName

const val FACELETS_NAMESPACE: String = "http://xmlns.jcp.org/jsf/facelets"
const val JSTL_CORE_NAMESPACE: String = "http://java.sun.com/jsp/jstl/core"
const val JSF_HTML_NAMESPACE: String = "http://xmlns.jcp.org/jsf/html"

enum class BindingKind {
    INCLUDE_PARAMETER,
    ITERATION_VAR,
    ITERATION_STATUS,
    SET_VAR,
    FOR_EACH_VAR,
    FOR_EACH_STATUS,
}

data class BindingCreationRule(
    val kind: BindingKind,
    val nameAttribute: String,
    val valueAttribute: String? = null,
)

interface TagRule {
    val bindingRules: List<BindingCreationRule>
    val isTransparentStructureWrapper: Boolean
    val isNamingContainer: Boolean
    val elAttributeNames: Set<String>
    val targetAttributeNames: Set<String>
}

data class StaticTagRule(
    override val bindingRules: List<BindingCreationRule> = emptyList(),
    override val isTransparentStructureWrapper: Boolean = false,
    override val isNamingContainer: Boolean = false,
    override val elAttributeNames: Set<String> = emptySet(),
    override val targetAttributeNames: Set<String> = emptySet(),
) : TagRule

interface TagRuleRegistry {
    fun ruleFor(name: LogicalName): TagRule?

    companion object {
        fun builtIns(): TagRuleRegistry = BuiltInTagRuleRegistry
    }
}

internal class StaticTagRuleRegistry(
    private val exactRules: Map<TagSelector, TagRule> = emptyMap(),
    private val namespaceDefaults: Map<String, TagRule> = emptyMap(),
    private val fallbackRule: TagRule? = null,
) : TagRuleRegistry {
    override fun ruleFor(name: LogicalName): TagRule? {
        var resolved = fallbackRule
        val namespaceRule = name.namespaceUri?.let(namespaceDefaults::get)
        val exactRule = exactRules[TagSelector(namespaceUri = name.namespaceUri, localName = name.localName)]

        if (namespaceRule != null) {
            resolved = resolved?.let(namespaceRule::overlay) ?: namespaceRule
        }

        if (exactRule != null) {
            resolved = resolved?.let(exactRule::overlay) ?: exactRule
        }

        return resolved
    }
}

object BuiltInTagRuleRegistry : TagRuleRegistry by StaticTagRuleRegistry(
    exactRules =
        mapOf(
            TagSelector(FACELETS_NAMESPACE, "include") to
                StaticTagRule(
                    isTransparentStructureWrapper = true,
                    elAttributeNames = linkedSetOf("src"),
                ),
            TagSelector(FACELETS_NAMESPACE, "param") to
                StaticTagRule(
                    bindingRules =
                        listOf(
                            BindingCreationRule(
                                kind = BindingKind.INCLUDE_PARAMETER,
                                nameAttribute = "name",
                                valueAttribute = "value",
                            ),
                        ),
                    elAttributeNames = linkedSetOf("value"),
                ),
            TagSelector(FACELETS_NAMESPACE, "composition") to
                StaticTagRule(isTransparentStructureWrapper = true),
            TagSelector(FACELETS_NAMESPACE, "fragment") to
                StaticTagRule(isTransparentStructureWrapper = true),
            TagSelector(FACELETS_NAMESPACE, "repeat") to
                StaticTagRule(
                    bindingRules =
                        listOf(
                            BindingCreationRule(
                                kind = BindingKind.ITERATION_VAR,
                                nameAttribute = "var",
                            ),
                            BindingCreationRule(
                                kind = BindingKind.ITERATION_STATUS,
                                nameAttribute = "varStatus",
                            ),
                        ),
                    elAttributeNames = linkedSetOf("value", "offset", "size", "step"),
                ),
            TagSelector(JSTL_CORE_NAMESPACE, "set") to
                StaticTagRule(
                    bindingRules =
                        listOf(
                            BindingCreationRule(
                                kind = BindingKind.SET_VAR,
                                nameAttribute = "var",
                                valueAttribute = "value",
                            ),
                        ),
                    elAttributeNames = linkedSetOf("value", "target"),
                ),
            TagSelector(JSTL_CORE_NAMESPACE, "forEach") to
                StaticTagRule(
                    bindingRules =
                        listOf(
                            BindingCreationRule(
                                kind = BindingKind.FOR_EACH_VAR,
                                nameAttribute = "var",
                            ),
                            BindingCreationRule(
                                kind = BindingKind.FOR_EACH_STATUS,
                                nameAttribute = "varStatus",
                            ),
                        ),
                    elAttributeNames = linkedSetOf("items", "begin", "end", "step"),
                ),
            TagSelector(JSTL_CORE_NAMESPACE, "if") to
                StaticTagRule(
                    elAttributeNames = linkedSetOf("test"),
                ),
            TagSelector(JSF_HTML_NAMESPACE, "form") to
                StaticTagRule(
                    isNamingContainer = true,
                ),
        ),
    namespaceDefaults =
        mapOf(
            JSF_HTML_NAMESPACE to
                StaticTagRule(
                    elAttributeNames = linkedSetOf("rendered"),
                    targetAttributeNames = linkedSetOf("for"),
                ),
        ),
    fallbackRule =
        StaticTagRule(
            elAttributeNames = linkedSetOf("rendered"),
            targetAttributeNames = linkedSetOf("for", "update", "render", "process", "execute"),
        ),
)

private data class TagSelector(
    val namespaceUri: String?,
    val localName: String,
)

private fun TagRule.overlay(base: TagRule): TagRule =
    StaticTagRule(
        bindingRules = (bindingRules + base.bindingRules).distinct(),
        isTransparentStructureWrapper = isTransparentStructureWrapper || base.isTransparentStructureWrapper,
        isNamingContainer = isNamingContainer || base.isNamingContainer,
        elAttributeNames = (elAttributeNames + base.elAttributeNames).toLinkedSet(),
        targetAttributeNames = (targetAttributeNames + base.targetAttributeNames).toLinkedSet(),
    )

private fun Iterable<String>.toLinkedSet(): LinkedHashSet<String> =
    LinkedHashSet<String>().also { result ->
        forEach(result::add)
    }
