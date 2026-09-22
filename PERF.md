# PERF run sheet

Per DESIGN.md Appendix A, verification results are logged here with device + Android version.

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
