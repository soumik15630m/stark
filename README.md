# Stark

Personal Android odometer + private, offline ride timeline that replaces a broken motorbike
display. See [DESIGN.md](DESIGN.md) for the full product vision.

This is the full **v1** build covering milestones **M1–M6**. Every release ships two APKs:

- `Stark-v<ver>.apk` — **keyless** (OpenStreetMap tiles, no API keys). This is the public build.
- `Stark-v<ver>-gmaps.apk` — same app with **Google Maps SDK** tiles, built from a gitignored
  `secrets.properties`. The two are the same package signed with the same key, so you can move
  between them with `adb install -r` and keep all your data.

## What's implemented

**Tracking engine (M1)** — foreground location service (Fused Location), motion-gated always-on
arming, hybrid sampling (~20 m / 3 s, 1 s on the dashboard, faster while charging, curvature-
adaptive on turns), a filter pipeline (adaptive accuracy gate, teleport rejection, stationary-snap
with a movement-release radius, Doppler speed), a **2D Kalman fusion** stage and **dead-reckoning**
gap estimation, trip-start warm-up backfill, incremental odometer (per-leg / day / lifetime, no
recompute), boot re-arm, START_STICKY, and a WorkManager watchdog. Battery tiers + thermal
throttling scale sampling down as the battery drains or the phone heats up.

**Trips & timeline (M2)** — leg/visit/outing segmentation, activity-recognition mode splits,
back-home outing summary notification (with an offline mini-map thumbnail), end-of-day summary,
map-first Timeline with a day scrubber, trip detail with route replay (0.5/1/2/4×, mode-aware
marker, per-marker speed readout), and editing (mode, label, delete, merge-with-previous,
split-at-point).

**Places & map (M3)** — incremental grid-cell place clustering + auto-merge, reverse geocoding,
naming & categories, a Map tab with all routes and a most-ridden heatmap (fit-to-bounds), and
route replay.

**Dashboard & stats (M4)** — full-screen speedometer (arc gauge, keep-awake, instant digits +
smoothed needle), home-screen widget (live today km, opens the PIN-free ride dashboard), Quick
Settings tile, calendar heatmap, week/month/year totals, riding streak, records board, and a
time-of-day histogram.

**Bike-computer & data (M5)** — fuel log with km/l & cost/km, encrypted `.stk` backup/restore
(AES-256-GCM), periodic daily auto-backup + restore-latest, Google Takeout Timeline import, and
privacy-clipped GPX trip sharing.

**Hardening & extras (M6)** — **full-database encryption (SQLCipher; a random DB key wrapped by an
Android Keystore key, unwrapped through a PIN → Argon2id envelope)**, PIN lock + biometric
auto-prompt + FLAG_SECURE + auto-lock, deniable **decoy volume** (duress PIN opens a separate
synthetic dataset — verified end-to-end on device), crypto-erase wipe-all, per-feature network
toggles + master kill switch, **OTA self-update** against GitHub Releases (TLS certificate pinning,
per-asset SHA-256 + signing-certificate verification before install, FileProvider installer),
automation broadcasts + a signature-gated command receiver + a Tasker/Locale plugin, night-riding
(red) mode, high-contrast sunlight mode, reduce-motion, root/tamper warning, find-my-bike,
share-current-location, and privacy zones.

**Storage** — routes are stored as delta + zig-zag + varint-packed blobs with cold deflate
recompression, plus incremental `auto_vacuum` and a tile-cache cap.

**Maps: keyless vs Google.** The keyless build uses OpenStreetMap tiles + the on-device Geocoder /
Nominatim and needs no keys at all. The `-gmaps` build renders Google Maps tiles from a build-time
key kept only in a gitignored `secrets.properties` (never in the APK manifest committed to the
repo, never in the release notes). The Google key is restricted to this package + signing SHA-1.
The in-app updater auto-picks the matching asset, so a phone on the `-gmaps` build keeps Google
Maps across OTA updates.

## Install

Public (keyless) build:

```bash
adb install -r Stark-v1.0.1.apk
```

Google Maps build:

```bash
adb install -r Stark-v1.0.1-gmaps.apk
```

Both are the same signed package — installing one over the other with `-r` keeps your PIN, keys,
and ride history intact. First launch runs onboarding (set a PIN, grant location + notifications +
background location, exempt from battery optimization, pin Home). In **More**, exempt from battery
optimization and enable Autostart on Xiaomi/Realme/Oppo/Samsung/OnePlus so tracking survives.

Updates arrive over the air: the app checks GitHub Releases and offers a verified download +
install. (OTA install requires a release-signed build — a debug build can't self-update because the
signature check will reject the mismatched signer.)

## Build

Requires JDK 17 + Android SDK (platform 35). Release signing reads a gitignored
`keystore.properties` (see [PERF.md](PERF.md)). The Google Maps build additionally reads a
gitignored `secrets.properties` with `MAPS_API_KEY=...`; without it, the build is keyless.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

## Verification

25 JVM unit tests (distance math, filter gates, segmentation, Kalman filter, dead-reckoning, point
codec, route simplification). On-device: encryption confirmed (the DB file is random bytes, not
`SQLite format 3`, at rest), the decoy PIN opens a separate synthetic dataset, tracking accumulates
distance matching the GPS path, data persists across process death **and across app updates**
(including keyless ↔ gmaps), OTA check + TLS pinning verified live, and onboarding → PIN lock →
all tabs render without crashes on both debug and R8 release builds. See
[PERF.md](PERF.md) and [IMPLEMENTATION.md](IMPLEMENTATION.md).
