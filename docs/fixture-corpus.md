# Fixture Corpus Plan

## Purpose

The implementation should be built around comparison fixtures from the beginning. Fixtures are the easiest way to prove that the tool is useful on real refactor scenarios rather than only on isolated parser mechanics.

## Goals

The fixture corpus should:

- model realistic JSF include-inlining refactors
- exercise both safe and unsafe changes
- make expected outcomes obvious to reviewers
- support unit, comparison, and CLI golden testing

## Recommended Layout

```text
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

## Fixture Contents

Each fixture directory should contain:

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
  "problemIds": ["P01"],
  "warningIds": [],
  "notes": "Variable capture introduced by flattening an inner ui:repeat."
}
```

The implementation can evolve the exact schema later, but the fixture contract should stay concise and deterministic.

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

## Fixture Review Standard

A good fixture should let a reviewer answer these questions quickly:

- what changed?
- should it be safe or unsafe?
- which exact semantic rule is being exercised?

If the answer is not obvious from the fixture layout and notes, the fixture needs improvement.
