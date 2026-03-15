# AGENTS.md

## Purpose

This repository is intended to be worked on by humans and coding agents. These instructions are here to keep work reliable, reviewable, and useful across long-running implementation threads.

## General Agent Instructions

- Read the current repository docs before making architectural or workflow changes, especially `README.md`, `SPEC.md`, and files under `docs/` that relate to the task.
- Prefer small, focused, reviewable changes over broad rewrites unless a broader change is explicitly requested.
- Keep the implementation aligned with the current specification and repository documentation; if code or docs need to diverge, update both deliberately.
- Preserve deterministic behavior in analysis, diagnostics, and tests wherever possible.
- Prefer explicit unsupported or inconclusive behavior over silent assumptions.
- Do not remove or weaken provenance, diagnostic precision, or fixture coverage without a strong reason.
- When adding new behavior, also add or update the most appropriate tests or fixtures unless the task is strictly documentation-only.
- Treat the fixture corpus and golden outputs as product assets, not incidental test scaffolding.

## Request Freshness And Caution

- Treat every requested task with caution, because some requests may become partially or fully irrelevant by the time an agent or developer reaches them.
- Before executing a task, verify that it still makes sense against the current repository state, current docs, and any newer changes already present in the branch.
- If a requested change appears outdated, superseded, already implemented, or risky in the current context, do not apply it blindly.
- In those cases, narrow the change to what is still relevant, or stop and surface the conflict clearly if proceeding would likely cause churn or regressions.

## Implementation Guardrails

- Keep loader, syntax, semantic, comparison, and reporting concerns separated.
- Keep matching logic stable and understandable; avoid introducing noisy heuristics without tests that justify them.
- Preserve exactness in file and location reporting wherever the current parser/tooling makes that possible.
- When behavior is intentionally approximate, unsupported, or inconclusive, make that explicit in diagnostics and tests.
- Favor extension through registries, models, and focused modules over hardcoded special cases spread across the codebase.

## Testing Expectations

- Prefer fixture-first validation for end-to-end behavior.
- Add unit tests for narrow semantic logic and fixture or integration tests for behavior that crosses module boundaries.
- If a change affects output shape or ordering, update golden tests or equivalent assertions.
- If tests cannot be run, say so explicitly in the handoff.

## Documentation Expectations

- Update documentation when implementation changes meaningfully affect architecture, scope, workflow, or usage.
- Keep docs concise and practical; optimize for helping the next implementer act correctly.
