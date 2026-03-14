package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.BindingKind
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
            val canonicalIdsByBindingId =
                visibleCanonicalBindingsFor(occurrence, scopeModel)
                    .mapIndexed { index, binding ->
                        binding.id to CanonicalBindingId("binding#${index + 1}")
                    }.toMap(linkedMapOf())
            val bindingReferences = linkedMapOf<CanonicalBindingId, SemanticElBindingReference>()
            val globalReferences = linkedMapOf<String, SemanticElGlobalReference>()
            val normalizedTemplate =
                NormalizedElTemplate(
                    segments =
                        template.segments.map { segment ->
                            when (segment) {
                                is ElLiteralSegment -> NormalizedElLiteralSegment(segment.text)
                                is ElExpressionSegment ->
                                    NormalizedElExpressionSegment(
                                        kind = segment.kind,
                                        expression =
                                            normalizeExpression(
                                                expression = segment.expression,
                                                occurrence = occurrence,
                                                scopeModel = scopeModel,
                                                canonicalIdsByBindingId = canonicalIdsByBindingId,
                                                bindingReferences = bindingReferences,
                                                globalReferences = globalReferences,
                                            ),
                                    )
                            }
                        },
                )
            NormalizedSemanticElOccurrence(
                occurrence = occurrence,
                normalizedTemplate = normalizedTemplate,
                bindingReferences = bindingReferences.values.toList(),
                globalReferences = globalReferences.values.toList(),
            )
        }

    private fun visibleCanonicalBindingsFor(
        occurrence: SemanticElOccurrence,
        scopeModel: ScopeStackModel,
    ): List<ScopeBinding> =
        scopeModel.visibleBindingsAt(occurrence.nodePath, ScopeLookupPosition.NODE)
            .asReversed()
            .filterNot { it.kind == BindingKind.VAR_STATUS }

    private fun normalizeExpression(
        expression: ElExpression,
        occurrence: SemanticElOccurrence,
        scopeModel: ScopeStackModel,
        canonicalIdsByBindingId: MutableMap<dev.xhtmlinlinecheck.domain.BindingId, CanonicalBindingId>,
        bindingReferences: MutableMap<CanonicalBindingId, SemanticElBindingReference>,
        globalReferences: MutableMap<String, SemanticElGlobalReference>,
    ): NormalizedElExpression =
        when (expression) {
            is ElIdentifier ->
                normalizeIdentifier(
                    expression,
                    occurrence,
                    scopeModel,
                    canonicalIdsByBindingId,
                    bindingReferences,
                    globalReferences,
                )

            is ElBooleanLiteral -> NormalizedElBooleanLiteral(expression.value)
            is ElNullLiteral -> NormalizedElNullLiteral
            is ElNumberLiteral -> NormalizedElNumberLiteral(expression.lexeme)
            is ElStringLiteral -> NormalizedElStringLiteral(expression.value, expression.quote)
            is ElGroupedExpression ->
                NormalizedElGroupedExpression(
                    expression =
                        normalizeExpression(
                            expression.expression,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                )

            is ElPropertyAccess ->
                NormalizedElPropertyAccess(
                    receiver =
                        normalizeExpression(
                            expression.receiver,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                    property = expression.property,
                )

            is ElIndexAccess ->
                NormalizedElIndexAccess(
                    receiver =
                        normalizeExpression(
                            expression.receiver,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                    index =
                        normalizeExpression(
                            expression.index,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                )

            is ElMethodCall ->
                NormalizedElMethodCall(
                    receiver =
                        normalizeExpression(
                            expression.receiver,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                    methodName = expression.methodName,
                    arguments = expression.arguments.map { argument ->
                        normalizeExpression(
                            argument,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        )
                    },
                )

            is ElUnaryOperation ->
                NormalizedElUnaryOperation(
                    operator = expression.operator,
                    operand =
                        normalizeExpression(
                            expression.operand,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                )

            is ElBinaryOperation ->
                NormalizedElBinaryOperation(
                    left =
                        normalizeExpression(
                            expression.left,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                    operator = expression.operator,
                    right =
                        normalizeExpression(
                            expression.right,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                )

            is ElTernaryOperation ->
                NormalizedElTernaryOperation(
                    condition =
                        normalizeExpression(
                            expression.condition,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                    whenTrue =
                        normalizeExpression(
                            expression.whenTrue,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                    whenFalse =
                        normalizeExpression(
                            expression.whenFalse,
                            occurrence,
                            scopeModel,
                            canonicalIdsByBindingId,
                            bindingReferences,
                            globalReferences,
                        ),
                )
        }

    private fun normalizeIdentifier(
        identifier: ElIdentifier,
        occurrence: SemanticElOccurrence,
        scopeModel: ScopeStackModel,
        canonicalIdsByBindingId: MutableMap<dev.xhtmlinlinecheck.domain.BindingId, CanonicalBindingId>,
        bindingReferences: MutableMap<CanonicalBindingId, SemanticElBindingReference>,
        globalReferences: MutableMap<String, SemanticElGlobalReference>,
    ): NormalizedElExpression {
        val binding = scopeModel.resolve(identifier.name, occurrence.nodePath, ScopeLookupPosition.NODE)
        if (binding == null) {
            globalReferences.putIfAbsent(
                identifier.name,
                SemanticElGlobalReference(writtenName = identifier.name),
            )
            return NormalizedElGlobalRoot(identifier.name)
        }

        val canonicalId =
            canonicalIdsByBindingId.getOrPut(binding.id) {
                CanonicalBindingId("binding#${canonicalIdsByBindingId.size + 1}")
            }
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
