1. Purpose

Build a CLI tool that compares two JSF Facelets root XHTML pages and determines whether they are statically equivalent with respect to EL scope and page structure after refactoring, especially include inlining.

The tool does not execute JSF lifecycle or browser rendering. It performs fast static verification and returns either:

success, with a compact summary of why the pages are equivalent under the static model

or failure, with precise, navigable diagnostics tied to concrete source locations and semantic mismatches

2. Scope

The tool verifies two things.

First, EL scope equivalence. Every EL expression in the old and new page must resolve its local roots against equivalent symbolic bindings.

Second, structural equivalence. Behaviorally relevant structure must match, including forms, naming-container boundaries, ids, iteration context, rendered guards, and target references such as `for`, `render`, `update`, `process`, and `execute`.

The tool is intended for JSF XHTML built from `ui:*`, JSTL, `h:*`, and component-library tags.

3. Non-goals

It does not prove runtime equivalence.

It does not evaluate managed beans, converters, validators, custom EL resolvers, or action methods.

It does not run a server, instantiate a component tree, or execute regression tests.

It does not guarantee equivalence when pages depend on dynamic includes, custom Java tag handlers, or runtime behavior hidden from the XHTML.

4. CLI contract

Executable name:

`facelets-verify`

Primary usage:

`facelets-verify oldRoot.xhtml newRoot.xhtml`

Optional flags:

`--base-old <dir>` to resolve includes relative to the old tree

`--base-new <dir>` to resolve includes relative to the new tree

`--format text|json`

`--verbose`

`--fail-on-warning`

`--max-problems <n>`

`--explain <problem-id>` to print deep detail for a previously reported issue

Exit codes:

`0` equivalent under static model

`1` not equivalent

`2` analysis incomplete due to unsupported dynamic constructs or parse failure

Example success output:

`EQUIVALENT`
`Expressions compared: 146`
`Resolved bindings matched: 146`
`Structural nodes matched: 89`
`Warnings: 1`
`Warning W12: dynamic map access at newRoot.xhtml:88 may hide runtime differences`

Example failure output:

`NOT EQUIVALENT`
`Problems: 3`

`P01 Scope mismatch`
`oldRoot.xhtml:114:23 -> #{row.label}`
`newRoot.xhtml:97:19  -> #{item.label}`
`Root binding differs`
`Old: ui:repeat var=row from /fragments/table.xhtml:8`
`New: ui:repeat var=item from /newRoot.xhtml:72`
`Reason: expression moved from inner iteration context to outer iteration context`

`P02 Form ancestry changed`
`Component id=saveBtn`
`Old path: /ui:composition/h:form[1]/p:commandButton[2]`
`New path: /ui:composition/p:commandButton[2]`
`Reason: component is no longer inside a form`

`P03 Update target no longer resolves the same way`
`oldRoot.xhtml:140: update="msgs panel"`
`newRoot.xhtml:125: update="panel"`
`Missing target: msgs`

5. High-level architecture

The tool has six stages.

Stage one reads and expands both root XHTMLs into a normalized source graph.

Stage two parses XHTML into a namespace-aware AST.

Stage three walks the AST and builds a symbolic scope model.

Stage four extracts a semantic model containing normalized EL usages and structural facts.

Stage five compares the two semantic models.

Stage six renders a result, either compact success or verbose failure with source-linked problems.

6. Core design

The comparison is based on semantic normalization, not raw text or raw DOM equality.

Each page is converted into an intermediate representation made of semantic nodes. A semantic node records:

source file and line/column

AST path

tag name and namespace

relevant attributes

whether it introduces variables

whether it is a naming container

current form ancestry

current iteration ancestry

id, `for`, `rendered`, `update`, `render`, `process`, `execute`

normalized EL expressions attached to the node

Each EL expression is parsed into a symbolic representation. The key normalization step replaces local variable names with canonical binding ids so that alpha-renaming remains equivalent.

Example:

Old `#{row.name}` and new `#{item.name}` are equal if both normalize to something like:

`IterBinding[#12].property(name)`

7. Supported constructs

Initial version should support:

`ui:include`

`ui:param`

`ui:composition`

`ui:decorate`

`ui:fragment`

`ui:repeat`

`c:set`

`c:forEach`

`c:if`

`h:form`

common JSF components with `id`, `rendered`, `for`

common AJAX attributes such as `update`, `render`, `process`, `execute`

component-library tags can be treated generically unless they create known naming containers or iteration scopes

Known tag semantics are modeled in a rule registry.

8. Include expansion model

The old tree may consist of many includes while the new tree may inline them. To compare fairly, both sides are converted into a fully expanded logical tree.

Expansion algorithm:

Resolve each `ui:include src=...` statically

Load included XHTML

Inject `ui:param` bindings into the child scope

Replace include node with an include boundary marker plus expanded child content

Record provenance so diagnostics can refer back to original files and include chains

If include `src` is dynamic EL, mark analysis incomplete for that subtree and emit a warning or failure depending on flags.

9. Scope model

The scope model is a stack of bindings active at each AST location.

Each binding has:

binding id

name as written

kind, such as `ui:param`, `ui:repeat var`, `varStatus`, `c:set`, `c:forEach`, implicit root

source location

parent scope id

symbolic origin descriptor

When the walker enters a node, it pushes new bindings introduced by that node. When leaving, it pops them.

For every EL expression, the tool resolves each root identifier against the current scope stack and records which binding it maps to.

Global bean names are treated as unresolved-global roots unless a local binding shadows them.

10. EL normalization

The tool need not evaluate EL. It only needs syntax-aware symbolic parsing.

For each EL occurrence, extract:

literal fragments

root identifiers

property chains

method-call roots

ternary and boolean structure only insofar as needed to preserve referenced roots

Normalize local references to canonical binding ids.

Examples:

`#{row.visible ? row.label : 'x'}` becomes something like
`ternary(binding#12.visible, binding#12.label, literal)`

`#{bean.map[row.code]}` becomes
`global(bean).map[index(binding#12.code)]`

This makes scope-sensitive changes visible while preserving enough structure to compare expressions meaningfully.

11. Structural model

For each relevant node, compute:

semantic signature

effective ancestry through forms and naming containers

iteration context

normalized rendered guard

target references

The comparator treats some tags as transparent wrappers, especially `ui:include`, `ui:composition`, and some `ui:fragment` cases, so include inlining does not create false mismatches.

The comparator is strict about:

id collisions

change in form ancestry

change in naming-container ancestry

change in iteration ancestry

loss or gain of rendered guards

change in message or AJAX targets

12. Comparison algorithm

Comparison runs in three passes.

Pass one matches semantic nodes between old and new trees using signatures and ancestry constraints.

Pass two compares EL usages at matched nodes after normalization.

Pass three checks global invariants, such as ids, target references, and ancestry consistency.

Possible outcomes for each compared fact are:

match

mismatch

unknown due to unsupported construct

The final result is:

equivalent if all compared facts match and no fatal unknowns exist

not equivalent if any mismatch exists

inconclusive if no mismatch exists but unsupported dynamic constructs prevent trustable comparison

13. Problem model

Every detected issue is emitted as a structured problem.

Fields:

problem id

severity: error or warning

category: scope, structure, target, parse, unsupported

summary

old location, if any

new location, if any

old snippet, if any

new snippet, if any

semantic explanation

remediation hint

example:

`P17`
`severity=error`
`category=scope`
`summary=Local variable resolves to different binding`
`old=/fragments/grid.xhtml:22:15`
`new=/pages/order.xhtml:88:11`
`oldSnippet=#{row.total}`
`newSnippet=#{row.total}`
`explanation=The name row referred to inner ui:repeat before refactor and now refers to outer ui:repeat`
`hint=Preserve the inner iteration scope or rename the outer var to avoid capture`

14. Output formats

Text format is human-focused and must be concise by default.

JSON format is machine-consumable and includes all problem metadata, source paths, include provenance, and normalized semantic excerpts.

Recommended JSON top-level structure:

`result`
`summary`
`problems`
`warnings`
`stats`

This enables IDE integrations, CI annotations, and editor navigation.

15. Linking to specific problems

Every problem must point to exact file, line, and column where possible.

To support that, the parser must preserve location metadata from the original source and include-expanded source graph.

When an issue originates inside an included fragment, the diagnostic should show both the physical file location and the logical include path, for example:

`/pages/order.xhtml -> /fragments/table.xhtml:22:15`

That gives immediate navigability even after include expansion.

16. Recommended implementation

Use Kotlin on the JVM.

Reasons:

easy CLI packaging

strong XML and EL ecosystem alignment

good data classes and sealed types for AST and diagnostics

simple integration with Jackson for JSON output

Suggested libraries:

Picocli for CLI

standard namespace-aware XML parser or Woodstox

a small internal EL parser, or Jakarta EL APIs where useful for syntax compatibility

Jackson for JSON output

JUnit for the tool’s own test suite

17. Internal modules

`cli`
argument parsing and exit codes

`loader`
file reading, include resolution, provenance tracking

`xml`
namespace-aware parsing and source location capture

`scope`
binding stack and scope resolution

`el`
EL tokenization, parsing, normalization

`semantic`
semantic node construction

`compare`
matching and mismatch detection

`report`
text and JSON rendering

18. Minimum viable product

The first version should support enough to validate the main refactor class.

That means:

static include expansion

`ui:param`

`ui:repeat`

`c:set`

`c:forEach`

`h:form`

ids

`rendered`

`for`

AJAX target attributes

local variable normalization in EL

verbose file-linked diagnostics

This already catches the most common inlining failures.

19. Risks and limitations

False negatives can occur when custom tags or runtime behavior alter semantics outside XHTML.

False positives can occur if third-party components are treated too generically and actually have transparent behavior.

Dynamic include paths, method expressions with nontrivial semantics, and custom EL resolvers should be reported as unsupported or uncertain, never silently assumed safe.

20. Acceptance criteria

The tool is acceptable when:

it runs from CLI with two root XHTML paths

it resolves includes and tracks provenance

it reports equivalence for known-safe include-inlining refactors

it reports clear, file-linked failures for known-unsafe scope and structure changes

it emits both human-readable and JSON output

it exits with stable codes suitable for CI

21. Example invocation

`facelets-verify legacy/order.xhtml refactored/order.xhtml --base-old legacy --base-new refactored --format text --verbose`

22. Example success contract

`EQUIVALENT`
`Static model says pages are equivalent`
`EL bindings matched: 93/93`
`Structural facts matched: 57/57`
`Warnings: 0`

23. Example failure contract

`NOT EQUIVALENT`
`3 errors, 1 warning`

`P04 Scope mismatch at legacy/fragments/row.xhtml:18:9`
`Old expression #{row.status}`
`New expression #{row.status}`
`Different binding origins`
`Old origin: ui:repeat at legacy/fragments/table.xhtml:6`
`New origin: ui:repeat at refactored/order.xhtml:44`

`P09 Component moved outside form at refactored/order.xhtml:77:5`
`id=submitBtn`
`Old form ancestry: mainForm`
`New form ancestry: none`

`W02 Unsupported dynamic include at legacy/order.xhtml:31:17`
`src=#{bean.fragmentPath}`
`Comparison beneath this node is inconclusive`

24. Recommendation

Build the MVP first as a strict static checker for include expansion, scope binding, and structural ancestry. That gives the best speed-to-value ratio and directly addresses the risky parts of subpage inlining. After that, add tag-library-specific rules only where real projects show gaps.

If you want, I can next turn this into a concrete implementation plan with class names, interfaces, and a sample JSON schema.
