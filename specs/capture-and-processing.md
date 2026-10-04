# Capture & Processing

## Multi-page capture

A person scanning paperwork usually has more than one page — a multi-page
letter, several receipts, both sides of a form. Capture must let them
photograph several pages in a row and end up with one PDF, not one PDF per
photo:

- Starting a capture session opens the camera and lets the user take a
  shot, see it added to a running list of captured pages (shown as a
  thumbnail strip), and immediately take the next one — no per-page
  "save" or "next" round trip back to a list screen. The strip
  auto-scrolls to the newest thumbnail and numbers every thumbnail with
  its position, so losing track of which page was just captured — easy
  to do with several physical pages in front of the user — doesn't
  require scrolling back to check.
- Capture is camera-only — it has no Import action of its own (see
  [UI Flows](ui-flows.md#2-capture)). Importing existing photos instead of
  using the camera is Home's separate Import flow, which produces its own
  document and can't be mixed with a camera session's pages.
- Capture is deliberately minimal: a page can only be captured or removed
  here. No reordering or retaking mid-session — reordering is Page
  Review's job (see [UI Flows](ui-flows.md#3-page-review)); to fix a bad
  shot, remove it and capture a fresh one.
- Any page can be removed from the list before finalizing. Removing the
  last remaining page leaves an empty capture session, not a document with
  zero pages.
- Finalizing (see [UI Flows](ui-flows.md#2-capture)) processes every page
  (below) and produces one [Document](data-model.md#document) whose page
  order matches the thumbnail strip's order at that moment.

## Automatic cropping and straightening

Each captured/imported page is processed to look scanned rather than
photographed at an angle:

- A photo's own orientation (e.g. the phone held sideways) is applied before
  anything else touches it, so the page is upright and shown the same way in
  every view — capture's thumbnail strip, the crop preview, the finalized
  page — never sideways in one and upright in another.
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
- Detection assumes the page is lighter than its surroundings and is only
  accepted when it covers a meaningful part of the photo and is
  recognizably a quadrilateral whose sides follow the detected region; a
  page that already fills the frame needs no crop (the full photo is kept).
- The user can always drag the corners, re-run detection, crop manually
  when nothing was detected, or choose the full photo (no crop).
- Re-running detection always gives explicit feedback about the outcome —
  edges found, or none found (falling back to the full photo) — even when
  that outcome doesn't change what's currently shown. A crop preview that
  looks identical before and after tapping the button is indistinguishable
  from the tap having done nothing at all.

## Page rotation

On top of a photo's own orientation (above), a person can rotate an
individual page in 90° steps — for content that still isn't right-side up
even once EXIF orientation is applied, or that the person simply wants
turned a different way:

- Chosen per page in Page Review (see [UI Flows](ui-flows.md#3-page-review)),
  independently of the other pages in the same document, defaulting to no
  rotation (0°).
- Rotating shows the effect immediately in that page's crop preview — what
  will be applied to the saved page is never a guess.
- Rotating a page whose crop was already set (detected or adjusted by hand)
  clears it back to "full photo": that crop was chosen against the old
  orientation and would grab the wrong region once rotated, so the page's
  edges are detected or adjusted again after rotating.
- Applied once, baked into the page's saved image (see
  [Data Model](data-model.md#page)) the same way for every view that shows
  that page — Page Review after saving, Document Detail, and the finalized
  PDF all show the identical, already-rotated result.

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
current capture session (or color for the first page of a new one — it's
the only one of the three that can't lose content, since grayscale and
black-and-white both discard information a page might actually need),
and can be changed at any time before finalizing (see
[UI Flows](ui-flows.md#3-page-review)).

Grayscale and black-and-white read as similar options, but can produce very
different results depending on what's on the page — grayscale keeps
shading/photos legible, black-and-white can wash them out. Each option is
presented with a live preview of what it would actually do to the page
being reviewed, not just its name, so the difference is obvious before
picking one rather than something to infer from the label.

## Printer-friendly pages

A scanned or imported document's PDF pages are built to a standard paper
size with margins, not sized to whatever pixel dimensions the source
photo happens to have — a page sized to an arbitrary photo resolution has
no defined margin and prints at an odd, non-standard size a printer has
to guess how to scale, often clipping content at the edges.

- Every page of a document uses the same **page size** — **A4** by
  default, or **Letter** — chosen in [Page Review](ui-flows.md#3-page-review)
  and remembered for every document finalized afterwards, until changed
  again. This is the one choice KeepSheet remembers across a fresh app
  start (see [Data Model](data-model.md#document-lifetime)): a UI
  preference, not document content (see
  [Permissions & Privacy](permissions-and-privacy.md#data-handling)).
- Each page's content is scaled to fit within the chosen page size minus
  a fixed margin on every side, preserving the content's own aspect ratio
  (never stretched or cropped to fill the page) and centered within that
  printable area.
- A page's own shape (portrait or landscape, from its width and height
  after crop/rotation) picks a matching portrait or landscape page
  orientation, rather than forcing every page to portrait regardless of
  its actual shape.
- This applies to scanned and imported documents' own generated pages.
  [Merged](merging.md) documents keep whatever page sizes their source
  PDFs already had — merging preserves them unchanged, not re-rendered.

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
