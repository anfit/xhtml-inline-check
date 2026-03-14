# Skill: Flatten a JSF/Facelets XHTML file

Merge all `<ui:include>` and `<ui:decorate>` references into a single self-contained XHTML file, preserving exact runtime behaviour.

---

## 1. Discovery phase

Before writing a single line, read **every** file in the include tree:

1. Read the root file and collect every `<ui:include src="...">` and `<ui:decorate template="...">`.
2. For each included file, repeat recursively — includes can be nested multiple levels deep.
3. List all directories referenced (`_dialogs/`, `_sections/`, `_templates/filter/`, `_templates/grid/`, `_templates/query/`, `_templates/form/`, `_templates/grid/total/`, etc.) and read their contents up front.
4. Build a **full include tree** before producing any output.

---

## 2. Namespace consolidation

The flattened file needs **one** root `<ui:composition>` (or `<ui:component>`) carrying the union of all XML namespace declarations used anywhere in the tree.

- Collect every `xmlns:*` from every file.
- Declare them all on the root element of the flattened file.
- Remove duplicate `xmlns:*` declarations from inlined content.

---

## 3. Inlining `<ui:include>`

Replace each `<ui:include src="...">` with the **body content** of the target file (everything inside its `<ui:composition>` or `<ui:component>` root element — the root element itself is dropped).

### 3a. Parameter substitution — the critical step

Every `<ui:param name="X" value="Y">` inside the `<ui:include>` block creates a **local variable `X`** scoped to the included file. After inlining, `X` no longer exists; every `#{X}` reference inside the included content must be replaced with the actual value `Y`.

Work through the substitution systematically:

| Scenario | What to do |
|---|---|
| `Y` is a literal string (e.g., `value="someId"`) | Replace `#{X}` with the literal string everywhere in the inlined content |
| `Y` is an EL expression (e.g., `value="#{row.plant}"`) | Replace `#{X}` with the inner expression (`row.plant`) everywhere — e.g. `#{X.bed.virtual}` → `#{row.plant.bed.virtual}` |
| `Y` is a method-call EL (e.g., `value="#{Bean.doSomething()}"`) | Substitute inline — `#{X}` used as a method reference becomes the full expression |
| `X` is a rename of a global bean (e.g., `SearchBean="#{SupplierLookup.searchBean}"`) | Every `#{SearchBean.*}` in the included file becomes `#{SupplierLookup.searchBean.*}` |
| Param overrides a `custom:defaults` default (e.g., `plantCodeDefaultValue="#{row.plant.plantCode}"`) | The passed value wins over the template default; use it directly |
| Param is consumed by `custom:injectAttributes names="X ..."` | Add `X="Y"` as a direct XML attribute on the `custom:injectAttributes` element |

### 3b. Variables that must NOT be substituted

Some EL variables are **ambient** — set by the enclosing component framework (e.g., `#{cell}` in a column body, `#{row}` in a table row, `#{pathToPlant}` set by a query builder, `#{idPrefix}` set by a parent column, `#{bedRender}` / `#{bedExecute}` as optional injectables). These were never `ui:param` values and must be left as-is.

**How to tell the difference:** if a variable name appears in `<ui:param name="...">` at the call site, it is a local param and must be substituted. If it does not appear in any `<ui:param>`, it is ambient.

### 3c. `custom:defaults` local variables

`custom:defaults foo="#{expr}"` introduces a local variable `foo` inside its body. After inlining:

- If `foo` was set from a `ui:param` that has now been substituted away, the `custom:defaults` attribute can be updated to the resolved expression.
- If `custom:defaults` still provides a useful default for an ambient variable, keep it.
- `custom:defaults` wrappers that only existed to capture a `ui:param` value and whose inner content now uses the resolved expression directly can be simplified or removed.

### 3d. Remove subpage-internal guards

`<custom:failIfNotDefined vars="X Y ...">` elements that exist inside a reusable template to catch missing params at call sites become meaningless (and potentially harmful) after inlining — **remove them all**.

Similarly, `<custom:fail test="...">` guards that validated param presence should be removed if their condition will always be false after substitution.

---

## 4. Inlining `<ui:decorate>`

`<ui:decorate template="...">` is the inverse of `<ui:include>`: the **template** provides the outer structure and the decorator provides named slot content via `<ui:define name="...">`.

To inline:
1. Read the template file.
2. Replace each `<ui:insert name="X" />` in the template with the corresponding `<ui:define name="X">` body from the decorator.
3. Apply parameter substitution as in §3a for any `<ui:param>` inside `<ui:decorate>`.
4. Drop the `<ui:decorate>` / `<ui:define>` wrapper elements; emit the resolved template body inline.

---

## 5. Multi-call-site templates

When the same template file is included **more than once** with different params (e.g., `YIELD_ACCURACY_COLUMN.xhtml` included 7 times, `BED_FILTER_SELECT_MANY.xhtml` included 3 times):

- Each call site is expanded independently with its own param values substituted.
- Do **not** share or reuse the expanded block between call sites.
- If a template generates component `id` attributes from params (e.g., `id="#{externalId}bedFilterSelectMany"`), ensure each expanded instance gets a unique id by substituting the `externalId` param value (e.g., `id="supplierCodebedFilterSelectMany"`, `id="gardenerCodebedFilterSelectMany"`).

---

## 6. Nested includes (includes inside includes)

Process the tree bottom-up: expand the deepest includes first, then work upward. This avoids having to re-process already-expanded content.

Example chain:
```
report.xhtml
  └─ _templates/filter/SUPPLIER_FILTER.xhtml
       ├─ _templates/filter/BED_FILTER_SELECT_MANY.xhtml   (SearchBean="#{SupplierLookup.searchBean}")
       ├─ _templates/filter/SUPPLIER_CLASS_FILTER.xhtml    (SearchBean="#{SupplierLookup.searchBean}")
       └─ _templates/query/CURRENT_SUPPLIER_CLASS_FILTER_QUERY.xhtml (supplier="", SearchBean="#{SupplierLookup.searchBean}")
```
Each level passes a **different** `SearchBean`; substitute correctly at each level.

---

## 7. `ui:param` inside `<ui:define>` (template slots)

`<ui:param>` elements placed inside a `<ui:define>` block scope their variable to that slot. After inlining the `<ui:decorate>`, apply the same substitution rules as §3a within the expanded slot content.

---

## 8. Checklist before finalising

Go through every `<ui:param>` in the original file tree and verify each one:

- [ ] The param's local variable name no longer appears as a bare `#{name}` anywhere in the flattened output (unless it also happens to be a real managed bean name).
- [ ] Every `custom:failIfNotDefined` and param-presence `custom:fail` guard has been removed.
- [ ] Parameters passed to `custom:injectAttributes` via `ui:param` are present as direct XML attributes on `custom:injectAttributes`.
- [ ] Renamed `SearchBean` / lookup-bean aliases are resolved to the full path at every use site.
- [ ] Template-default values (from `custom:defaults`) are correctly overridden where the call site passed an explicit param.
- [ ] Multi-instance templates produce unique component IDs at each call site.
- [ ] All namespace declarations are present on the root element.
- [ ] No inlined file still contains its own `<ui:composition>` or `<ui:component>` root element.

---

## 9. Add traceability comments

For each merged block, add an XML comment documenting its origin and the param values used:

```xml
<!-- [merged] _templates/grid/HARVEST_PICKS_COLUMN.xhtml
     harvestHistory="#{row.harvestHistory}"  plant="#{row.plant}"  period="#{row.period}" -->
```

This makes the flattened file auditable and easier to re-sync if the source templates change.
