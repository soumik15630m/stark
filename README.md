# Stark

Personal Android odometer + ride timeline that replaces a broken motorbike display.
See [DESIGN.md](DESIGN.md) for the full product vision.

This repository currently implements **M1 — the trustworthy odometer** (the first milestone
in DESIGN.md §14), plus the live speedometer. It is a real, installable app you can ride with.

## What works in this build (v0.1.0)

- **Live tracking service** — foreground service (type `location`) using the Fused Location
  Provider, hybrid sampling (~20 m / 3 s, 1 s on the dashboard, faster while charging).
- **Filter pipeline** — adaptive accuracy gate, teleport rejection, and a stationary snap that
  holds an anchor against parked drift but releases on real movement.
- **Incremental odometer** — distance is summed per fix (equirectangular for short hops,
  haversine for long spans) and folded into per-leg, per-day and lifetime totals with no
  recompute. Totals survive process death (WAL).
- **Segmentation** — legs open on movement and close after a 5-minute stop; sub-50 m legs are
  discarded as false starts.
- **UI** — Today hub (lifetime/today bike + all-mode odometers, live card, today's trips),
  Trips timeline grouped by day, full-screen Speedometer (arc gauge, keep-awake, 1 Hz sampling),
  and a More tab (diagnostics, battery-optimization / OEM guidance, app info).
- **Reliability** — `START_STICKY`, boot receiver re-arms tracking if it was active, and a
  fix-age watchdog that zeroes the dial when GPS goes quiet.

## Not in this build yet (next milestones, per DESIGN.md)

- Encryption (SQLCipher + Argon2id) — the DB schema is encryption-ready; v0.1.0 uses plain Room
  for field-test reliability.
- Maps, route replay, heatmap, stats/records, places & geocoding.
- Trip-start backfill buffer, activity-recognition auto-start, dead-reckoning gaps.
- Fuel log, `.stk` backup/restore, Timeline import, sharing.
- OTA self-update, decoy volume, automation/Tasker, network toggles.

## Install (sideload)

`Stark-v0.1.0.apk` (repo root) is a signed release build.

```bash
adb install -r Stark-v0.1.0.apk
```

Or copy it to the phone and open it (allow "install unknown apps" for your file manager).
On first launch, tap **Start tracking** and grant location + notifications; then allow
**background location** ("Allow all the time") so rides are captured with the screen off.
In **More**, exempt Stark from battery optimization (and enable Autostart on Xiaomi/Realme/
Oppo/Samsung/OnePlus) so the OS doesn't kill tracking.

## Build from source

Requires JDK 17 and the Android SDK (platform 35, build-tools 35).

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:assembleRelease      # signed; needs keystore.properties (not committed)
```

Signing for release reads `keystore.properties` at the repo root (gitignored):

```
storeFile=app/stark-release.jks
storePassword=...
keyAlias=stark
keyPassword=...
```

## Verification

- 13 JVM unit tests cover the distance math (`GeoTest`), the filter gates (`LocationFilterTest`),
  and incremental segmentation (`SegmenterTest`).
- End-to-end on the emulator: an injected GPS ride produced a closed leg whose distance matched
  the captured GPS path (711 m over the ~710 m captured span), with day and lifetime odometers
  incremented to match and the trip persisted across a force-stop. See [PERF.md](PERF.md).
