# Fixtures

This directory holds the shared comparison corpus used by analyzer, reporter, and CLI-facing tests.

The layout and fixture conventions are documented in [docs/fixture-corpus.md](../docs/fixture-corpus.md).

Current first-pass canonical fixtures:

- `fixtures/equivalent/safe-include-inline/`
- `fixtures/equivalent/safe-alpha-renaming/`
- `fixtures/not-equivalent/lost-ui-param/`
- `fixtures/not-equivalent/variable-capture-regression/`
- `fixtures/not-equivalent/form-ancestry-drift/`
- `fixtures/not-equivalent/changed-for-target/`
- `fixtures/not-equivalent/changed-ajax-target/`
- `fixtures/inconclusive/dynamic-include/`
- `fixtures/inconclusive/inconclusive-but-not-proven-wrong/`

Subtle scenarios may include `notes.md` to explain why the expected verdict is stable.

`fixtures/support/` is reserved for reusable smoke inputs that baseline JUnit helpers and early pipeline tests can share before full comparison fixtures land.

The repository-wide realistic sample lives separately under [`dummy/`](../dummy/). Use that tree as a source for carving future canonical fixtures and for smoke-style parser/analyzer coverage, but do not treat it as a classified expected-outcome fixture until the original/refactored pair has been reviewed.
