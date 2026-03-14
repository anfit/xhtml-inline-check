package dev.xhtmlinlinecheck.domain

enum class AnalysisResult(val exitCode: Int) {
    EQUIVALENT(0),
    NOT_EQUIVALENT(1),
    INCONCLUSIVE(2),
}
