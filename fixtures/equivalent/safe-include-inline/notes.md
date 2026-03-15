This fixture is the canonical safe include-inlining baseline.

The old tree uses one static `ui:include` and the new tree inlines the same content directly. It stays intentionally small so structural matching, reporter output, and CLI smoke-style tests can reuse one stable equivalent case without depending on the larger unclassified `dummy/` sample.
