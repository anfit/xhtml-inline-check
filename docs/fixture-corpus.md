# Fixture Corpus

## Purpose

The implementation is built around comparison fixtures. Fixtures are the main way this repository proves that the tool is useful on real refactor scenarios rather than only on isolated parser mechanics.

## Goals

The fixture corpus is intended to:

- model realistic JSF include-inlining refactors
- exercise both safe and unsafe changes
- make expected outcomes obvious to reviewers
- support unit, comparison, and CLI golden testing

The repository already includes one realistic sample tree under `dummy/`. Treat that directory as a reference corpus for carving out future fixtures and smoke tests, not as a canonical expected-outcome fixture on its own. `dummy/old/report.xhtml` is the original page and `dummy/new/report-flattened.xhtml` is a refactored counterpart whose semantic equivalence has not been verified yet.

## Recommended Layout

```text
dummy/
  report.xhtml
  report-flattened.xhtml
  ...
fixtures/
  equivalent/
    safe-include-inline/
      old/
        root.xhtml
        fragments/
      new/
        root.xhtml
      expected.json
  not-equivalent/
    variable-capture/
      old/
      new/
      expected.json
  inconclusive/
    dynamic-include/
      old/
      new/
      expected.json
```

Usage split:

- keep `dummy/` intact as a representative sample page tree
- derive minimized canonical fixtures under `fixtures/` from focused slices of that sample when a semantic rule needs a deterministic expected outcome
- keep any direct tests over `dummy/` at smoke or parser/analyzer-coverage level unless and until the pair has been manually classified

## Fixture Contents

Each fixture directory contains:

- `old/` with the legacy tree
- `new/` with the refactored tree
- `expected.json` with the expected result contract

Optional additions:

- `notes.md` for human explanation when the scenario is subtle
- `expected.txt` for reporter golden output if the case is used by CLI tests

## Suggested `expected.json` Shape

```json
{
  "result": "NOT_EQUIVALENT",
  "problemIds": ["P-SCOPE-BINDING_MISMATCH"],
  "warningIds": [],
  "notes": "Variable capture introduced by flattening an inner ui:repeat."
}
```

Id convention requirements for fixtures:

- `problemIds` and `warningIds` should store stable diagnostic-kind ids, not report-local ordinals
- error ids use `P-<CATEGORY>-<SLUG>`
- warning ids use `W-<CATEGORY>-<SLUG>`
- `<SLUG>` should stay stable even if renderer ordering, wording, or counts change

The fixture contract should stay concise, deterministic, and keyed on those stable ids.

## Minimum Fixture Categories

- safe include inlining with unchanged semantics
- safe alpha-renaming of iterator variables
- variable capture after inlining
- lost `ui:param` after removing an include boundary
- component moved outside `h:form`
- changed naming-container ancestry
- changed `for` target resolution
- changed AJAX target resolution
- unsupported dynamic include

Current repository status:

- the first-pass canonical corpus now covers safe include inlining, safe alpha-renaming, lost `ui:param`, variable capture, form ancestry drift, naming-container ancestry drift, changed `for` target resolution, changed AJAX target resolution, and inconclusive unsupported cases under `fixtures/`
- the dedicated naming-container ancestry fixture uses `h:dataTable` as a non-form built-in naming container so the corpus can prove `P-STRUCTURE-NAMING_CONTAINER_ANCESTRY_CHANGED` independently of `h:form` drift

## Realistic Sample Baseline

Use the checked-in `dummy/` report page as the realism baseline when:

- validating that loader and parser changes still handle include-heavy pages with many shared fragments
- choosing additional canonical fixture scenarios for scope, ancestry, and target-resolution work
- reviewing whether a new rule or diagnostic still matches the kind of XHTML the project is meant to analyze

The `dummy/` pair is still intentionally not part of the canonical expected-outcome corpus. Do not place it under `fixtures/equivalent/` or `fixtures/not-equivalent/`, and do not assign it an `expected.json` contract that would imply a trusted semantic verdict.

## Fixture Review Standard

A good fixture should let a reviewer answer these questions quickly:

- what changed?
- should it be safe or unsafe?
- which exact semantic rule is being exercised?

If the answer is not obvious from the fixture layout and notes, the fixture needs improvement.
