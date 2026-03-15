package dev.xhtmlinlinecheck.rules

import dev.xhtmlinlinecheck.domain.BindingKind
import dev.xhtmlinlinecheck.syntax.LogicalName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TagRuleRegistryTest {
    private val registry = TagRuleRegistry.builtIns()

    @Test
    fun `built-in lookup is namespace-aware and prefix-independent`() {
        val compositionRule =
            registry.ruleFor(
                LogicalName(
                    localName = "composition",
                    namespaceUri = FACELETS_NAMESPACE,
                    prefix = "facelets",
                ),
            )

        assertThat(compositionRule).isNotNull()
        assertThat(compositionRule!!.isTransparentStructureWrapper).isTrue()
        assertThat(compositionRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(compositionRule.inheritsFallbackRule).isFalse()
        assertThat(compositionRule.bindingRules).isEmpty()
        assertThat(compositionRule.elAttributeNames).isEmpty()
        assertThat(compositionRule.targetAttributeNames).isEmpty()
    }

    @Test
    fun `legacy facelets namespace keeps the same include and wrapper semantics`() {
        val includeRule = registry.resolve(
            LogicalName(
                localName = "include",
                namespaceUri = LEGACY_FACELETS_NAMESPACE,
                prefix = "ui"
            )
        )
        val compositionRule = registry.resolve(
            LogicalName(
                localName = "composition",
                namespaceUri = LEGACY_FACELETS_NAMESPACE,
                prefix = "ui"
            )
        )

        assertThat(includeRule.syntaxRole).isEqualTo(SyntaxRole.INCLUDE)
        assertThat(includeRule.isTransparentStructureWrapper).isTrue()
        assertThat(includeRule.elAttributeNames).containsExactly("src")
        assertThat(compositionRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(compositionRule.isTransparentStructureWrapper).isTrue()
        assertThat(compositionRule.elAttributeNames).isEmpty()
        assertThat(compositionRule.targetAttributeNames).isEmpty()
    }

    @Test
    fun `ui repeat rule exposes binding and EL semantics`() {
        val repeatRule = registry.ruleFor(LogicalName(localName = "repeat", namespaceUri = FACELETS_NAMESPACE))

        assertThat(repeatRule).isNotNull()
        val resolvedRepeatRule = repeatRule!!

        assertThat(resolvedRepeatRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(resolvedRepeatRule.isForm).isFalse()
        assertThat(resolvedRepeatRule.bindingRules).containsExactly(
            BindingCreationRule(
                kind = BindingKind.ITERATION_VAR,
                nameAttribute = "var",
            ),
            BindingCreationRule(
                kind = BindingKind.VAR_STATUS,
                nameAttribute = "varStatus",
            ),
        )
        assertThat(resolvedRepeatRule.elAttributeNames).containsExactly("value", "offset", "size", "step", "rendered")
        assertThat(resolvedRepeatRule.targetAttributeNames).containsExactly(
            "for",
            "update",
            "render",
            "process",
            "execute"
        )
    }

    @Test
    fun `jstl core rules expose scope and guard semantics through the shared registry`() {
        val setRule = registry.resolve(LogicalName(localName = "set", namespaceUri = JSTL_CORE_NAMESPACE))
        val forEachRule = registry.resolve(LogicalName(localName = "forEach", namespaceUri = JSTL_CORE_NAMESPACE))
        val ifRule = registry.resolve(LogicalName(localName = "if", namespaceUri = JSTL_CORE_NAMESPACE))

        assertThat(setRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(setRule.bindingRules).containsExactly(
            BindingCreationRule(
                kind = BindingKind.C_SET,
                nameAttribute = "var",
                valueAttribute = "value",
            ),
        )
        assertThat(setRule.elAttributeNames).containsExactly("value", "target", "rendered")
        assertThat(setRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")

        assertThat(forEachRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(forEachRule.bindingRules).containsExactly(
            BindingCreationRule(
                kind = BindingKind.ITERATION_VAR,
                nameAttribute = "var",
            ),
            BindingCreationRule(
                kind = BindingKind.VAR_STATUS,
                nameAttribute = "varStatus",
            ),
        )
        assertThat(forEachRule.elAttributeNames).containsExactly("items", "begin", "end", "step", "rendered")
        assertThat(forEachRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")

        assertThat(ifRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(ifRule.bindingRules).isEmpty()
        assertThat(ifRule.elAttributeNames).containsExactly("test", "rendered")
        assertThat(ifRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
    }

    @Test
    fun `namespace defaults merge with exact tag rules deterministically`() {
        val formRule = registry.ruleFor(LogicalName(localName = "form", namespaceUri = JSF_HTML_NAMESPACE))
        val dataTableRule = registry.ruleFor(LogicalName(localName = "dataTable", namespaceUri = JSF_HTML_NAMESPACE))

        assertThat(formRule).isNotNull()
        assertThat(formRule!!.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(formRule.isForm).isTrue()
        assertThat(formRule.isNamingContainer).isTrue()
        assertThat(formRule.bindingRules).isEmpty()
        assertThat(formRule.elAttributeNames).containsExactly("rendered")
        assertThat(formRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")

        assertThat(dataTableRule).isNotNull()
        assertThat(dataTableRule!!.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(dataTableRule.isForm).isFalse()
        assertThat(dataTableRule.isNamingContainer).isTrue()
        assertThat(dataTableRule.bindingRules).isEmpty()
        assertThat(dataTableRule.elAttributeNames).containsExactly("rendered")
        assertThat(dataTableRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
    }

    @Test
    fun `synthetic registry overlay order stays deterministic across exact namespace and fallback layers`() {
        val customRegistry =
            syntheticRegistry(
                exactRules =
                    mapOf(
                        "panel" to
                                StaticTagRule(
                                    bindingRules =
                                        listOf(
                                            BindingCreationRule(
                                                kind = BindingKind.C_SET,
                                                nameAttribute = "exactVar",
                                            ),
                                        ),
                                    elAttributeNames = linkedSetOf("exactOnly", "shared"),
                                    targetAttributeNames = linkedSetOf("exactTarget", "sharedTarget"),
                                ),
                    ),
                namespaceDefaults =
                    mapOf(
                        "urn:test" to
                                StaticTagRule(
                                    bindingRules =
                                        listOf(
                                            BindingCreationRule(
                                                kind = BindingKind.ITERATION_VAR,
                                                nameAttribute = "namespaceVar",
                                            ),
                                        ),
                                    elAttributeNames = linkedSetOf("namespaceOnly", "shared"),
                                    targetAttributeNames = linkedSetOf("namespaceTarget", "sharedTarget"),
                                ),
                    ),
                fallbackRule =
                    StaticTagRule(
                        bindingRules =
                            listOf(
                                BindingCreationRule(
                                    kind = BindingKind.ITERATION_VAR,
                                    nameAttribute = "fallbackVar",
                                ),
                            ),
                        elAttributeNames = linkedSetOf("fallbackOnly", "shared"),
                        targetAttributeNames = linkedSetOf("fallbackTarget", "sharedTarget"),
                    ),
            )

        val firstLookup =
            customRegistry.resolve(LogicalName(localName = "panel", namespaceUri = "urn:test", prefix = "a"))
        val secondLookup =
            customRegistry.resolve(LogicalName(localName = "panel", namespaceUri = "urn:test", prefix = "b"))

        assertThat(firstLookup).isEqualTo(secondLookup)
        assertThat(firstLookup.bindingRules).containsExactly(
            BindingCreationRule(
                kind = BindingKind.C_SET,
                nameAttribute = "exactVar",
            ),
            BindingCreationRule(
                kind = BindingKind.ITERATION_VAR,
                nameAttribute = "namespaceVar",
            ),
            BindingCreationRule(
                kind = BindingKind.ITERATION_VAR,
                nameAttribute = "fallbackVar",
            ),
        )
        assertThat(firstLookup.elAttributeNames).containsExactly("exactOnly", "shared", "namespaceOnly", "fallbackOnly")
        assertThat(firstLookup.targetAttributeNames)
            .containsExactly("exactTarget", "sharedTarget", "namespaceTarget", "fallbackTarget")
    }

    @Test
    fun `h form stays an explicit built in rule for form ancestry dependent tasks`() {
        val prefixedFormRule =
            registry.resolve(
                LogicalName(
                    localName = "form",
                    namespaceUri = JSF_HTML_NAMESPACE,
                    prefix = "h",
                ),
            )

        assertThat(prefixedFormRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(prefixedFormRule.isForm).isTrue()
        assertThat(prefixedFormRule.isNamingContainer).isTrue()
        assertThat(prefixedFormRule.isTransparentStructureWrapper).isFalse()
        assertThat(prefixedFormRule.elAttributeNames).containsExactly("rendered")
        assertThat(prefixedFormRule.targetAttributeNames).containsExactly(
            "for",
            "update",
            "render",
            "process",
            "execute"
        )
    }

    @Test
    fun `fallback rule still describes generic component EL and target attributes`() {
        val genericRule =
            registry.ruleFor(
                LogicalName(
                    localName = "commandButton",
                    namespaceUri = "http://primefaces.org/ui",
                    prefix = "p",
                ),
            )

        assertThat(genericRule).isNotNull()
        assertThat(genericRule!!.bindingRules).isEmpty()
        assertThat(genericRule.isTransparentStructureWrapper).isFalse()
        assertThat(genericRule.isForm).isFalse()
        assertThat(genericRule.isNamingContainer).isFalse()
        assertThat(genericRule.elAttributeNames).containsExactly("rendered")
        assertThat(genericRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
    }

    @Test
    fun `same local name in another namespace does not inherit facelets include semantics`() {
        val faceletsInclude =
            registry.resolve(LogicalName(localName = "include", namespaceUri = FACELETS_NAMESPACE, prefix = "ui"))
        val genericInclude = registry.resolve(LogicalName(localName = "include", namespaceUri = null))

        assertThat(faceletsInclude.syntaxRole).isEqualTo(SyntaxRole.INCLUDE)
        assertThat(faceletsInclude.elAttributeNames).containsExactly("src")
        assertThat(faceletsInclude.targetAttributeNames).isEmpty()
        assertThat(genericInclude.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(genericInclude.isIncludeTag).isFalse()
        assertThat(genericInclude.elAttributeNames).containsExactly("rendered")
        assertThat(genericInclude.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
    }

    @Test
    fun `include and param tags expose explicit syntax roles for loader and syntax stages`() {
        val includeRule = registry.resolve(LogicalName(localName = "include", namespaceUri = FACELETS_NAMESPACE))
        val paramRule = registry.resolve(LogicalName(localName = "param", namespaceUri = FACELETS_NAMESPACE))

        assertThat(includeRule.syntaxRole).isEqualTo(SyntaxRole.INCLUDE)
        assertThat(includeRule.isIncludeTag).isTrue()
        assertThat(includeRule.inheritsFallbackRule).isFalse()
        assertThat(includeRule.isTransparentStructureWrapper).isTrue()
        assertThat(includeRule.elAttributeNames).containsExactly("src")
        assertThat(includeRule.targetAttributeNames).isEmpty()
        assertThat(paramRule.syntaxRole).isEqualTo(SyntaxRole.INCLUDE_PARAMETER)
        assertThat(paramRule.isIncludeParameterTag).isTrue()
        assertThat(paramRule.inheritsFallbackRule).isFalse()
        assertThat(paramRule.bindingRules).containsExactly(
            BindingCreationRule(
                kind = BindingKind.UI_PARAM,
                nameAttribute = "name",
                valueAttribute = "value",
            ),
        )
        assertThat(paramRule.elAttributeNames).containsExactly("value")
        assertThat(paramRule.targetAttributeNames).isEmpty()
    }

    @Test
    fun `transparent facelets wrappers stay explicit instead of inheriting generic component attributes`() {
        val compositionRule =
            registry.resolve(LogicalName(localName = "composition", namespaceUri = FACELETS_NAMESPACE))
        val decorateRule = registry.resolve(LogicalName(localName = "decorate", namespaceUri = FACELETS_NAMESPACE))
        val defineRule = registry.resolve(LogicalName(localName = "define", namespaceUri = FACELETS_NAMESPACE))
        val fragmentRule = registry.resolve(LogicalName(localName = "fragment", namespaceUri = FACELETS_NAMESPACE))
        val insertRule = registry.resolve(LogicalName(localName = "insert", namespaceUri = FACELETS_NAMESPACE))
        val componentRule = registry.resolve(LogicalName(localName = "component", namespaceUri = FACELETS_NAMESPACE))

        assertThat(compositionRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(compositionRule.isTransparentStructureWrapper).isTrue()
        assertThat(compositionRule.isForm).isFalse()
        assertThat(compositionRule.elAttributeNames).isEmpty()
        assertThat(compositionRule.targetAttributeNames).isEmpty()
        assertThat(decorateRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(decorateRule.isTransparentStructureWrapper).isTrue()
        assertThat(decorateRule.elAttributeNames).isEmpty()
        assertThat(decorateRule.targetAttributeNames).isEmpty()
        assertThat(defineRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(defineRule.isTransparentStructureWrapper).isTrue()
        assertThat(defineRule.elAttributeNames).isEmpty()
        assertThat(defineRule.targetAttributeNames).isEmpty()
        assertThat(fragmentRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(fragmentRule.isTransparentStructureWrapper).isTrue()
        assertThat(fragmentRule.elAttributeNames).isEmpty()
        assertThat(fragmentRule.targetAttributeNames).isEmpty()
        assertThat(insertRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(insertRule.isTransparentStructureWrapper).isTrue()
        assertThat(insertRule.elAttributeNames).isEmpty()
        assertThat(insertRule.targetAttributeNames).isEmpty()
        assertThat(componentRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(componentRule.isTransparentStructureWrapper).isTrue()
        assertThat(componentRule.elAttributeNames).isEmpty()
        assertThat(componentRule.targetAttributeNames).isEmpty()
    }

    @Test
    fun `representative third party tags from the dummy sample have explicit wrapper and naming container coverage`() {
        val defaultsRule = registry.resolve(
            LogicalName(
                localName = "defaults",
                namespaceUri = COMPANY_COMPONENT_NAMESPACE,
                prefix = "custom"
            )
        )
        val injectAttributesRule =
            registry.resolve(
                LogicalName(
                    localName = "injectAttributes",
                    namespaceUri = COMPANY_COMPONENT_NAMESPACE,
                    prefix = "custom"
                )
            )
        val withRule = registry.resolve(
            LogicalName(
                localName = "with",
                namespaceUri = COMPANY_COMPONENT_NAMESPACE,
                prefix = "custom"
            )
        )
        val dataTableRule = registry.resolve(
            LogicalName(
                localName = "dataTable",
                namespaceUri = COMPANY_COMPONENT_NAMESPACE,
                prefix = "custom"
            )
        )
        val modalPanelRule = registry.resolve(
            LogicalName(
                localName = "modalPanel",
                namespaceUri = COMPANY_COMPONENT_NAMESPACE,
                prefix = "custom"
            )
        )
        val facetRule =
            registry.resolve(LogicalName(localName = "facet", namespaceUri = JSF_CORE_NAMESPACE, prefix = "f"))
        val metadataRule =
            registry.resolve(LogicalName(localName = "metadata", namespaceUri = JSF_CORE_NAMESPACE, prefix = "f"))

        assertThat(defaultsRule.isTransparentStructureWrapper).isTrue()
        assertThat(defaultsRule.elAttributeNames).containsExactly("rendered")
        assertThat(defaultsRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
        assertThat(injectAttributesRule.isTransparentStructureWrapper).isTrue()
        assertThat(injectAttributesRule.elAttributeNames).containsExactly("rendered")
        assertThat(injectAttributesRule.targetAttributeNames).containsExactly(
            "for",
            "update",
            "render",
            "process",
            "execute"
        )
        assertThat(withRule.isTransparentStructureWrapper).isTrue()
        assertThat(withRule.elAttributeNames).containsExactly("rendered")
        assertThat(withRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
        assertThat(dataTableRule.isTransparentStructureWrapper).isFalse()
        assertThat(dataTableRule.isNamingContainer).isTrue()
        assertThat(dataTableRule.elAttributeNames).containsExactly("rendered")
        assertThat(dataTableRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
        assertThat(modalPanelRule.isNamingContainer).isTrue()
        assertThat(facetRule.isTransparentStructureWrapper).isTrue()
        assertThat(facetRule.elAttributeNames).isEmpty()
        assertThat(facetRule.targetAttributeNames).isEmpty()
        assertThat(metadataRule.isTransparentStructureWrapper).isTrue()
        assertThat(metadataRule.elAttributeNames).isEmpty()
        assertThat(metadataRule.targetAttributeNames).isEmpty()
    }

    @Test
    fun `resolve returns deterministic empty semantics for unknown tags`() {
        val unknownRule =
            registry.resolve(
                LogicalName(
                    localName = "unknown",
                    namespaceUri = "urn:test",
                ),
            )

        assertThat(unknownRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(unknownRule.bindingRules).isEmpty()
    }

    private fun syntheticRegistry(
        exactRules: Map<String, TagRule>,
        namespaceDefaults: Map<String, TagRule>,
        fallbackRule: TagRule,
    ): TagRuleRegistry {
        val selectorClass = Class.forName("dev.xhtmlinlinecheck.rules.TagSelector")
        val selectorConstructor = selectorClass.getDeclaredConstructor(String::class.java, String::class.java)
        selectorConstructor.isAccessible = true
        val exactRuleMap =
            exactRules.mapKeys { (localName, _) ->
                selectorConstructor.newInstance("urn:test", localName)
            }
        val registryClass = Class.forName("dev.xhtmlinlinecheck.rules.StaticTagRuleRegistry")
        val registryConstructor =
            registryClass.getDeclaredConstructor(Map::class.java, Map::class.java, TagRule::class.java)
        registryConstructor.isAccessible = true
        return registryConstructor.newInstance(exactRuleMap, namespaceDefaults, fallbackRule) as TagRuleRegistry
    }
}
