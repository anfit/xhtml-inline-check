package dev.xhtmlinlinecheck.domain

import kotlin.ConsistentCopyVisibility

enum class DiagnosticIdKind(
    val prefix: String,
    val severity: Severity,
) {
    PROBLEM(prefix = "P", severity = Severity.ERROR), WARNING(prefix = "W", severity = Severity.WARNING), ;
}

@ConsistentCopyVisibility
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

        fun problem(category: ProblemCategory, slug: String): DiagnosticId = DiagnosticId(
            kind = DiagnosticIdKind.PROBLEM,
            category = category,
            slug = slug,
        )

        fun warning(category: ProblemCategory, slug: String): DiagnosticId = DiagnosticId(
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

data class DiagnosticDefinition(
    val id: DiagnosticId,
    val summary: String,
    val explanation: String,
    val hint: String? = null,
    val blocking: Boolean = id.kind == DiagnosticIdKind.PROBLEM,
)

object ProblemIds {
    val SCOPE_BINDING_MISMATCH: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.SCOPE,
        slug = "BINDING_MISMATCH",
    )

    val STRUCTURE_UNMATCHED_NODE: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.STRUCTURE,
        slug = "UNMATCHED_NODE",
    )

    val STRUCTURE_ID_COLLISION: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.STRUCTURE,
        slug = "ID_COLLISION",
    )

    val STRUCTURE_ID_SANITY_CHANGED: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.STRUCTURE,
        slug = "ID_SANITY_CHANGED",
    )

    val STRUCTURE_FORM_ANCESTRY_CHANGED: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.STRUCTURE,
        slug = "FORM_ANCESTRY_CHANGED",
    )

    val STRUCTURE_NAMING_CONTAINER_ANCESTRY_CHANGED: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.STRUCTURE,
        slug = "NAMING_CONTAINER_ANCESTRY_CHANGED",
    )

    val STRUCTURE_ITERATION_ANCESTRY_CHANGED: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.STRUCTURE,
        slug = "ITERATION_ANCESTRY_CHANGED",
    )

    val STRUCTURE_RENDERED_GUARD_CHANGED: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.STRUCTURE,
        slug = "RENDERED_GUARD_CHANGED",
    )

    val STRUCTURE_ANCESTRY_SANITY_CHANGED: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.STRUCTURE,
        slug = "ANCESTRY_SANITY_CHANGED",
    )

    val TARGET_RESOLUTION_CHANGED: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.TARGET,
        slug = "RESOLUTION_CHANGED",
    )

    val TARGET_SANITY_CHANGED: DiagnosticId = DiagnosticId.problem(
        category = ProblemCategory.TARGET,
        slug = "SANITY_CHANGED",
    )
}

object WarningIds {
    val UNSUPPORTED_UNRESOLVED_GLOBAL_ROOT: DiagnosticId = DiagnosticId.warning(
        category = ProblemCategory.UNSUPPORTED,
        slug = "UNRESOLVED_GLOBAL_ROOT",
    )

    val UNSUPPORTED_EXTRACTED_EL: DiagnosticId = DiagnosticId.warning(
        category = ProblemCategory.UNSUPPORTED,
        slug = "EXTRACTED_EL",
    )

    val UNSUPPORTED_DYNAMIC_INCLUDE: DiagnosticId = DiagnosticId.warning(
        category = ProblemCategory.UNSUPPORTED,
        slug = "DYNAMIC_INCLUDE",
    )

    val UNSUPPORTED_INCLUDE_CYCLE: DiagnosticId = DiagnosticId.warning(
        category = ProblemCategory.UNSUPPORTED,
        slug = "INCLUDE_CYCLE",
    )

    val UNSUPPORTED_MISSING_INCLUDE: DiagnosticId = DiagnosticId.warning(
        category = ProblemCategory.UNSUPPORTED,
        slug = "MISSING_INCLUDE",
    )

    val UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD: DiagnosticId = DiagnosticId.warning(
        category = ProblemCategory.UNSUPPORTED,
        slug = "ANALYZER_PIPELINE_SCAFFOLD",
    )
}

object DiagnosticCatalog {
    private val definitionsById: Map<DiagnosticId, DiagnosticDefinition> = listOf(
        DiagnosticDefinition(
            id = ProblemIds.SCOPE_BINDING_MISMATCH,
            summary = "Local variable resolves to different binding",
            explanation = "The same EL expression shape now resolves against a different local binding or scope frame.",
            hint = "Preserve the original local scope or rename bindings to avoid capture.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.STRUCTURE_UNMATCHED_NODE,
            summary = "No trustworthy structural match was found",
            explanation = "The matcher could not align a structural node across the old and new trees with enough confidence.",
            hint = "Restore a stable structural anchor such as an explicit id, target relationship, or matching ancestor context.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.STRUCTURE_ID_COLLISION,
            summary = "Explicit component id collides within one tree",
            explanation = "The same explicit component id appears more than once on one side, so matching and target resolution are no longer trustworthy.",
            hint = "Make component ids unique within the relevant naming-container scope.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.STRUCTURE_ID_SANITY_CHANGED,
            summary = "Explicit id inventory changed in an unmatched region",
            explanation = "After structural matching, the remaining unmatched nodes expose a changed set of explicit component ids.",
            hint = "Recheck ids added, removed, or moved inside the affected subtree.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.STRUCTURE_FORM_ANCESTRY_CHANGED,
            summary = "Component form ancestry changed",
            explanation = "A matched component no longer sits under the same effective form ancestry, which can change submit and validation behavior.",
            hint = "Restore the original form wrapper or move the component back under an equivalent form context.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.STRUCTURE_NAMING_CONTAINER_ANCESTRY_CHANGED,
            summary = "Naming-container ancestry changed",
            explanation = "A matched component now resolves ids within a different naming-container chain.",
            hint = "Preserve the original naming-container structure around the affected component.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.STRUCTURE_ITERATION_ANCESTRY_CHANGED,
            summary = "Iteration ancestry changed",
            explanation = "A matched node now sits under a different iteration context, which can change binding and target meaning.",
            hint = "Restore the original iterator scope or keep the node within an equivalent repeated region.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.STRUCTURE_RENDERED_GUARD_CHANGED,
            summary = "Rendered guard changed",
            explanation = "The normalized rendered condition on a matched node changed, so the node may no longer appear under the same circumstances.",
            hint = "Keep the rendered expression and surrounding scope equivalent.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.STRUCTURE_ANCESTRY_SANITY_CHANGED,
            summary = "Structural ancestry inventory changed in an unmatched region",
            explanation = "After matching, the remaining unmatched nodes show changed ancestry-sensitive structure such as forms, naming containers, or iterators.",
            hint = "Inspect wrapper and container changes around the affected unmatched subtree.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.TARGET_RESOLUTION_CHANGED,
            summary = "Component target no longer resolves the same way",
            explanation = "A target-bearing attribute such as for, update, render, process, or execute now resolves to a different semantic target set.",
            hint = "Restore the original target ids or equivalent resolution context.",
        ),
        DiagnosticDefinition(
            id = ProblemIds.TARGET_SANITY_CHANGED,
            summary = "Target-bearing attribute inventory changed in an unmatched region",
            explanation = "After matching, the remaining unmatched nodes changed the set or meaning of target-bearing attributes.",
            hint = "Inspect labels, messages, or AJAX/process attributes in the affected unmatched subtree.",
        ),
        DiagnosticDefinition(
            id = WarningIds.UNSUPPORTED_UNRESOLVED_GLOBAL_ROOT,
            summary = "Global-root meaning is still unresolved",
            explanation = "The analyzer preserved a global EL root symbolically but cannot prove the bean-level meaning is equivalent.",
            hint = "Review the affected bean-backed expression manually if this warning must be eliminated.",
            blocking = true,
        ),
        DiagnosticDefinition(
            id = WarningIds.UNSUPPORTED_EXTRACTED_EL,
            summary = "Extracted EL falls outside the supported subset",
            explanation = "The analyzer extracted EL relevant to comparison but could not normalize it within the documented MVP grammar subset.",
            hint = "Simplify the EL shape or extend the supported subset before relying on an equivalent result.",
            blocking = true,
        ),
        DiagnosticDefinition(
            id = WarningIds.UNSUPPORTED_DYNAMIC_INCLUDE,
            summary = "Include path is dynamic and not statically resolvable",
            explanation = "A ui:include src uses EL or another dynamic form, so the tool cannot expand the subtree deterministically.",
            hint = "Use a static include path or accept an inconclusive result for that region.",
            blocking = true,
        ),
        DiagnosticDefinition(
            id = WarningIds.UNSUPPORTED_INCLUDE_CYCLE,
            summary = "Include expansion hit a cycle",
            explanation = "Recursive include expansion revisited the same document chain, so analysis stops at the cycle boundary.",
            hint = "Break the include cycle or exclude the affected region from equivalence claims.",
            blocking = true,
        ),
        DiagnosticDefinition(
            id = WarningIds.UNSUPPORTED_MISSING_INCLUDE,
            summary = "Resolved include file is missing",
            explanation = "A static include path resolved to a document that was not present on disk, so expansion could not continue.",
            hint = "Restore the missing file or correct the include path before trusting the comparison.",
            blocking = true,
        ),
        DiagnosticDefinition(
            id = WarningIds.UNSUPPORTED_ANALYZER_PIPELINE_SCAFFOLD,
            summary = "Analyzer pipeline is still scaffolded",
            explanation = "The current implementation still emits a placeholder warning from unfinished analyzer stages.",
            hint = "Treat this as informational while scaffolded stages remain; it should disappear as the analyzer is completed.",
            blocking = false,
        ),
    ).associateBy { it.id }

    val definitions: List<DiagnosticDefinition>
        get() = definitionsById.values.sortedBy { it.id.value }

    fun definitionFor(id: DiagnosticId): DiagnosticDefinition? = definitionsById[id]
}
