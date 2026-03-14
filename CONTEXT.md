# CONTEXT

- The repository now has a single-module Gradle Kotlin CLI scaffold rooted at `build.gradle.kts` and `settings.gradle.kts`.
- The Gradle build is configured as a JVM application named `facelets-verify`; `gradle run`, `installDist`, `distZip`, and `distTar` should all target that executable/distribution name.
- Main source packages are split by responsibility under `src/main/kotlin/dev/xhtmlinlinecheck`: `cli`, `domain`, `loader`, `syntax`, `semantic`, `compare`, and `report`.
- The current analyzer stages are placeholders that intentionally return an `INCONCLUSIVE` result with warning `W00`; later tasks should replace stage internals without collapsing those package boundaries.
- Baseline tests live under `src/test/kotlin` and currently exercise CLI startup, usage handling, and JSON/text rendering shape.
- The executable jar manifest is wired to `dev.xhtmlinlinecheck.cli.MainKt` so the plain jar remains directly runnable outside Gradle-managed distributions.
- This environment has Java 17 but no local `gradle` executable and restricted network access, so Gradle-based compilation, packaging, and wrapper generation could not be validated during this task.
