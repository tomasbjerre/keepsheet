# Merging

A person often has several existing PDFs — an already-scanned cover
letter, an emailed invoice, a signed contract page — that belong together
as one file.

## Selecting files and order

- The user picks two or more existing PDF files (from KeepSheet's own
  document list, from device storage, or a mix of both — see
  [UI Flows](ui-flows.md#4-merge)).
- Selected files are shown as a reorderable list (drag to reorder), so the
  user controls the exact page order of the result — every page of a
  given file stays together and in its original internal order; only the
  order of whole files relative to each other is chosen here, not
  individual pages within one.
- Nothing is merged until the user explicitly confirms — selecting files
  only stages them.

## Result

- Produces one new [Document](data-model.md#document) with `source` =
  `merged`, containing every page of every selected file concatenated in
  the chosen order.
- The merged PDF's pages are the original files' pages as they already
  exist (not re-rendered through the [capture filters](capture-and-processing.md#document-filters)) —
  merging preserves whatever each source PDF already contains, including
  any text layer it already had.
- OCR (see [Capture & Processing](capture-and-processing.md#text-recognition-ocr))
  still runs on the merged result in the background, so a source PDF that
  had no text layer of its own still contributes to the merged document's
  `searchText`.
- The source files selected for merging are left untouched — merging
  always produces a new file, never modifies or deletes the originals.
- Gets an automatically suggested name the same way any other document
  does (see [File Naming](file-naming.md)).
