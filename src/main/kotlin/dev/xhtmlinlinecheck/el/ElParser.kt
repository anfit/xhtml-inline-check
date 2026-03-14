package dev.xhtmlinlinecheck.el

object ElParser {
    fun tokenizeExpression(expression: String): List<ElToken> = ElTokenizer.tokenize(expression)

    fun parseExpression(expression: String): ElExpression = ExpressionParser(expression).parse()

    fun parseTemplate(template: String): ElTemplate {
        if (template.isEmpty()) {
            return ElTemplate(emptyList())
        }

        val segments = mutableListOf<ElTemplateSegment>()
        var literalStart = 0
        var index = 0
        while (index < template.length) {
            val containerKind =
                when {
                    template.startsWith("#{", index) -> ElContainerKind.DEFERRED
                    template.startsWith("\${", index) -> ElContainerKind.IMMEDIATE
                    else -> null
                }
            if (containerKind == null) {
                index++
                continue
            }

            if (literalStart < index) {
                segments += ElLiteralSegment(template.substring(literalStart, index))
            }

            val bodyEnd = findContainerEnd(template, index + 2)
            val source = template.substring(index, bodyEnd + 1)
            val expression = parseExpression(template.substring(index + 2, bodyEnd))
            segments += ElExpressionSegment(kind = containerKind, expression = expression, source = source)
            index = bodyEnd + 1
            literalStart = index
        }

        if (literalStart < template.length) {
            segments += ElLiteralSegment(template.substring(literalStart))
        }

        return ElTemplate(segments)
    }

    private fun findContainerEnd(template: String, start: Int): Int {
        var index = start
        var quote: Char? = null
        var escaped = false
        while (index < template.length) {
            val current = template[index]
            when {
                quote != null && escaped -> escaped = false
                quote != null && current == '\\' -> escaped = true
                quote != null && current == quote -> quote = null
                quote == null && (current == '\'' || current == '"') -> quote = current
                quote == null && current == '}' -> return index
            }
            index++
        }
        throw ElParseException("Unterminated EL container", start - 2)
    }
}

private class ExpressionParser(
    private val source: String,
) {
    private val tokens: List<ElToken> = ElTokenizer.tokenize(source)
    private var position: Int = 0

    fun parse(): ElExpression {
        val expression = parseTernary()
        if (!isAtEnd()) {
            throw error("Unexpected token '${peek().lexeme}'")
        }
        return expression
    }

    private fun parseTernary(): ElExpression {
        val condition = parseOr()
        if (!match(ElTokenType.QUESTION)) {
            return condition
        }
        val whenTrue = parseTernary()
        consume(ElTokenType.COLON, "Expected ':' in ternary expression")
        val whenFalse = parseTernary()
        return ElTernaryOperation(condition = condition, whenTrue = whenTrue, whenFalse = whenFalse)
    }

    private fun parseOr(): ElExpression = parseLeftAssociative(::parseAnd, ElTokenType.OR) { ElBinaryOperator.OR }

    private fun parseAnd(): ElExpression = parseLeftAssociative(::parseEquality, ElTokenType.AND) { ElBinaryOperator.AND }

    private fun parseEquality(): ElExpression =
        parseLeftAssociative(::parseRelational, ElTokenType.EQ, ElTokenType.NE) {
            when (previous().type) {
                ElTokenType.EQ -> ElBinaryOperator.EQUALS
                ElTokenType.NE -> ElBinaryOperator.NOT_EQUALS
                else -> throw IllegalStateException("Unexpected equality operator ${previous().type}")
            }
        }

    private fun parseRelational(): ElExpression =
        parseLeftAssociative(
            ::parseUnary,
            ElTokenType.LT,
            ElTokenType.LE,
            ElTokenType.GT,
            ElTokenType.GE,
        ) {
            when (previous().type) {
                ElTokenType.LT -> ElBinaryOperator.LESS_THAN
                ElTokenType.LE -> ElBinaryOperator.LESS_THAN_OR_EQUAL
                ElTokenType.GT -> ElBinaryOperator.GREATER_THAN
                ElTokenType.GE -> ElBinaryOperator.GREATER_THAN_OR_EQUAL
                else -> throw IllegalStateException("Unexpected relational operator ${previous().type}")
            }
        }

    private fun parseUnary(): ElExpression {
        if (match(ElTokenType.NOT)) {
            return ElUnaryOperation(operator = ElUnaryOperator.NOT, operand = parseUnary())
        }
        if (match(ElTokenType.EMPTY)) {
            return ElUnaryOperation(operator = ElUnaryOperator.EMPTY, operand = parseUnary())
        }
        if (match(ElTokenType.MINUS)) {
            return ElUnaryOperation(operator = ElUnaryOperator.NEGATE, operand = parseUnary())
        }
        return parsePrimary()
    }

    private fun parsePrimary(): ElExpression {
        var expression = parseAtom()
        while (true) {
            expression =
                when {
                    match(ElTokenType.DOT) -> {
                        val identifier = consume(ElTokenType.IDENTIFIER, "Expected identifier after '.'")
                        if (match(ElTokenType.LEFT_PAREN)) {
                            ElMethodCall(
                                receiver = expression,
                                methodName = identifier.lexeme,
                                arguments = parseArgumentList(),
                            )
                        } else {
                            ElPropertyAccess(receiver = expression, property = identifier.lexeme)
                        }
                    }
                    match(ElTokenType.LEFT_BRACKET) -> {
                        val index = parseTernary()
                        consume(ElTokenType.RIGHT_BRACKET, "Expected ']' after index expression")
                        ElIndexAccess(receiver = expression, index = index)
                    }
                    match(ElTokenType.LEFT_PAREN) ->
                        throw error("Bare function or method invocation is unsupported")
                    else -> return expression
                }
        }
    }

    private fun parseAtom(): ElExpression =
        when {
            match(ElTokenType.IDENTIFIER) -> ElIdentifier(previous().lexeme)
            match(ElTokenType.TRUE) -> ElBooleanLiteral(true)
            match(ElTokenType.FALSE) -> ElBooleanLiteral(false)
            match(ElTokenType.NULL) -> ElNullLiteral
            match(ElTokenType.NUMBER) -> ElNumberLiteral(previous().lexeme)
            match(ElTokenType.STRING) -> parseStringLiteral(previous())
            match(ElTokenType.LEFT_PAREN) -> {
                val grouped = parseTernary()
                consume(ElTokenType.RIGHT_PAREN, "Expected ')' after grouped expression")
                ElGroupedExpression(grouped)
            }
            else -> throw error("Expected EL expression")
        }

    private fun parseStringLiteral(token: ElToken): ElStringLiteral {
        val quote = token.lexeme.first()
        val value = buildString {
            var escaped = false
            for (index in 1 until token.lexeme.lastIndex) {
                val ch = token.lexeme[index]
                when {
                    escaped -> {
                        append(ch)
                        escaped = false
                    }
                    ch == '\\' -> escaped = true
                    else -> append(ch)
                }
            }
        }
        return ElStringLiteral(value = value, quote = quote)
    }

    private fun parseArgumentList(): List<ElExpression> {
        if (match(ElTokenType.RIGHT_PAREN)) {
            return emptyList()
        }
        val arguments = mutableListOf<ElExpression>()
        do {
            arguments += parseTernary()
        } while (match(ElTokenType.COMMA))
        consume(ElTokenType.RIGHT_PAREN, "Expected ')' after method arguments")
        return arguments
    }

    private fun parseLeftAssociative(
        next: () -> ElExpression,
        vararg operators: ElTokenType,
        operatorOf: () -> ElBinaryOperator,
    ): ElExpression {
        var expression = next()
        while (match(*operators)) {
            val operator = operatorOf()
            val right = next()
            expression = ElBinaryOperation(left = expression, operator = operator, right = right)
        }
        return expression
    }

    private fun match(vararg types: ElTokenType): Boolean {
        if (types.any { check(it) }) {
            advance()
            return true
        }
        return false
    }

    private fun consume(type: ElTokenType, message: String): ElToken {
        if (check(type)) {
            return advance()
        }
        throw error(message)
    }

    private fun check(type: ElTokenType): Boolean = !isAtEnd() && peek().type == type

    private fun advance(): ElToken {
        if (!isAtEnd()) {
            position++
        }
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == ElTokenType.EOF

    private fun peek(): ElToken = tokens[position]

    private fun previous(): ElToken = tokens[position - 1]

    private fun error(message: String): ElParseException = ElParseException(message, peek().offset)
}
