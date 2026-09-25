# Working in this repository

KeepSheet keeps its specification separate from its implementation on
purpose — see [`specs/README.md`](specs/README.md). This is so KeepSheet
can eventually have multiple technical implementations (Android today,
iOS later, maybe others) that all behave the same way.

## Rule: specs first

Any new requirement — a new feature, a changed behavior, a new constraint
— must be added or updated in `specs/` as part of the same change, before
or alongside touching `android/` (or any other implementation). This
applies regardless of which platform prompted the requirement: something
asked for "for Android" still belongs in the platform-agnostic spec if
it's a behavior, not an Android-specific implementation detail.

- If a change is purely a technical/implementation detail with no
  user-visible or contract-level behavior difference (e.g. swapping a
  library, refactoring), it does not need a spec change.
- If you're unsure whether something is a spec-level requirement or an
  implementation detail, prefer adding it to `specs/` — it's cheap to
  document and expensive to lose track of.
- Never let `android/` (or any implementation) be the only place a
  behavior is defined. If you find implementation behavior that isn't
  reflected in `specs/`, fix that by updating the spec, not by leaving it
  undocumented.

## Rule: test behavior, not mocks

Every requirement in `specs/` should have a test that verifies the actual
requirement, not an implementation detail. See
[`android/README.md#testing`](android/README.md#testing) for how this
works in the Android implementation.

- No mocking libraries (Mockito, MockK, etc.). Test against real
  collaborators — a real in-memory database, a pure function fed real
  input — not a mock told what to return. A passing test should mean the
  requirement works, not that a mock was configured correctly.
- If a class is hard to test without mocks, that's usually a sign it's
  doing too much — prefer extracting the pure/testable logic over
  reaching for a mock.
- When you add or change a requirement in `specs/`, add or update the test
  that covers it in the same change.

## Rule: ship every bug fix and feature as a PR

Work on a branch and open it as a pull request (`gh pr create`) instead of
pushing straight to `main` — this applies to every change that fixes a bug
or implements a feature (i.e. anything that would get a `fix`/`feat`
commit type per the commit conventions). Purely mechanical chores (e.g.
`chore`/`ci`/`docs` one-liners) can still go straight to `main` if asked.

- Enable auto-merge on the PR right after opening it —
  `gh pr merge --auto --squash` (squash is this repo's default merge
  method; `main`'s ruleset only requires the `build` status check to
  pass, no manual review, so this is what lets a PR land on its own once
  CI is green instead of sitting there needing a manual merge click).
  Skip this only if asked to hold off, or if the change needs a human
  look before merging (e.g. something you're unsure about, or the user
  said to wait).
- If the change is user-visible (touches a Home/Capture/Page
  Review/Merge/Document Detail screen — see `specs/ui-flows.md`), the PR
  description must include a screenshot of the affected screen. Capture
  it with the local Android emulator (see
  [`android/README.md#screenshots`](android/README.md#screenshots))
  rather than skipping it — there's no other reliable way to prove the
  change actually renders correctly.
- Prefer sourcing that screenshot from `docs/screenshots/` (adding/
  updating the relevant one there as part of the PR) so the same image
  documents the feature in the repo and in the PR, instead of a throwaway
  image that only lives in the PR description.
- Embed it in the PR body as
  `![<label>](https://raw.githubusercontent.com/tomasbjerre/keepsheet/<branch>/docs/screenshots/<file>.jpg)`
  once the branch is pushed — GitHub doesn't render repo-relative image
  paths in PR bodies, only absolute URLs.
- If the change adds or changes a feature (not a pure bug fix), also
  update [`docs/user-manual.md`](docs/user-manual.md) in the same PR —
  it's the user-facing walkthrough of every screen/control, and it going
  stale is exactly how a manual stops being trustworthy. A pure bug fix
  usually doesn't need this (the screen already worked the way the manual
  describes); a new control, a changed flow, or a new screen state does.

## Layout

- `specs/` — what KeepSheet does, independent of any implementation.
- `android/` — the native Android (Kotlin + Jetpack Compose)
  implementation.
