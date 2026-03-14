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
        assertThat(compositionRule.bindingRules).isEmpty()
    }

    @Test
    fun `ui repeat rule exposes binding and EL semantics`() {
        val repeatRule = registry.ruleFor(LogicalName(localName = "repeat", namespaceUri = FACELETS_NAMESPACE))

        assertThat(repeatRule).isNotNull()
        assertThat(repeatRule!!.bindingRules).containsExactly(
            BindingCreationRule(
                kind = BindingKind.ITERATION_VAR,
                nameAttribute = "var",
            ),
            BindingCreationRule(
                kind = BindingKind.ITERATION_STATUS,
                nameAttribute = "varStatus",
            ),
        )
        assertThat(repeatRule.elAttributeNames).containsExactly("value", "offset", "size", "step", "rendered")
        assertThat(repeatRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
    }

    @Test
    fun `namespace defaults merge with exact tag rules deterministically`() {
        val formRule = registry.ruleFor(LogicalName(localName = "form", namespaceUri = JSF_HTML_NAMESPACE))

        assertThat(formRule).isNotNull()
        assertThat(formRule!!.isNamingContainer).isTrue()
        assertThat(formRule.elAttributeNames).containsExactly("rendered")
        assertThat(formRule.targetAttributeNames).containsExactly("for", "update", "render", "process", "execute")
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
}
