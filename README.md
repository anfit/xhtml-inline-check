# xhtml-inline-check

`xhtml-inline-check` is a CLI tool for verifying whether a refactored JSF Facelets XHTML tree remains statically equivalent to the original after include inlining.

The project exists to support a common modernization step in legacy JSF 2.2 codebases: flattening `ui:include`-heavy page trees into more self-contained XHTML without accidentally changing EL scope, form ancestry, naming-container behavior, or target resolution.

## Capabilities

- `facelets-verify` CLI with text and JSON output
- static `ui:include` discovery and expansion with provenance
- semantic extraction for bindings, normalized EL facts, ancestry, ids, rendered guards, and target-bearing attributes
- structural comparison for scope drift, ancestry drift, target-resolution drift, duplicate ids, unmatched nodes, and explicit unsupported cases
- deterministic diagnostics, `--max-problems`, `--fail-on-warning`, and `--explain`
- fixture-backed comparison coverage under `fixtures/equivalent/`, `fixtures/not-equivalent/`, `fixtures/inconclusive/`, and `fixtures/support/`

## Limits

- static analysis only; no JSF runtime execution
- unsupported constructs remain explicit and can lead to `INCONCLUSIVE`
- project-specific tag semantics may require execution-root configuration

## Outcomes

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

## CLI

```text
facelets-verify legacy/order.xhtml refactored/order.xhtml --base-old legacy --base-new refactored --format text --verbose
```

## Configuration

Tag semantics are configuration-driven.

- The application ships with bundled defaults for Facelets, JSTL, and core JSF rules.
- If a file named `.xhtml-inline-check.json` exists in the execution root, its rules are merged on top.
- This repository keeps its project-specific schema handling in that root config file.

## Documentation Map

- [Specification](SPEC.md)
- [Architecture Overview](docs/architecture.md)
- [Build, Run, And Release](docs/build-run-release.md)
- [EL Grammar Subset](docs/el-grammar-subset.md)
- [Fixture Corpus](docs/fixture-corpus.md)
- [Release Readiness](docs/release-readiness.md)
- [Contributing Guide](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)

## Repository Shape

The repository is organized around the implemented analyzer pipeline and fixture corpus:

```text
docs/         product and engineering documentation
dummy/        realistic sample XHTML tree used to shape fixtures and smoke coverage
fixtures/     comparison fixtures used by tests
src/          Kotlin source sets for the CLI and analyzer
build.gradle.kts
settings.gradle.kts
```

## Build And Run

The Gradle application setup is configured around the `facelets-verify` entrypoint. With a system Gradle installation:

- compile and test with `gradle test`
- run the CLI with `gradle runFaceletsVerify --args="legacy.xhtml refactored.xhtml"`
- assemble the runnable distribution in `build/facelets-verify/` with `gradle installDist`
- assemble distribution archives with `gradle distZip distTar`

A Gradle wrapper has not been generated yet, so repository verification currently assumes a system Gradle installation.

For a repository-level baseline verification run:

- on Unix-like shells, run `scripts/verify-baseline.bash`
- on Windows PowerShell, run `scripts/verify-baseline.ps1`

Both scripts execute `gradle test`, `gradle installDist`, and `gradle runFaceletsVerify` against `fixtures/support/smoke/`.

For deterministic-output verification:

- on Unix-like shells, run `scripts/verify-deterministic-output.bash`
- on Windows PowerShell, run `scripts/verify-deterministic-output.ps1`

On Windows, the PowerShell helpers pin `GRADLE_USER_HOME`, `TEMP`, and `TMP` inside the repository before invoking Gradle. That avoids failures caused by inaccessible profile or temporary directories in restricted environments.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
