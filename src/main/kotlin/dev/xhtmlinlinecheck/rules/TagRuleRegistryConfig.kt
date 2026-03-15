package dev.xhtmlinlinecheck.rules

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.xhtmlinlinecheck.domain.BindingKind
import java.nio.file.Files
import java.nio.file.Path

private const val DEFAULT_RULES_RESOURCE = "/dev/xhtmlinlinecheck/rules/default-tag-rules.json"
private const val EXECUTION_RULES_FILE = ".xhtml-inline-check.json"

internal object TagRuleRegistryLoader {
    private val mapper = jacksonObjectMapper()

    fun loadBundledDefaults(): TagRuleRegistry =
        registryFrom(readConfigResource(DEFAULT_RULES_RESOURCE))

    fun loadForExecutionRoot(executionRoot: Path): TagRuleRegistry {
        val overridePath = executionRoot.toAbsolutePath().normalize().resolve(EXECUTION_RULES_FILE)
        val bundled = readConfigResource(DEFAULT_RULES_RESOURCE)
        val merged =
            if (Files.exists(overridePath)) {
                bundled.merge(readConfigFile(overridePath))
            } else {
                bundled
            }
        return registryFrom(merged)
    }

    private fun registryFrom(config: TagRuleRegistryConfig): TagRuleRegistry =
        StaticTagRuleRegistry(
            exactRules =
                config.exactRules.associate { rule ->
                    TagSelector(namespaceUri = rule.namespaceUri, localName = rule.localName) to rule.toStaticTagRule()
                },
            namespaceDefaults =
                config.namespaceDefaults.associate { rule ->
                    rule.namespaceUri to rule.toStaticTagRule()
                },
            fallbackRule = config.fallbackRule?.toStaticTagRule(),
        )

    private fun readConfigResource(resourcePath: String): TagRuleRegistryConfig =
        TagRuleRegistryLoader::class.java.getResourceAsStream(resourcePath)
            ?.use(mapper::readValue)
            ?: error("Missing bundled tag rule resource: $resourcePath")

    private fun readConfigFile(path: Path): TagRuleRegistryConfig =
        Files.newInputStream(path).use(mapper::readValue)
}

internal data class TagRuleRegistryConfig(
    val exactRules: List<ConfiguredExactTagRule> = emptyList(),
    val namespaceDefaults: List<ConfiguredNamespaceTagRule> = emptyList(),
    val fallbackRule: ConfiguredTagRule? = null,
) {
    fun merge(override: TagRuleRegistryConfig): TagRuleRegistryConfig =
        TagRuleRegistryConfig(
            exactRules = mergeExactRules(exactRules, override.exactRules),
            namespaceDefaults = mergeNamespaceDefaults(namespaceDefaults, override.namespaceDefaults),
            fallbackRule =
                when {
                    fallbackRule == null -> override.fallbackRule
                    override.fallbackRule == null -> fallbackRule
                    else -> override.fallbackRule.merge(fallbackRule)
                },
        )

    private fun mergeExactRules(
        base: List<ConfiguredExactTagRule>,
        override: List<ConfiguredExactTagRule>,
    ): List<ConfiguredExactTagRule> {
        val merged = linkedMapOf<Pair<String?, String>, ConfiguredExactTagRule>()
        base.forEach { rule -> merged[rule.namespaceUri to rule.localName] = rule }
        override.forEach { rule ->
            val key = rule.namespaceUri to rule.localName
            merged[key] = rule.merge(merged[key])
        }
        return merged.values.toList()
    }

    private fun mergeNamespaceDefaults(
        base: List<ConfiguredNamespaceTagRule>,
        override: List<ConfiguredNamespaceTagRule>,
    ): List<ConfiguredNamespaceTagRule> {
        val merged = linkedMapOf<String, ConfiguredNamespaceTagRule>()
        base.forEach { rule -> merged[rule.namespaceUri] = rule }
        override.forEach { rule ->
            merged[rule.namespaceUri] = rule.merge(merged[rule.namespaceUri])
        }
        return merged.values.toList()
    }
}

internal data class ConfiguredTagRule(
    val syntaxRole: SyntaxRole = SyntaxRole.ELEMENT,
    val bindingRules: List<ConfiguredBindingCreationRule> = emptyList(),
    val inheritsFallbackRule: Boolean = true,
    val isTransparentStructureWrapper: Boolean = false,
    val isForm: Boolean = false,
    val isNamingContainer: Boolean = false,
    val elAttributeNames: List<String> = emptyList(),
    val targetAttributeNames: List<String> = emptyList(),
) {
    fun toStaticTagRule(): StaticTagRule =
        StaticTagRule(
            syntaxRole = syntaxRole,
            bindingRules = bindingRules.map { it.toBindingCreationRule() },
            inheritsFallbackRule = inheritsFallbackRule,
            isTransparentStructureWrapper = isTransparentStructureWrapper,
            isForm = isForm,
            isNamingContainer = isNamingContainer,
            elAttributeNames = LinkedHashSet(elAttributeNames),
            targetAttributeNames = LinkedHashSet(targetAttributeNames),
        )

    fun merge(base: ConfiguredTagRule?): ConfiguredTagRule {
        if (base == null) {
            return this
        }
        return ConfiguredTagRule(
            syntaxRole = if (syntaxRole != SyntaxRole.ELEMENT) syntaxRole else base.syntaxRole,
            bindingRules = (bindingRules + base.bindingRules).distinct(),
            inheritsFallbackRule = inheritsFallbackRule && base.inheritsFallbackRule,
            isTransparentStructureWrapper = isTransparentStructureWrapper || base.isTransparentStructureWrapper,
            isForm = isForm || base.isForm,
            isNamingContainer = isNamingContainer || base.isNamingContainer,
            elAttributeNames = (elAttributeNames + base.elAttributeNames).distinct(),
            targetAttributeNames = (targetAttributeNames + base.targetAttributeNames).distinct(),
        )
    }
}

internal data class ConfiguredExactTagRule(
    val namespaceUri: String? = null,
    val localName: String,
    val syntaxRole: SyntaxRole = SyntaxRole.ELEMENT,
    val bindingRules: List<ConfiguredBindingCreationRule> = emptyList(),
    val inheritsFallbackRule: Boolean = true,
    val isTransparentStructureWrapper: Boolean = false,
    val isForm: Boolean = false,
    val isNamingContainer: Boolean = false,
    val elAttributeNames: List<String> = emptyList(),
    val targetAttributeNames: List<String> = emptyList(),
) {
    fun toStaticTagRule(): StaticTagRule = asConfiguredTagRule().toStaticTagRule()

    fun merge(base: ConfiguredExactTagRule?): ConfiguredExactTagRule {
        if (base == null) {
            return this
        }
        val merged = asConfiguredTagRule().merge(base.asConfiguredTagRule())
        return copyFrom(merged)
    }

    private fun asConfiguredTagRule(): ConfiguredTagRule =
        ConfiguredTagRule(
            syntaxRole = syntaxRole,
            bindingRules = bindingRules,
            inheritsFallbackRule = inheritsFallbackRule,
            isTransparentStructureWrapper = isTransparentStructureWrapper,
            isForm = isForm,
            isNamingContainer = isNamingContainer,
            elAttributeNames = elAttributeNames,
            targetAttributeNames = targetAttributeNames,
        )

    private fun copyFrom(rule: ConfiguredTagRule): ConfiguredExactTagRule =
        ConfiguredExactTagRule(
            namespaceUri = namespaceUri,
            localName = localName,
            syntaxRole = rule.syntaxRole,
            bindingRules = rule.bindingRules,
            inheritsFallbackRule = rule.inheritsFallbackRule,
            isTransparentStructureWrapper = rule.isTransparentStructureWrapper,
            isForm = rule.isForm,
            isNamingContainer = rule.isNamingContainer,
            elAttributeNames = rule.elAttributeNames,
            targetAttributeNames = rule.targetAttributeNames,
        )
}

internal data class ConfiguredNamespaceTagRule(
    val namespaceUri: String,
    val syntaxRole: SyntaxRole = SyntaxRole.ELEMENT,
    val bindingRules: List<ConfiguredBindingCreationRule> = emptyList(),
    val inheritsFallbackRule: Boolean = true,
    val isTransparentStructureWrapper: Boolean = false,
    val isForm: Boolean = false,
    val isNamingContainer: Boolean = false,
    val elAttributeNames: List<String> = emptyList(),
    val targetAttributeNames: List<String> = emptyList(),
) {
    fun toStaticTagRule(): StaticTagRule = asConfiguredTagRule().toStaticTagRule()

    fun merge(base: ConfiguredNamespaceTagRule?): ConfiguredNamespaceTagRule {
        if (base == null) {
            return this
        }
        val merged = asConfiguredTagRule().merge(base.asConfiguredTagRule())
        return copyFrom(merged)
    }

    private fun asConfiguredTagRule(): ConfiguredTagRule =
        ConfiguredTagRule(
            syntaxRole = syntaxRole,
            bindingRules = bindingRules,
            inheritsFallbackRule = inheritsFallbackRule,
            isTransparentStructureWrapper = isTransparentStructureWrapper,
            isForm = isForm,
            isNamingContainer = isNamingContainer,
            elAttributeNames = elAttributeNames,
            targetAttributeNames = targetAttributeNames,
        )

    private fun copyFrom(rule: ConfiguredTagRule): ConfiguredNamespaceTagRule =
        ConfiguredNamespaceTagRule(
            namespaceUri = namespaceUri,
            syntaxRole = rule.syntaxRole,
            bindingRules = rule.bindingRules,
            inheritsFallbackRule = rule.inheritsFallbackRule,
            isTransparentStructureWrapper = rule.isTransparentStructureWrapper,
            isForm = rule.isForm,
            isNamingContainer = rule.isNamingContainer,
            elAttributeNames = rule.elAttributeNames,
            targetAttributeNames = rule.targetAttributeNames,
        )
}

internal data class ConfiguredBindingCreationRule(
    val kind: BindingKind,
    val nameAttribute: String,
    val valueAttribute: String? = null,
) {
    fun toBindingCreationRule(): BindingCreationRule =
        BindingCreationRule(
            kind = kind,
            nameAttribute = nameAttribute,
            valueAttribute = valueAttribute,
        )
}
