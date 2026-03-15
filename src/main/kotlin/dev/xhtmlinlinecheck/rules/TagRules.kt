package dev.xhtmlinlinecheck.rules

import dev.xhtmlinlinecheck.domain.BindingKind
import dev.xhtmlinlinecheck.syntax.LogicalName
import java.nio.file.Path

const val FACELETS_NAMESPACE: String = "http://xmlns.jcp.org/jsf/facelets"
const val LEGACY_FACELETS_NAMESPACE: String = "http://java.sun.com/jsf/facelets"
const val JSTL_CORE_NAMESPACE: String = "http://java.sun.com/jsp/jstl/core"
const val JSF_HTML_NAMESPACE: String = "http://xmlns.jcp.org/jsf/html"
const val JSF_CORE_NAMESPACE: String = "http://java.sun.com/jsf/core"
const val COMPANY_COMPONENT_NAMESPACE: String = "http://www.company.com/components"
const val COMPANY_APP_COMPONENT_NAMESPACE: String = "http://www.company.com/components/app"
const val COMPANY_LAYOUT_COMPONENT_NAMESPACE: String = "http://www.company.com/components/layouts"

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

        fun forExecutionRoot(executionRoot: Path): TagRuleRegistry =
            TagRuleRegistryLoader.loadForExecutionRoot(executionRoot)
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

object BuiltInTagRuleRegistry : TagRuleRegistry by TagRuleRegistryLoader.loadBundledDefaults()

internal data class TagSelector(
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
