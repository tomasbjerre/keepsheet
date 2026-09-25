# Accessibility

Cross-cutting requirements that apply everywhere KeepSheet shows a page
image or document information — not tied to one screen. When adding a new
screen or overlay, it must follow these too, not just the ones that
happened to exist when this was written.

## Filter and crop feedback

- The detected page-edge overlay in [Capture](ui-flows.md#2-capture) and
  the crop handles in [Page Review](ui-flows.md#3-page-review) are shown
  with enough contrast to stay visible against any photographed
  background — not just "a color", but a treatment (e.g. an outline/halo)
  that holds up against light paper, dark wood, patterned fabric, etc.
- The three [document filters](capture-and-processing.md#document-filters)
  (color, grayscale, black-and-white) are distinguished in their picker by
  a labeled thumbnail preview of the actual page under that filter, not by
  color alone — so the choice is legible to a user who can't rely on
  color to tell them apart.

## Text contrast

- All text presenting document information must meet WCAG AA contrast
  against its background: **4.5:1** for normal text, **3:1** for large
  text (roughly 18pt+, or 14pt+ bold).
- A page thumbnail is not a solid background — its content varies by what
  was scanned. Text is never placed directly over a page thumbnail relying
  on a single fixed color to "probably" contrast; where document info
  needs to be near a thumbnail (e.g. Document Detail's summary), it sits
  on its own solid-background panel instead.
- Meeting the contrast requirement and looking good for users who don't
  need it are the same goal, not a trade-off — pick a palette that
  satisfies both rather than defaulting to plain black-on-white only
  where accessibility is being checked.

## Touch targets

- Capture's shutter action, and every per-page action in the thumbnail
  strip/review list (reorder handle, retake, remove), must meet a minimum
  touch target size (44×44dp or larger) even when the thumbnail itself is
  smaller — a small preview image is not an excuse for a small tap target
  layered on top of it.
