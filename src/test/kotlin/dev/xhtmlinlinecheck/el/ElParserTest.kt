package dev.xhtmlinlinecheck.el

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ElParserTest {
    @Test
    fun `tokenizer emits deterministic tokens for supported expression operators and suffixes`() {
        val tokens = ElParser.tokenizeExpression("not empty bean.map[row.code] and helper.resolve(row.id)")

        assertThat(tokens.map { it.type })
            .containsExactly(
                ElTokenType.NOT,
                ElTokenType.EMPTY,
                ElTokenType.IDENTIFIER,
                ElTokenType.DOT,
                ElTokenType.IDENTIFIER,
                ElTokenType.LEFT_BRACKET,
                ElTokenType.IDENTIFIER,
                ElTokenType.DOT,
                ElTokenType.IDENTIFIER,
                ElTokenType.RIGHT_BRACKET,
                ElTokenType.AND,
                ElTokenType.IDENTIFIER,
                ElTokenType.DOT,
                ElTokenType.IDENTIFIER,
                ElTokenType.LEFT_PAREN,
                ElTokenType.IDENTIFIER,
                ElTokenType.DOT,
                ElTokenType.IDENTIFIER,
                ElTokenType.RIGHT_PAREN,
                ElTokenType.EOF,
            )
    }

    @Test
    fun `parser supports chained property index and method access rooted at identifiers`() {
        val parsed = ElParser.parseExpression("bean.map[row.code].resolve(item.id, 'label')")

        assertThat(parsed).isEqualTo(
            ElMethodCall(
                receiver =
                    ElIndexAccess(
                        receiver =
                            ElPropertyAccess(
                                receiver = ElIdentifier("bean"),
                                property = "map",
                            ),
                        index =
                            ElPropertyAccess(
                                receiver = ElIdentifier("row"),
                                property = "code",
                            ),
                    ),
                methodName = "resolve",
                arguments =
                    listOf(
                        ElPropertyAccess(
                            receiver = ElIdentifier("item"),
                            property = "id",
                        ),
                        ElStringLiteral(value = "label", quote = '\''),
                    ),
            ),
        )
    }

    @Test
    fun `parser supports nested property access inside index expressions`() {
        val parsed = ElParser.parseExpression("bean.rows[outer.items[index].code].selected")

        assertThat(parsed).isEqualTo(
            ElPropertyAccess(
                receiver =
                    ElIndexAccess(
                        receiver =
                            ElPropertyAccess(
                                receiver = ElIdentifier("bean"),
                                property = "rows",
                            ),
                        index =
                            ElPropertyAccess(
                                receiver =
                                    ElIndexAccess(
                                        receiver =
                                            ElPropertyAccess(
                                                receiver = ElIdentifier("outer"),
                                                property = "items",
                                            ),
                                        index = ElIdentifier("index"),
                                    ),
                                property = "code",
                            ),
                    ),
                property = "selected",
            ),
        )
    }

    @Test
    fun `parser preserves boolean grouping unary operators and ternary shape`() {
        val parsed =
            ElParser.parseExpression("(!disabled and not empty bean.items) ? bean.open(item) : helper.resolve(row.id)")

        assertThat(parsed).isEqualTo(
            ElTernaryOperation(
                condition =
                    ElGroupedExpression(
                        ElBinaryOperation(
                            left =
                                ElUnaryOperation(
                                    operator = ElUnaryOperator.NOT,
                                    operand = ElIdentifier("disabled"),
                                ),
                            operator = ElBinaryOperator.AND,
                            right =
                                ElUnaryOperation(
                                    operator = ElUnaryOperator.NOT,
                                    operand =
                                        ElUnaryOperation(
                                            operator = ElUnaryOperator.EMPTY,
                                            operand =
                                                ElPropertyAccess(
                                                    receiver = ElIdentifier("bean"),
                                                    property = "items",
                                                ),
                                        ),
                                ),
                        ),
                    ),
                whenTrue =
                    ElMethodCall(
                        receiver = ElIdentifier("bean"),
                        methodName = "open",
                        arguments = listOf(ElIdentifier("item")),
                    ),
                whenFalse =
                    ElMethodCall(
                        receiver = ElIdentifier("helper"),
                        methodName = "resolve",
                        arguments =
                            listOf(
                                ElPropertyAccess(
                                    receiver = ElIdentifier("row"),
                                    property = "id",
                                ),
                            ),
                    ),
            ),
        )
    }

    @Test
    fun `parser preserves boolean literals inside ternaries and method arguments`() {
        val parsed = ElParser.parseExpression("enabled ? helper.resolve(true, false) : bean.defaultFlag")

        assertThat(parsed).isEqualTo(
            ElTernaryOperation(
                condition = ElIdentifier("enabled"),
                whenTrue =
                    ElMethodCall(
                        receiver = ElIdentifier("helper"),
                        methodName = "resolve",
                        arguments =
                            listOf(
                                ElBooleanLiteral(true),
                                ElBooleanLiteral(false),
                            ),
                    ),
                whenFalse =
                    ElPropertyAccess(
                        receiver = ElIdentifier("bean"),
                        property = "defaultFlag",
                    ),
            ),
        )
    }

    @Test
    fun `template parser preserves literal segments and both EL container kinds`() {
        val parsed = ElParser.parseTemplate("prefix-#{row.code}-\${helper.resolve(item.id)}-suffix")

        assertThat(parsed).isEqualTo(
            ElTemplate(
                segments =
                    listOf(
                        ElLiteralSegment("prefix-"),
                        ElExpressionSegment(
                            kind = ElContainerKind.DEFERRED,
                            expression =
                                ElPropertyAccess(
                                    receiver = ElIdentifier("row"),
                                    property = "code",
                                ),
                            source = "#{row.code}",
                        ),
                        ElLiteralSegment("-"),
                        ElExpressionSegment(
                            kind = ElContainerKind.IMMEDIATE,
                            expression =
                                ElMethodCall(
                                    receiver = ElIdentifier("helper"),
                                    methodName = "resolve",
                                    arguments =
                                        listOf(
                                            ElPropertyAccess(
                                                receiver = ElIdentifier("item"),
                                                property = "id",
                                            ),
                                        ),
                                ),
                            source = "\${helper.resolve(item.id)}",
                        ),
                        ElLiteralSegment("-suffix"),
                    ),
            ),
        )
    }

    @Test
    fun `parser rejects unsupported collection assignment and semicolon constructs explicitly`() {
        assertThatThrownBy { ElParser.parseExpression("{'label': row.code}") }
            .isInstanceOf(ElParseException::class.java)
            .hasMessageContaining("Unsupported EL token '{'")

        assertThatThrownBy { ElParser.parseExpression("row = item") }
            .isInstanceOf(ElParseException::class.java)
            .hasMessageContaining("Unsupported EL token '='")

        assertThatThrownBy { ElParser.parseExpression("bean.first(); bean.second()") }
            .isInstanceOf(ElParseException::class.java)
            .hasMessageContaining("Unsupported EL token ';'")
    }

    @Test
    fun `parser rejects unsupported namespaced functions and bare calls`() {
        assertThatThrownBy { ElParser.parseExpression("fn:length(items)") }
            .isInstanceOf(ElParseException::class.java)
            .hasMessageContaining("Unexpected token ':'")

        assertThatThrownBy { ElParser.parseExpression("resolve(item)") }
            .isInstanceOf(ElParseException::class.java)
            .hasMessageContaining("Bare function or method invocation is unsupported")
    }

    @Test
    fun `parser rejects unsupported arithmetic and unterminated containers explicitly`() {
        assertThatThrownBy { ElParser.parseExpression("row.count + 1") }
            .isInstanceOf(ElParseException::class.java)
            .hasMessageContaining("Unsupported EL token '+'")

        assertThatThrownBy { ElParser.parseTemplate("prefix #{row.code") }
            .isInstanceOf(ElParseException::class.java)
            .hasMessageContaining("Unterminated EL container")
    }
}
