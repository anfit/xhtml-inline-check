# Release Readiness

This document is the repository's practical release-prep guide for the first MVP release.

It focuses on four gates that already exist in the codebase and should stay aligned:

- fixture-backed analyzer behavior
- integration and entrypoint coverage
- deterministic reporter output
- distributable CLI packaging

## Packaging Instructions

This repository currently uses a system Gradle installation. A Gradle wrapper is not checked in yet.

Build the runnable distribution:

```text
gradle installDist
```

Expected output:

- launcher directory: `build/facelets-verify/`
- launchers: `build/facelets-verify/bin/facelets-verify` and platform-specific companion scripts
- runtime libs: `build/facelets-verify/lib/`

Build release archives:

```text
gradle distZip distTar
```

Expected output:

- `build/distributions/facelets-verify-<version>.zip`
- `build/distributions/facelets-verify-<version>.tar`

Build the plain jar if needed for inspection or alternate JVM launch flows:

```text
gradle jar
```

The jar manifest points at `dev.xhtmlinlinecheck.cli.MainKt`, but the packaged distribution under `build/facelets-verify/` is the primary release artifact because it includes the runtime classpath and the stable `facelets-verify` launcher name.

## Recommended Verification Order

1. Run fixture and unit coverage with `gradle test`.
2. Run baseline packaging and smoke verification with `scripts/verify-baseline.bash` or `scripts/verify-baseline.ps1`.
3. Run deterministic-output verification with `scripts/verify-deterministic-output.bash` or `scripts/verify-deterministic-output.ps1`.
4. Build archives with `gradle distZip distTar`.
5. Smoke the installed launcher from `build/facelets-verify/bin/`.

## MVP Release Checklist

Use this checklist before cutting the first non-snapshot MVP release.

### Fixtures

- [ ] Canonical fixture corpus under `fixtures/equivalent/`, `fixtures/not-equivalent/`, and `fixtures/inconclusive/` passes through `CanonicalFixtureComparisonTest`.
- [ ] Support fixtures under `fixtures/support/` still cover include expansion, provenance, missing includes, include cycles, and smoke execution paths.
- [ ] Any intentional fixture contract change updates the relevant `expected.json`, golden outputs, and fixture notes together.

### Integration And CLI

- [ ] `gradle test` passes, including CLI entrypoint coverage in `FaceletsVerifyEntrypointSmokeTest`.
- [ ] Reporter golden tests pass, including `ReportRendererGoldenTest` and `JsonReportRendererOrderingTest`.
- [ ] Real-entrypoint deterministic coverage passes in `FaceletsVerifyDeterministicOutputTest`.
- [ ] `--explain`, `--max-problems`, `--fail-on-warning`, `--format`, and base-directory options still behave as documented.

### Deterministic Output

- [ ] `scripts/verify-deterministic-output.bash` or `scripts/verify-deterministic-output.ps1` passes without byte drift across repeated runs.
- [ ] Text and JSON output remain stable for representative equivalent, mismatch, inconclusive, capped-output, and `--explain` invocations.
- [ ] New diagnostics preserve stable ids and deterministic ordering before release notes or CI consumers depend on them.

### Packaging

- [ ] `gradle installDist` produces `build/facelets-verify/` with a working launcher.
- [ ] `gradle distZip distTar` produces versioned archives under `build/distributions/`.
- [ ] The packaged launcher can analyze `fixtures/support/smoke/old/root.xhtml` vs `fixtures/support/smoke/new/root.xhtml` using explicit `--base-old` and `--base-new` paths.
- [ ] Release notes and `CHANGELOG.md` are updated for the version being cut.
- [ ] The version in `build.gradle.kts` is changed from snapshot to the intended release version before archives are published.

## First MVP Release Notes Template

Use the following structure when cutting the first release.

```markdown
## xhtml-inline-check v0.1.0

### Scope
- First MVP release of the static `facelets-verify` CLI for JSF Facelets include-inlining checks.

### Included In This Release
- Fixture-backed comparison coverage for equivalent, not-equivalent, and inconclusive outcomes.
- Text and JSON reporting with stable diagnostic ids and `--explain` support.
- Installable CLI distribution archives produced by Gradle.

### Verification
- `gradle test`
- `scripts/verify-baseline.<bash|ps1>`
- `scripts/verify-deterministic-output.<bash|ps1>`
- `gradle distZip distTar`

### Known Limitations
- Static analysis only; no JSF runtime execution.
- Unsupported constructs remain explicit and can force `INCONCLUSIVE`.
- A system Gradle installation is still required until a wrapper is added.
```
