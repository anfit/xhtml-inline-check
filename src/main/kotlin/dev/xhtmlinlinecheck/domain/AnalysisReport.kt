package dev.xhtmlinlinecheck.domain

data class AnalysisStats(
    val checkedFacts: Int = 0,
    val matchedFacts: Int = 0,
    val problemCount: Int = 0,
    val warningCount: Int = 0,
)

data class AnalysisReport(
    val result: AnalysisResult,
    val summary: String,
    val problems: List<Problem> = emptyList(),
    val stats: AnalysisStats = AnalysisStats(),
)
