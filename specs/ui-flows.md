# UI Flows

KeepSheet has five main screens — Home, Capture, Page Review, Merge, and
Document Detail. No settings screen, no onboarding wizard, no
account/login — simplicity is a feature.

Whenever a screen/view listed here is added, renamed, or removed, update
the "which screen" explainer and dropdown in
[`.github/ISSUE_TEMPLATE/`](../.github/ISSUE_TEMPLATE/) (feature_request,
bug_report, support) to match — those exist so issue reporters can name
the right screen, and go stale silently otherwise.

## 1. Home

The app's entry point.

- Two prominent actions, always available: **Scan** (opens
  [Capture](#2-capture)) and **Merge** (opens [Merge](#4-merge)).
- An **Import** action alongside them, for turning existing photos
  straight into a document without opening the camera — adds to the same
  running page list [Capture](#2-capture) uses, then proceeds to
  [Page Review](#3-page-review) the same way.
- Below those, a list of documents (most recent first), each row showing:
  name, date, page count, size.
- A **search** field filters the list by name or recognized text (see
  [Data Model](data-model.md#required-queries)) as the user types.
- Tapping a row opens that document's **Document Detail** screen.
- Each row also has a **Delete** action of its own (e.g. a trash icon),
  with the same confirmation step as Document Detail's Delete, so a
  document can be removed without opening it first.
- An empty state ("No documents yet — tap Scan to create your first
  PDF.") when there is no history and no active search.

## 2. Capture

Entered by tapping Scan on Home, or by choosing Import.

- A live camera view with a real-time overlay hinting at the detected page
  edges, so the user can see before tapping the shutter whether the whole
  page is in frame.
- A shutter action that captures a page, runs
  [automatic cropping and straightening](capture-and-processing.md#automatic-cropping-and-straightening),
  and adds it to a thumbnail strip of the pages captured so far in this
  session.
- The thumbnail strip supports reordering (drag) and retaking (replace one
  page with a new shot) — see
  [Multi-page capture](capture-and-processing.md#multi-page-capture).
- An **Import** action within this same screen adds existing photos to the
  same running list, so camera shots and imported photos can be mixed in
  one document.
- A **Done** action (enabled once at least one page has been captured)
  proceeds to [Page Review](#3-page-review).
- The system/gesture back action, with at least one page captured, asks
  for confirmation before discarding the session — losing several photos
  to an accidental back tap is exactly the kind of thing worth one extra
  tap to prevent.
- If camera permission is missing or denied, this screen must explain
  what's needed and offer a way to grant it, and still let the user
  proceed via Import alone (see
  [Permissions & Privacy](permissions-and-privacy.md)).

## 3. Page Review

Reached from Capture's Done action, or directly from Home's Import.

- Every captured/imported page, in order, each showing: its processed
  thumbnail, its current [filter](capture-and-processing.md#document-filters),
  and per-page **retake**, **remove**, and **reorder** (drag) actions —
  the same capabilities Capture's thumbnail strip offers, plus the ability
  to fine-tune a page's detected crop by hand.
- A filter picker, applying to the currently selected page (with a
  "apply to all pages" shortcut, since most documents use one filter
  throughout).
- A **Save** action finalizes the document: builds the PDF from the pages
  in their current order/filters, creates a
  [Document](data-model.md#document) with an automatically suggested name
  (see [File Naming](file-naming.md)), starts
  [OCR](capture-and-processing.md#text-recognition-ocr) in the background,
  and navigates to that document's **Document Detail** screen.
- The system/gesture back action returns to Capture with the session's
  pages intact (not discarded) — only Capture's own back action (above)
  discards the session, and only after confirmation.

## 4. Merge

Entered by tapping Merge on Home.

- A picker for selecting two or more existing PDF files — from
  KeepSheet's own document list, or from device storage — added to a
  running, reorderable selection list as they're chosen (see
  [Merging](merging.md#selecting-files-and-order)).
- The selection list supports reordering (drag) and removing an item
  before merging.
- A **Merge** action (enabled once at least two files are selected)
  produces the combined [Document](data-model.md#document) and navigates
  to its **Document Detail** screen.
- The system/gesture back action returns to Home; an in-progress
  selection isn't persisted.

## 5. Document Detail

A saved document — scanned, imported, or merged.

- A preview of the PDF's pages (thumbnails, or a swipeable page view).
- The document's **name** (see [File Naming](file-naming.md)), editable
  in place at any time.
- Summary info: date, page count, file size.
- **Back**, **Share** (hands the PDF to the platform's share sheet),
  and **Delete** controls.
- Delete has a confirmation step before it actually deletes.

## Feedback and support

It must be clear to a user how to report feedback, problems, or feature
requests, and how to find technical details (like the app version) that
issue reports ask for. KeepSheet has no in-app support flow or settings
screen of its own, so this is a single icon on Home, kept out of the way
of the primary actions and the document list, opening an **Information**
view — not another full screen/navigation destination, since there's
nothing here that needs one; a dialog over Home is enough. It shows:

- The app's version and the device model/Android version, so a user
  filing an issue doesn't have to go digging for either elsewhere — and
  can copy them straight into the matching fields the issue templates
  already ask for.
- A link to https://github.com/tomasbjerre/keepsheet/issues, opening in
  the user's browser, to report a problem or request a feature.
- A link to the [user manual](https://github.com/tomasbjerre/keepsheet/blob/main/docs/user-manual.md),
  also opening in the user's browser.

## Navigation

```
                 ┌──(tap Scan)───▶ Capture ──(tap Done)──┐
Home ────────────┤                                       ├──▶ Page Review ──(tap Save)──▶ Document Detail
                 └──(tap Import)─────────────────────────┘                                      ▲
  │                                                                                               │
  ├──(tap Merge)──▶ Merge ──(tap Merge)─────────────────────────────────────────────────────────┘
  │
  └──(tap a document row)────────────────────────────────────────────────────────────────────────┘
```
