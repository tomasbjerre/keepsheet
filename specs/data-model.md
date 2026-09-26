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
| `nameEditedByUser` | boolean | true once the user renamed the document; an automatic rename never overwrites a user-chosen name |
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
6. On every fresh app process start (see
   [Document lifetime](#document-lifetime)), delete every document, page,
   and underlying file, before any of them are shown — the same "nothing
   orphaned" guarantee as deleting one document, applied to all of them
   at once.

## Document lifetime

KeepSheet does not keep a permanent archive — see
[Overview](overview.md#in-scope). Every Document, every Page, and any
page image still on disk from a capture session that was never finished,
is deleted before anything else happens on a fresh app process start —
not when the app is merely backgrounded, has its screen locked, or is
switched away from, all of which leave a session's documents untouched
for as long as that process keeps running.

- "A fresh process start" covers every way the previous process could
  have ended — the user swiping KeepSheet away, the OS reclaiming its
  memory under pressure, a device reboot, an app update — without
  needing to tell these apart: the effect (start clean) is identical
  either way.
- The only way for a document to survive past its session is exporting
  it — sharing it (see [UI Flows](ui-flows.md#5-document-detail)) to
  wherever the user chooses. Once exported, that copy is outside
  KeepSheet's own storage; KeepSheet doesn't track which documents have
  been exported, or treat them specially — everything in the list is
  equally temporary.
- This applies the same way to every Document regardless of `source`.
- A consequence for implementations: a storage schema change never needs
  to carry old data forward, since nothing is ever worth preserving
  across a restart anyway — recreating storage from scratch on a schema
  change is fine.
