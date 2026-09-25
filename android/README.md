# KeepSheet — Android

Native Android implementation (Kotlin + Jetpack Compose) of the spec in
[`../specs`](../specs/README.md). If you're about to change behavior, check
the spec first — it's the source of truth, not this code.

## Stack

- Kotlin + Jetpack Compose (Material 3), no XML layouts.
- [Room](https://developer.android.com/training/data-storage/room) for local
  storage (documents/pages, once implemented — see
  `specs/data-model.md`).
- [CameraX](https://developer.android.com/training/camerax) for capturing
  pages (`specs/capture-and-processing.md#multi-page-capture`).
- Still to be added, as the corresponding user stories are implemented (see
  the `TODO` in `app/build.gradle.kts`):
  - An edge-detection/perspective-correction library (e.g.
    [OpenCV](https://opencv.org/), Apache 2.0) for
    `specs/capture-and-processing.md#automatic-cropping-and-straightening`.
  - An on-device OCR engine (e.g.
    [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android),
    which supports Swedish) for
    `specs/capture-and-processing.md#text-recognition-ocr`.
  - A PDF library capable of merging existing documents (not just
    rendering/rasterizing pages) for `specs/merging.md`.

## Local development setup

1. Install [Android Studio](https://developer.android.com/studio). It
   bundles a JDK; no separate Java install needed.
2. Open this `android/` directory in Android Studio (**not** the repo
   root) — `File > Open`.
3. Let Android Studio's first-run Gradle sync finish. It will prompt to
   install the Android SDK platform/build-tools this project needs
   (`compileSdk 36`, `minSdk 26`) — accept that.
4. Run the `app` configuration on an emulator or a physical device (USB
   debugging enabled) via the ▶ button, or:

   ```bash
   ./gradlew installDebug
   ```

No API keys, accounts, or backend setup are required to build and run.

### Command line only (no Android Studio)

```bash
export ANDROID_HOME=/path/to/android/sdk   # needs platform-tools, platforms;android-36, build-tools;36.0.0
./gradlew assembleDebug                     # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                 # unit tests — see "Testing" below
./gradlew installDebug                      # installs on a connected device/emulator
```

## Code quality

These all run in CI (`../.github/workflows/ci_android.yml`, via the shared
[`bundle-android-ci.yaml`](https://github.com/tomasbjerre/.github/blob/master/.github/workflows/bundle-android-ci.yaml))
and locally:

```bash
./gradlew ktlintCheck   # Kotlin style (ktlintFormat to auto-fix)
./gradlew detekt        # Kotlin static analysis (config/detekt.yml)
./gradlew lintDebug     # Android Lint
./gradlew spotlessCheck # formatting for non-Kotlin files (spotlessApply to auto-fix)
./gradlew violations    # aggregates the above into one summary; fails on ERROR-level findings
                         # — see https://github.com/tomasbjerre/violations-gradle-plugin
```

### Keeping dependencies current

```bash
./gradlew showUpdateableDependencies   # what's outdated
./gradlew updateDependencies           # bump build.gradle.kts in place
```

From [update-versions-gradle-plugin](https://github.com/tomasbjerre/update-versions-gradle-plugin).
Dependency versions are plain literals in `build.gradle.kts` (no
`libs.versions.toml`) specifically so this plugin can rewrite them — it
doesn't support version catalogs. Renovate (`../renovate.json`) also keeps
this repo's dependencies current via PRs.

## Testing

No mocking libraries (Mockito, MockK, etc.) — tests exercise real behavior
against real collaborators instead of mocked ones, so a passing test means
the requirement actually works, not that a mock was told to say so:

- **Pure logic** — plain JUnit Jupiter tests, no framework dependencies.
- **Storage** (once Room is wired up) — runs against a real in-memory
  SQLite database via Room + [Robolectric](http://robolectric.org/), not a
  mocked DAO, verifying every query listed in
  `specs/data-model.md#required-queries` directly.
- **Schema migrations** — see `specs/data-model.md#data-integrity-on-start`:
  a schema change must carry existing data forward, not silently drop it.
- Assertions use [AssertJ](https://assertj.github.io/doc/).

Run them with `./gradlew testDebugUnitTest`.

When you add a requirement to `specs/`, add a test for it here — see
[`../AGENTS.md`](../AGENTS.md).

### Screenshots

`../.github/workflows/instrumented_android.yml` runs instrumented tests on
an emulator and pulls any screenshots a `ScreenshotTest` (once one exists —
see `app/src/androidTest`) writes to `/sdcard/keepsheet-screenshots`, the
same convention the shared release workflow
([`bundle-android-release.yaml`](https://github.com/tomasbjerre/.github/blob/master/.github/workflows/bundle-android-release.yaml))
expects for installing screenshots into the Play listing and
`../docs/screenshots/`. This workflow is intentionally **local** to this
repo (not part of the shared `bundle-android-ci.yaml`), since emulator
setup — seeded data, permissions, any sensor simulation a future camera
test needs — tends to be app-specific. It runs as a sibling job from both
CI and Release Android (`../.github/workflows/release_android.yml`).

## Play Store release

1. [`../.github/workflows/release.yml`](../.github/workflows/release.yml)
   keeps a **draft** GitHub Release up to date with a changelog rendered
   from [Conventional Commits](../AGENTS.md) on every push to `main` — via
   the shared
   [`bundle-draft-release.yaml`](https://github.com/tomasbjerre/.github/blob/master/.github/workflows/bundle-draft-release.yaml)
   in [`tomasbjerre/.github`](https://github.com/tomasbjerre/.github). It
   never tags or publishes anything itself.
2. Publishing that draft — by hand in the GitHub UI, `gh release edit
   --draft=false`, or the org-wide scheduled `publish-draft-releases.yaml`
   (runs monthly across every `tomasbjerre` repo) — creates the real tag
   and fires a `release: published` event.
3. [`../.github/workflows/release_android.yml`](../.github/workflows/release_android.yml)
   listens for that event: runs this repo's own `instrumented_android.yml`
   for fresh screenshots, then calls the shared
   [`bundle-android-release.yaml`](https://github.com/tomasbjerre/.github/blob/master/.github/workflows/bundle-android-release.yaml),
   which installs those screenshots into the Play listing and
   `../docs/screenshots/`, builds a signed App Bundle and APK, uploads the
   bundle to the Play Console's **internal** track
   ([Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)),
   attaches the build outputs to the GitHub Release, and updates
   `../CHANGELOG.md`.

Promoting a release from internal → production is a manual step in the
[Play Console](https://play.google.com/console) — intentionally not
automated, so a real person always looks at a release before it reaches
real users.

### One-time setup (not automatable — Google account actions)

1. Create a [Play Console](https://play.google.com/console) developer
   account (one-time $25 fee, identity verification) — skip this step if
   already done for another app under the same account.
2. Create the app in the Console (package name
   `com.github.tomasbjerre.keepsheet`, permanent once chosen) and complete
   its first store listing, content rating, and data safety form — Google
   requires this once per app before the API can publish to it. For the
   store listing's privacy policy URL, use the GitHub-rendered link to
   [`../PRIVACY.md`](../PRIVACY.md), e.g.
   `https://github.com/tomasbjerre/keepsheet/blob/main/PRIVACY.md`.
3. Create a Google Cloud project, enable the Android Publisher API, create
   a service account with a JSON key, and grant it publishing access to
   this app in Play Console → Users and permissions. Full steps:
   [GPP's Service Account guide](https://github.com/Triple-T/gradle-play-publisher#service-account).
4. Add these repo secrets (`Settings → Secrets and variables → Actions`):
   - `PLAY_SERVICE_ACCOUNT_JSON` — the full JSON key from step 3.
   - `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
     `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` — the app signing
     (upload) key. Generate one and back it up somewhere durable — it's
     the only copy, and losing it means losing the ability to publish
     updates under this app's identity.
