package dev.xhtmlinlinecheck.domain

enum class DiagnosticIdKind(
    val prefix: String,
    val severity: Severity,
) {
    PROBLEM(prefix = "P", severity = Severity.ERROR),
    WARNING(prefix = "W", severity = Severity.WARNING),
    ;
}

data class DiagnosticId private constructor(
    val kind: DiagnosticIdKind,
    val category: ProblemCategory,
    val slug: String,
) {
    init {
        require(slugPattern.matches(slug)) {
            "slug must use stable uppercase snake case"
        }
    }

    val value: String = "${kind.prefix}-${category.name}-${slug}"

    override fun toString(): String = value

    companion object {
        private val slugPattern = Regex("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*")
        private val idPattern = Regex("(P|W)-([A-Z]+)-([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*)")

        fun problem(category: ProblemCategory, slug: String): DiagnosticId =
            DiagnosticId(
                kind = DiagnosticIdKind.PROBLEM,
                category = category,
                slug = slug,
            )

        fun warning(category: ProblemCategory, slug: String): DiagnosticId =
            DiagnosticId(
                kind = DiagnosticIdKind.WARNING,
                category = category,
                slug = slug,
            )

        fun parse(value: String): DiagnosticId? {
            val match = idPattern.matchEntire(value) ?: return null
            val kind = when (match.groupValues[1]) {
                "P" -> DiagnosticIdKind.PROBLEM
                "W" -> DiagnosticIdKind.WARNING
                else -> return null
            }
            val category = ProblemCategory.entries.find { it.name == match.groupValues[2] } ?: return null
            return DiagnosticId(
                kind = kind,
                category = category,
                slug = match.groupValues[3],
            )
        }
    }
}

object ProblemIds {
    val SCOPE_BINDING_MISMATCH: DiagnosticId =
        DiagnosticId.problem(
            category = ProblemCategory.SCOPE,
            slug = "BINDING_MISMATCH",
        )

    val STRUCTURE_UNMATCHED_NODE: DiagnosticId =
        DiagnosticId.problem(
            category = ProblemCategory.STRUCTURE,
            slug = "UNMATCHED_NODE",
        )

    val STRUCTURE_FORM_ANCESTRY_CHANGED: DiagnosticId =
        DiagnosticId.problem(
            category = ProblemCategory.STRUCTURE,
            slug = "FORM_ANCESTRY_CHANGED",
        )

    val TARGET_RESOLUTION_CHANGED: DiagnosticId =
        DiagnosticId.problem(
            category = ProblemCategory.TARGET,
            slug = "RESOLUTION_CHANGED",
        )
}

object WarningIds {
    val UNSUPPORTED_UNRESOLVED_GLOBAL_ROOT: DiagnosticId =
        DiagnosticId.warning(
            category = ProblemCategory.UNSUPPORTED,
            slug = "UNRESOLVED_GLOBAL_ROOT",
        )

    val UNSUPPORTED_EXTRACTED_EL: DiagnosticId =
        DiagnosticId.warning(
            category = ProblemCategory.UNSUPPORTED,
            slug = "EXTRACTED_EL",
        )

    val UNSUPPORTED_DYNAMIC_INCLUDE: DiagnosticId =
        DiagnosticId.warning(
            category = ProblemCategory.UNSUPPORTED,
            slug = "DYNAMIC_INCLUDE",
        )

    val UNSUPPORTED_INCLUDE_CYCLE: DiagnosticId =
        DiagnosticId.warning(
            category = ProblemCategory.UNSUPPORTED,
            slug = "INCLUDE_CYCLE",
        )

    val UNSUPPORTED_MISSING_INCLUDE: DiagnosticId =
        DiagnosticId.warning(
            category = ProblemCategory.UNSUPPORTED,
            slug = "MISSING_INCLUDE",
        )

    val UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD: DiagnosticId =
        DiagnosticId.warning(
            category = ProblemCategory.UNSUPPORTED,
            slug = "ANALYZER_PIPELINE_SCAFFOLD",
        )
}
