package dev.xhtmlinlinecheck.report

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.xhtmlinlinecheck.domain.DiagnosticDefinition

class TextDiagnosticExplanationRenderer {
    fun render(definition: DiagnosticDefinition): String =
        buildString {
            append(definition.id.value)
            append(System.lineSeparator())
            append("Severity: ")
            append(definition.id.kind.severity.name)
            append(System.lineSeparator())
            append("Category: ")
            append(definition.id.category.name)
            append(System.lineSeparator())
            append("Blocking: ")
            append(if (definition.blocking) "yes" else "no")
            append(System.lineSeparator())
            append("Summary: ")
            append(definition.summary)
            append(System.lineSeparator())
            append("Explanation: ")
            append(definition.explanation)
            definition.hint?.let { hint ->
                append(System.lineSeparator())
                append("Hint: ")
                append(hint)
            }
        }
}

class JsonDiagnosticExplanationRenderer {
    private val mapper =
        jacksonObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .disable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

    fun render(definition: DiagnosticDefinition): String =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
            linkedMapOf(
                "id" to definition.id.value,
                "severity" to definition.id.kind.severity.name,
                "category" to definition.id.category.name,
                "blocking" to definition.blocking,
                "summary" to definition.summary,
                "explanation" to definition.explanation,
                "hint" to definition.hint,
            ),
        )
}
