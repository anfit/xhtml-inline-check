# Specification

## 1. Product Goal

Build a CLI tool that compares two JSF Facelets root XHTML pages and determines whether they are statically equivalent for the purpose of include-inlining refactors.

The tool answers a practical migration question:

"Did the refactored XHTML preserve the same effective scope and behaviorally relevant structure as the original page, under a static model?"

This project is intentionally a static verifier. It does not execute a JSF runtime, build a live component tree, or prove runtime equivalence.

## 2. Primary Use Case

Teams have legacy JSF 2.2 XHTML composed from nested includes and fragments. They want to inline those fragments to simplify ownership and enable larger UI overhauls, but they need a fast way to check whether a refactored page still means the same thing.

The tool compares:

- an "old" root XHTML, often include-heavy
- a "new" root XHTML, often partially or fully inlined

The output must be useful both during local refactoring and in CI.

## 3. Result States

The tool can return exactly three semantic outcomes:

1. `EQUIVALENT`
   - all checked facts match under the static model
   - no fatal unsupported constructs were encountered
2. `NOT_EQUIVALENT`
   - at least one checked fact mismatched
3. `INCONCLUSIVE`
   - no mismatch was proven, but unsupported constructs or parse limitations prevent a trustworthy equivalence claim

These states are central to the product. Unsupported constructs must never be silently treated as equivalent.

## 4. Scope

The verifier checks two classes of invariants.

### 4.1 Scope Equivalence

Every EL expression in the old and new trees must resolve local roots against equivalent symbolic bindings.

This must detect:

- iterator-variable capture
- lost `ui:param` scope
- accidental shadowing changes
- alpha-renaming that is safe and should be treated as equivalent

### 4.2 Structural Equivalence

Behaviorally relevant page structure must remain equivalent, especially:

- component ids
- form ancestry
- naming-container ancestry
- iteration ancestry
- rendered guards
- label/message targets such as `for`
- AJAX/process targets such as `update`, `render`, `process`, and `execute`

The tool targets JSF XHTML built from `ui:*`, JSTL, `h:*`, and common component-library tags.

## 5. Non-Goals

The initial product does not:

- evaluate managed beans, converters, validators, or action methods
- emulate JSF lifecycle phases
- render HTML or execute browser behavior
- infer semantics hidden in custom Java tag handlers
- guarantee equivalence when runtime-only behavior dominates the page

## 6. CLI Contract

Executable name:

`facelets-verify`

Primary usage:

`facelets-verify oldRoot.xhtml newRoot.xhtml`

Options:

- `--base-old <dir>` resolves includes relative to the old tree
- `--base-new <dir>` resolves includes relative to the new tree
- `--format text|json`
- `--verbose`
- `--fail-on-warning`
- `--max-problems <n>`
- `--explain <problem-id>`

Exit codes:

- `0` equivalent
- `1` not equivalent
- `2` inconclusive or analysis failure

### 6.1 Example Success Output

```text
EQUIVALENT
Static model says pages are equivalent
EL bindings matched: 146/146
Structural facts matched: 89/89
Warnings: 1
W-UNSUPPORTED-DYNAMIC_MAP_ACCESS Dynamic map access at newRoot.xhtml:88 may hide runtime differences
```

### 6.2 Example Failure Output

```text
NOT EQUIVALENT
Problems: 3

P-SCOPE-BINDING_MISMATCH Scope mismatch
oldRoot.xhtml:114:23 -> #{row.label}
newRoot.xhtml:97:19  -> #{item.label}
Root binding differs
Old: ui:repeat var=row from /fragments/table.xhtml:8
New: ui:repeat var=item from /newRoot.xhtml:72
Reason: expression moved from inner iteration context to outer iteration context

P-STRUCTURE-FORM_ANCESTRY_CHANGED Form ancestry changed
Component id=saveBtn
Old path: /ui:composition/h:form[1]/p:commandButton[2]
New path: /ui:composition/p:commandButton[2]
Reason: component is no longer inside a form

P-TARGET-RESOLUTION_CHANGED Update target no longer resolves the same way
oldRoot.xhtml:140: update="msgs panel"
newRoot.xhtml:125: update="panel"
Missing target: msgs
```

### 6.3 Example Inconclusive Output

```text
INCONCLUSIVE
Warnings: 1

W-UNSUPPORTED-DYNAMIC_INCLUDE Unsupported dynamic include
legacy/order.xhtml:31:17 -> src=#{bean.fragmentPath}
Comparison beneath this node is not trustworthy
```

## 7. Core Design Decisions

### 7.1 Comparison Happens On Semantic Models

The tool compares semantic facts, not raw text and not raw XML tree equality.

Each page is converted into an intermediate representation of semantic nodes. Each node records:

- physical source file and line/column
- logical include path
- AST path
- tag name and namespace
- relevant attributes
- binding introductions
- naming-container status
- form ancestry
- iteration ancestry
- relevant ids and target references
- normalized EL expressions attached to the node

### 7.2 Include Expansion Is Mandatory

Both sides are converted into fully expanded logical trees before semantic comparison.

Why this is required:

- the old tree may be fragmented across many includes
- the new tree may inline those fragments directly
- meaningful comparison depends on the same logical view of the page

### 7.3 Unsupported Constructs Must Surface Explicitly

Dynamic includes, unsupported EL syntax, or unknown tag semantics may reduce confidence. They must be reported as warnings or fatal limitations, not hidden behind optimistic assumptions.

### 7.4 Matching Must Be Anchor-First

Comparison must not begin as a generic tree diff. The matching strategy should prefer:

1. explicit component ids
2. explicit target relationships
3. semantic signatures built from tag, ancestry, iteration context, and rendered guard
4. unmatched-node diagnostics when no reliable pairing exists

This is a spec requirement because output stability is part of product quality.

## 8. Supported Semantics

### 8.1 MVP Support

The minimum viable product must support:

- `ui:include`
- `ui:param`
- `ui:composition`
- `ui:fragment`
- `ui:repeat`
- `c:set`
- `c:forEach`
- `c:if`
- `h:form`
- common JSF components with `id`, `rendered`, and `for`
- AJAX/process attributes such as `update`, `render`, `process`, and `execute`
- local-variable normalization in EL
- file-linked diagnostics with include provenance

### 8.2 Deferred Support

The first production-ready release may treat the following as secondary or registry-driven:

- `ui:decorate`
- third-party naming containers beyond the built-in rule set
- method-expression details beyond root/method shape
- advanced EL features that do not affect local-root binding

## 9. Include Expansion Model

Expansion algorithm requirements:

1. resolve each `ui:include src=...` statically
2. load included XHTML
3. inject `ui:param` bindings into the child scope
4. replace the include node with an include-boundary marker plus expanded child content
5. record provenance so diagnostics can refer to both physical file and logical include chain

If `src` is a dynamic EL expression, the analyzer must mark the subtree unsupported and return `INCONCLUSIVE` unless flags upgrade the warning to failure.

Cycle detection and missing-file handling are required for MVP.

## 10. Scope Model

The scope model is a stack of bindings active at each AST location.

Each binding records:

- binding id
- written name
- kind such as `ui:param`, `ui:repeat var`, `varStatus`, `c:set`, `c:forEach`, or implicit root
- source location
- parent scope id
- symbolic origin descriptor

When walking the tree:

- entering a node may push bindings
- leaving a node pops them
- every EL root reference is resolved against the active stack

Global bean names remain symbolic global roots unless shadowed by local bindings.

## 11. EL Normalization

The tool does not evaluate EL. It performs syntax-aware symbolic normalization.

For each EL occurrence, the analyzer must preserve enough structure to compare meaningfully:

- root identifiers
- property chains
- index access
- method-call roots
- boolean and ternary shape when needed to preserve referenced roots

Local bindings are normalized to canonical ids so alpha-renaming remains equivalent.

Examples:

`#{row.name}` and `#{item.name}` are equivalent if both normalize to:

`binding#12.property(name)`

`#{bean.map[row.code]}` may normalize to:

`global(bean).map[index(binding#12.code)]`

### 11.1 MVP EL Grammar Subset

The MVP EL parser is intentionally a subset parser, not a full Jakarta EL implementation. Its exact contract is defined in [docs/el-grammar-subset.md](docs/el-grammar-subset.md).

The supported subset must cover the forms required by scope comparison, semantic extraction, and structural checks:

- deferred and immediate EL containers embedded in literal templates
- root identifiers
- dotted property chains
- bracket index access
- method-call shape with recursively parsed arguments
- boolean and emptiness guards
- equality and relational operators
- ternary expressions
- parentheses and scalar literals needed to preserve expression shape

Within that subset, the analyzer must preserve symbolic structure rather than evaluate runtime values. Unsupported EL must never be treated as equivalent by fallback.

The following EL forms are explicitly out of MVP scope and must contribute to explicit unsupported handling:

- namespaced EL functions such as `fn:length(...)`
- collection or map literals
- lambda expressions
- assignment or mutation forms
- arithmetic or concatenation operators beyond unary minus
- semicolon-separated expressions
- selection, projection, or other advanced collection operators
- type or static-member references
- parser-invalid or unterminated EL containers

If one of those unsupported forms appears in an extracted EL occurrence relevant to scope, semantic extraction, or structural comparison, the analyzer must surface a file-linked unsupported diagnostic and treat the affected comparison fact as unknown, contributing to `INCONCLUSIVE` unless a mismatch was already proven.

### 11.2 Attribute-Level Precision

Attribute-level line and column should be captured where practical. If the initial parser can only report the owning element location plus attribute name, that fallback is acceptable for MVP, but the limitation must be explicit in diagnostics and documentation.

## 12. Structural Model

For each relevant semantic node, compute:

- semantic signature
- effective form ancestry
- effective naming-container ancestry
- effective iteration ancestry
- normalized rendered guard
- outgoing target references

Transparent wrappers must not create false mismatches. At minimum, `ui:include`, `ui:composition`, and transparent `ui:fragment` cases are treated as non-semantic for matching.

The comparator is strict about:

- id collisions
- changes in form ancestry
- changes in naming-container ancestry
- changes in iteration ancestry
- loss or gain of rendered guards
- changes in message/AJAX target resolution

## 13. Rule Registry

Known tag semantics must live in a rule registry rather than hardcoded parser branches.

Each rule answers questions such as:

- does this tag introduce bindings?
- does it create a naming container?
- is it transparent during matching?
- which attributes contain EL?
- which attributes contain target references?

This enables safe incremental support for third-party component libraries.

## 14. Comparison Algorithm

Comparison runs in three passes:

1. match semantic nodes using anchors and ancestry constraints
2. compare EL usages at matched nodes after normalization
3. verify global invariants such as ids, targets, and ancestry consistency

Possible outcomes for each fact:

- match
- mismatch
- unknown due to unsupported construct

Final result rules:

- `EQUIVALENT` if all checked facts match and no fatal unknowns exist
- `NOT_EQUIVALENT` if any mismatch exists
- `INCONCLUSIVE` if no mismatch exists but unsupported constructs prevent confidence

The tool should suppress duplicate cascade problems where a single upstream mismatch already explains a group of downstream symptoms.

## 15. Problem Model

Every issue must be emitted as a structured problem with:

- problem id
- severity: `error` or `warning`
- category: `scope`, `structure`, `target`, `parse`, `unsupported`
- summary
- old location, if any
- new location, if any
- old snippet, if any
- new snippet, if any
- semantic explanation
- remediation hint

Problem-id and warning-id convention:

- ids identify stable diagnostic kinds, not per-run ordinal positions
- error ids use `P-<CATEGORY>-<SLUG>`
- warning ids use `W-<CATEGORY>-<SLUG>`
- `<CATEGORY>` is one of the structured problem categories such as `SCOPE`, `STRUCTURE`, `TARGET`, `PARSE`, `UNSUPPORTED`, or `INTERNAL`
- `<SLUG>` is uppercase ASCII snake case naming the rule family, for example `BINDING_MISMATCH` or `DYNAMIC_INCLUDE`
- `--explain <problem-id>`, JSON output, and fixture assertions should key off this stable id string directly

Example:

```text
P-SCOPE-BINDING_MISMATCH
severity=error
category=scope
summary=Local variable resolves to different binding
old=/fragments/grid.xhtml:22:15
new=/pages/order.xhtml:88:11
oldSnippet=#{row.total}
newSnippet=#{row.total}
explanation=The name row referred to inner ui:repeat before refactor and now refers to outer ui:repeat
hint=Preserve the inner iteration scope or rename the outer var to avoid capture
```

## 16. Output Formats

### 16.1 Text Output

Text output is concise and human-focused.

Requirements:

- success output stays compact
- failure output leads with the most actionable mismatch
- warnings about unsupported constructs are always visible
- ordering is deterministic

### 16.2 JSON Output

JSON output is machine-consumable and must include:

- result
- summary
- problems
- warnings
- stats
- provenance metadata needed for editor or CI navigation

Recommended top-level shape:

```json
{
  "result": "NOT_EQUIVALENT",
  "summary": {},
  "problems": [],
  "warnings": [],
  "stats": {}
}
```

## 17. Internal Modules

Recommended module boundaries:

- `cli`
- `loader`
- `syntax`
- `scope`
- `el`
- `semantic`
- `compare`
- `report`

The implementation may collapse or split these packages, but the responsibilities must remain clearly separated.

## 18. Implementation Plan

The recommended implementation sequence is part of the spec because delivery order affects correctness.

### Phase 1: Input And Provenance

Build:

- CLI shell
- loader
- include expansion
- provenance model
- namespace-aware XHTML parser

### Phase 2: Scope And EL

Build:

- binding stack
- EL extraction
- symbolic EL parser/normalizer
- scope-based mismatch reporting

### Phase 3: Structural Checks

Build:

- ancestry tracking
- id and target resolution
- structural comparator

### Phase 4: Reporting And Hardening

Build:

- text reporter
- JSON reporter
- problem explanation mode
- deterministic sorting and suppression
- CI-oriented fixture coverage

## 19. Test Strategy

The project should be fixture-driven from the start.

Required test layers:

1. unit tests for loader, scope, EL normalization, and target resolution
2. semantic fixture tests for extracted facts
3. comparison fixture tests for equivalent, non-equivalent, and inconclusive outcomes
4. CLI golden tests for text and JSON output

Minimum fixture categories:

- safe include inlining
- safe variable alpha-renaming
- unsafe variable capture
- lost `ui:param`
- moved component outside form
- changed naming-container ancestry
- changed AJAX target
- unresolved dynamic include

## 20. Risks And Limitations

- false negatives are possible when custom tags or runtime behavior alter semantics outside XHTML
- false positives are possible when third-party components are treated too generically
- dynamic includes and advanced EL forms must be reported as unsupported, never assumed safe
- parser location fidelity may vary depending on XML tooling, especially for attributes

## 21. Acceptance Criteria

The tool is acceptable when:

- it runs from CLI with two root XHTML paths
- it resolves includes and tracks provenance
- it reports `EQUIVALENT` for known-safe include-inlining refactors
- it reports clear file-linked failures for known-unsafe scope and structure changes
- it reports `INCONCLUSIVE` for unsupported constructs that prevent confidence
- it emits both human-readable and JSON output
- it exits with stable codes suitable for CI

## 22. Example Invocation

```text
facelets-verify legacy/order.xhtml refactored/order.xhtml --base-old legacy --base-new refactored --format text --verbose
```

## 23. Recommendation

Build the MVP as a strict static checker for include expansion, scope binding, and structural ancestry. That gives the best speed-to-value ratio for subpage inlining work. Expand the rule registry only when real project samples reveal gaps, not before.
