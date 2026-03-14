# Fixtures

This directory will hold the comparison corpus used by analyzer tests.

The planned layout and fixture conventions are documented in [docs/fixture-corpus.md](../docs/fixture-corpus.md).

`fixtures/support/` is reserved for reusable smoke inputs that baseline JUnit helpers and early pipeline tests can share before full comparison fixtures land.

The repository-wide realistic sample lives separately under [`dummy/`](../dummy/). Use that tree as a source for carving future canonical fixtures and for smoke-style parser/analyzer coverage, but do not treat it as a classified expected-outcome fixture until the original/refactored pair has been reviewed.
