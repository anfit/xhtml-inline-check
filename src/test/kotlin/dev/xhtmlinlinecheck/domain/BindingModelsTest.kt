package dev.xhtmlinlinecheck.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

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
        val document = SourceDocument.fromPath(AnalysisSide.OLD, Path.of("legacy", "table.xhtml"))
        val origin = BindingOrigin(descriptor = "ui:repeat var=row", provenance = Provenance.forRoot(document))

        assertThat(bindingId.value).isEqualTo(12)
        assertThat(scopeId.value).isEqualTo(7)
        assertThat(origin.descriptor).isEqualTo("ui:repeat var=row")
        assertThat(origin.provenance?.logicalLocation?.document).isEqualTo(document)
        assertThat(origin.render()).isEqualTo("ui:repeat var=row from legacy/table.xhtml")
    }
}
