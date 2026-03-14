This fixture locks the canonical unsupported dynamic-include path.

The old tree contains a `ui:include` whose `src` is EL-backed, so the loader and comparator must preserve a dedicated `W-UNSUPPORTED-DYNAMIC_INCLUDE` warning and derive `INCONCLUSIVE` even though no structural mismatch has been proven.
