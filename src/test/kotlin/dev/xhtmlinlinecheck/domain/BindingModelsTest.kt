package dev.xhtmlinlinecheck.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BindingModelsTest {
    @Test
    fun `binding kinds expose the shared scope and el vocabulary`() {
        assertThat(BindingKind.entries)
            .containsExactly(
                BindingKind.UI_PARAM,
                BindingKind.ITERATION_VAR,
                BindingKind.VAR_STATUS,
                BindingKind.C_SET,
                BindingKind.IMPLICIT_GLOBAL,
            )
    }

    @Test
    fun `binding ids scope ids and origin descriptors provide stable scope metadata`() {
        val bindingId = BindingId(12)
        val scopeId = ScopeId(7)
        val origin = BindingOrigin(descriptor = "ui:repeat var=row")

        assertThat(bindingId.value).isEqualTo(12)
        assertThat(scopeId.value).isEqualTo(7)
        assertThat(origin.descriptor).isEqualTo("ui:repeat var=row")
    }
}
