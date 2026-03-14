package dev.xhtmlinlinecheck.el

enum class ElTokenType {
    IDENTIFIER,
    TRUE,
    FALSE,
    NULL,
    NUMBER,
    STRING,
    DOT,
    LEFT_PAREN,
    RIGHT_PAREN,
    LEFT_BRACKET,
    RIGHT_BRACKET,
    COMMA,
    QUESTION,
    COLON,
    OR,
    AND,
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE,
    NOT,
    EMPTY,
    MINUS,
    EOF,
}

data class ElToken(
    val type: ElTokenType,
    val lexeme: String,
    val offset: Int,
)

class ElParseException(
    message: String,
    val offset: Int,
) : IllegalArgumentException("$message at offset $offset")

object ElTokenizer {
    fun tokenize(expression: String): List<ElToken> {
        val tokens = mutableListOf<ElToken>()
        var index = 0
        while (index < expression.length) {
            val ch = expression[index]
            when {
                ch.isWhitespace() -> index++
                ch == '.' -> tokens += ElToken(ElTokenType.DOT, ".", index).also { index++ }
                ch == '(' -> tokens += ElToken(ElTokenType.LEFT_PAREN, "(", index).also { index++ }
                ch == ')' -> tokens += ElToken(ElTokenType.RIGHT_PAREN, ")", index).also { index++ }
                ch == '[' -> tokens += ElToken(ElTokenType.LEFT_BRACKET, "[", index).also { index++ }
                ch == ']' -> tokens += ElToken(ElTokenType.RIGHT_BRACKET, "]", index).also { index++ }
                ch == ',' -> tokens += ElToken(ElTokenType.COMMA, ",", index).also { index++ }
                ch == '?' -> tokens += ElToken(ElTokenType.QUESTION, "?", index).also { index++ }
                ch == ':' -> tokens += ElToken(ElTokenType.COLON, ":", index).also { index++ }
                ch == '-' -> tokens += ElToken(ElTokenType.MINUS, "-", index).also { index++ }
                ch == '!' ->
                    if (expression.startsWith("!=", index)) {
                        tokens += ElToken(ElTokenType.NE, "!=", index)
                        index += 2
                    } else {
                        tokens += ElToken(ElTokenType.NOT, "!", index)
                        index++
                    }
                ch == '=' ->
                    if (expression.startsWith("==", index)) {
                        tokens += ElToken(ElTokenType.EQ, "==", index)
                        index += 2
                    } else {
                        throw ElParseException("Unsupported EL token '='", index)
                    }
                ch == '<' ->
                    if (expression.startsWith("<=", index)) {
                        tokens += ElToken(ElTokenType.LE, "<=", index)
                        index += 2
                    } else {
                        tokens += ElToken(ElTokenType.LT, "<", index)
                        index++
                    }
                ch == '>' ->
                    if (expression.startsWith(">=", index)) {
                        tokens += ElToken(ElTokenType.GE, ">=", index)
                        index += 2
                    } else {
                        tokens += ElToken(ElTokenType.GT, ">", index)
                        index++
                    }
                ch == '&' ->
                    if (expression.startsWith("&&", index)) {
                        tokens += ElToken(ElTokenType.AND, "&&", index)
                        index += 2
                    } else {
                        throw ElParseException("Unsupported EL token '&'", index)
                    }
                ch == '|' ->
                    if (expression.startsWith("||", index)) {
                        tokens += ElToken(ElTokenType.OR, "||", index)
                        index += 2
                    } else {
                        throw ElParseException("Unsupported EL token '|'", index)
                    }
                ch == '\'' || ch == '"' -> {
                    tokens += readString(expression, index)
                    index = tokens.last().offset + tokens.last().lexeme.length
                }
                ch.isDigit() -> {
                    tokens += readNumber(expression, index)
                    index = tokens.last().offset + tokens.last().lexeme.length
                }
                isIdentifierStart(ch) -> {
                    tokens += readIdentifier(expression, index)
                    index = tokens.last().offset + tokens.last().lexeme.length
                }
                else -> throw ElParseException("Unsupported EL token '$ch'", index)
            }
        }
        tokens += ElToken(ElTokenType.EOF, "", expression.length)
        return tokens
    }

    private fun readString(expression: String, start: Int): ElToken {
        val quote = expression[start]
        var index = start + 1
        var escaped = false
        while (index < expression.length) {
            val current = expression[index]
            when {
                escaped -> escaped = false
                current == '\\' -> escaped = true
                current == quote -> {
                    val lexeme = expression.substring(start, index + 1)
                    return ElToken(ElTokenType.STRING, lexeme, start)
                }
            }
            index++
        }
        throw ElParseException("Unterminated string literal", start)
    }

    private fun readNumber(expression: String, start: Int): ElToken {
        var index = start
        while (index < expression.length && expression[index].isDigit()) {
            index++
        }
        if (index < expression.length && expression[index] == '.') {
            val decimalPoint = index
            index++
            if (index >= expression.length || !expression[index].isDigit()) {
                throw ElParseException("Invalid decimal literal", decimalPoint)
            }
            while (index < expression.length && expression[index].isDigit()) {
                index++
            }
        }
        return ElToken(ElTokenType.NUMBER, expression.substring(start, index), start)
    }

    private fun readIdentifier(expression: String, start: Int): ElToken {
        var index = start + 1
        while (index < expression.length && isIdentifierPart(expression[index])) {
            index++
        }
        val lexeme = expression.substring(start, index)
        return ElToken(keywordType(lexeme), lexeme, start)
    }

    private fun keywordType(lexeme: String): ElTokenType =
        when (lexeme) {
            "true" -> ElTokenType.TRUE
            "false" -> ElTokenType.FALSE
            "null" -> ElTokenType.NULL
            "or" -> ElTokenType.OR
            "and" -> ElTokenType.AND
            "eq" -> ElTokenType.EQ
            "ne" -> ElTokenType.NE
            "lt" -> ElTokenType.LT
            "le" -> ElTokenType.LE
            "gt" -> ElTokenType.GT
            "ge" -> ElTokenType.GE
            "not" -> ElTokenType.NOT
            "empty" -> ElTokenType.EMPTY
            else -> ElTokenType.IDENTIFIER
        }

    private fun isIdentifierStart(ch: Char): Boolean = ch == '_' || ch == '$' || Character.isJavaIdentifierStart(ch)

    private fun isIdentifierPart(ch: Char): Boolean = ch == '$' || Character.isJavaIdentifierPart(ch)
}
