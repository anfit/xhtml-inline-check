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

data class ProblemLocation(
    val provenance: Provenance,
    val snippet: String? = null,
) {
    val physicalLocation: SourceLocation
        get() = provenance.physicalLocation

    val logicalLocation: SourceLocation
        get() = provenance.logicalLocation

    fun render(): String = logicalLocation.render()
}

data class ProblemLocations(
    val old: ProblemLocation? = null,
    val new: ProblemLocation? = null,
)

data class Problem(
    val id: DiagnosticId,
    val severity: Severity,
    val category: ProblemCategory,
    val summary: String,
    val locations: ProblemLocations = ProblemLocations(),
    val explanation: String,
    val hint: String? = null,
) {
    init {
        require(id.category == category) {
            "diagnostic id category must match problem category"
        }
        require(id.kind.severity == severity) {
            "diagnostic id kind must match problem severity"
        }
    }
}
