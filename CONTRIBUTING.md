# Contributing

## Working Principles

This project should stay deliberately strict, deterministic, and fixture-driven.

When contributing, optimize for:

- trustworthy refactor diagnostics over broad but fuzzy support
- explicit unsupported outcomes over silent guesses
- stable, repeatable output that works well in CI
- source provenance that makes every finding navigable

## Before Writing Code

Start by aligning with the current planning docs:

- [Specification](SPEC.md)
- [Execution Plan](docs/execution-plan.md)
- [Architecture Overview](docs/architecture.md)
- [Fixture Corpus Plan](docs/fixture-corpus.md)

If a code change materially changes behavior, update the relevant documentation in the same change.

## Development Expectations

- Prefer small, focused modules with clear ownership boundaries.
- Keep loader, syntax, semantic, comparison, and reporting logic separated.
- Avoid fallback behavior that hides uncertainty.
- Treat unsupported constructs as first-class diagnostics.
- Preserve deterministic ordering in reports and tests.

## Testing Expectations

Every behavior change should be covered by at least one of these:

- a unit test
- a semantic fixture test
- a comparison fixture test
- a CLI golden test

The default testing style for end-to-end behavior should be fixture-first.

## Fixture Expectations

Fixtures should model real refactor situations, not just parser trivia.

Prioritize cases such as:

- safe include inlining
- safe iterator renaming
- variable capture regressions
- lost `ui:param` bindings
- form ancestry drift
- target-resolution drift
- unsupported dynamic includes

## Commit Style

Keep commits narrow and reviewable. Good patterns for this repository:

- `docs: refine specification for delivery`
- `build: add Kotlin CLI skeleton`
- `feat: add ui:include expansion`
- `test: add variable capture fixtures`

## Pull Request Quality Bar

Before asking for review, make sure:

- docs match behavior
- tests demonstrate the intended outcome
- output remains deterministic
- unsupported cases are still explicit
