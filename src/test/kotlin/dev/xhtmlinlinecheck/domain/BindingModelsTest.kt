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
                BindingKind.C_FOR_EACH,
                BindingKind.IMPLICIT_GLOBAL,
            )
    }
}
