# File Naming

Manually renaming every scan or merge is exactly the kind of friction
KeepSheet should remove. Every [Document](data-model.md#document) —
scanned, imported, or merged — gets an automatically suggested name at the
moment it's finalized, following one pattern:

```
<date>_<type>_<name>.pdf
```

e.g. `2026-09-25_Invoice_BlekingeBygg.pdf`.

## Fields

- **`<date>`** — `yyyy-MM-dd`. For a scanned/imported document, a date
  recognized in the page text (see
  [Capture & Processing](capture-and-processing.md#text-recognition-ocr))
  if one is found with reasonable confidence, otherwise the date the
  document was finalized. For a merged document, always the date it was
  merged — a merge combines files that may each carry their own internal
  date, so there's no single one to prefer.
- **`<type>`** — a best-effort category word (e.g. `Invoice`, `Receipt`,
  `Contract`, `Letter`) matched from a small set of recognizable keywords
  in the page text. Falls back to `Document` when nothing matches.
- **`<name>`** — a best-effort short label pulled from the page text (e.g.
  a company or sender name), with spaces and punctuation stripped so it
  stays filesystem-safe. Falls back to `Untitled` when nothing usable is
  found.

## Rules

- The suggestion is exactly that — a starting point, never forced. The
  user can edit a document's name at any time (see
  [UI Flows](ui-flows.md#5-document-detail)), and doing so never affects
  any other field.
- Naming never blocks finalizing a document: if OCR hasn't completed yet
  (see [Capture & Processing](capture-and-processing.md#text-recognition-ocr)),
  the document is created with a fallback name (finalize date + `Document`
  + `Untitled`) and silently renamed once recognition finishes with
  something more specific — unless the user has already renamed it by
  then, in which case the automatic rename never overwrites a
  user-chosen name.
- If a suggested (or renamed) name collides with an existing document's
  name, a numeric suffix (` (2)`, ` (3)`, ...) is appended so files never
  silently overwrite one another.
- Characters illegal in file names on common platforms are stripped from
  every field before assembly.
