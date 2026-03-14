package dev.xhtmlinlinecheck.domain

/**
 * Stable top-level outcome vocabulary shared by comparison, reporting, and CLI exit-code mapping.
 *
 * Ordering is intentional: "equivalent" is the strongest positive claim, "not equivalent" is a proven
 * mismatch, and "inconclusive" means equivalence could not be claimed under the static model.
 */
enum class AnalysisResult(val exitCode: Int) {
    EQUIVALENT(0),
    NOT_EQUIVALENT(1),
    INCONCLUSIVE(2),
    ;

    val claimsEquivalence: Boolean
        get() = this == EQUIVALENT

    companion object {
        /**
         * Derives the final top-level result from the comparison engine's two deciding facts:
         * whether any mismatch was proven and whether unsupported or uncertain analysis blocked an
         * equivalence claim.
         */
        fun derive(
            hasMismatch: Boolean,
            blocksEquivalenceClaim: Boolean,
        ): AnalysisResult =
            when {
                hasMismatch -> NOT_EQUIVALENT
                blocksEquivalenceClaim -> INCONCLUSIVE
                else -> EQUIVALENT
            }
    }
}
