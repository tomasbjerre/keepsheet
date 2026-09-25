# Data Model

These entities are storage-technology-agnostic. An implementation may use
any local persistence mechanism (SQL database, files, etc.) as long as it
can represent this shape and answer the queries listed at the bottom.

## Document

One PDF file KeepSheet has produced — by scanning/importing pages (see
[Capture & Processing](capture-and-processing.md)) or by merging existing
PDFs (see [Merging](merging.md)).

| Field | Type | Notes |
|---|---|---|
| `id` | identifier | unique per document |
| `createdAt` | timestamp | when the document was first produced |
| `name` | text | the file name (without extension); auto-suggested (see [File Naming](file-naming.md)), user-editable at any time |
| `pdfPath` | text | where the produced PDF file lives on-device |
| `pageCount` | integer | number of pages in the PDF |
| `sizeBytes` | number | size of the PDF file, for display in the document list |
| `searchText` | text, nullable | recognized text (see [Capture & Processing](capture-and-processing.md#text-recognition-ocr)) concatenated across all pages, used to search the document list; null if OCR hasn't completed or found nothing |
| `source` | enum: `scanned`, `imported`, `merged` | how the document was produced — `scanned`/`imported` come from [Capture & Processing](capture-and-processing.md), `merged` from [Merging](merging.md) |

`pageCount` and `sizeBytes` are derived from the PDF file but stored (not
recomputed on every read) so the document list stays cheap to render.
`searchText` is filled in after OCR finishes and stored once known — it
never blocks a document from appearing in the list or being opened.

## Page

One page belonging to a `scanned`/`imported` document, before and while
it's part of the PDF. Not applicable to a `merged` document — merging
combines existing PDFs' pages directly, without re-deriving this
per-page record (see [Merging](merging.md)).

| Field | Type | Notes |
|---|---|---|
| `id` | identifier | unique per page |
| `documentId` | identifier | foreign key to Document |
| `sequence` | integer | ordering within the document |
| `imagePath` | text | the processed (cropped, straightened, filtered) image this page was built from |
| `filter` | enum: `color`, `grayscale`, `blackAndWhite` | the document filter applied (see [Capture & Processing](capture-and-processing.md#document-filters)) |
| `searchText` | text, nullable | recognized text for this page alone; null if OCR hasn't completed or found nothing |

## Required queries

Any implementation's storage layer must support:

1. Create a document and append pages to it incrementally while capturing
   (not just a single bulk write at the end) — see
   [Capture & Processing](capture-and-processing.md#multi-page-capture).
2. List all documents, most recent first, with enough fields to render a
   list row (name, date, page count, size) without loading every page.
3. Load one document's pages, in `sequence` order, to show its review/edit
   view before the PDF is finalized.
4. Search documents whose `name` or `searchText` contains a given term
   (case-insensitive), most recent match first.
5. Delete a document, its pages, and their underlying image/PDF files —
   nothing orphaned left behind.

## Data integrity on start

Stored data an implementation reads must never be trusted blindly just
because it came from local storage — a previous run could have been
killed mid-write, or a future version of KeepSheet could change what a
stored value is allowed to mean. On every app start:

- A document left in an impossible state — e.g. a `pdfPath` pointing at a
  file that no longer exists — is fixed if a fix is well-defined (e.g.
  rebuilt from its still-present pages), or removed from the list if it
  isn't, rather than shown as a broken row.
- A storage schema change (a new field, a changed meaning for an existing
  one, a new required relationship) must carry the existing data forward
  — via an explicit, tested migration — rather than discarding it. This
  applies from the first release KeepSheet actually ships to real users
  onward: once real devices hold real documents, an implementation detail
  changing shape is never a reason to silently erase someone's files. A
  schema change with no reasonable migration path (rare) must say why in
  the change itself, not leave it unstated.
- Future storage-integrity checks belong here as they're identified —
  this section is the list, not just the one example above.
