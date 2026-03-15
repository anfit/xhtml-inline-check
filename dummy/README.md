# Dummy Sample

This directory contains a realistic sample XHTML page tree used for smoke coverage and for carving out future fixtures.

- `old/report.xhtml` is the original include-heavy page.
- `new/report-flattened.xhtml` is a refactored page intended to represent an inlining/flattening pass.
- The pair is intentionally unverified: `report-flattened.xhtml` may still be equivalent to the original, or it may contain refactor-introduced mistakes.

Recommended invocation:

`gradle runFaceletsVerify --args="old/report.xhtml new/report-flattened.xhtml --base-old dummy --base-new dummy"`

Usage guidance:

- keep this tree intact as a representative sample of the XHTML shape the analyzer is expected to handle
- derive minimized canonical fixtures under `fixtures/` from focused excerpts of this sample when specific semantic rules need deterministic expected outcomes
- use direct tests over `dummy/` only for smoke-level loader/parser/analyzer coverage unless the pair is later reviewed and classified
