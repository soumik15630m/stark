# Stark — Design Document

> Personal Android app that replaces a broken motorbike display: a trustworthy live
> speedometer + lifetime odometer, plus a detailed, private, offline "Google Maps
> Timeline on crack." Tuned so distance reads **truer (higher)** than Google Maps,
> which systematically under-counts.

- **App name:** Stark
- **Package id:** `com.soumik.stark`
- **Backup file extension:** `.stk` (= st(**ar**)k), encrypted container
- **Platform:** Native Android (Kotlin), **min SDK 31 (Android 12)**, iOS later (UI rewrite; core logic portable)
- **Distribution:** Sideload-only, self-updating via **public GitHub Releases** (not Play Store)
- **Owner:** Soumik (motorbike rider, college student, hostel — no wifi, phone stays on ring)

---

## 1. Product goals & non-goals

### Goals
1. **Accurate odometer** — daily and lifetime km that the user trusts more than Google's under-count.
2. **Live dashboard** — glanceable speedometer + trip distance/time, handlebar-mountable (bike's real display is broken).
3. **Detailed timeline** — every outing broken into trips/legs/visits, down to ~20 m resolution.
4. **Negligible battery** — target ≤3–5% on a typical commute day; never sacrifice accuracy for it, keep the two in balance.
5. **Fully private & offline-first** — tracking never needs network; strong on-device encryption.
6. **Self-updating** from day one via GitHub Releases, with tamper-proof verification.

### Non-goals (v1)
- Play Store distribution.
- Social/sharing beyond a single-trip export and a temporary live-share.
- Fitness metrics (cadence, HR), Strava-style social features.
- Cloud sync/automation (deferred; local `.stk` backup only).
- Maintenance reminders, odometer seed/calibration, hands-free/voice, Wear OS, crash/fall detection (all deferred or declined).

---

## 2. Core concepts & vocabulary

| Term | Definition |
|---|---|
| **Point** | One filtered GPS fix: time, lat, lng, accuracy, speed, mode-guess, confidence. |
| **Leg** | A continuous moving segment between two stops. Has distance, duration, avg/max speed, mode. |
| **Visit** | A stationary stop ≥ ~5 min at a place. |
| **Place** | A clustered location the user visits repeatedly; can be named + tagged. |
| **Outing** | The loop from leaving **base** to returning to **base**. Fires the "back home" summary. |
| **Base** | Where the day starts / last long overnight stop. Usually Home (pinned in onboarding) but **smart-anchored** so travel days work. |
| **Trip** | User-facing unit = a leg (or user-merged legs). Editable. |
| **Mode** | walk / run / bicycle / vehicle. Vehicle defaults to **motorbike** (user overrides rare car trips). |

---

## 3. Architecture overview

### 3.1 Module / package layout (`com.soumik.stark.*`)

```
com.soumik.stark
├── app                 // Application class, DI wiring, lifecycle
├── core
│   ├── crypto          // SQLCipher key mgmt, Argon2id KDF, Keystore wrap, AES-GCM for .stk
│   ├── security        // app-lock, biometric, decoy dual-volume, tamper detect, FLAG_SECURE
│   ├── time            // UTC+offset handling, day/week boundaries
│   └── util            // geo math (Haversine), int-scaling, delta codec
├── tracking
│   ├── service         // TrackingForegroundService (location fg-service)
│   ├── gating          // ActivityRecognition + accelerometer prewake + geofence-exit
│   ├── location        // FusedLocation config, sampling policy (hybrid 20m/10s)
│   ├── filter          // Kalman + adaptive accuracy/speed gate + stationary snap
│   ├── backfill        // always-on low-power buffer → trip-start recovery
│   ├── segmentation    // stop detection, leg/visit/outing state machine
│   ├── power           // charging boost, low-battery tiers, battery budget
│   └── watchdog        // WorkManager health-check + AlarmManager + START_STICKY
├── data
│   ├── db              // Room + SQLCipher; DAOs; migrations
│   ├── entity          // tables (see §5)
│   ├── repo            // repositories; incremental totals; derived-stats cache
│   └── backup          // .stk read/write, restore, auto-snapshot
├── domain
│   ├── distance        // incremental cumulative totals
│   ├── places          // clustering (DBSCAN), auto-merge, tagging, geocode-when-online
│   ├── stats           // heatmap tiles, records, patterns, recurring-trip mining
│   └── outing          // smart-anchor logic, summaries
├── update              // GitHub Releases check, signature+checksum+rollback verify, installer
├── automation          // broadcast intents (out), command receiver (in), Tasker plugin
├── location_import     // Google Maps Timeline (Takeout) importer
├── share               // single-trip export (image/GPX) with privacy-zone clipping
└── ui
    ├── theme           // Material 3 dynamic color, night (red) mode
    ├── nav             // 5-tab bottom nav shell
    ├── today           // hub dashboard (Today tab)
    ├── timeline        // map-first + day scrubber
    ├── map             // routes, heatmap, replay
    ├── stats           // calendar heatmap, weekly/monthly/yearly, per-place, records
    ├── more            // fuel, settings, backup, automation, about/update
    ├── speedo          // live speedometer (hybrid gauge)
    ├── trip_detail     // full trip detail + edit/merge/split
    ├── onboarding      // guided setup flow
    ├── widget          // home-screen widget
    └── qstile          // Quick Settings tile
```

### 3.2 Tech stack
- **Language/UI:** Kotlin + Jetpack Compose, Material 3 (dynamic color / Material You).
- **DB:** Room over **SQLCipher** (AES-256), full-database encryption.
- **Location:** Google **Fused Location Provider**; **Activity Recognition** API.
- **Background:** foreground service (type `location`) + **WorkManager** + **AlarmManager**.
- **Maps:** **keyless by default** — MapLibre/osmdroid + OSM tiles (no key, offline-friendly). **Optional:** if the user adds their own Google key, switch to Google **Maps SDK for Android**. (See §6.6.)
- **Geocoding:** **keyless by default** — Android on-device `Geocoder` → OSM Nominatim fallback → user labels; cached by geohash. Optional Google Geocoding when the user supplies a key.
- **KDF:** Argon2id (native lib). **Crypto:** AES-256-GCM for backups.
- **Automation:** Android intents + Tasker plugin (Locale/Tasker plugin protocol).

---

## 4. Tracking engine (the heart)

### 4.1 State machine (`tracking.segmentation`)

```
        ┌─────────┐  motion trigger (AR / accel / geofence-exit)
        │  IDLE   │ ───────────────────────────────────────────► DETECTING
        │(no fg   │                                                  │
        │ service)│ ◄────────── stop confirmed (≥5 min still) ─────┐ │ movement confirmed
        └─────────┘                                                │ ▼
             ▲                                              ┌──────────────┐
             │                                              │  TRACKING    │  (fg service ON)
             │                                              │  - sample    │
             │                                              │  - filter    │
             │                                              │  - batch DB  │
   crypto-erase / wipe                                      └──────────────┘
                                                                   │ start confirmed
                                                                   ▼
                                                            BACKFILL start
                                                            (buffered pts)
                                                                   │
                                                            (continue TRACKING)
                                                                   │ stationary ≥ threshold
                                                                   ▼
                                                            STOP-DETECT → close leg,
                                                            open Visit; if at base → close Outing
```

- **Foreground service runs ONLY during trips** (DETECTING→TRACKING). No persistent notification when idle at home.
- On leg close: run stop-detect; if the stop is at **base**, close the outing and fire the back-home summary.

### 4.2 Motion gating (triple, all near-zero battery)
1. **Activity Recognition** transitions — primary gate (still ↔ walking/bicycle/vehicle).
2. **Accelerometer / significant-motion sensor** — faster "started moving" prewake to spin GPS up before AR confirms.
3. **Geofence-exit** from the current stop — cheap trigger when leaving a known location.

Any of the three can promote IDLE→DETECTING; TRACKING is entered once motion is confirmed.

### 4.3 Sampling policy (`tracking.location`)
- **Hybrid: a point every ~20 m of movement OR at least every 10 s, whichever comes first.**
  - Distance floor → even spatial density, hits the ~20 m goal, samples less when slow.
  - Time ceiling → liveness for the speedometer and gap detection.
- **Charging boost:** when plugged in, sample at **max rate (1 s)** regardless of battery rules (mounted rides).
- **Dashboard mode:** while the live speedometer is open, sample at **1 s** for a smooth readout; drop back to hybrid when closed.
- **Low battery tiers:**
  - `< ~15%` (configurable): degrade to low-power sampling (longer intervals, accept slight under-count), flag segments.
  - `< critical` (configurable): **pause tracking + notify**; do not log degraded data.

### 4.4 Filter pipeline (`tracking.filter`) — accuracy-critical
Order per incoming raw fix:
1. **Adaptive accuracy gate:** reject fixes worse than ~20 m; **relax** the threshold when all recent fixes are poor (urban canyon/tunnel) and mark those points/segments **lower-confidence** rather than dropping the whole segment.
2. **Speed/teleport rejection:** discard physically impossible jumps (implied speed above a mode ceiling).
3. **Kalman filter — 4-state 2D matrix (position + velocity), run in a local ENU projection (meters).** Built from the start as the single coherent **sensor-fusion engine**: fuses GNSS position, **Doppler speed**, and (in gaps) **accel/gyro dead-reckoning**. Measurement noise driven by GPS accuracy. (CPU cost is negligible at these rates; chosen over a 1D scalar filter to avoid a later rewrite and to have one fusion home.)
4. **Stationary snap:** when speed ≈ 0, snap to a single point to kill parked GPS drift (prevents idle inflation).

**Map-matching is NOT used for distance.** Rationale: OSM road data is incomplete/mis-mapped in the user's region, so road-snapping can produce confidently-wrong numbers and re-introduce under-counting. It may be added **later, display-only** (prettify drawn routes + an optional secondary "road-matched distance" for comparison), toggleable, never overriding the odometer.

### 4.5 Distance & trip start
- **Distance = sum of Haversine over filtered points**, computed **incrementally on insert**; running per-trip/day/lifetime totals kept in indexed summary rows (instant odometer, no full recompute).
- **Trip-start backfill:** an always-on **low-power** location stream keeps a short rolling buffer; when a trip is confirmed, the missed opening stretch (lost to AR lag) is **backfilled** from the buffer (~1–2%/day battery cost, accepted).
- **Gap handling** (tunnel / dead battery / force-close): connect the gap with a **straight line, count its distance, flag the segment "estimated."**

### 4.6 Outing / base logic (`domain.outing`)
- **Base** = Home (pinned in onboarding) by default, but **smart-anchored**: on days not starting from home (travel, friend's place), base = the last long overnight stop.
- If base is ambiguous, the app **prompts once**; if no response for hours, it **silently falls back to the smart guess.**
- Outing = base → … → base. Closing an outing fires the **back-home summary** (total km, places, time out).

### 4.7 Power & battery budget (`tracking.power`)
- Target ≤3–5%/day typical use; balance, never sacrifice accuracy.
- Levers: trip-only foreground service, triple low-power gating, hybrid distance sampling, batched encrypted writes, charging boost.

### 4.8 Reliability (`tracking.watchdog`)
- **Full OEM anti-kill suite:** request battery-optimization exemption; detect OEM (Xiaomi/Realme/Oppo/Samsung/OnePlus) and show exact autostart / lock-in-recents guidance; restart on kill.
- **Layered watchdog:** periodic **WorkManager** health-check restarts a dead service + backup **exact AlarmManager** ping + `START_STICKY` / restart-on-task-removed. Multiple redundant revival paths.
- **Boot:** `BOOT_COMPLETED` receiver re-arms tracking.
- **Writes:** buffer points in memory, bulk-insert every ~10–15 s / N points; **flush immediately on trip-end, low battery, or app-background** (crash loses at most a few seconds).

---

## 4.9 Positioning quality & GNSS (`tracking.location`)
- **Constellations:** request **all** available — GPS + GLONASS + Galileo + BeiDou + **NavIC** — and **dual-frequency (L5/E5)** where supported (big urban-multipath win; NavIC strong in India). Chip does the work — no extra battery. Graceful fallback on older phones.
- **Speed source:** **GNSS Doppler speed** (`Location.getSpeed()`) for the speedometer and speed-based filtering — more accurate/less noisy than position-derived; derived only as fallback.
- **First-fix (TTFF):** **warm-keep** GNSS briefly after a trip and during DETECTING for fast re-acquisition; **A-GPS** assistance when online; the low-power **backfill buffer** covers any slow lock so the ride start is never lost. Not always-warm (avoids idle drain).
- **Hardware batching:** when the live dashboard is NOT open, use FusedLocation **hardware FIFO batching** (`setMaxUpdateDelayMillis`) so the chip buffers fixes and wakes the CPU far less often (major idle-ride battery win). Real-time delivery **only when the speedometer/live UI is open.**
- **Curvature-adaptive sampling:** on top of the 20 m/10 s hybrid, sample **denser through turns** (heading-change detected) and **sparser on straights** — better accuracy AND less battery than fixed spacing.

## 4.10 Sensor fusion & thermal
- **Dead-reckoning in gaps:** during **short** GPS gaps, estimate the path from **accelerometer + gyro + heading** instead of a naive straight line (accurate distance through curved tunnels/underpasses); still flagged 'estimated'. Long gaps fall back to straight-line (inertial drift compounds).
- **Accelerometer-confirmed stops:** declare a real stop only when **GPS near-zero speed AND accelerometer shows no motion** — avoids false stops at traffic lights (idle vibration) and avoids counting parked drift.
- **Thermal-aware throttling:** watch Android's **thermal status**; when hot (mounted + charging + screen-on + 1 s GPS on long rides), dim the dashboard and ease sampling/animations to shed heat and protect battery longevity. Log to local telemetry.

## 4.11 Timing & segmentation correctness
- **Time source:** **GNSS satellite time is authoritative** (immune to a wrong/changed phone clock or drift); fall back to system clock when no fix. Store UTC + offset → durations/ordering stay correct across timezone changes or a bad clock.
- **Mode-change splitting:** a **sustained, confirmed** mode change (ride → walk → ride) **starts a new leg** with the new mode, keeping per-mode km accurate; brief/uncertain flickers are ignored via **hysteresis** to avoid over-splitting.
- **Pause vs stop (two thresholds + idle exclusion):** `< ~5 min` stationary = **PAUSE** — same trip continues, idle time excluded from moving-average speed, trip not split; `≥ ~5 min` = **STOP** — ends the leg, creates a **Visit**. Both thresholds configurable. (Stops red lights don't fragment trips; real stops do.)
- **Confidence model (3 levels):** **HIGH** = good-accuracy real fixes; **LOW** = relaxed-gate fixes in poor signal (kept, flagged); **ESTIMATED** = dead-reckoned or straight-lined gaps. Distance counts all three, but LOW/ESTIMATED render **dashed/faded** and are summarized ("~0.4 km estimated") so totals stay honest and transparent.

## 4A. Performance & diagnostics
- **Cold start:** UI shell appears instantly with a skeleton; **DB unlock (Argon2id + SQLCipher open) runs off the main thread**; content fills in when ready; tracking service is independent of UI unlock (hides the ~0.5–1 s crypto cost).
- **DB tuning:** SQLCipher **WAL mode**; indices (time, leg_id, place_id, date_local); point-writes in **batched transactions off the main thread**; periodic **VACUUM/optimize** while charging/idle; tuned `cache_size`/`mmap` pragmas. **Never touch the DB on the UI thread.**
- **Render:** **zoom-bucketed simplified geometry** + **cached heatmap tiles** + **marker clustering** + replay **point-decimation** to a smooth frame budget. Instant at any zoom/history; raw stays the distance source of truth.
- **Local telemetry (zero network):** on-device metrics — service uptime, missed/degraded windows, last-fix age, sampling mode, tracking battery/day, thermal events. Never leaves the phone.
- **Diagnostics screen:** surfaces the telemetry so the user can trust/debug the odometer.
- **Accuracy self-check mode:** a "calibration ride" — ride a known distance, compare the app's number, see % error to validate filter tuning.
- **Battery profiling hooks:** structured logs compatible with **Android Battery Historian** / profiler for development.

## 4B. Battery & computation tricks

**Battery / wake-up minimization (CPU wake count is the real battery driver)**
- **Significant Motion Sensor (TYPE_SIGNIFICANT_MOTION)** is the deepest-IDLE gate — a hardware one-shot that fires only on real sustained movement at ~0 power (no polling). It arms Activity Recognition + GPS only once you actually move. (AR/accelerometer refine after it fires.)
- **Passive location provider piggyback:** register a PASSIVE listener to harvest fixes other apps (Maps, etc.) already triggered — free data at zero added battery; enriches the backfill buffer and can catch a ride start early.
- **Wake-up coalescing:** align all periodic work (batched writes, watchdog, stats, backup) to the **same wake tick** and to **Doze maintenance windows**; inexact alarms/WorkManager for non-urgent work, **exact alarms only for the daily summary**. One wake does everything.
- **Accelerometer hardware batching:** register with a `maxReportLatency` so the **sensor hub** batches readings in its FIFO and wakes the main CPU rarely, at the lowest rate that still detects motion/stillness.

**Computation tricks (cheap math, same accuracy)**
- **Distance:** **equirectangular (flat-earth) approximation for short consecutive hops** (~20 m — accurate to mm, a fraction of Haversine's trig); full **Haversine only for long spans** (gap straight-lines). Runs on the hottest path.
- **Spatial index:** snap coordinates to **geohash / quadkey cells** — turns "which place / privacy-zone / heat-tile is this?" into an integer key lookup instead of distance-checking every candidate. One mechanism for clustering, zone tests, and heat tiles. No heavy geo library.
- **Place clustering / auto-merge:** **incremental grid-cell clustering** — each visit snaps to a geohash cell and merges with the existing place there (+ small radius check). O(1) per visit, no batch DBSCAN pass, auto-merges duplicates.
- **Heatmap:** **streaming per-point tile increment** — finalizing a point bumps its tile weight once; heatmap is always current with zero re-aggregation.
- **Point encoding:** scaled-int (×1e7) → **delta between consecutive coords → zigzag → varint**, packed as one blob per trip (same idea as encoded polyline). Tiny on disk, fast sequential decode.
- **Speedometer display:** **EMA on top of the filter's speed** for a calm, non-jittery needle (display only; the recorded speed stays the accurate filtered value).

**Render simplification (display geometry, per zoom bucket)**
- **Douglas-Peucker + radial-distance prefilter is the default** (crisp corners at junctions, clean straights, legible at every zoom — best for a turn-heavy road timeline).
- **Visvalingam-Whyatt offered as a display setting/toggle** (smoother/organic look, consistent point density) so the user can compare on real routes and choose.

**CPU / concurrency**
- **Dedicated single-thread dispatcher** for the whole per-fix pipeline (filter → 2D Kalman fusion → segmentation → buffer): ordered, lock-free, off the main thread, minimal context-switch churn. **Separate IO dispatcher** for DB writes. Structured coroutines throughout.
- **Zero-allocation hot loop:** reuse point/vector objects and buffers in the filter/fusion loop (no per-fix allocation) to avoid GC churn and micro-stutter.
- **Notification throttle:** refresh the live-odometer notification at most every ~5–10 s / on meaningful change, **not per fix** (system UI wakes far less).
- **Compose discipline:** scope state so only the changing element recomposes (speed digits update at 1 Hz, not the whole gauge/map); `derivedStateOf`/`snapshotFlow`, stable keys, gauge drawn on Canvas.
- **AOT:** ship **Baseline Profiles** (hot paths — startup, tracking loop, map — AOT-compiled from first launch) + **R8 full mode** shrinking/optimization.

**Display power (mounted dashboard)**
- **True-black OLED theme** (pixels off = real power saving) + **lowered display refresh rate** (60 Hz/lower — a speedometer doesn't need 120 Hz). Pairs with red night mode.

**Wakelock discipline**
- With hardware location batching, hold a **partial wakelock only while draining/processing a delivered batch**, then release so the CPU sleeps until the next batch. The foreground service keeps tracking alive without pinning the CPU awake.

**Geocoding (network-frugal)**
- Never block on geocoding. **Queue** unknown places, debounce, and **batch-resolve only when network is actually available** (and the geocode toggle is on); **cache by geohash cell forever** so a place is looked up at most once.

**Storage management**
- **Cold recompression:** recent trips stay as fast delta+varint blobs; trips older than N months get an extra **lossless pass (zstd/deflate)** during idle+charging. Full precision preserved, smaller on disk, transparent on read.
- **DB maintenance:** **incremental auto_vacuum** (reclaim in small steps, no long global lock) + tuned **`wal_autocheckpoint`** so the WAL doesn't balloon on long rides; full optimize only occasionally while charging/idle.
- **Tile cache:** map/heat tiles on disk with **LRU eviction + configurable size cap** (e.g. 100–500 MB) and a **storage-budget screen** (points vs tiles vs backups + "clear tile cache"). User data never auto-deleted — only cached tiles.

---

## 5. Data model (Room + SQLCipher)

All tables in the **encrypted** DB. Coordinates stored as **scaled integers (×1e7)** and **delta-encoded** within a track (lossless, ~3–5× smaller). Times stored as **UTC epoch millis + offset minutes**.

```
Vehicle(id, name, type[MOTORBIKE], created_at)              // single now, schema multi-ready
Point(id, leg_id, t_utc, offset_min, lat_e7, lng_e7,        // delta-encoded per leg
      accuracy_m, speed_mps, confidence[HIGH|LOW|EST])
Leg(id, outing_id, vehicle_id, mode, start_t, end_t,
    distance_m, duration_s, avg_speed, max_speed,
    start_place_id, end_place_id, has_estimated_gap, label)  // label = auto recurring name
Visit(id, place_id, arrive_t, depart_t, duration_s)
Place(id, lat_e7, lng_e7, radius_m, name, category,          // category: HOME/WORK/FOOD/FRIENDS/FUEL/…
      is_base_home, visit_count, first_seen, last_seen)
Outing(id, base_place_id, start_t, end_t, distance_m,
       leg_count, place_count, summary_sent)
FuelFill(id, vehicle_id, t, litres, cost_inr, odo_m_at_fill) // → km/l, cost/km
Record(type, value, achieved_t, ref_leg_id)                  // longest ride, top speed, etc.
DailyTotal(date_local, distance_bike_m, distance_all_m, trip_count)  // incremental
LifetimeTotal(vehicle_id, distance_bike_m, distance_all_m)   // incremental, headline odometer
DisplayGeom(leg_id, zoom_bucket, simplified_blob)            // Douglas-Peucker, render-only
HeatTile(z, x, y, weight)                                    // aggregated heatmap
Setting(key, value)                                          // encrypted prefs
PrivacyZone(id, lat_e7, lng_e7, radius_m, label)
```
- **Decoy volume:** a *separate* encrypted store with its own key (see §6.2) holding synthetic `Place/Leg/Outing/Point` data. Same schema, generic-area fake life.

**Two headline odometers** shown side-by-side: `LifetimeTotal.distance_bike_m` (dashboard replacement) and `distance_all_m` (all modes).

---

## 6. Security & privacy architecture

### 6.1 Crypto core
- **Data at rest:** full SQLite encryption via **SQLCipher (AES-256)**.
- **Key hierarchy:**
  1. User **PIN/passphrase** → key via **Argon2id, high cost** (~256 MB, 3 iterations; ~0.5–1 s unlock).
  2. That key **unwraps a master DB key wrapped in the hardware Keystore** — **StrongBox / secure element when available**, else TEE. Master key never exists in extractable form.
- **Biometric model:** **PIN is root** (always derives the real secret). Biometric = convenience shortcut to a Keystore-cached key. **PIN required after reboot, biometric change, or auto-lock timeout.**
- **Backup (.stk):** **AES-256-GCM** (authenticated, tamper-detecting), custom magic header, per-backup random salt + nonce, key from a **separate backup passphrase** (Argon2id). App PIN never leaves the device; a leaked backup can't reveal the PIN. Other apps opening a `.stk` see garbage.

### 6.2 Deniable decoy (duress PIN)
- **Deniable dual-volume:** two separately-encrypted volumes with **different keys**. The **decoy PIN opens only the fake volume**; the real volume is indistinguishable from random/free space → **no cryptographic proof more data exists** under coercion.
- Decoy content = **fully synthetic, generic-area** fake life (fake Home/Work, believable commute), seeded once, **not derivable/reversible** from real data (must never leak the real neighborhood/home).

### 6.3 Threat model & data hygiene
- **Tamper/root:** **detect + warn, don't block** (Play Integrity + local checks); logs the condition; still runs (user may root own phone).
- **Data minimization:** keep **full GPS precision** (needed for 20 m goal); store **no** device ID, IP, ad ID, wifi/cell scans. Coordinates are the only sensitive field.
- **Deletion:** hard-delete rows + VACUUM; **"wipe all" = crypto-erase** (destroy Keystore master key → whole DB instantly unrecoverable). Optional **duress-wipe PIN**. Confirm dialog; no recycle bin.
- `allowBackup=false` (no adb/cloud extraction). **FLAG_SECURE** (no screenshots, blank recents preview). **Auto-lock timeout.** Widget/notifications **hide exact km/location until unlocked.**

### 6.4 Network egress & transport
- **Offline-first.** Only 3 network touchpoints: map tiles, geocoding, OTA update check.
- **Per-feature toggles** (map / geocode / update) **+ master kill switch** ("airplane mode"). Nothing else ever leaves the device. **Zero telemetry**; crash/debug logs → **user-chosen local folder** (set in onboarding).
- **TLS + certificate/public-key pinning** for GitHub & Google (MITM-proof); graceful pin rotation.

### 6.5 Permissions (minimal)
`ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`, `REQUEST_INSTALL_PACKAGES` (OTA), `SCHEDULE_EXACT_ALARM` (daily summary/watchdog), `RECEIVE_BOOT_COMPLETED`, `HIGH_SAMPLING_RATE_SENSORS` (accel prewake, if needed).
Requested during the **guided onboarding walkthrough** with per-permission plain-language rationale (background location may route through system Settings per OS rules).

### 6.6 API keys & cost safety (public-repo-safe)
**Principle:** an API key cannot be hidden in an app, so the app **ships with ZERO keys** — nothing billable is ever in the public repo or the APK, so nothing can leak and no one can run up a bill.

- **Keyless by default (free):** map = MapLibre/osmdroid + OSM tiles; geocoding = Android on-device `Geocoder` → OSM Nominatim → user labels. No Google account or billing needed at all.
- **Optional user-supplied Google key:** the user may enter **their own** Google Maps Platform key in **Settings**; it's stored **in the encrypted SQLCipher DB — never in the repo/APK**. When present → map uses Google **Maps SDK for Android**, geocoding may use Google Geocoding.
- **Why this is safe:** the only key that can ever exist is one the user personally added, on **their** Google account with **their** caps. Map SDK loads are free anyway; the sole billable surface (Geocoding) only exists if the user opts in with their own key.
- **In-app guidance shown when adding a key:** restrict to package name + signing-cert **SHA-1**; restrict APIs to only what's used; use a **dedicated GCP project**; set a **hard budget cap + alerts + billing auto-disable** (Pub/Sub → Cloud Function) so worst case is bounded to near-zero.
- **Repo hygiene:** no keys committed (there are none to commit). If any build-time key is ever introduced, use a gitignored `secrets.properties` injected via the Secrets Gradle Plugin, with a committed `*.defaults` template; CI secrets for release builds.
- Governed by the existing **per-feature network toggles + master kill switch** (§6.4): map tiles / geocoding / updates each independently disablable.

---

## 7. OTA update flow (`update`)

1. On launch + daily (respecting the update toggle), query the **public GitHub Releases API** over pinned TLS.
2. If a newer release exists, show a **notification/banner + changelog**.
3. On user tap: download the APK; verify **(a)** signature against the **pinned signing certificate**, **(b)** **SHA-256 checksum** from the signed release notes, **(c)** **monotonic version counter** (refuse downgrade-to-vulnerable).
4. Only on all-pass → launch the system installer (`REQUEST_INSTALL_PACKAGES`).
5. Any verification failure → refuse + alert, keep current version.

---

## 8. UI / UX

### 8.1 Shell & theme
- **5-tab bottom nav:** Today · Timeline · Map · Stats · More.
- **Material 3 + dynamic color** (Material You), follows system light/dark.
- **Night-riding mode:** red/high-contrast dashboard (auto at night or manual), preserves night vision.

### 8.2 Screens
- **Today (hub dashboard):** big **two-odometer** header (bike km + all-mode km) with today's km; scrollable quick-access cards (Map / Speedometer / Fuel / Stats) that jump to their tabs; today's trip list.
- **Timeline:** **map-first with a day time-scrubber** — routes on the map, slide through the day; chronological legs/visits below.
- **Map:** full routes, **most-ridden-roads heatmap**, **route replay** (moving dot + speed).
- **Stats:** calendar heatmap; weekly/monthly/yearly totals; per-place stats; **records board**; time-of-day/traffic patterns.
- **More:** fuel log (fills → km/l, cost/km), settings, backup/restore, automation, network toggles, about/update.
- **Speedometer (full-screen):** **hybrid** — big digital km/h with a thin arc/gauge ring; trip distance/time/max below; night mode; 1 s sampling; optional keep-awake toggle.
- **Trip detail (full):** route + replay; distance, duration, avg/max speed; mode w/ override; start/end places & times; confidence/estimated-gap flags; **long-press quick actions** (delete / re-tag / merge-with-neighbor) + edit screen to **split at a map point**; privacy-zoned **share** (image/GPX).
- **Widget:** today's km + **tap-to-open speedometer** button (hides exact values until unlocked).
- **Quick Settings tile:** start / stop / pause tracking; "this is a car" for the current trip.

### 8.3 Places & editing
- Auto-detect repeat stops → prompt to **name once**; **online reverse-geocode when available**, cached.
- **Categories/tags** (Home/Work/Food/Friends/Fuel) power filters & stats; **auto-merge duplicate places**.
- **Full search & filters:** by place name, date range, mode, distance, tags.
- **Auto-labeled recurring trips** ("Morning commute", "Evening return"), editable.

### 8.4 Notifications (separate channel per type, each tunable in system settings)
- **Channels:** `live-odometer` (silent/ongoing foreground) · `back-home summary` · `end-of-day` (~21:30, configurable) · `milestones` · `mid-day pings` (off by default) · `tracking-paused` · `update-available`.
- **Rich summaries:** back-home & end-of-day notifications expand to show a **locally-rendered mini map** of the routes (no network) + stats; collapse to a one-liner.
- **Live-odometer:** **purely informational, no action buttons** (control via the Quick Settings tile / app).
- **Back-home outing summary (full recap):** total km (bike + all), trip count, places visited, time out, longest leg, top speed, any new record — with route thumbnail; tap opens the outing in the timeline.
- **Delivery:** tiered + bundled, quiet by default — live-odometer silent/min-importance; summaries & milestones default-importance (gentle sound, respects DND); update-available low. Related notifications bundle to avoid shade spam. Every channel user-overridable in system settings.

### 8.5 Interaction & layout detail
- **Today hub:** fixed smart order — odometer header → today's trips → quick tiles (Map/Speed/Fuel/Stats) → streak/records glance. Not reorderable (clean/predictable).
- **Odometer display:** large **hero = bike km** (dash replacement) with smaller **'all modes: X km'** beneath; a toggle flips the hero between **Today** and **Lifetime**.
- **Timeline navigation:** **swipe left/right** day-to-day + tap the date for a **calendar jump** (with heatmap).
- **Trip detail:** **expandable bottom sheet over the map** — drag between peek (key stats) and full (everything + edit); keeps map context.
- **Visual semantics:** consistent **color + icon per mode** (bike/walk/run/car); **estimated / low-confidence / dead-reckoned segments render dashed + faded** with an 'estimated' chip (honest about uncertainty).

### 8.6 Riding / speedometer UX (safety-critical)
- **Orientation:** auto-rotate with **purpose-built portrait AND landscape** layouts; a **rotation-lock toggle** so bumps don't flip it mid-ride.
- **Content:** **rich dashboard** (speed, trip distance, trip time, avg, max, odometer, mini-map) but with **speed and trip distance visually boosted** as the dominant elements; auto-scaling gauge range; extras behind a tap.
- **At-speed interaction:** **glance-only by design** — no small controls; oversized **glove-friendly** start/stop only; optional **tap-lock** so accidental/rain touches do nothing. Interaction happens before/after, not during.
- **Feedback:** **haptic on start/stop confirm** only (no voice; declined).

### 8.7 Motion, accessibility, empty states
- **Motion:** rich, purposeful animation (route replay, odometer count-up, transitions) with a **reduce-motion toggle** and honoring the **system reduce-motion** setting.
- **Accessibility:** **dynamic type** (respect system font scaling); **high-contrast sunlight mode** (boosted brightness/contrast for direct sun); **one-handed / thumb-zone reach** for key actions. (Riding mode already has large targets.)
- **Empty / first-run states:** helpful + encouraging — explain what will appear and nudge the first action ('Take your first ride — your odometer starts here'), subtle illustration.

### 8.8 Stats, widget, branding, locale
- **Stats tab:** **clean minimal charts** (GitHub-style calendar heatmap, simple bar/line trends, ranked place/record lists — one idea per screen, mode-consistent colors) **PLUS swipeable insight/story cards** ('You rode 12% more this month'). (Follow dataviz principles: ≤2 color ramps, encode meaning not sequence, accessible in light/dark.)
- **Widget:** **small only** — today's km + tap-to-open speedometer button (hides exact values until unlocked).
- **Branding:** app icon/accent **deferred** (placeholder now, iterate near release); in-app accent follows Material You dynamic color.
- **Locale:** **English only, i18n-ready** (all strings externalized for easy later translation; no hardcoded text).

### 8.9 Animation
- **Route replay:** animated **dot travels the route drawing a trailing path**, live speed shown; **play/pause + scrubber + 1x/2x/4x**; camera follows with a 'fit whole route' toggle. **The moving marker's icon reflects the active mode** — bike glyph on bike legs, walker on walk legs, a resting dot while stopped.
- **Number motion:** **count-up roll** when totals/odometer load or change (premium feel); the **live speedometer updates instantly** (its EMA smooths it) — no rolling animation at speed.
- **Transitions:** **shared-element** morph (a trip's route → the detail sheet) + gentle tab **fades**; falls back to instant under reduce-motion.
- **Micro-motion (tasteful, reduce-motion-governed):** brief **milestone celebration** (+ start/stop-style haptic), **chart entrance** (bars grow / lines draw on stats open), **skeleton loaders** during async decrypt/load. Kept restrained, not gratuitous.
- All of the above obey the **reduce-motion toggle** and the system reduce-motion setting.

---

## 9. Feature modules

- **Fuel & mileage:** log fills (litres + ₹ cost); compute km/l and cost/km against distance. (No maintenance reminders in v1.)
- **Find my bike:** remember last parking (last stop), point back to it.
- **Riding streaks:** consecutive-days-ridden + rest days.
- **Records board:** longest ride, most km/day-week-month, fastest avg, top speed.
- **Route replay:** animate a trip/day on the map.
- **Most-ridden-roads heatmap:** aggregate all trips (precomputed tiles).
- **Time-of-day / traffic patterns:** when/where you ride, slow routes by hour.
- **Trip sharing:** export a ride as map image or GPX, **privacy zones auto-clipped**, opt-in per share.
- **Import Google Maps Timeline:** one-time offline **Takeout** import to bootstrap history.
- **Temporary live-location share:** "track me home," auto-expiring, network only while active.

---

## 10. Automation & integration (`automation`)

- **Mechanism:** documented Android **broadcast/activity intents** + a **Tasker plugin** (event conditions + actions; also MacroDroid/Automate). No network, no localhost socket.
- **Outbound events (emit all; non-sensitive payloads, NO raw coordinates):** trip start/end (mode, distance, duration), arrive/leave named place, milestones, daily summary, mode change, low-battery/degrade.
- **Inbound commands (accepted):** **force backup now**, **add manual place/marker at current location**. (No start/stop/pause or trip-tag via automation.)
- **Security:** inbound commands require a **custom signature-level permission**; broadcasts never carry raw coordinates unless a specific automation is explicitly opted in.

---

## 11. Backup / restore (`data.backup`)

- **`.stk`** = AES-256-GCM encrypted container (see §6.1), separate passphrase.
- **Auto-backup (v1):** silent periodic `.stk` to a chosen local folder **+** manual export button. Format = **periodic full snapshot (e.g. monthly) + small incremental deltas in between** (only new/changed data); restore replays full + deltas; each piece independently AES-256-GCM encrypted. Efficient for frequent auto-backups.
- **Restore:** BOTH **guided import during onboarding** ("Restore from backup?" → pick `.stk` → passphrase) AND **auto-detect `.stk` files** in the backup folder on fresh install. Handle merge-vs-replace when data already exists. Re-import anytime from settings.
- Cloud automation deferred.

---

## 12. Onboarding flow

Welcome → **restore from `.stk`?** → set **app PIN + biometric** → set **backup passphrase + backup folder** → choose **log folder** → **permissions walkthrough** (with rationale) → **battery-opt exemption + OEM autostart guide** → **pin Home** → done. Tracking starts after onboarding.

---

## 13. Formats & conventions
- Units: **km**, **litres**, **km/l**, currency **₹/INR**.
- Time: 24-hour; **day boundary = local midnight**; **week starts Monday**; store **UTC + offset** per point (correct across travel/DST).

---

## 14. Build order (milestones)

Everything is targeted for v1, but sequenced so a working odometer exists first:

1. **M1 — Trustworthy odometer:** tracking service + gating + hybrid sampling + filter pipeline + incremental distance + encrypted DB + minimal Today screen (two odometers) + foreground notification + boot/watchdog. Result: accurate daily & lifetime km.
2. **M2 — Trips & timeline:** segmentation (legs/visits/outings), trip list, trip detail + editing, back-home summary, notifications, security lock (PIN/biometric, FLAG_SECURE).
3. **M3 — Places & map:** clustering + naming + geocode-when-online + tags + auto-merge; map-first timeline + day scrubber; route replay.
4. **M4 — Dashboard & stats:** hybrid speedometer, widget, Quick Settings tile, calendar heatmap, weekly/monthly/yearly, records, most-ridden heatmap, patterns.
5. **M5 — Bike-computer & data:** fuel log/mileage, `.stk` backup + auto-snapshot + restore, Timeline import, trip sharing.
6. **M6 — Hardening & extras:** deniable decoy volume, full OEM anti-kill polish, OTA verification (signature/checksum/rollback + pinning), automation intents + Tasker plugin, network toggles/kill switch, night mode, temporary live-share.

---

## 15. Open / deferred items
- Map-matching (display-only) — later.
- iOS port — later.
- Cloud backup automation — later.
- Maintenance reminders, odometer seed/calibration — deferred.
- Declined: hands-free/voice, Wear OS, always-on dashboard, crash/fall detection + SOS, on-this-day/year-in-review, cost/CO₂-saved.

---

## Appendix A — Performance acceptance targets

The measurable "definition of done." Targets are for a mid-range reference device (≈Snapdragon 6-series, Android 12+, OLED). Each has a **hard gate** (must pass to ship) and, where useful, a **stretch** goal. Verified via the listed method; results logged to the local telemetry store (§4A) and a `PERF.md` run sheet.

### A.1 Battery
| Metric | Hard gate | Stretch | How verified |
|---|---|---|---|
| Idle drain (armed, parked all day, no trips) | ≤ **1%/day** attributable to the app | ≤ 0.5%/day | Battery Historian over 24 h idle; significant-motion gate must show ~0 GPS wake-ups |
| Typical commute day (2–4 short rides, phone in pocket) | ≤ **5%/day** | ≤ 3%/day | Battery Historian, real-use log over 3 days averaged |
| Active tracking, pocket (hardware-batched) | ≤ **4%/hour** | ≤ 2.5%/hour | 1 h ride, screen off, batched delivery |
| Live dashboard, mounted + charging | net **non-draining** (charge ≥ draw) | — | 1 h mounted with charger; battery % non-decreasing |
| Idle CPU wake-ups | ≤ **2/hour** while parked | ≤ 1/hour | Battery Historian wakeup count; confirms wake-coalescing + significant-motion gate |

### A.2 Accuracy (the core promise — beat Google's under-count)
| Metric | Hard gate | Stretch | How verified |
|---|---|---|---|
| Distance error on a known route | within **±2%** of true (and **never systematically under**) | ±1% | Calibration-ride mode over a surveyed/odometer-measured 10 km loop, 3 runs |
| vs Google Maps Timeline on same ride | **≥ Google's** distance (closer to true) | — | Same-ride comparison |
| Corner/curve fidelity | no visible corner-cutting at junctions on the drawn route | — | Visual check on map vs known turns |
| Parked-drift inflation | **0 m** counted while stationary ≥ 5 min | — | Sit stationary 15 min; distance delta must be 0 (stationary-snap + accel-confirmed stop) |
| Trip-start capture (backfill) | ≥ **95%** of the opening stretch recovered | ≥ 98% | Compare backfilled start vs a manual force-start reference ride |
| Stop/pause classification | ≥ **95%** correct (traffic-light pause not split; real stop split) | — | Labeled test rides through signals + real stops |
| Speedometer accuracy | within **±2 km/h** of a reference GPS speed | ±1 km/h | Compare Doppler speed vs a second GPS device |

### A.3 Responsiveness / latency
| Metric | Hard gate | Stretch | How verified |
|---|---|---|---|
| Cold start to interactive shell | ≤ **800 ms** (skeleton visible) | ≤ 500 ms | Macrobenchmark, `StartupTimingMetric`, Baseline Profiles on |
| PIN unlock → data visible (async decrypt) | ≤ **1.2 s** (Argon2id high-cost + open) | ≤ 800 ms | Macrobenchmark; Argon2 params tuned to hit this |
| Live speedometer frame rate | **60 fps**, zero dropped frames at 1 Hz updates | 90 fps if display allows | `FrameTimingMetric`, JankStats; only speed digits recompose |
| Map / heatmap render over 1 yr of data | ≤ **1 s** to first paint at any zoom | ≤ 500 ms | Macrobenchmark on a seeded 1-year DB; zoom-bucketed geometry + tile cache |
| Screen transitions | ≤ **300 ms**, no jank | — | JankStats; shared-element + fades |
| Odometer read (any period) | ≤ **50 ms** | — | Instrumented repo call; incremental totals, no full recompute |

### A.4 Storage
| Metric | Hard gate | Stretch | How verified |
|---|---|---|---|
| Raw point on disk (encrypted, delta+varint) | ≤ **8 bytes/point** avg | ≤ 6 bytes | Measure DB growth over a known point count |
| Growth per 1,000 km ridden | ≤ **3 MB** | ≤ 2 MB | Seeded/real ride accounting |
| Cold-recompressed old trips | ≥ **40%** smaller than fresh blobs | ≥ 55% | Compare pre/post zstd pass, verify lossless round-trip |
| Tile cache | never exceeds the configured cap; LRU evicts correctly | — | Fill past cap, confirm eviction + budget screen totals |

### A.5 Reliability
| Metric | Hard gate | Stretch | How verified |
|---|---|---|---|
| Trip capture rate (rides not missed) | ≥ **98%** of rides logged | ≥ 99.5% | 2-week real-use log vs a manual ride diary |
| Service survives reboot | resumes tracking within **2 min** of boot | — | Reboot test; `BOOT_COMPLETED` re-arm |
| Service survives OEM kill | watchdog revives within **≤ 15 min** worst case | ≤ 5 min | Force-stop via OEM battery manager; confirm revival path |
| Data loss on crash mid-ride | ≤ **15 s** of track lost | ≤ 5 s | Kill process mid-ride; measure gap after recovery (batched-write flush) |
| Crypto-erase / wipe | DB unrecoverable after wipe | — | Attempt read after key destruction |

### A.6 Thermal
| Metric | Hard gate | How verified |
|---|---|---|
| Sustained mounted ride (dashboard + charging) | no thermal `SEVERE`/throttle over 60 min | `PowerManager.getThermalHeadroom()` logged over a 1 h ride; throttling kicks in before OS `SEVERE` |

### A.7 Verification cadence
- **Every release:** A.3 (Macrobenchmark/JankStats), A.4 growth, A.5 reboot/crash — run in CI where possible.
- **Milestone gates (M1, M4):** full A.1 battery + A.2 accuracy calibration ride.
- **Ad-hoc:** thermal on the first hot-weather long ride; OEM-kill test on the actual daily-driver phone.
- All numbers recorded to `PERF.md` with device + Android version so regressions are visible over time.
