# Capture & Processing

## Multi-page capture

A person scanning paperwork usually has more than one page — a multi-page
letter, several receipts, both sides of a form. Capture must let them
photograph several pages in a row and end up with one PDF, not one PDF per
photo:

- Starting a capture session opens the camera and lets the user take a
  shot, see it added to a running list of captured pages (shown as a
  thumbnail strip), and immediately take the next one — no per-page
  "save" or "next" round trip back to a list screen.
- Import (choosing existing photos instead of the camera) adds to the same
  running list the same way, and the two can be mixed in one document —
  a photo of page 1 taken with the camera, page 2 imported from the photo
  library.
- Any captured/imported page can be **reordered** (drag within the
  thumbnail strip) or **retaken** (replace it with a new camera shot)
  before the document is finalized.
- Any page can be removed from the list before finalizing. Removing the
  last remaining page leaves an empty capture session, not a document with
  zero pages.
- Finalizing (see [UI Flows](ui-flows.md#2-capture)) processes every page
  (below) and produces one [Document](data-model.md#document) whose page
  order matches the thumbnail strip's order at that moment.

## Automatic cropping and straightening

Each captured/imported page is processed to look scanned rather than
photographed at an angle:

- Detect the paper's edges within the photo.
- Correct the perspective (a rectangular warp) so the page fills the
  frame as if it had been scanned flat, not shot from an angle.
- If edges can't be confidently detected (e.g. the paper doesn't contrast
  with its background), fall back to the original, uncropped photo rather
  than guessing a wrong crop — a person reviewing pages before finalizing
  (see [UI Flows](ui-flows.md#3-page-review)) can always crop manually.
- The detected crop is shown to the user before it's applied, with a
  chance to adjust the corners by hand — automatic detection is a
  starting point, not a silent, unreviewable transformation.

## Document filters

Each page can have one of three filters applied, independently of the
others in the same document:

- **Color** — the cropped/straightened photo as captured, no color
  processing.
- **Grayscale** — converted to grayscale, reducing file size versus color
  while keeping shading/photos on the page legible.
- **Black and white** — thresholded to pure black text/marks on a white
  background: the crispest text and the smallest file size, best for
  plain text documents; least suitable for a page with photos or shaded
  content, where grayscale reads better.

The filter is chosen per page, defaulting to the last filter used in the
current capture session (or black-and-white for the first page of a new
one — the most common case, a text document), and can be changed at any
time before finalizing (see [UI Flows](ui-flows.md#3-page-review)).

## Text recognition (OCR)

After a document is finalized, KeepSheet recognizes text on each page so
the document becomes searchable (see
[Data Model](data-model.md#document)):

- Runs entirely on-device — recognized text is never sent anywhere else
  (see [Permissions & Privacy](permissions-and-privacy.md)).
- Best-effort and asynchronous: finalizing a document never waits on OCR
  to complete. The document appears in the list immediately with
  `searchText` null, and becomes searchable once recognition finishes in
  the background.
- If recognition fails or finds no text (e.g. a blank or purely graphical
  page), the document/page's `searchText` simply stays null — never
  treated as an error shown to the user.
- Recognizes text in the device's configured language(s) where supported;
  a document with text in an unsupported language still gets a PDF, it
  just isn't searchable by that text.
  Swedish and English are supported; any other device language falls
  back to English recognition.
- Every page's recognized text is stored on the page, and the document's
  `searchText` is all pages' text concatenated in page order.
- Feeds the automatic file name suggestion (see
  [File Naming](file-naming.md)) as well as document search (see
  [Data Model](data-model.md#required-queries)).
