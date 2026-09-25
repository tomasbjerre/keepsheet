# KeepSheet

Turn paper and photos into clean, searchable PDFs — scan pages with your
camera or import photos, auto-crop and straighten them, and merge existing
PDFs into one, in whatever order you choose. No accounts, no cloud —
everything happens and stays on your device.

<a href="https://play.google.com/store/apps/details?id=com.github.tomasbjerre.keepsheet">
  <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="60">
</a>

*(Link goes live once the first release is published — see [Play Store release](android/README.md#play-store-release).)*

New to KeepSheet? See the [user manual](docs/user-manual.md) for a
walkthrough of every screen and control.

## Screenshots

<img src="docs/screenshots/1-home.jpg" alt="Home screen" width="200">

More, covering every screen/state as they're implemented, in
[`docs/screenshots/`](docs/screenshots/).

## Structure

- [`specs/`](specs/README.md) — implementation-independent specification
  of what KeepSheet does. Read this first. Any implementation must
  conform to it; if behavior needs to change, change the spec, not just
  the code.
- [`android/`](android/README.md) — the native Android (Kotlin + Jetpack
  Compose) implementation.
- [`docs/user-manual.md`](docs/user-manual.md) — the user-facing
  walkthrough of every screen and control, with screenshots.

## Android quick start

See [`android/README.md`](android/README.md) for local dev setup.

```bash
cd android
./gradlew assembleDebug
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).
