package dev.xhtmlinlinecheck.domain

data class AggregateCounts(
    val checked: Int = 0,
    val matched: Int = 0,
    val mismatched: Int = 0,
) {
    init {
        require(checked >= 0) { "checked must be non-negative" }
        require(matched >= 0) { "matched must be non-negative" }
        require(mismatched >= 0) { "mismatched must be non-negative" }
        require(matched + mismatched <= checked) {
            "matched + mismatched must not exceed checked"
        }
    }
}

data class AggregateCoverage(
    val covered: Int = 0,
    val total: Int = 0,
) {
    init {
        require(covered >= 0) { "covered must be non-negative" }
        require(total >= 0) { "total must be non-negative" }
        require(covered <= total) { "covered must not exceed total" }
    }

    val percent: Double?
        get() = if (total == 0) null else (covered.toDouble() / total.toDouble()) * 100.0

    companion object {
        fun from(counts: AggregateCounts): AggregateCoverage =
            AggregateCoverage(
                covered = counts.matched + counts.mismatched,
                total = counts.checked,
            )
    }
}

data class WarningTotals(
    val total: Int = 0,
    val blocking: Int = 0,
) {
    init {
        require(total >= 0) { "total must be non-negative" }
        require(blocking >= 0) { "blocking must be non-negative" }
        require(blocking <= total) { "blocking must not exceed total" }
    }
}

data class AnalysisSummary(
    val headline: String,
    val counts: AggregateCounts = AggregateCounts(),
    val coverage: AggregateCoverage = AggregateCoverage.from(counts),
    val warnings: WarningTotals = WarningTotals(),
) {
    init {
        require(coverage == AggregateCoverage.from(counts)) {
            "coverage must match aggregate counts"
        }
    }
}

data class AnalysisStats(
    val counts: AggregateCounts = AggregateCounts(),
    val coverage: AggregateCoverage = AggregateCoverage.from(counts),
    val warnings: WarningTotals = WarningTotals(),
) {
    init {
        require(coverage == AggregateCoverage.from(counts)) {
            "coverage must match aggregate counts"
        }
    }
}

data class AnalysisReport(
    val result: AnalysisResult,
    val summary: AnalysisSummary,
    val problems: List<Problem> = emptyList(),
    val stats: AnalysisStats = AnalysisStats(
        counts = summary.counts,
        coverage = summary.coverage,
        warnings = summary.warnings,
    ),
)
