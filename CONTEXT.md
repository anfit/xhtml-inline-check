# CONTEXT

- The repository now has a single-module Gradle Kotlin CLI scaffold rooted at `build.gradle.kts` and `settings.gradle.kts`.
- Main source packages are split by responsibility under `src/main/kotlin/dev/xhtmlinlinecheck`: `cli`, `domain`, `loader`, `syntax`, `semantic`, `compare`, and `report`.
- The current analyzer stages are placeholders that intentionally return an `INCONCLUSIVE` result with warning `W00`; later tasks should replace stage internals without collapsing those package boundaries.
- Baseline tests live under `src/test/kotlin` and currently exercise CLI startup, usage handling, and JSON/text rendering shape.
- This environment has Java 17 but no local `gradle` executable and restricted network access, so a Gradle wrapper could not be generated or validated during this task.
