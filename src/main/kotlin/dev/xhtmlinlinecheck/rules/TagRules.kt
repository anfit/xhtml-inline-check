package dev.xhtmlinlinecheck.rules

import dev.xhtmlinlinecheck.domain.BindingKind
import dev.xhtmlinlinecheck.syntax.LogicalName

const val FACELETS_NAMESPACE: String = "http://xmlns.jcp.org/jsf/facelets"
const val JSTL_CORE_NAMESPACE: String = "http://java.sun.com/jsp/jstl/core"
const val JSF_HTML_NAMESPACE: String = "http://xmlns.jcp.org/jsf/html"

enum class SyntaxRole {
    ELEMENT,
    INCLUDE,
    INCLUDE_PARAMETER,
}

data class BindingCreationRule(
    val kind: BindingKind,
    val nameAttribute: String,
    val valueAttribute: String? = null,
)

interface TagRule {
    val syntaxRole: SyntaxRole
    val bindingRules: List<BindingCreationRule>
    val inheritsFallbackRule: Boolean
    val isTransparentStructureWrapper: Boolean
    val isForm: Boolean
    val isNamingContainer: Boolean
    val elAttributeNames: Set<String>
    val targetAttributeNames: Set<String>
}

data class StaticTagRule(
    override val syntaxRole: SyntaxRole = SyntaxRole.ELEMENT,
    override val bindingRules: List<BindingCreationRule> = emptyList(),
    override val inheritsFallbackRule: Boolean = true,
    override val isTransparentStructureWrapper: Boolean = false,
    override val isForm: Boolean = false,
    override val isNamingContainer: Boolean = false,
    override val elAttributeNames: Set<String> = emptySet(),
    override val targetAttributeNames: Set<String> = emptySet(),
) : TagRule

val TagRule.isIncludeTag: Boolean
    get() = syntaxRole == SyntaxRole.INCLUDE

val TagRule.isIncludeParameterTag: Boolean
    get() = syntaxRole == SyntaxRole.INCLUDE_PARAMETER

interface TagRuleRegistry {
    fun ruleFor(name: LogicalName): TagRule?
    fun resolve(name: LogicalName): TagRule

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
        val namespaceRule = name.namespaceUri?.let(namespaceDefaults::get)
        val exactRule = exactRules[TagSelector(namespaceUri = name.namespaceUri, localName = name.localName)]
        var resolved: TagRule? = namespaceRule

        if (exactRule != null) {
            resolved = resolved?.let(exactRule::overlay) ?: exactRule
        }

        if (resolved == null) {
            return fallbackRule
        }

        return if (resolved.inheritsFallbackRule) {
            fallbackRule?.let { resolved.overlay(it) } ?: resolved
        } else {
            resolved
        }
    }

    override fun resolve(name: LogicalName): TagRule = ruleFor(name) ?: DEFAULT_TAG_RULE
}

object BuiltInTagRuleRegistry : TagRuleRegistry by StaticTagRuleRegistry(
    exactRules =
        mapOf(
            TagSelector(FACELETS_NAMESPACE, "include") to
                StaticTagRule(
                    syntaxRole = SyntaxRole.INCLUDE,
                    inheritsFallbackRule = false,
                    isTransparentStructureWrapper = true,
                    elAttributeNames = linkedSetOf("src"),
                ),
            TagSelector(FACELETS_NAMESPACE, "param") to
                StaticTagRule(
                    syntaxRole = SyntaxRole.INCLUDE_PARAMETER,
                    inheritsFallbackRule = false,
                    bindingRules =
                        listOf(
                            BindingCreationRule(
                                kind = BindingKind.UI_PARAM,
                                nameAttribute = "name",
                                valueAttribute = "value",
                            ),
                        ),
                    elAttributeNames = linkedSetOf("value"),
                ),
            TagSelector(FACELETS_NAMESPACE, "composition") to
                StaticTagRule(
                    inheritsFallbackRule = false,
                    isTransparentStructureWrapper = true,
                ),
            TagSelector(FACELETS_NAMESPACE, "fragment") to
                StaticTagRule(
                    inheritsFallbackRule = false,
                    isTransparentStructureWrapper = true,
                ),
            TagSelector(FACELETS_NAMESPACE, "repeat") to
                StaticTagRule(
                    bindingRules =
                        listOf(
                            BindingCreationRule(
                                kind = BindingKind.ITERATION_VAR,
                                nameAttribute = "var",
                            ),
                            BindingCreationRule(
                                kind = BindingKind.VAR_STATUS,
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
                                kind = BindingKind.C_SET,
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
                                kind = BindingKind.ITERATION_VAR,
                                nameAttribute = "var",
                            ),
                            BindingCreationRule(
                                kind = BindingKind.VAR_STATUS,
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
                    isForm = true,
                    isNamingContainer = true,
                ),
            TagSelector(JSF_HTML_NAMESPACE, "dataTable") to
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

private val DEFAULT_TAG_RULE: TagRule = StaticTagRule()

private fun TagRule.overlay(base: TagRule): TagRule =
    StaticTagRule(
        syntaxRole = if (syntaxRole != SyntaxRole.ELEMENT) syntaxRole else base.syntaxRole,
        bindingRules = (bindingRules + base.bindingRules).distinct(),
        inheritsFallbackRule = inheritsFallbackRule && base.inheritsFallbackRule,
        isTransparentStructureWrapper = isTransparentStructureWrapper || base.isTransparentStructureWrapper,
        isForm = isForm || base.isForm,
        isNamingContainer = isNamingContainer || base.isNamingContainer,
        elAttributeNames = (elAttributeNames + base.elAttributeNames).toLinkedSet(),
        targetAttributeNames = (targetAttributeNames + base.targetAttributeNames).toLinkedSet(),
    )

private fun Iterable<String>.toLinkedSet(): LinkedHashSet<String> =
    LinkedHashSet<String>().also { result ->
        forEach(result::add)
    }
