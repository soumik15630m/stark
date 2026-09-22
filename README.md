# Stark

Personal Android odometer + ride timeline that replaces a broken motorbike display.
See [DESIGN.md](DESIGN.md) for the full product vision.

`Stark-v0.2.0.apk` (repo root) is a signed release build covering milestones **M1–M6**.

## What's implemented

**Tracking engine (M1)** — foreground location service (Fused Location), hybrid sampling
(~20 m / 3 s, 1 s on the dashboard, faster while charging), filter pipeline (adaptive accuracy
gate, teleport rejection, stationary-snap with a movement-release radius, Doppler speed),
incremental odometer (per-leg / day / lifetime, no recompute), boot re-arm, START_STICKY, and a
WorkManager watchdog.

**Trips & timeline (M2)** — leg/visit/outing segmentation, back-home outing summary
notification, end-of-day summary, map-first Timeline with a day scrubber, trip detail with route
replay, and editing (mode, label, delete, merge-with-previous, split-at-point).

**Places & map (M3)** — incremental grid-cell place clustering + auto-merge, reverse geocoding,
naming & categories, a Map tab with all routes and a most-ridden heatmap, and route replay.

**Dashboard & stats (M4)** — full-screen speedometer (arc gauge, keep-awake, 1 Hz), home-screen
widget, Quick Settings tile, calendar heatmap, week/month/year totals, riding streak, records
board, and a time-of-day histogram.

**Bike-computer & data (M5)** — fuel log with km/l & cost/km, encrypted `.stk` backup/restore
(AES-256-GCM), Google Takeout Timeline import, and privacy-clipped GPX trip sharing.

**Hardening & extras (M6)** — **full-database encryption (SQLCipher, key in the Android
Keystore)**, PIN lock + biometric + FLAG_SECURE + auto-lock, deniable **decoy volume** (duress
PIN opens a separate synthetic dataset), per-feature network toggles + master kill switch,
OTA update check against GitHub Releases (checksum + installer), automation broadcasts + a
signature-gated command receiver, night-riding (red) mode, share-current-location, and privacy
zones.

**Optional Google path (§6.6)** — keyless by default (OpenStreetMap + on-device Geocoder +
Nominatim). If you enter *your own* Google Maps Platform key in **More → Automation & network**,
reverse-geocoding switches to Google. The key is stored only in the encrypted DB, never in the
APK or repo. (Google *map-tile rendering* still uses OSM — swapping to the Google Maps SDK needs
a build-time manifest key, which would break the "zero keys in a public repo" guarantee; ask if
you want that wired behind a gitignored `secrets.properties`.)

## Known caveats / not yet done

- The deeper tracking refinements in DESIGN §4.9–4.10 (trip-start backfill buffer, activity-
  recognition auto-start, significant-motion gate, dead-reckoning in gaps, full 2D Kalman fusion)
  are **not** in this build. v1 tracks from the manual/auto foreground service with the filter
  above; it field-tested accurately, but a trip's first ~metres can be clipped by GNSS warm-up.
- Decoy volume is implemented (separate encrypted DB, synthetic seed, duress-PIN switch) but the
  full decoy-unlock switch hasn't been exercised end-to-end on a device yet.
- Encryption uses a Keystore-wrapped random key (design's master-key-in-Keystore); the stricter
  PIN→Argon2id→unwrap chain (§6.1) is a refinement not yet applied.

## Install

```bash
adb install -r Stark-v0.2.0.apk
```

First launch runs onboarding (set a PIN, grant location + notifications + background location,
exempt from battery optimization, pin Home). In **More**, exempt from battery optimization and
enable Autostart on Xiaomi/Realme/Oppo/Samsung/OnePlus so tracking survives.

## Build

Requires JDK 17 + Android SDK (platform 35). Release signing reads a gitignored
`keystore.properties` (see [PERF.md](PERF.md)).

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

## Verification

13 JVM unit tests (distance math, filter gates, segmentation). On-device: encryption confirmed
(DB file is not `SQLite format 3` at rest), tracking accumulates distance matching the GPS path,
data persists across process death, and onboarding → PIN lock → all five tabs render without
crashes on both debug and R8 release builds. See [PERF.md](PERF.md).
