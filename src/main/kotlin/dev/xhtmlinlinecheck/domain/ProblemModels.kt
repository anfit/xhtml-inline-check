package dev.xhtmlinlinecheck.domain

enum class Severity {
    ERROR,
    WARNING,
}

enum class ProblemCategory {
    SCOPE,
    STRUCTURE,
    TARGET,
    PARSE,
    UNSUPPORTED,
    INTERNAL,
}

data class Problem(
    val id: String,
    val severity: Severity,
    val category: ProblemCategory,
    val summary: String,
    val explanation: String,
    val hint: String? = null,
    val oldLocation: SourceLocation? = null,
    val newLocation: SourceLocation? = null,
    val oldSnippet: String? = null,
    val newSnippet: String? = null,
)
