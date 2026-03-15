# MVP EL Grammar Subset

## Purpose

This document defines the exact EL subset that the analyzer's MVP is expected to parse symbolically. It is deliberately narrower than full Jakarta EL and exists to keep scope comparison, semantic extraction, and structural checks deterministic.

If an extracted EL occurrence falls outside this subset, the analyzer must emit an explicit unsupported diagnostic and treat the affected comparison as inconclusive rather than silently degrading.

## Scope Of This Contract

The subset applies anywhere the analyzer extracts EL:

- EL-bearing attributes from the tag-rule registry
- text nodes when the semantic layer elects to analyze mixed text content
- include-related values such as `ui:include @src` and `ui:param @value`
- structural attributes that may combine literals and EL segments, such as `id`, `for`, `update`, `render`, `process`, and `execute`

## Supported Container Forms

The analyzer should support EL inside both deferred and immediate containers:

```text
Template           ::= Segment+
Segment            ::= LiteralText | DeferredExpr | ImmediateExpr
DeferredExpr       ::= "#{" Expr "}"
ImmediateExpr      ::= "${" Expr "}"
LiteralText        ::= any text outside an EL container
```

For template-bearing attributes, the analyzer should preserve the literal segments and parse each EL segment independently. Scope comparison operates on the parsed EL segments; structural checks may also compare the surrounding literal tokens.

## Supported Expression Grammar

The MVP expression grammar is intentionally symbolic. It preserves reference shape and guard structure without evaluating runtime values.

```text
Expr               ::= TernaryExpr
TernaryExpr        ::= OrExpr ("?" Expr ":" Expr)?
OrExpr             ::= AndExpr (("or" | "||") AndExpr)*
AndExpr            ::= EqualityExpr (("and" | "&&") EqualityExpr)*
EqualityExpr       ::= RelExpr (("==" | "!=" | "eq" | "ne") RelExpr)*
RelExpr            ::= UnaryExpr (("<" | "<=" | ">" | ">=" | "lt" | "le" | "gt" | "ge") UnaryExpr)*
UnaryExpr          ::= ("!" | "not" | "empty" | "-") UnaryExpr | PrimaryExpr
PrimaryExpr        ::= Atom Suffix*
Atom               ::= Identifier
                    | "true"
                    | "false"
                    | "null"
                    | NumberLiteral
                    | StringLiteral
                    | "(" Expr ")"
Suffix             ::= PropertyAccess | IndexAccess | MethodCall
PropertyAccess     ::= "." Identifier
IndexAccess        ::= "[" Expr "]"
MethodCall         ::= "." Identifier "(" ArgumentList? ")"
ArgumentList       ::= Expr ("," Expr)*
Identifier         ::= Java-identifier-like token accepted by the parser
NumberLiteral      ::= integer or decimal literal
StringLiteral      ::= single-quoted or double-quoted literal
```

## Supported Semantic Shapes

This grammar is sufficient for the MVP behaviors implemented in the repository:

- scope comparison:
  - local-root identifiers
  - property chains such as `#{row.plant.code}`
  - indexed lookups such as `#{bean.map[row.code]}`
  - iterator, `ui:param`, and `c:set` shadowing/capture through normalized root bindings
- semantic extraction:
  - EL-bearing attributes from tag rules, including `value`, `rendered`, `test`, `items`, `begin`, `end`, `step`, `offset`, `size`, `target`, and include-parameter values
  - mixed literal-plus-EL templates where the structural meaning depends on both parts, such as `id="#{prefix}suffix"` or `execute="@this,#{bedExecute}"`
- structural checks:
  - boolean guards such as `#{!disabled}`, `#{not empty bean.items}`, and `#{a and b}`
  - ternaries needed to preserve shape in guarded ids or rendered expressions
  - method-call shape when it materially affects the referenced roots, such as `#{helper.resolve(row.id)}` or `#{bean.open(item)}`

## Normalization Expectations

Within the supported subset, the EL layer should:

- resolve the leading identifier of each reference against the active scope stack
- canonicalize local bindings by binding id so safe alpha-renames compare equal
- preserve unresolved global roots symbolically instead of treating them as local matches
- preserve operator and call structure so guards and structural templates remain comparable
- preserve literal template segments around EL containers for structural attributes and text extraction

## Unsupported Forms

The MVP does not attempt full Jakarta EL coverage. The following forms are outside the supported subset and must contribute to explicit unsupported handling:

- function invocations with namespace prefixes such as `#{fn:length(items)}` or `#{util:label(row)}`
- collection, list, set, or map literals
- lambda expressions or arrow syntax
- assignment or mutation forms
- chained calls without an explicit property/method receiver shape the parser already understands
- arithmetic and string-concatenation operators beyond unary `-`
- `+=`, `-=`, `*=`, `/=`, `%=` or similar compound operators
- semicolon-separated expressions
- selection, projection, stream-style, or collection-filter operators
- type references, static-member access, or constructor-like forms
- parser-invalid or unterminated EL containers
- any expression that mixes supported tokens into a shape the grammar above cannot parse unambiguously

## Inconclusive Handling Requirement

Unsupported EL must never be normalized as if it were equivalent. When the analyzer encounters an unsupported EL form in a relevant attribute or text node, it should:

1. attach a file-linked unsupported diagnostic to the exact EL occurrence when location fidelity allows
2. preserve enough snippet context to explain which EL text was rejected
3. mark the affected fact as unknown rather than matched
4. contribute to a final `INCONCLUSIVE` result unless another proven mismatch already yields `NOT_EQUIVALENT`

This rule applies equally to old-side and new-side EL and to mixed template values whose literal parts were otherwise parseable.
