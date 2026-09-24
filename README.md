<div align="center">

# Stark

**A private, offline motorbike odometer and ride-timeline for Android.**

Built to replace a broken bike display — it tracks distance, speed, trips and places entirely
on-device, with an encrypted database and no accounts, servers, or telemetry.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%2012%2B-3DDC84.svg?logo=android&logoColor=white)](#requirements)
[![Made with Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF.svg?logo=kotlin&logoColor=white)](#tech-stack)
[![Latest release](https://img.shields.io/github/v/release/soumik15630m/stark?label=release)](https://github.com/soumik15630m/stark/releases/latest)

</div>

---

## Table of contents

- [Features](#features)
- [Download](#download)
- [Requirements](#requirements)
- [Building from source](#building-from-source)
- [Updates](#updates)
- [Privacy & security](#privacy--security)
- [Tech stack](#tech-stack)
- [Project layout](#project-layout)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Accurate tracking** — foreground location service with a filter pipeline (accuracy gate,
  teleport rejection, stationary snapping, Doppler speed), a 2D Kalman fusion stage, dead-reckoning
  through GPS gaps, and trip-start warm-up backfill. Motion-gated auto-start, boot re-arm, and a
  watchdog keep it running; battery and thermal tiers scale sampling down as needed.
- **Trips & timeline** — automatic leg / visit / outing segmentation, a map-first timeline with a
  day scrubber, route replay (0.5–4×), and full editing (label, mode, delete, merge, split).
- **Places & maps** — grid-cell place clustering, reverse geocoding, and a map with all your routes
  plus a most-ridden heatmap.
- **Dashboard & stats** — full-screen speedometer, home-screen widget, Quick Settings tile,
  calendar heatmap, week/month/year totals, streaks, records, and a time-of-day histogram.
- **Bike computer** — fuel log with km/l and cost/km, encrypted `.stk` backup/restore, Google
  Takeout import, and privacy-clipped GPX export.
- **Private by design** — full-database encryption, PIN + biometric lock, a deniable decoy volume,
  per-feature network toggles with a master kill switch, and crypto-erase.
- **Over-the-air updates** — checks GitHub Releases and installs verified updates in place.

See [DESIGN.md](DESIGN.md) for the full product vision and [IMPLEMENTATION.md](IMPLEMENTATION.md)
for how each piece is built.

<!-- Screenshots: add images to docs/ and reference them here, e.g.
## Screenshots
| Dashboard | Timeline | Speedometer |
|---|---|---|
| ![](docs/dashboard.png) | ![](docs/timeline.png) | ![](docs/speedo.png) |
-->

## Download

Grab the latest APK from the [**Releases**](https://github.com/soumik15630m/stark/releases/latest)
page. Each release ships two builds of the same signed app:

| Build | File | Maps |
|---|---|---|
| **Keyless** (recommended) | `Stark-v<version>.apk` | OpenStreetMap — no API keys |
| **Google Maps** | `Stark-v<version>-gmaps.apk` | Google Maps SDK |

Install with:

```bash
adb install Stark-v1.0.1.apk
```

Both builds share the same package and signing key, so you can switch between them with
`adb install -r` without losing any data.

On first launch, onboarding walks you through setting a PIN and granting location, notification, and
background-location permissions. On Xiaomi / Realme / Oppo / Samsung / OnePlus, also enable
Autostart and exempt the app from battery optimization so tracking survives in the background.

## Requirements

- Android 12 (API 31) or newer.
- To build: **JDK 17** and the **Android SDK** (platform 35).

## Building from source

```bash
git clone https://github.com/soumik15630m/stark.git
cd stark

# Debug build
./gradlew :app:assembleDebug

# Run the unit tests
./gradlew :app:testDebugUnitTest

# Signed release build
./gradlew :app:assembleRelease
```

**Signing** (release builds) reads a gitignored `keystore.properties`:

```properties
storeFile=stark-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

**Google Maps build** (optional) reads a gitignored `secrets.properties`:

```properties
MAPS_API_KEY=your_android_maps_key
```

Without `secrets.properties`, the build is keyless and uses OpenStreetMap. The Maps key never
enters the committed source, the APK manifest in the repo, or the release notes; restrict it to this
package (`com.soumik.stark`) and your signing SHA-1.

## Updates

The app checks GitHub Releases and offers to download and install newer builds over the air. Updates
are verified before install — TLS certificate pinning on the download, a per-asset SHA-256 checksum,
and a signing-certificate match against the installed app. The updater automatically picks the asset
matching your build, so a phone on the Google Maps build keeps Google Maps across updates.

> OTA self-update only works on a release-signed build. A locally built debug APK cannot update
> itself, because the signature check rejects the mismatched signer.

## Privacy & security

Stark is offline-first and stores everything on the device:

- **Encrypted at rest** — the whole database is encrypted with SQLCipher. A random database key is
  wrapped by an Android Keystore key and unlocked through a PIN → Argon2id envelope.
- **Locked** — PIN plus optional biometrics, screenshot blocking (`FLAG_SECURE`), and auto-lock.
- **Deniable** — an optional decoy volume: a duress PIN opens a separate synthetic dataset.
- **No servers** — the only network calls are optional reverse geocoding and the GitHub update
  check, each behind its own toggle with a master network kill switch. Crypto-erase wipes all data.

There is no analytics, no account, and no cloud sync.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Room + SQLCipher · Fused Location Provider ·
WorkManager · osmdroid (OpenStreetMap) / Google Maps SDK · Argon2id · Android Keystore.

## Project layout

```
app/            Android application module (Kotlin/Compose)
  src/main      app code (tracking, data, ui, crypto, update, …)
  src/test      JVM unit tests
DESIGN.md         product vision & specification
IMPLEMENTATION.md what was built, mapped to the design
PERF.md           performance notes & signing setup
```

## Documentation

- [DESIGN.md](DESIGN.md) — the product vision and specification.
- [IMPLEMENTATION.md](IMPLEMENTATION.md) — how each designed feature maps to the code.
- [PERF.md](PERF.md) — performance notes and build/signing setup.

## Contributing

This is a personal project, but issues and pull requests are welcome. Please open an issue to
discuss significant changes first, keep changes focused, and make sure `./gradlew :app:testDebugUnitTest`
passes before submitting.

## License

Licensed under the [Apache License 2.0](LICENSE).
