package dev.xhtmlinlinecheck.el

enum class ElContainerKind {
    DEFERRED,
    IMMEDIATE,
}

data class ElTemplate(
    val segments: List<ElTemplateSegment>,
)

sealed interface ElTemplateSegment

data class ElLiteralSegment(
    val text: String,
) : ElTemplateSegment

data class ElExpressionSegment(
    val kind: ElContainerKind,
    val expression: ElExpression,
    val source: String,
) : ElTemplateSegment

sealed interface ElExpression

data class ElIdentifier(
    val name: String,
) : ElExpression

data class ElBooleanLiteral(
    val value: Boolean,
) : ElExpression

data object ElNullLiteral : ElExpression

data class ElNumberLiteral(
    val lexeme: String,
) : ElExpression

data class ElStringLiteral(
    val value: String,
    val quote: Char,
) : ElExpression

data class ElGroupedExpression(
    val expression: ElExpression,
) : ElExpression

data class ElPropertyAccess(
    val receiver: ElExpression,
    val property: String,
) : ElExpression

data class ElIndexAccess(
    val receiver: ElExpression,
    val index: ElExpression,
) : ElExpression

data class ElMethodCall(
    val receiver: ElExpression,
    val methodName: String,
    val arguments: List<ElExpression>,
) : ElExpression

enum class ElUnaryOperator {
    NOT,
    EMPTY,
    NEGATE,
}

data class ElUnaryOperation(
    val operator: ElUnaryOperator,
    val operand: ElExpression,
) : ElExpression

enum class ElBinaryOperator {
    OR,
    AND,
    EQUALS,
    NOT_EQUALS,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
}

data class ElBinaryOperation(
    val left: ElExpression,
    val operator: ElBinaryOperator,
    val right: ElExpression,
) : ElExpression

data class ElTernaryOperation(
    val condition: ElExpression,
    val whenTrue: ElExpression,
    val whenFalse: ElExpression,
) : ElExpression
