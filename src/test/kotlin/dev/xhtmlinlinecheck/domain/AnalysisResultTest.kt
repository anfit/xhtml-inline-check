package dev.xhtmlinlinecheck.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnalysisResultTest {
    @Test
    fun `keeps stable outcome ordering and exit-code mapping`() {
        assertThat(AnalysisResult.entries).containsExactly(
            AnalysisResult.EQUIVALENT,
            AnalysisResult.NOT_EQUIVALENT,
            AnalysisResult.INCONCLUSIVE,
        )

        assertThat(AnalysisResult.EQUIVALENT.exitCode).isEqualTo(0)
        assertThat(AnalysisResult.NOT_EQUIVALENT.exitCode).isEqualTo(1)
        assertThat(AnalysisResult.INCONCLUSIVE.exitCode).isEqualTo(2)
    }

    @Test
    fun `claims equivalence only for equivalent outcome`() {
        assertThat(AnalysisResult.EQUIVALENT.claimsEquivalence).isTrue()
        assertThat(AnalysisResult.NOT_EQUIVALENT.claimsEquivalence).isFalse()
        assertThat(AnalysisResult.INCONCLUSIVE.claimsEquivalence).isFalse()
    }

    @Test
    fun `derives not equivalent when a mismatch is proven`() {
        val result = AnalysisResult.derive(
            hasMismatch = true,
            blocksEquivalenceClaim = false,
        )

        assertThat(result).isEqualTo(AnalysisResult.NOT_EQUIVALENT)
    }

    @Test
    fun `derives inconclusive when no mismatch is proven but equivalence is blocked`() {
        val result = AnalysisResult.derive(
            hasMismatch = false,
            blocksEquivalenceClaim = true,
        )

        assertThat(result).isEqualTo(AnalysisResult.INCONCLUSIVE)
    }

    @Test
    fun `derives equivalent only when no mismatch or blocker exists`() {
        val result = AnalysisResult.derive(
            hasMismatch = false,
            blocksEquivalenceClaim = false,
        )

        assertThat(result).isEqualTo(AnalysisResult.EQUIVALENT)
    }
}
