Implement a central registry for known tag rules so the syntax-walker, scope builder, structural analyzer, and later extension tasks can resolve semantics deterministically.
Add registry rules for `ui:include`, `ui:param`, `ui:composition`, and `ui:fragment`, building on the parser and transparent-wrapper tasks so include-related behavior is explicit.
Add a registry rule for `ui:repeat`, because the binding, iteration-ancestry, EL-normalization, and variable-capture comparison tasks depend on its semantics.
Add registry rules for `c:set`, `c:forEach`, and `c:if`, extending the same registry introduced for Facelets tags so JSTL-controlled scope and guards can be modeled consistently.
Add a registry rule for `h:form`, because the form-ancestry, target-resolution, and moved-component diagnostics tasks depend on it.
Add generic JSF or component rules for `id`, `rendered`, `for`, `update`, `render`, `process`, and `execute`, connecting the registry work to semantic extraction and target comparison.
Add tests proving rule lookup is deterministic and namespace-aware, covering the tag-rule interface and central-registry tasks before downstream analyzers depend on them.
Define binding kinds for `ui:param`, iterator vars, `varStatus`, `c:set`, `c:forEach`, and implicit globals, because the scope stack and EL normalizer need a shared vocabulary.
Implement the scope-stack model, building on the binding-kind task and the syntax-tree task so bindings can be resolved relative to tree position.
Implement scope push and pop behavior while walking the syntax tree, using the rule-registry tasks so binding-introducing tags change scope at the right points.
Implement binding creation for `ui:param`, building on the include-parameter extraction task and the scope-stack task.
Implement binding creation for `ui:repeat var` and `varStatus`, using the `ui:repeat` rule and scope-walker tasks so iterator bindings become visible to EL normalization.
Implement binding creation for `c:set`, using the JSTL rule-registry task and scope-walker task.
Implement binding creation for `c:forEach`, using the JSTL rule-registry task and scope-walker task to mirror iterator semantics outside Facelets.
Implement local-shadowing behavior so the scope stack resolves inner bindings over outer ones, because the variable-capture and alpha-renaming tasks depend on correct shadowing.
Implement symbolic origin descriptors for bindings, reusing the provenance model so comparison diagnostics can explain where a binding came from.
Add unit tests for nested scopes, validating the scope-stack, push-pop, and binding-creation tasks together.
Add unit tests for shadowing, targeting the local-shadowing task and ensuring later EL comparison catches capture regressions correctly.
Add fixture tests for `ui:param` visibility across include expansion, connecting the include-parameter, include-expansion, and binding-creation tasks.
Define the supported EL grammar subset for MVP, explicitly covering the forms required by the binding, semantic-node, and comparison tasks while identifying unsupported syntax for inconclusive handling.
Implement EL extraction from relevant attributes, using the rule-registry and syntax-tree tasks so only semantically meaningful attributes are parsed.
Implement EL extraction from text nodes where applicable, extending the same extraction pipeline introduced for attributes so mixed XHTML content is not ignored.
Implement tokenization for EL expressions, building on the MVP grammar task so parsing can be deterministic and testable.
Implement parsing for root identifiers, property chains, index access, method-call roots, boolean operations, and ternaries, because these are the EL forms required by the scope-equivalence tasks.
Implement symbolic normalization of local bindings to canonical ids, using the scope-stack and binding-origin tasks so safe alpha-renames compare equal.
Implement symbolic handling of unresolved global roots so the EL layer can distinguish local-binding equivalence from bean-level uncertainty.
Implement unsupported diagnostics for EL forms outside the MVP grammar, reusing the problem-model and result-model tasks so unsupported syntax can contribute to `INCONCLUSIVE`.
Add unit tests for alpha-renaming equivalence, validating the EL parser, binding normalization, and unresolved-global handling tasks together.
Add unit tests for index expressions and nested property access, targeting the EL parsing and normalization tasks.
Add unit tests for ternaries and boolean expressions, targeting the MVP grammar and parser tasks so structural EL shape is preserved.
Add unit tests for unsupported EL constructs, proving the unsupported-diagnostic task produces explicit analyzer behavior instead of silent fallback.
Define the semantic-node model that will carry normalized EL, ids, ancestry, target references, and matching hints, combining the location, provenance, and syntax foundations.
Implement a syntax-tree walker that emits semantic nodes, building on the syntax-tree, rule-registry, and scope-stack tasks.
Attach normalized EL expressions to semantic nodes, using the EL extraction, parsing, and normalization tasks.
Compute form ancestry for each relevant semantic node, using the `h:form` rule and syntax-walker tasks.
Compute naming-container ancestry for each relevant semantic node, building on the rule-registry task so future component-library extensions can plug in cleanly.
Compute iteration ancestry for each relevant semantic node, using the `ui:repeat`, `c:forEach`, and scope-walker tasks.
Capture `id`, `rendered`, `for`, `update`, `render`, `process`, and `execute` on semantic nodes, using the generic component-rule task and EL-extraction task where needed.
Mark transparent wrappers so they do not distort matching, reusing the syntax-level transparent-wrapper task and the include-related rule tasks.
Add tests for ancestry tracking, validating the form, naming-container, and iteration-ancestry tasks together.
Add tests for transparent-wrapper behavior, targeting the semantic-node and matching-hint tasks so include flattening does not create false mismatches.
Define the internal representation for component-target references, because the semantic-node, target-resolution, and comparison tasks need a shared target model.
Implement extraction of tokenized target lists from `for`, `update`, `render`, `process`, and `execute`, building on the semantic-node attribute-capture and generic-component-rule tasks.
Implement resolution of target references against available semantic nodes, using the ancestry and id-capture tasks so target meaning reflects JSF structure.
Implement diagnostics for missing or changed targets, reusing the problem-model, target-representation, and target-resolution tasks.
Add tests for same-form target resolution, validating the `h:form`, id-capture, and target-resolution tasks.
Add tests for broken target resolution after refactor, targeting the changed-target diagnostic task and preparing the way for comparison fixtures.
Define semantic signatures used during matching, combining tag identity, ancestry, iteration context, rendered guards, and other stable semantic facts.
Implement anchor-first matching by explicit component `id`, because the node-matching and duplicate-suppression tasks should prefer the most stable anchors first.
Implement secondary matching by explicit target relationships, building on the target-resolution tasks so label and AJAX connections can help align nodes.
Implement fallback matching by semantic signature plus ancestry constraints, using the semantic-signature and ancestry-computation tasks when ids and targets are insufficient.
Implement unmatched-node tracking on both old and new sides, reusing the problem-model task so the comparator can explain when no trustworthy pair exists.
Add tests proving stable matching for include-inlined pages, validating the explicit-id, target-based, and fallback-matching tasks together.
Add tests proving wrapper-only changes do not create false mismatches, targeting the transparent-wrapper and fallback-matching tasks.
Implement EL equivalence comparison on matched nodes, using the normalized-EL and node-matching tasks so scope changes become concrete diagnostics.
Implement form-ancestry comparison on matched nodes, using the form-ancestry and node-matching tasks.
Implement naming-container-ancestry comparison on matched nodes, using the naming-container and node-matching tasks.
Implement iteration-ancestry comparison on matched nodes, using the iteration-ancestry and node-matching tasks so iterator capture changes can be surfaced cleanly.
Implement rendered-guard comparison on matched nodes, building on the semantic-node attribute-capture and normalized-EL tasks.
Implement id-collision checks across each analyzed tree, using the semantic-node id-capture task so structural validity problems are visible even before cross-tree comparison.
Implement target-resolution comparison across matched nodes, using the target-resolution and node-matching tasks so changed `for` and AJAX references are detected.
Implement global invariant checks that run after node matching, combining the id-collision, target-resolution, and ancestry tasks into whole-tree sanity checks.
Implement mismatch-to-problem translation, reusing the problem-model, binding-origin, target-diagnostic, and location/provenance tasks so every mismatch becomes navigable output.
Implement duplicate and cascade suppression rules, building on unmatched-node and mismatch-translation tasks so one upstream drift does not explode into noisy output.
Implement final result derivation across mismatches and unsupported findings, using the result-model, unsupported-diagnostic, and duplicate-suppression tasks.
Add fixture tests for safe alpha-renaming, validating the binding-normalization, node-matching, and EL-comparison tasks together.
Add fixture tests for variable-capture regressions, validating the shadowing, iteration-ancestry, binding-origin, and EL-comparison tasks together.
Add fixture tests for form-ancestry drift, targeting the `h:form`, ancestry, matching, and mismatch-translation tasks.
Add fixture tests for changed AJAX targets, targeting the generic-component-rule, target-resolution, matching, and comparison tasks.
Add fixture tests for inconclusive-but-not-proven-wrong cases, tying together unsupported diagnostics, result derivation, and reporter expectations.
Implement concise text rendering for equivalent results, using the result, summary, and statistics tasks so success output stays short and stable.
Implement detailed text rendering for mismatch results, building on the problem-model and mismatch-translation tasks so diagnostics are useful by default.
Implement visible text rendering for inconclusive results, using the unsupported-diagnostic and result-derivation tasks so uncertainty is never hidden.
Implement deterministic problem ordering in reporters, reusing the id-convention, result-ordering, and duplicate-suppression tasks.
Implement JSON output with stable field shapes, using the result, problem, summary, and provenance tasks so fixtures and CI can rely on machine-readable output.
Implement summary-statistics output in both text and JSON reporters, building on the summary-model task so counts stay consistent.
Implement `--max-problems`, using the deterministic-ordering and mismatch-translation tasks so truncation is predictable.
Implement `--fail-on-warning`, using the warning model and result-derivation tasks so teams can tighten policy without rewriting analyzer logic.
Implement `--explain <problem-id>`, using the problem-id convention, mismatch-translation, and reporter tasks so previously reported issues can be expanded deterministically.
Add golden tests for text output, validating the equivalent, mismatch, inconclusive, ordering, and truncation reporter tasks together.
Add golden tests for JSON output, validating the JSON shape, provenance, problem ids, and statistics tasks together.
Wire argument parsing to the analysis pipeline, building on the entrypoint, dependency, and reporter tasks so the CLI can run the end-to-end flow.
Wire base-directory options into the loader, using the root-path and include-resolution tasks so CLI flags affect analysis deterministically.
Wire output-format options into the reporters, using the text-renderer and JSON-renderer tasks.
Map semantic outcomes to process exit codes, using the result-model and CLI-entrypoint tasks so shell automation sees stable behavior.
Add integration tests for representative CLI invocations, combining the loader, parser, comparator, reporter, and exit-code tasks end to end.
Add integration tests for invalid input and parser failure paths, targeting the loader, parser, problem-model, and exit-code tasks so error behavior is explicit.
Create a first-pass fixture corpus under `fixtures/`, using the fixture-layout guidance already documented so loader, comparator, and CLI tests can share assets.
Add one canonical equivalent fixture for safe include inlining, exercising the include-expansion, provenance, transparent-wrapper, and matching tasks.
Add one canonical equivalent fixture for safe iterator alpha-renaming, exercising the binding-normalization, EL-comparison, and matching tasks.
Add one canonical non-equivalent fixture for variable capture, exercising the shadowing, binding-origin, iteration-ancestry, and mismatch-translation tasks.
Add one canonical non-equivalent fixture for lost `ui:param`, exercising the include-parameter, scope-binding, and EL-comparison tasks.
Add one canonical non-equivalent fixture for a component moved outside `h:form`, exercising the form-ancestry, matching, and mismatch-translation tasks.
Add one canonical non-equivalent fixture for changed naming-container ancestry, exercising the naming-container, matching, and comparison tasks.
Add one canonical non-equivalent fixture for changed `for` target resolution, exercising the target-tokenization, target-resolution, and target-comparison tasks.
Add one canonical non-equivalent fixture for changed AJAX target resolution, exercising the same target pipeline used by `update`, `render`, `process`, and `execute`.
Add one canonical inconclusive fixture for dynamic include paths, exercising the dynamic-include diagnostic, result-derivation, and reporter tasks.
Add short notes for any subtle fixture where the semantic risk is not obvious, linking the fixture corpus to the problem-model and reporter tasks so human reviewers have context.
Profile the analyzer on larger real-world XHTML trees, using the complete end-to-end pipeline so performance work is based on real bottlenecks rather than guesses.
Reduce unnecessary reparsing or duplicate tree walks where profiling shows waste, taking care not to break the provenance, scope, and comparison guarantees established by earlier tasks.
Review the rule registry against representative third-party component tags, using the existing registry interface so extension work does not leak into parser or comparator code.
Improve diagnostics for the highest-noise mismatch categories, using findings from fixture runs and end-to-end profiling to refine mismatch translation and duplicate suppression.
Validate deterministic output across repeated runs, covering the matching, ordering, suppression, and reporter tasks as a release-readiness gate.
Add release packaging instructions, building on the Gradle, application, and CLI-entrypoint tasks so the first MVP can be distributed consistently.
Add an initial changelog or release-notes template that reflects the milestone structure established by the bootstrap, analyzer, and reporter tasks.
Define the first release checklist for MVP completion, referencing the fixture corpus, integration tests, deterministic-output validation, and packaging tasks as exit criteria.
