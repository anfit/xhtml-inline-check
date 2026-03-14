# CONTEXT

- The repository now has a single-module Gradle Kotlin CLI scaffold rooted at `build.gradle.kts` and `settings.gradle.kts`.
- The Gradle build is configured as a JVM application named `facelets-verify`; `gradle run`, `installDist`, `distZip`, and `distTar` should all target that executable/distribution name.
- Use `gradle runFaceletsVerify --args="old.xhtml new.xhtml"` as the dedicated Gradle-backed CLI entrypoint; `installDist` now installs a stable launcher under `build/facelets-verify/bin/`.
- Main source packages are split by responsibility under `src/main/kotlin/dev/xhtmlinlinecheck`: `cli`, `domain`, `loader`, `syntax`, `semantic`, `compare`, and `report`.
- The current analyzer stages are placeholders that intentionally return an `INCONCLUSIVE` result with warning `W00`; later tasks should replace stage internals without collapsing those package boundaries.
- Baseline tests live under `src/test/kotlin`; reusable helpers are under `src/test/kotlin/dev/xhtmlinlinecheck/testing` with `FixtureScenarios` for repository fixtures, `TemporaryProjectTree` for per-test XHTML trees, and `assertThatReport(...)` for concise report assertions.
- Core library choices are now wired in `build.gradle.kts`: use `Clikt` for CLI parsing, `Woodstox` for namespace-aware StAX parsing and location capture, and `Jackson Kotlin` for deterministic JSON reporting plus fixture-contract loading.
- Test infrastructure now includes JUnit Jupiter parameterized tests and AssertJ in addition to `kotlin("test")`; prefer those for fixture matrices, golden-output assertions, and concise domain-model checks. The shared smoke fixture currently lives at `fixtures/support/smoke/`.
- The executable jar manifest is wired to `dev.xhtmlinlinecheck.cli.MainKt` so the plain jar remains directly runnable outside Gradle-managed distributions.
- Launcher smoke coverage now includes a child-JVM test at `src/test/kotlin/dev/xhtmlinlinecheck/cli/FaceletsVerifyEntrypointSmokeTest.kt`; it runs `dev.xhtmlinlinecheck.cli.MainKt` directly against `fixtures/support/smoke/` using the current test JVM classpath and asserts the scaffolded `INCONCLUSIVE` exit code path remains stable.
- The current local baseline verification entrypoint is `scripts/verify-baseline.bash`; it intentionally wraps `gradle test`, `gradle installDist`, and `gradle runFaceletsVerify --args="<repo>/fixtures/support/smoke/old/root.xhtml <repo>/fixtures/support/smoke/new/root.xhtml"` so packaging, the named CLI task, and smoke fixtures stay exercised together.
- This environment has Java 17 but no local `gradle` executable and restricted network access, so Gradle-based compilation, packaging, and wrapper generation could not be validated during this task.
