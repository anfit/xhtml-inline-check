package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.el.ElBinaryOperation
import dev.xhtmlinlinecheck.el.ElBooleanLiteral
import dev.xhtmlinlinecheck.el.ElExpression
import dev.xhtmlinlinecheck.el.ElExpressionSegment
import dev.xhtmlinlinecheck.el.ElGroupedExpression
import dev.xhtmlinlinecheck.el.ElIdentifier
import dev.xhtmlinlinecheck.el.ElIndexAccess
import dev.xhtmlinlinecheck.el.ElLiteralSegment
import dev.xhtmlinlinecheck.el.ElMethodCall
import dev.xhtmlinlinecheck.el.ElNullLiteral
import dev.xhtmlinlinecheck.el.ElNumberLiteral
import dev.xhtmlinlinecheck.el.ElPropertyAccess
import dev.xhtmlinlinecheck.el.ElStringLiteral
import dev.xhtmlinlinecheck.el.ElTernaryOperation
import dev.xhtmlinlinecheck.el.ElUnaryOperation

internal object SemanticElNormalizer {
    fun normalize(
        occurrences: List<SemanticElOccurrence>,
        scopeModel: ScopeStackModel,
    ): List<NormalizedSemanticElOccurrence> =
        occurrences.mapNotNull { occurrence ->
            val template = occurrence.template ?: return@mapNotNull null
            val bindingReferences = linkedMapOf<CanonicalBindingId, SemanticElBindingReference>()
            val normalizedTemplate =
                NormalizedElTemplate(
                    segments =
                        template.segments.map { segment ->
                            when (segment) {
                                is ElLiteralSegment -> NormalizedElLiteralSegment(segment.text)
                                is ElExpressionSegment ->
                                    NormalizedElExpressionSegment(
                                        kind = segment.kind,
                                        expression = normalizeExpression(segment.expression, occurrence, scopeModel, bindingReferences),
                                    )
                            }
                        },
                )
            NormalizedSemanticElOccurrence(
                occurrence = occurrence,
                normalizedTemplate = normalizedTemplate,
                bindingReferences = bindingReferences.values.toList(),
            )
        }

    private fun normalizeExpression(
        expression: ElExpression,
        occurrence: SemanticElOccurrence,
        scopeModel: ScopeStackModel,
        bindingReferences: MutableMap<CanonicalBindingId, SemanticElBindingReference>,
    ): NormalizedElExpression =
        when (expression) {
            is ElIdentifier -> normalizeIdentifier(expression, occurrence, scopeModel, bindingReferences)
            is ElBooleanLiteral -> NormalizedElBooleanLiteral(expression.value)
            is ElNullLiteral -> NormalizedElNullLiteral
            is ElNumberLiteral -> NormalizedElNumberLiteral(expression.lexeme)
            is ElStringLiteral -> NormalizedElStringLiteral(expression.value, expression.quote)
            is ElGroupedExpression ->
                NormalizedElGroupedExpression(
                    expression = normalizeExpression(expression.expression, occurrence, scopeModel, bindingReferences),
                )
            is ElPropertyAccess ->
                NormalizedElPropertyAccess(
                    receiver = normalizeExpression(expression.receiver, occurrence, scopeModel, bindingReferences),
                    property = expression.property,
                )
            is ElIndexAccess ->
                NormalizedElIndexAccess(
                    receiver = normalizeExpression(expression.receiver, occurrence, scopeModel, bindingReferences),
                    index = normalizeExpression(expression.index, occurrence, scopeModel, bindingReferences),
                )
            is ElMethodCall ->
                NormalizedElMethodCall(
                    receiver = normalizeExpression(expression.receiver, occurrence, scopeModel, bindingReferences),
                    methodName = expression.methodName,
                    arguments = expression.arguments.map { argument ->
                        normalizeExpression(argument, occurrence, scopeModel, bindingReferences)
                    },
                )
            is ElUnaryOperation ->
                NormalizedElUnaryOperation(
                    operator = expression.operator,
                    operand = normalizeExpression(expression.operand, occurrence, scopeModel, bindingReferences),
                )
            is ElBinaryOperation ->
                NormalizedElBinaryOperation(
                    left = normalizeExpression(expression.left, occurrence, scopeModel, bindingReferences),
                    operator = expression.operator,
                    right = normalizeExpression(expression.right, occurrence, scopeModel, bindingReferences),
                )
            is ElTernaryOperation ->
                NormalizedElTernaryOperation(
                    condition = normalizeExpression(expression.condition, occurrence, scopeModel, bindingReferences),
                    whenTrue = normalizeExpression(expression.whenTrue, occurrence, scopeModel, bindingReferences),
                    whenFalse = normalizeExpression(expression.whenFalse, occurrence, scopeModel, bindingReferences),
                )
        }

    private fun normalizeIdentifier(
        identifier: ElIdentifier,
        occurrence: SemanticElOccurrence,
        scopeModel: ScopeStackModel,
        bindingReferences: MutableMap<CanonicalBindingId, SemanticElBindingReference>,
    ): NormalizedElExpression {
        val binding = scopeModel.resolve(identifier.name, occurrence.nodePath, ScopeLookupPosition.NODE)
        if (binding == null) {
            return NormalizedElGlobalRoot(identifier.name)
        }

        val canonicalId = CanonicalBindingId("binding#${binding.id.value}")
        bindingReferences.putIfAbsent(
            canonicalId,
            SemanticElBindingReference(
                writtenName = identifier.name,
                canonicalId = canonicalId,
                binding = binding,
            ),
        )
        return NormalizedElLocalBinding(canonicalId)
    }
}
