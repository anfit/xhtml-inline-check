package dev.xhtmlinlinecheck.semantic

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.el.ElTemplate
import dev.xhtmlinlinecheck.syntax.LogicalNodePath

@JvmInline
value class CanonicalBindingId(
    val value: String,
) {
    override fun toString(): String = value
}

enum class SemanticElCarrierKind {
    ELEMENT_ATTRIBUTE,
    TEXT_NODE,
    INCLUDE_ATTRIBUTE,
    INCLUDE_PARAMETER,
}

data class SemanticElParseFailure(
    val message: String,
    val offset: Int,
)

data class SemanticElOccurrence(
    val carrierKind: SemanticElCarrierKind,
    val nodePath: LogicalNodePath,
    val ownerTagName: String,
    val ownerName: String? = null,
    val attributeName: String? = null,
    val rawValue: String,
    val location: SourceLocation,
    val provenance: Provenance,
    val template: ElTemplate? = null,
    val parseFailure: SemanticElParseFailure? = null,
) {
    init {
        require((template == null) != (parseFailure == null)) {
            "semantic EL occurrences must carry either a parsed template or a parse failure"
        }
    }

    val isSupported: Boolean
        get() = parseFailure == null
}

data class SemanticElBindingReference(
    val writtenName: String,
    val canonicalId: CanonicalBindingId,
    val binding: ScopeBinding,
)

data class NormalizedSemanticElOccurrence(
    val occurrence: SemanticElOccurrence,
    val normalizedTemplate: NormalizedElTemplate,
    val bindingReferences: List<SemanticElBindingReference>,
)

data class NormalizedElTemplate(
    val segments: List<NormalizedElTemplateSegment>,
) {
    fun render(): String = segments.joinToString(separator = "") { it.render() }
}

sealed interface NormalizedElTemplateSegment {
    fun render(): String
}

data class NormalizedElLiteralSegment(
    val text: String,
) : NormalizedElTemplateSegment {
    override fun render(): String = text
}

data class NormalizedElExpressionSegment(
    val kind: dev.xhtmlinlinecheck.el.ElContainerKind,
    val expression: NormalizedElExpression,
) : NormalizedElTemplateSegment {
    override fun render(): String {
        val opening = when (kind) {
            dev.xhtmlinlinecheck.el.ElContainerKind.DEFERRED -> "#{"
            dev.xhtmlinlinecheck.el.ElContainerKind.IMMEDIATE -> "\${"
        }
        return "$opening${expression.render()}}"
    }
}

sealed interface NormalizedElExpression {
    fun render(): String
}

data class NormalizedElLocalBinding(
    val canonicalId: CanonicalBindingId,
) : NormalizedElExpression {
    override fun render(): String = canonicalId.value
}

data class NormalizedElGlobalRoot(
    val name: String,
) : NormalizedElExpression {
    override fun render(): String = "global($name)"
}

data class NormalizedElBooleanLiteral(
    val value: Boolean,
) : NormalizedElExpression {
    override fun render(): String = value.toString()
}

data object NormalizedElNullLiteral : NormalizedElExpression {
    override fun render(): String = "null"
}

data class NormalizedElNumberLiteral(
    val lexeme: String,
) : NormalizedElExpression {
    override fun render(): String = lexeme
}

data class NormalizedElStringLiteral(
    val value: String,
    val quote: Char,
) : NormalizedElExpression {
    override fun render(): String = "$quote$value$quote"
}

data class NormalizedElGroupedExpression(
    val expression: NormalizedElExpression,
) : NormalizedElExpression {
    override fun render(): String = "(${expression.render()})"
}

data class NormalizedElPropertyAccess(
    val receiver: NormalizedElExpression,
    val property: String,
) : NormalizedElExpression {
    override fun render(): String = "${receiver.render()}.$property"
}

data class NormalizedElIndexAccess(
    val receiver: NormalizedElExpression,
    val index: NormalizedElExpression,
) : NormalizedElExpression {
    override fun render(): String = "${receiver.render()}[${index.render()}]"
}

data class NormalizedElMethodCall(
    val receiver: NormalizedElExpression,
    val methodName: String,
    val arguments: List<NormalizedElExpression>,
) : NormalizedElExpression {
    override fun render(): String = "${receiver.render()}.$methodName(${arguments.joinToString(",") { it.render() }})"
}

data class NormalizedElUnaryOperation(
    val operator: dev.xhtmlinlinecheck.el.ElUnaryOperator,
    val operand: NormalizedElExpression,
) : NormalizedElExpression {
    override fun render(): String =
        when (operator) {
            dev.xhtmlinlinecheck.el.ElUnaryOperator.NOT -> "not ${operand.render()}"
            dev.xhtmlinlinecheck.el.ElUnaryOperator.EMPTY -> "empty ${operand.render()}"
            dev.xhtmlinlinecheck.el.ElUnaryOperator.NEGATE -> "-${operand.render()}"
        }
}

data class NormalizedElBinaryOperation(
    val left: NormalizedElExpression,
    val operator: dev.xhtmlinlinecheck.el.ElBinaryOperator,
    val right: NormalizedElExpression,
) : NormalizedElExpression {
    override fun render(): String = "${left.render()} ${operator.render()} ${right.render()}"
}

data class NormalizedElTernaryOperation(
    val condition: NormalizedElExpression,
    val whenTrue: NormalizedElExpression,
    val whenFalse: NormalizedElExpression,
) : NormalizedElExpression {
    override fun render(): String = "${condition.render()} ? ${whenTrue.render()} : ${whenFalse.render()}"
}

private fun dev.xhtmlinlinecheck.el.ElBinaryOperator.render(): String =
    when (this) {
        dev.xhtmlinlinecheck.el.ElBinaryOperator.OR -> "or"
        dev.xhtmlinlinecheck.el.ElBinaryOperator.AND -> "and"
        dev.xhtmlinlinecheck.el.ElBinaryOperator.EQUALS -> "=="
        dev.xhtmlinlinecheck.el.ElBinaryOperator.NOT_EQUALS -> "!="
        dev.xhtmlinlinecheck.el.ElBinaryOperator.LESS_THAN -> "<"
        dev.xhtmlinlinecheck.el.ElBinaryOperator.LESS_THAN_OR_EQUAL -> "<="
        dev.xhtmlinlinecheck.el.ElBinaryOperator.GREATER_THAN -> ">"
        dev.xhtmlinlinecheck.el.ElBinaryOperator.GREATER_THAN_OR_EQUAL -> ">="
    }
