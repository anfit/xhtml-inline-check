# Changelog

This file is the repository-level release log.

The project is still pre-release. Keep entries concise and oriented around operator-visible behavior, fixture contract changes, packaging, and release-significant diagnostics.

## [Unreleased]

### Added
- Release-readiness documentation covering packaging instructions, deterministic-output verification, and the MVP release checklist.
- Initial release-notes template for the first MVP cut.

### Changed
- README documentation map now points to the release-readiness guide and changelog.

## [0.1.0] - YYYY-MM-DD

### Added
- First MVP release of the `facelets-verify` CLI.
- Fixture-backed analyzer coverage for equivalent, mismatch, and inconclusive scenarios.
- Deterministic text and JSON reporting with stable diagnostic ids.
- Gradle-built installable distributions under the `facelets-verify` application name.

### Verification
- `gradle test`
- `scripts/verify-baseline.bash` or `scripts/verify-baseline.ps1`
- `scripts/verify-deterministic-output.bash` or `scripts/verify-deterministic-output.ps1`
- `gradle installDist distZip distTar`

### Notes
- Replace this template section with the actual release summary, concrete highlights, and the final release date when cutting `0.1.0`.
