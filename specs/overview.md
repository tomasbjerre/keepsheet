# Overview

## Goal

KeepSheet turns paper and loose photos into organized, searchable PDF
documents, and lets you combine existing PDFs into one. It should be
trivial to go from "papers on my desk" to "one clean PDF, named sensibly,
ready to file or send" — open the app, capture or import, done.

## In scope

- Capture one or more pages with the camera, or import existing photos,
  and turn them into a single PDF (see
  [Capture & Processing](capture-and-processing.md)).
- Automatically detect a page's edges and correct its perspective so it
  looks scanned, not photographed at an angle.
- Apply a document filter (color, grayscale, or black-and-white) to make
  text crisp and shrink file size.
- Recognize text on each page (OCR) so the resulting PDF is searchable.
- Automatically suggest a sensible file name for every PDF produced (see
  [File Naming](file-naming.md)).
- Merge multiple existing PDF files into one, in a user-chosen order (see
  [Merging](merging.md)).
- Keep every document on the device for as long as the current session
  lasts — see [Data Model](data-model.md#document-lifetime) for exactly
  what a "session" is. Sharing a document (see below) is how you keep a
  copy of it beyond that.
- Browse the current session's documents and open one to view, rename,
  share, or delete it.
- Search the current session's documents by name or recognized text.

## Explicitly out of scope (for now)

- Annotating, signing, or editing the content of a page after it's
  captured (beyond crop/straighten/filter as part of turning it into a
  PDF).
- Cloud storage, sync, accounts, or sharing documents with other users
  inside the app — sharing out to other apps (email, messaging, cloud
  drives) via the platform's own share mechanism is in scope; a KeepSheet
  account or backend is not.
- Automatic classification into folders/tags, beyond the best-effort
  labeling that feeds the suggested file name.
- Multi-device sync. Documents live on the device that created them.
- A permanent, cross-session document archive/library. Documents are
  session-scoped by design (see
  [Data Model](data-model.md#document-lifetime)), specifically to avoid
  holding onto sensitive scanned content — invoices, IDs, contracts —
  any longer than the user is actively working with it. Export (share)
  whatever you want to keep.

If a future need arises for any of the above, it should be added as a new
spec document rather than bent into the existing ones.

## Design principle

Simplicity beats configurability. When a decision could be a user-facing
setting or a fixed sane default, prefer the fixed default.
