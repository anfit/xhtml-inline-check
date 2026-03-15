# Build, Run, And Release

This document is the practical operator guide for building, running, and releasing the `xhtml-inline-check` module.

It is intentionally command-focused and complements, rather than replaces, the broader release checklist in [release-readiness.md](release-readiness.md).

## Prerequisites

- Java 17 available on `PATH`
- a system Gradle installation available as `gradle`

This repository does not currently include a Gradle wrapper, so all commands below assume an installed Gradle.

## Build

Compile and run the test suite:

```text
gradle test
```

Build the plain jar:

```text
gradle jar
```

Build the installable CLI distribution:

```text
gradle installDist
```

Expected output:

- plain jar: `build/libs/`
- installed distribution: `build/facelets-verify/`
- launcher scripts: `build/facelets-verify/bin/`
- runtime libraries: `build/facelets-verify/lib/`

Build release archives:

```text
gradle distZip distTar
```

Expected output:

- `build/distributions/facelets-verify-<version>.zip`
- `build/distributions/facelets-verify-<version>.tar`

## Run

### Run Through Gradle

Use the dedicated Gradle task when you want to invoke the CLI from source without installing the distribution:

```text
gradle runFaceletsVerify --args="old/root.xhtml new/root.xhtml --base-old old --base-new new"
```

The executable name exposed by the Gradle application packaging is `facelets-verify`.

### Run The Installed Distribution

After `gradle installDist`, use the generated launcher from `build/facelets-verify/bin/`.

Unix-like shells:

```text
build/facelets-verify/bin/facelets-verify old/root.xhtml new/root.xhtml --base-old old --base-new new
```

Windows PowerShell:

```powershell
.\build\facelets-verify\bin\facelets-verify.bat old\root.xhtml new\root.xhtml --base-old old --base-new new
```

### Representative Smoke Run

From the repository root, the built-in smoke fixture can be checked with:

```text
gradle runFaceletsVerify --args="fixtures/support/smoke/old/root.xhtml fixtures/support/smoke/new/root.xhtml --base-old fixtures/support/smoke/old --base-new fixtures/support/smoke/new"
```

### Common CLI Options

- `--format text|json`
- `--max-problems <n>`
- `--fail-on-warning`
- `--explain <problem-id>`
- `--base-old <dir>`
- `--base-new <dir>`

For the full CLI contract and result semantics, see [README.md](../README.md) and [SPEC.md](../SPEC.md).

## Verification Scripts

For the standard repository verification flow, use the checked-in helper scripts:

- Unix-like shells: `scripts/verify-baseline.bash`
- Windows PowerShell: `scripts/verify-baseline.ps1`

These scripts run:

- `gradle test`
- `gradle installDist`
- `gradle runFaceletsVerify` against the smoke fixture

For deterministic-output verification, use:

- Unix-like shells: `scripts/verify-deterministic-output.bash`
- Windows PowerShell: `scripts/verify-deterministic-output.ps1`

Those scripts rerun representative CLI invocations and assert byte-stable output across repeated executions.

## Release

Use [release-readiness.md](release-readiness.md) as the canonical release checklist. The short operational flow is:

1. Run `gradle test`.
2. Run `scripts/verify-baseline.bash` or `scripts/verify-baseline.ps1`.
3. Run `scripts/verify-deterministic-output.bash` or `scripts/verify-deterministic-output.ps1`.
4. Update `CHANGELOG.md` for the release.
5. Change the version in `build.gradle.kts` from snapshot to the intended release version.
6. Build artifacts with `gradle installDist distZip distTar`.
7. Smoke the packaged launcher from `build/facelets-verify/bin/`.

Release artifacts are the packaged distribution and archives produced by Gradle, not just the plain jar. The distribution is preferred because it includes the runtime classpath and the stable `facelets-verify` launcher.

## Environment Notes

On Windows in restricted environments, the repository already includes PowerShell helper scripts that pin `GRADLE_USER_HOME`, `TEMP`, and `TMP` inside the repository before invoking Gradle. Prefer those scripts if local Gradle startup fails because of inaccessible profile or temporary directories.
