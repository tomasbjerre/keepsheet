# KeepSheet — Privacy Policy

_Last updated: 2026-09-25_

KeepSheet turns photos and camera captures into PDFs, and merges existing
PDF files into one. This page explains what data it accesses and what
happens to it. It exists to satisfy Google Play's privacy policy
requirement and must stay in sync with
[`specs/permissions-and-privacy.md`](specs/permissions-and-privacy.md),
which is the source of truth for KeepSheet's data-handling behavior.

## Data KeepSheet accesses

- **Camera**, only when you're capturing a page in the app.
- **Photos/media**, only when you choose to import a photo as a page, or
  pick an existing PDF file to merge.
- The documents this produces: the page images you capture or import, the
  PDF files KeepSheet builds or merges, and the text recognized on those
  pages (so the PDFs are searchable).

## Where your data goes

- Everything above stays **on your device**, in local storage. KeepSheet
  has no backend, no server, and no account system — there is nothing for
  it to upload data to, even if it wanted to.
- A page you capture or import never leaves KeepSheet's own private
  storage for your device's shared photo library — it won't show up in
  your camera roll, and won't get swept into that library's own cloud
  backup.
- Cropping, straightening, filtering, and text recognition (OCR) all run
  **on-device**. No page image, PDF, or recognized text is ever sent
  anywhere.
- KeepSheet does not use analytics, crash reporting, or advertising SDKs
  of any kind, and does not sell or share your data with anyone.

## Your controls

- Deleting a document in the app removes its pages and PDF file
  immediately — nothing orphaned is left behind.
- Uninstalling KeepSheet removes all of its local data from your device.
- KeepSheet doesn't keep a permanent archive: every document (and any
  page still on disk from a scan you didn't finish) is cleared the next
  time you open KeepSheet after it's been fully closed — not while
  you're just using another app in the meantime. Share (export) anything
  you want to keep, to wherever you choose, before then.

## Changes

If what KeepSheet collects or does with it ever changes, this page will be
updated in the same change that changes the behavior (see
[`AGENTS.md`](AGENTS.md)'s "specs first" rule) — it will never describe
behavior the app doesn't actually have.

## Contact

Questions about this policy or KeepSheet's data handling: open an issue at
<https://github.com/tomasbjerre/keepsheet/issues>.
