This fixture isolates naming-container ancestry drift without using `h:form`.

The old tree places `statusPanel` under `h:dataTable`, which the built-in registry treats as a naming container. The new tree keeps the same component id but removes that naming-container ancestor, so the canonical mismatch should be `P-STRUCTURE-NAMING_CONTAINER_ANCESTRY_CHANGED` and not the form-ancestry diagnostic.
