# Stark — design vs. implementation

What [DESIGN.md](DESIGN.md) asked for, and what's actually in the build as of **v0.3.0**.
Legend: ✅ done · 🟡 partial · ⬜ not yet.

## 1. Goals
| Goal | Status | Notes |
|---|---|---|
| Accurate odometer (truer than Google) | ✅ | Incremental Haversine, 2D Kalman de-jitter, dead-reckoned gaps, never-undercount rules |
| Live dashboard / speedometer | ✅ | Full-screen gauge + PIN-free ride dashboard |
| Detailed timeline (legs/visits/outings) | ✅ | |
| Negligible battery (≤3–5%/day) | 🟡 | Levers built (trip-only FG, gating, batching, hardware FIFO); not yet benchmarked on hardware |
| Fully private & offline-first | ✅ | SQLCipher at rest, per-feature network toggles + kill switch, zero telemetry |
| Self-updating via GitHub Releases | ✅ | Check + checksum verify + installer |

## 2. Vocabulary / data model (§2, §5)
Point, Leg, Visit, Place, Outing, DailyTotal, LifetimeTotal, FuelFill, Record, HeatTile,
PrivacyZone, Setting — all modelled ✅. `Vehicle` is implicit (id=1) ⬜. Coordinates are scaled
int e7 ✅ but **not** delta+varint packed ⬜ (stored as absolute e7). `DisplayGeom` table exists
but simplified render geometry isn't generated yet 🟡.

## 3. Architecture & stack (§3)
Package layout matches the design ✅. Kotlin + Compose + M3 dynamic color ✅. Room over SQLCipher
✅. Fused Location + Activity Recognition ✅. Foreground service + WorkManager ✅ (exact
AlarmManager ping ⬜ — WorkManager only). osmdroid + OSM tiles ✅. Optional Google (geocoding
runtime + maps build-variant) ✅. Argon2id + AES-256-GCM ✅. Automation intents ✅ (formal
Tasker/Locale plugin ⬜).

## 4. Tracking engine (§4)
| Item | Status | Notes |
|---|---|---|
| State machine IDLE→DETECTING→TRACKING, FG only on trips | ✅ | Auto-start via Activity Recognition |
| Triple gating: AR / significant-motion / geofence-exit | 🟡 | AR ✅ + significant-motion ✅; geofence-exit ⬜ |
| Hybrid 20 m / time-ceiling sampling | ✅ | 20 m/3 s, dashboard 1 s, charging boost |
| Low-battery tiers (degrade / pause) | 🟡 | LOW_POWER profile exists; not auto-triggered by battery % yet |
| Accuracy gate (adaptive) | ✅ | Relaxes in persistent poor signal, flags LOW |
| Speed/teleport rejection | ✅ | |
| 2D Kalman sensor-fusion (pos+vel, ENU) | ✅ | Doppler-seeded; unit-tested (de-jitter = truer distance) |
| Stationary snap | ✅ | Anchor + movement-release radius |
| Map-matching NOT used for distance | ✅ | By design |
| Incremental distance | ✅ | equirectangular short hop / haversine long |
| Trip-start backfill | 🟡 | Fresh warmup fix on start; always-on idle buffer limited by Android bg rules |
| Gap handling (straight-line + flag) + dead-reckoning | ✅ | Short gaps counted at speed×time (truer, bounded); flagged ESTIMATED; unit-tested |
| Outing / base smart-anchor | 🟡 | Base = Home or first place; one-time ambiguity prompt ⬜ |
| Watchdog: WorkManager + boot re-arm + START_STICKY | ✅ | OEM anti-kill guidance in-app |
| GNSS constellations / dual-frequency | 🟡 | Relies on HIGH_ACCURACY (OS chooses); no explicit GnssMeasurement tuning |
| Doppler speed + hardware FIFO batching | ✅ | maxUpdateDelay when dashboard closed |
| Curvature-adaptive sampling | ⬜ | |
| Accel-confirmed stops / thermal throttling | ⬜ | Stops are GPS-speed + time based |
| GNSS-time authoritative | 🟡 | Uses fix time (GPS-sourced) + stored offset; no explicit clock-vs-GNSS arbitration |
| Mode-change leg splitting | ⬜ | Mode is per-leg + manual override |
| Pause vs stop (2 thresholds) + confidence HIGH/LOW/EST | ✅ | |

## 4A / 4B. Performance & tricks
Single-thread per-fix dispatcher ✅. Equirectangular short-hop math ✅. Geohash/quadkey cells for
places + heat tiles ✅. Incremental grid clustering ✅. Streaming heat-tile increment ✅. EMA
speedo needle ✅. Notification throttle ✅. True-black OLED theme ✅.
Not yet: delta+varint point packing ⬜, Douglas-Peucker/Visvalingam simplification ⬜, zoom-bucket
geometry ⬜, passive-provider piggyback ⬜, baseline profiles ⬜, cold zstd recompression ⬜,
incremental auto_vacuum ⬜, tile-cache LRU cap ⬜, local telemetry / calibration-ride / Battery
Historian hooks ⬜, lowered display refresh ⬜.

## 6. Security (§6)
| Item | Status | Notes |
|---|---|---|
| SQLCipher full-DB encryption (AES-256) | ✅ | Verified: DB is not `SQLite format 3` at rest |
| Argon2id KDF | ✅ | PIN verifier + .stk backup + PIN→Argon2id→master envelope |
| Keystore-wrapped master key | ✅ | Enables unattended ride capture; PIN envelope added in parallel |
| Biometric convenience unlock | ✅ | |
| PIN as sole cryptographic root | 🟡 | Deliberately not sole root — a Keystore copy allows background tracking after reboot (core promise). Documented trade-off. |
| Deniable decoy volume (duress PIN) | ✅ | Verified end-to-end: real vs decoy encrypted volumes, synthetic seed |
| `.stk` backup AES-256-GCM, separate passphrase | ✅ | |
| Tamper/root detect (Play Integrity) | ⬜ | |
| Data minimization (coords only) | ✅ | |
| allowBackup=false, FLAG_SECURE, auto-lock, widget masks km | ✅ | |
| Network toggles + master kill switch, zero telemetry | ✅ | |
| TLS / certificate pinning | ⬜ | Plain HTTPS |
| Keyless default + optional own-key (§6.6) | ✅ | Geocoding runtime key + maps build-variant + safety guidance |
| Crypto-erase "wipe all" button | ⬜ | Decoy + uninstall cover the coercion/erasure cases |

## 7. OTA (§7)
Check GitHub Releases ✅, SHA-256 checksum verify ✅, downgrade guard ✅, installer via
FileProvider ✅. In-app banner ⬜ (status shown in settings), pinned TLS ⬜.

## 8. UI/UX (§8)
5-tab nav ✅, dynamic color ✅, night-red mode ✅. Today hub ✅, map-first Timeline + day scrubber
✅, Map (routes + heatmap) ✅, Stats (calendar heatmap, week/month/year, streak, records, hour
histogram) ✅, More ✅, full-screen speedometer ✅, trip detail + replay + edit (mode/label/
delete/merge/split) ✅, widget ✅, QS tile ✅.
Partial/absent: search & filters ⬜, auto-labeled recurring-trip mining ⬜, 7 separate
notification channels 🟡 (2 channels), rich mini-map notifications ⬜, purpose-built landscape /
tap-lock / haptics ⬜, reduce-motion + high-contrast sunlight + dynamic-type toggles ⬜,
swipeable insight/story cards ⬜, shared-element transitions ⬜.

## 9. Feature modules (§9)
Fuel & mileage ✅, riding streaks ✅, records ✅, route replay ✅, heatmap ✅, time-of-day patterns
🟡 (hour histogram), GPX sharing with privacy-zone clipping ✅ (image share ⬜), Takeout import ✅,
find-my-bike ⬜, temporary live-share 🟡 (one-shot "share current location"; continuous
auto-expiring relay ⬜ — needs a backend).

## 10. Automation (§10)
Outbound broadcasts (trip start/end, milestone, daily) ✅, inbound command receiver with
signature permission ✅ (actions stubbed), formal Tasker/Locale plugin ⬜.

## 11. Backup (§11)
Manual `.stk` export/restore ✅. Periodic auto-snapshot + incremental deltas ⬜, auto-detect
`.stk` on fresh install ⬜, merge-vs-replace ⬜ (restore is additive).

## 12–13. Onboarding & formats
Onboarding: welcome → PIN + biometric → permissions → battery-opt → pin Home → done ✅.
Restore-from-backup / backup-passphrase / log-folder steps ⬜. Units km/litres/₹, 24 h, week =
Monday, UTC+offset storage ✅.

## Appendix A — acceptance targets
Functionally verified: distance summation vs GPS path, encryption at rest, crash/process-death
persistence, decoy isolation, R8 release integrity. **Not yet formally benchmarked**: battery
(A.1), latency/jank (A.3), storage growth (A.4), thermal (A.6) — these need on-device
Macrobenchmark / Battery Historian runs.

## Headline gaps to close next
1. On-device battery + accuracy benchmarking against the Appendix-A gates.
2. Search/filters, recurring-trip labels, richer notifications.
3. Periodic auto-backup with deltas + auto-detect restore.
4. Delta+varint point packing and zoom-bucketed render geometry (storage + render targets).
5. Curvature-adaptive sampling, accel-confirmed stops, thermal throttling.
6. TLS pinning + Play Integrity tamper detection.
