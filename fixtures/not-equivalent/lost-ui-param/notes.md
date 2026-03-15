This fixture distinguishes lost include-parameter scope from generic global-root uncertainty.

Both old and new trees bind `title` locally, but they bind it through different mechanisms. The old side resolves `#{title.label}` from `ui:param`, while the new side falls back to the surrounding `c:set`, so the analyzer can prove `P-SCOPE-BINDING_MISMATCH` instead of only warning about an unresolved bean root.
