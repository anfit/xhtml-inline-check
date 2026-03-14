package dev.xhtmlinlinecheck.domain

enum class BindingKind {
    UI_PARAM,
    ITERATION_VAR,
    VAR_STATUS,
    C_SET,
    C_FOR_EACH,
    IMPLICIT_GLOBAL,
}

@JvmInline
value class BindingId(
    val value: Int,
)

@JvmInline
value class ScopeId(
    val value: Int,
)

data class BindingOrigin(
    val descriptor: String,
)
