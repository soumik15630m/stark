# PERF run sheet

Per DESIGN.md Appendix A, verification results are logged here with device + Android version.

## 2026-09-23 — v0.4.x (field-feedback fixes)

Addressing real-device feedback:
- **Security re-lock:** app now re-locks on every background/screen-off (verified on device: home
  → reopen and screen-off → reopen both require PIN). PIN-free ride dashboard still covers
  glanceable needs.
- **Biometric on open:** the lock screen auto-launches the biometric prompt (PIN fallback).
- **Speedometer jitter:** EMA 0.35 (calmer), needle tween 600 ms, digit animated (rolls through
  values). Recorded speed unchanged.
- **Idle / false trips:** legs whose max speed never reaches ~9 km/h are discarded as parked
  drift (unit-tested); stationary-snap release is accuracy-aware. Fixes the "0.1 km trip while
  asleep" report.
- **Honest average speed:** avg is now distance ÷ *moving* time (idle excluded); segmenter tracks
  moving time.
- **Always-on service:** persistent foreground notification (survives app close, restarts on
  boot), motion-gated GPS (ARMED idle → ACTIVE on significant motion → ARMED on a confirmed
  stop), with **Pause / Resume / Stop** notification actions. Verified on device: armed persistent
  notification present; ARMED→ACTIVE records a trip (29 km/h, 0.6 km) to the encrypted DB.
  Hardened the disable→re-enable race with stopSelf(startId).
- **Timeline:** home-to-home outing aggregate, speed-colour-graded route, stop markers (≥5 min)
  and brief-pause markers (1–5 min), per-trip summary (avg/max/wait/places).
- **Optional Google Maps:** all map surfaces route through one `MapSurface` that uses the Google
  Maps SDK when a key is present (gitignored secrets.properties), else OSM.

## 2026-09-23 — v0.3.0 (deep tracking + Argon2id + decoy + optional Google)

- **2D Kalman fusion:** integrated into the filter; unit test confirms it doesn't exceed the raw
  jittered distance and stays within 15% of truth on a noisy straight line.
- **Dead-reckoning:** short-gap distance = speed×time (truer through tunnels), bounded ≤3× the
  straight line, long gaps fall back to straight line; 4 unit tests.
- **Argon2id:** PIN verifier + `.stk` backup KDF + PIN→Argon2id→master-key envelope. Onboarding
  set-PIN and unlock run Argon2id off the main thread (no ANR); verified on device.
- **Decoy volume — end-to-end on device:** real PIN → `stark_enc.db` (0 km); decoy PIN →
  `stark_decoy.db` (223 km synthetic). Two separate encrypted volumes confirmed.
- **PIN-free ride dashboard:** speedometer + today/trip/lifetime + start/stop reachable from the
  lock screen without the PIN; verified.
- **Activity-Recognition auto-start** + significant-motion arming wired (toggle in More).
- **Optional Google path:** geocoding uses a user key at runtime (encrypted Setting); Google map
  rendering behind a gitignored `secrets.properties` (BuildConfig.HAS_GOOGLE_MAPS), default OSM.
- Unit tests: 19 pass. R8 release builds and installs clean.

## 2026-09-23 — v0.2.0 (M1–M6)

- **Real-device (Soumik's phone):** M1 tracking + odometer + speedometer rode flawlessly.
  Follow-up: speedometer made more responsive (EMA 0.6, needle tween 220 ms, dashboard sampling
  min-interval 500 ms) — display-only, recorded speed unchanged.
- **Encryption at rest (emulator):** `stark_enc.db` header is random bytes, not `SQLite format 3`
  — SQLCipher encryption confirmed. Tracking + odometer work on the encrypted build.
- **R8 release:** installs and launches with SQLCipher + osmdroid (keep rules added); no strip.
- **Full UI walkthrough (emulator):** onboarding → PIN set → lock → PIN unlock → all five tabs
  (Today, Timeline, Map, Stats, More) render with no app crashes.
- **Custom permission** scoped to `${applicationId}` so debug + release coexist.
- Unit tests: 13 pass.

## 2026-09-22 — v0.1.0 (M1)

**Environment:** Android emulator, Pixel 8 Pro AVD (Android 16 / API 36 system image),
swiftshader GPU, headless. Functional verification via injected GPS (`adb emu geo fix`) and
direct DB inspection.

### A.2 Accuracy (functional, emulator)

| Check | Result |
|---|---|
| Distance summation on a straight injected ride | leg distance 711.4 m vs ~710 m captured GPS span (latE7 129719583→129783565) — matches |
| Day + lifetime odometer consistency | both = 711.4 m, equal to the single leg — incremental totals correct |
| Parked-drift inflation | unit-tested: speed≈0 within 15 m radius contributes 0 m (`stationary_snap_holds_anchor_against_drift`) |
| Teleport rejection | unit-tested: >70 m/s implied jump dropped (`rejects_teleport`) |

Note: the injected trip lost its opening ~50 m because the emulator's fused provider began
delivering ~9 s after injection started (cold GNSS, no warm-keep). On real hardware this is the
gap the design's trip-start backfill closes (deferred past M1).

### A.5 Reliability (functional, emulator)

| Check | Result |
|---|---|
| Data loss on process kill | trip fully persisted after `am force-stop` mid-session (WAL) — 0 loss for flushed segments |
| Foreground service | starts with `foregroundServiceType=location` (0x8), live-odometer notification shown |
| Boot re-arm | `BootReceiver` restarts the service when tracking was active (code path; not yet reboot-tested on hardware) |

### Unit tests

13 tests pass (`GeoTest` 4, `LocationFilterTest` 6, `SegmenterTest` 3): distance math,
filter gates, incremental segmentation.

### Not yet measured (need real hardware / instrumentation)

Battery (A.1), responsiveness/latency (A.3), storage growth (A.4), thermal (A.6). These require
a real device and Macrobenchmark/Battery Historian runs — to be filled in during the field test.
