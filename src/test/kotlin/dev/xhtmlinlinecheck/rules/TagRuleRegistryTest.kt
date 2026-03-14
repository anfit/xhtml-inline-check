package dev.xhtmlinlinecheck.rules

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
    fun `ui repeat rule exposes binding and EL semantics`() {
        val repeatRule = registry.ruleFor(LogicalName(localName = "repeat", namespaceUri = FACELETS_NAMESPACE))

        assertThat(repeatRule).isNotNull()
        val resolvedRepeatRule = repeatRule!!

        assertThat(resolvedRepeatRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(resolvedRepeatRule.bindingRules).containsExactly(
            BindingCreationRule(
                kind = BindingKind.ITERATION_VAR,
                nameAttribute = "var",
            ),
            BindingCreationRule(
                kind = BindingKind.ITERATION_STATUS,
                nameAttribute = "varStatus",
            ),
        )
        assertThat(resolvedRepeatRule.elAttributeNames).containsExactly("value", "offset", "size", "step", "rendered")
        assertThat(resolvedRepeatRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
    }

    @Test
    fun `jstl core rules expose scope and guard semantics through the shared registry`() {
        val setRule = registry.resolve(LogicalName(localName = "set", namespaceUri = JSTL_CORE_NAMESPACE))
        val forEachRule = registry.resolve(LogicalName(localName = "forEach", namespaceUri = JSTL_CORE_NAMESPACE))
        val ifRule = registry.resolve(LogicalName(localName = "if", namespaceUri = JSTL_CORE_NAMESPACE))

        assertThat(setRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(setRule.bindingRules).containsExactly(
            BindingCreationRule(
                kind = BindingKind.SET_VAR,
                nameAttribute = "var",
                valueAttribute = "value",
            ),
        )
        assertThat(setRule.elAttributeNames).containsExactly("value", "target", "rendered")
        assertThat(setRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")

        assertThat(forEachRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(forEachRule.bindingRules).containsExactly(
            BindingCreationRule(
                kind = BindingKind.FOR_EACH_VAR,
                nameAttribute = "var",
            ),
            BindingCreationRule(
                kind = BindingKind.FOR_EACH_STATUS,
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

        assertThat(formRule).isNotNull()
        assertThat(formRule!!.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(formRule.isNamingContainer).isTrue()
        assertThat(formRule.bindingRules).isEmpty()
        assertThat(formRule.elAttributeNames).containsExactly("rendered")
        assertThat(formRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
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
        assertThat(prefixedFormRule.isNamingContainer).isTrue()
        assertThat(prefixedFormRule.isTransparentStructureWrapper).isFalse()
        assertThat(prefixedFormRule.elAttributeNames).containsExactly("rendered")
        assertThat(prefixedFormRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
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
        assertThat(genericRule.isNamingContainer).isFalse()
        assertThat(genericRule.elAttributeNames).containsExactly("rendered")
        assertThat(genericRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
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
                kind = BindingKind.INCLUDE_PARAMETER,
                nameAttribute = "name",
                valueAttribute = "value",
            ),
        )
        assertThat(paramRule.elAttributeNames).containsExactly("value")
        assertThat(paramRule.targetAttributeNames).isEmpty()
    }

    @Test
    fun `transparent facelets wrappers stay explicit instead of inheriting generic component attributes`() {
        val compositionRule = registry.resolve(LogicalName(localName = "composition", namespaceUri = FACELETS_NAMESPACE))
        val fragmentRule = registry.resolve(LogicalName(localName = "fragment", namespaceUri = FACELETS_NAMESPACE))

        assertThat(compositionRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(compositionRule.isTransparentStructureWrapper).isTrue()
        assertThat(compositionRule.elAttributeNames).isEmpty()
        assertThat(compositionRule.targetAttributeNames).isEmpty()
        assertThat(fragmentRule.syntaxRole).isEqualTo(SyntaxRole.ELEMENT)
        assertThat(fragmentRule.isTransparentStructureWrapper).isTrue()
        assertThat(fragmentRule.elAttributeNames).isEmpty()
        assertThat(fragmentRule.targetAttributeNames).isEmpty()
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
}
