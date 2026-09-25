# KeepSheet — Specifications

KeepSheet turns photos, camera captures, and existing PDF files into clean,
organized PDF documents. It scans paper into crisp, searchable pages and
lets you merge multiple PDFs into one, in whatever order you choose.

These specs describe **what** KeepSheet does and the **contracts** an
implementation must satisfy. They intentionally say nothing about
frameworks, languages, or libraries, so that multiple technical
implementations (e.g. native Android, native iOS, a future desktop
companion) can all be built against the same behavior.

Implementations live in their own top-level directories (e.g. `android/`)
and must conform to these documents. If an implementation needs to deviate
from a spec, the spec should be updated first — code should never be the
source of truth for behavior.

## Documents

- [Overview](overview.md) — product scope, goals, and non-goals
- [Data Model](data-model.md) — the entities every implementation must
  persist, independent of storage technology
- [Capture & Processing](capture-and-processing.md) — turning photos or
  camera shots into clean scanned pages: cropping, straightening, filters,
  multi-page capture, and OCR
- [Merging](merging.md) — combining multiple PDF files into one, in a
  chosen order
- [File Naming](file-naming.md) — the automatic naming pattern applied to
  every PDF KeepSheet produces
- [UI Flows](ui-flows.md) — the screens and interactions a user goes through
- [Permissions & Privacy](permissions-and-privacy.md) — what access is
  needed and how data is handled
- [Accessibility](accessibility.md) — legibility and contrast, everywhere
