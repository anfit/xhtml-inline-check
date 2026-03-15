# Changelog

This file is the repository-level release log.

Keep entries concise and oriented around operator-visible behavior, fixture contract changes, packaging, and release-significant diagnostics.

## [Unreleased]

### Changed
- Documentation cleanup for a product-focused repository layout and release process.

## [0.1.0] - YYYY-MM-DD

### Added
- First release of the `facelets-verify` CLI.
- Fixture-backed analyzer coverage for equivalent, mismatch, and inconclusive scenarios.
- Deterministic text and JSON reporting with stable diagnostic ids.
- Gradle-built installable distributions under the `facelets-verify` application name.

### Verification
- `gradle test`
- `scripts/verify-baseline.bash` or `scripts/verify-baseline.ps1`
- `scripts/verify-deterministic-output.bash` or `scripts/verify-deterministic-output.ps1`
- `gradle installDist distZip distTar`
