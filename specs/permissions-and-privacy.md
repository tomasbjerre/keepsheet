# Permissions & Privacy

## Required access

- **Camera**, to capture pages in [Capture](ui-flows.md#2-capture).
  Requested the first time the user opens Capture, not at first launch.
  Declining it still leaves Import usable — camera access is not required
  to use KeepSheet at all.
- **Photos/media** (or the platform's equivalent, e.g. the system photo
  picker which may need no runtime permission at all), to import existing
  photos as pages, and to pick existing PDF files for
  [Merging](merging.md). Requested only when the user taps Import or
  Merge's file picker, not at first launch.
- Storage to save produced PDF files where the platform's share/save
  mechanism and other apps can reach them — using the platform's scoped
  storage/document APIs rather than requesting broad filesystem access.

## Denied or restricted permission

- If camera permission is denied, [Capture](ui-flows.md#2-capture) must
  say so clearly, offer a direct way to grant it (deep link to app
  settings if the platform requires that after a permanent denial), and
  still offer Import as a working alternative. It must never silently
  show a blank/frozen camera view.
- If photo/media access is denied, Import and Merge's file picker must say
  so clearly and offer a way to grant it, rather than silently doing
  nothing when tapped.

## Data handling

- All data (documents, pages, recognized text) stays on-device. Nothing is
  uploaded anywhere by default — there is no backend in scope for
  KeepSheet (see [Overview](overview.md)).
- Cropping, straightening, filtering, and text recognition (OCR) all run
  **on-device** — no page image or recognized text is ever sent to a
  server, KeepSheet's or anyone else's (see
  [Capture & Processing](capture-and-processing.md)).
- No analytics or crash reporting that transmits document content.
- Deleting a document (see [UI Flows](ui-flows.md)) must remove its pages
  and underlying files too — no orphaned data left behind.

## Privacy policy

- KeepSheet must publish a privacy policy (`PRIVACY.md` at the repo root)
  describing what data is accessed and where it stays. App stores (e.g.
  Google Play) require a URL to this document before they'll publish a
  listing that requests sensitive permissions (camera, photos).
- Whenever a change to this spec's "Required access" or "Data handling"
  sections changes what the app actually does with user data,
  `PRIVACY.md` must be updated in the same change — it must never
  describe behavior the app no longer has, or omit behavior it has
  gained.
