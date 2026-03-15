# Execution Plan

Status: completed as the baseline MVP implementation. This document is now historical guidance for why the current architecture and delivery order look the way they do.

## Objective

Deliver a fast static verifier that answers one practical question for JSF 2.2 refactors:

"Did the refactored XHTML preserve the same effective scope and structure as the original, after include inlining?"

The fastest route to value is not a full JSF simulator. It is a deterministic static analyzer that is intentionally strict about the constructs most likely to break during include flattening.

## Delivery Principles

1. Optimize for refactor safety, not theoretical completeness.
2. Prefer explicit "inconclusive" outcomes over silent assumptions.
3. Preserve provenance at every stage so every mismatch can be traced back to a real file and include chain.
4. Keep the semantic model small enough to reason about and test.
5. Build the matcher around stable anchors first, then add broader heuristics only where needed.

## Recommended Technical Shape

### Language and Packaging

Use Kotlin with Gradle and a single CLI entrypoint.

Why this is the best fit:

- strong support for immutable domain models
- straightforward JVM packaging for enterprise environments
- good XML tooling and simple JSON serialization
- easy test setup for fixture-heavy analyzer work

### Analysis Pipeline

Use a six-part pipeline, but implement it as four independently testable layers:

1. `loader`
   - resolves root files, include chains, and provenance
   - performs static include expansion
   - records unsupported dynamic includes without losing the rest of the tree
2. `syntax`
   - parses expanded XHTML into a namespace-aware tree with source locations
   - preserves attributes relevant to JSF semantics
3. `semantic`
   - builds bindings, EL references, ancestry, ids, and target relationships
   - applies rule-registry behavior for known tags
4. `compare`
   - matches nodes
   - compares normalized facts
   - emits structured problems and summaries

This separation keeps the hardest moving parts isolated:

- include expansion bugs stay in `loader`
- source-location issues stay in `syntax`
- JSF semantics stay in `semantic`
- false-match behavior stays in `compare`

## What To Build First

### Phase 1: Trustworthy Input and Provenance

Build the file loader, include expander, and namespace-aware parser first.

Exit criteria:

- can load both trees from CLI paths
- can expand `ui:include` with `ui:param`
- can detect include cycles and missing files
- can preserve physical file location plus logical include stack

Without this layer, all later diagnostics will be too weak to trust.

### Phase 2: Scope-Safe EL Comparison

Build the minimum symbolic binding engine next.

Support:

- `ui:param`
- `ui:repeat var` and `varStatus`
- `c:set`
- `c:forEach`
- local-shadowing detection
- EL extraction from attributes and text nodes

Important design choice:

Do not attempt a full Jakarta EL implementation in the first pass. Parse only the syntax needed to preserve root references, property/index access, method-call roots, boolean structure, and ternary shape. Everything else should either normalize conservatively or be marked unsupported.

Use [docs/el-grammar-subset.md](docs/el-grammar-subset.md) as the source of truth for that boundary. If extracted EL falls outside that grammar, the semantic layer should emit an explicit unsupported diagnostic and let the final result derive `INCONCLUSIVE` rather than guessing.

This keeps the MVP tractable while still catching the most dangerous refactor regressions:

- alpha-renaming mistakes
- variable capture
- scope loss from include flattening

### Phase 3: Structural Equivalence

Add structural checks only after scope comparison is stable.

Support:

- component ids
- form ancestry
- naming-container ancestry
- iteration ancestry
- `rendered`
- `for`
- AJAX targets such as `update`, `render`, `process`, and `execute`

Important design choice:

Treat wrappers like `ui:composition`, `ui:include`, and transparent `ui:fragment` cases as non-semantic during matching. This prevents the tool from penalizing the exact refactor it is meant to validate.

### Phase 4: Reporting, JSON, and Hardening

Once the analyzer can already detect real mismatches, finalize the operator experience:

- text and JSON reporters
- stable problem ids and sorting
- capped problem counts
- CI-friendly exit codes
- golden-file fixtures for real-world regressions

## Matching Strategy

The comparison should not begin with generic tree diffing. That produces noisy and unstable results.

Use anchor-based matching in this order:

1. explicit ids on relevant components
2. explicit target relationships such as `for` and AJAX references
3. semantic signatures built from tag, ancestry, iteration context, and rendered guard
4. unmatched-node diagnostics when no trustworthy pairing exists

This makes the output more understandable and lowers the chance of one early mismatch cascading into dozens of misleading downstream errors.

## Rule Registry Design

Tag semantics should live in a registry, not in parser conditionals.

Each rule should answer questions like:

- does this tag introduce bindings?
- does it create a naming container?
- is it transparent for matching?
- which attributes contain EL?
- which attributes contain component-target references?

Why this matters:

- it keeps support for third-party component libraries incremental
- it allows project-specific tuning later without rewriting the analyzer core
- it creates a clean seam for tests

## Source Location Strategy

Line and column precision are core to usability. The parser must preserve:

- element location
- relevant attribute location where possible
- include stack provenance

If attribute-level positions are too expensive in the initial parser choice, the fallback should still point to the element plus attribute name. That is acceptable for MVP, but the limitation should be explicit in the spec and docs.

## Test Strategy

The project should be built around fixtures from the start.

Recommended test layers:

1. unit tests
   - include resolution
   - scope stack behavior
   - EL normalization
   - target resolution
2. semantic fixture tests
   - input XHTML tree -> expected semantic facts
3. comparison fixture tests
   - old tree + new tree -> equivalent / not equivalent / inconclusive
4. CLI golden tests
   - expected text output
   - expected JSON output

Fixture categories to create early:

- safe include inlining
- unsafe variable capture
- moved component outside form
- renamed iterator variable with preserved meaning
- lost `ui:param`
- unresolved dynamic include
- changed AJAX target

## Output Philosophy

The best version of this tool is opinionated and concise.

Desired operator experience:

- success output stays short
- failure output points to the smallest actionable mismatch
- unsupported constructs are prominent, never buried
- JSON output is deterministic enough for CI and editor tooling

## Risks To Manage Early

### XML Reality Risk

Real XHTML may be messy even if formally valid. Parser and loader code should be validated against representative project samples early, not just toy fixtures.

The repository's `dummy/` tree is now the first such representative sample. Use it to smoke-test loader, parser, provenance, and additional semantic work, while keeping canonical expected-outcome assertions in minimized fixtures under `fixtures/`.

### EL Complexity Risk

Trying to perfectly parse all EL forms too early will slow the project down. The safer path is a deliberately scoped symbolic parser plus explicit unsupported diagnostics.

### Third-Party Component Risk

Some component libraries behave like naming containers or iteration sources even when generic treatment would miss that. Start with a small built-in registry and add extension points before the rule set becomes large.

### Diagnostic Noise Risk

If matching is unstable, one structural drift can explode into many false mismatches. That is why anchor-first matching and deterministic suppression rules should be part of the design, not a later polish item.

## Suggested Repository Milestones

1. `docs/` foundation and clarified spec
2. Gradle Kotlin CLI skeleton
3. loader + provenance model
4. XHTML syntax tree with namespace support
5. scope and EL normalization engine
6. structural facts and comparator
7. text/JSON reporting
8. fixture corpus and CI

## Recommended Success Definition For MVP

The MVP is successful when a developer can run the tool against a legacy page and its inlined counterpart and get one of three trustworthy outcomes:

- equivalent
- not equivalent with precise actionable diagnostics
- inconclusive with clearly named unsupported constructs

That outcome is enough to unlock large-scale include-inlining work without waiting for a full JSF runtime simulator.
