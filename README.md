# xhtml-inline-check

`xhtml-inline-check` is a planned CLI tool for verifying whether a refactored JSF Facelets XHTML tree remains statically equivalent to the original after include inlining.

The project exists to support a common modernization step in legacy JSF 2.2 codebases: flattening `ui:include`-heavy page trees into more self-contained XHTML without accidentally changing EL scope, form ancestry, naming-container behavior, or target resolution.

## Status

This repository is currently in specification and project-groundwork mode.

What exists today:

- the product specification
- an execution plan
- architecture and fixture-planning docs
- repository conventions for future implementation

What does not exist yet:

- a build
- parser and analyzer code
- fixture corpus contents
- CI automation

## Planned Outcome

The tool will compare two root XHTML pages and return one of three outcomes:

- `EQUIVALENT`
- `NOT_EQUIVALENT`
- `INCONCLUSIVE`

The check is intentionally static. It will not run JSF lifecycle phases, instantiate managed beans, or render a browser view.

## What The MVP Will Check

- static `ui:include` expansion with provenance
- `ui:param`, `ui:repeat`, `c:set`, and `c:forEach` scope behavior
- EL root binding normalization so safe alpha-renames remain equivalent
- form ancestry and naming-container ancestry
- ids, `rendered`, `for`, and common AJAX/process targets
- concise text output plus machine-readable JSON output

## Why This Approach

The goal is not perfect JSF emulation. The goal is a fast, trustworthy refactor guardrail that catches the failures most likely to happen while flattening heavily componentized XHTML trees.

That means the product is designed around:

- explicit provenance
- symbolic scope comparison
- anchor-first structural matching
- visible unsupported cases instead of silent assumptions

## Planned CLI

```text
facelets-verify legacy/order.xhtml refactored/order.xhtml --base-old legacy --base-new refactored --format text --verbose
```

## Documentation Map

- [Specification](SPEC.md)
- [Execution Plan](docs/execution-plan.md)
- [Architecture Overview](docs/architecture.md)
- [Fixture Corpus Plan](docs/fixture-corpus.md)
- [Contributing Guide](CONTRIBUTING.md)

## Expected Repository Shape

The repo is being prepared for a fixture-driven Kotlin CLI implementation. The intended top-level layout is:

```text
docs/         product and engineering documentation
fixtures/     comparison fixtures used by tests
src/          future Kotlin source sets
```

## Near-Term Roadmap

1. Bootstrap the Kotlin/Gradle CLI shell.
2. Implement loader, include expansion, and provenance tracking.
3. Implement scope resolution and symbolic EL normalization.
4. Implement structural comparison and reporters.
5. Build out the fixture corpus and CI coverage.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
