# Auto-detecting workouts from Android health data → suggest a kind 1301 post

Research date: 2026-06-16. Goal: when the user finishes a workout that lands in
their phone's health data (Samsung Watch, Wear OS, Fitbit, Garmin, Strava,
Google Fit, manual entry in any health app), Amethyst should **notice it** and
**offer to publish it** as a NIP-101e `WorkoutRecordEvent` (kind 1301) — the
event type Quartz already implements and that RUNSTR and other fitness clients
read off relays.

## 1. How RUNSTR sources workouts (and why we copy the Android half)

RUNSTR (React Native + NDK) imports from **Apple HealthKit** on iOS and
**Google Health Connect** on Android, polling Health Connect on a ~15-minute
background cadence. It advertises Strava, Nike Run Club, Garmin, Apple Watch,
Fitbit and Google Fit as sources — but on Android every one of those funnels
through a single platform API: **Health Connect**.

The Samsung Watch case the request calls out is the same story:
`Galaxy Watch → Samsung Health → Health Connect`. Samsung Health, Google Fit,
Fitbit, Strava and Garmin Connect all write `ExerciseSessionRecord`s into
Health Connect. So instead of integrating each vendor SDK (most of which are
proprietary and would fail F-Droid), we read the one aggregator, exactly as
RUNSTR does on Android.

Health Connect is `androidx.health.connect:connect-client`, **Apache-2.0**
(permissive — fine for both the `play` and `fdroid` flavors; it is an AndroidX
library, not a Google Play Services blob). On Android 14+ the data store is
part of the OS; on 8–13 it is a separately installable system app.

## 2. What already exists (reuse, don't rebuild)

- `quartz` `WorkoutRecordEvent` (kind 1301) + the full NIP-101e/RUNSTR tag set
  (`distance`, `duration`, `calories`, `avg/max_heart_rate`, `steps`,
  `elevation_gain/loss`, `source`, `workout_start_time`, splits, strength…).
  **No protocol work needed** beyond adding a `source` constant.
- A manual composer: `NewWorkoutScreen` + `NewWorkoutViewModel`, publishing via
  `account.signAndComputeBroadcast`. This is the screen we pre-fill.
- `Route.NewWorkout` navigation target and the `WorkoutsScreen` (kind-1301
  feed) where the suggestion surfaces.

The only missing layer is **detection → suggestion → pre-fill**.

## 3. Decision: foreground scan (no background service)

Per product decision (2026-06-16) this first cut runs **only while the app is
open** — no `WorkManager`, no `POST_NOTIFICATIONS`, no background-read
permission. That keeps the Play Store data-safety surface minimal and avoids a
foreground service. The seam for a future ~15-min background poll (RUNSTR
parity) is noted in §7.

## 4. Architecture

```
service/workouts/health/
  HealthConnectManager.kt   — wraps HealthConnectClient: availability,
                              permission set, readNewWorkouts(since)
  DetectedWorkout.kt        — platform-neutral mapped result (one session +
                              aggregated metrics), with toRoute()
  ExerciseTypeMapper.kt     — HC exercise-type Int → quartz ExerciseType
  HealthConnectStore.kt     — per-npub "last scan" watermark + dismissed ids
                              (SharedPreferences)

ui/screen/loggedIn/workouts/
  WorkoutSuggestionViewModel.kt — holds StateFlow<List<DetectedWorkout>>;
                                   scan(), dismiss(), permission state
  WorkoutSuggestionCard.kt      — banner at top of WorkoutsScreen
  NewWorkoutViewModel.kt        — +prefill(route) + richer metrics in template
```

Data flow:

1. `WorkoutsScreen` opens → `WorkoutSuggestionViewModel.scan()` checks Health
   Connect availability + granted permissions. If unavailable/denied, no card.
2. `HealthConnectManager.readNewWorkouts(since = watermark)` reads
   `ExerciseSessionRecord`s ending after the watermark, and for each aggregates
   `DistanceRecord`, `TotalCaloriesBurnedRecord`, `HeartRateRecord` (avg/max),
   `StepsRecord`, `ElevationGainedRecord` over the session window.
3. Sessions already dismissed or already published (matched by
   `workout_start_time` + `source=health_connect` in `LocalCache`) are filtered.
4. Remaining → `StateFlow` → `WorkoutSuggestionCard` ("New run detected · 5.2 km
   · 28:14 — share it?"). Tap → `nav.nav(detected.toRoute())` opens the composer
   pre-filled; user reviews and posts. Dismiss → add id to dismissed set.
5. After a scan the watermark advances to `now`, so each session is offered once.

## 5. Permissions

Health Connect uses its own permission strings and request contract
(`PermissionController.createRequestPermissionResultContract()`), *not* the
standard runtime-permission dialog. Manifest adds read-only:

```
android.permission.health.READ_EXERCISE
android.permission.health.READ_DISTANCE
android.permission.health.READ_TOTAL_CALORIES_BURNED
android.permission.health.READ_HEART_RATE
android.permission.health.READ_STEPS
android.permission.health.READ_ELEVATION_GAINED
```

plus the `<queries>` entry for the Health Connect package and the
privacy-policy `ACTION_SHOW_PERMISSIONS_RATIONALE` intent-filter Google
requires. The card only asks for permission when the user taps "Connect" — we
never request on cold start.

## 6. Mapping (Health Connect → kind 1301)

| Health Connect | kind 1301 tag |
|---|---|
| `ExerciseSessionRecord.exerciseType` | `exercise` (+ `t` hashtag) via `ExerciseTypeMapper` |
| `ExerciseSessionRecord.title` | `title` |
| `endTime − startTime` | `duration` (seconds) |
| `startTime` (epoch s) | `workout_start_time` |
| Σ `DistanceRecord` (m) | `distance` (km, 2-dp) |
| Σ `ActiveCaloriesBurnedRecord`, fallback `TotalCaloriesBurnedRecord` (kcal) | `calories` |
| avg/max `HeartRateRecord.bpm` | `avg_heart_rate` / `max_heart_rate` |
| Σ `StepsRecord.count` | `steps` |
| Σ `ElevationGainedRecord` (m) | `elevation_gain` |
| constant | `source = health_connect` |

`source = "health_connect"` is added as a `SourceTag` constant in quartz
(alongside `gps`/`manual`; other clients already publish free-form sources).

### RUNSTR parity (verified 2026-06-16 against `healthConnectService.ts` +
`workoutPublishingService.ts`)

- **Calories**: RUNSTR reads `ActiveCaloriesBurned`. We match (prefer active,
  fall back to total) so our figure lines up with theirs.
- **Title**: RUNSTR always emits a `title`, generating it from the activity when
  none exists. We default the pre-filled title to the activity name to match.
- **Richer superset (RUNSTR still parses)**: we also publish
  `avg/max_heart_rate` and `elevation_gain`, which RUNSTR reads but does not
  publish from Health Connect. Optional tags, no conflict.
- **Activity verbs**: RUNSTR buckets swimming/rowing/yoga into `gym`/`other` on
  import; we map them to their own NIP-101e verbs (more specific, still in the
  spec's verb set).
- **Coverage gap (not an incompatibility)**: RUNSTR imports elliptical / HIIT /
  pilates / dance / stairs as generic `gym`/`other`. Our `ExerciseType` enum has
  no generic verb, so those sessions are skipped. Adding a generic verb to
  quartz would close the gap — future work.

## 7. Future seam (out of scope now)

A `WorkManager` `CoroutineWorker` calling the same
`HealthConnectManager.readNewWorkouts` on a 15-min `PeriodicWorkRequest`, gated
behind a setting, posting a `POST_NOTIFICATIONS` notification that deep-links to
the pre-filled composer. The reader, mapper and `DetectedWorkout.toRoute()` are
built to be reused verbatim; only the trigger + notification + extra Play
data-safety disclosure would be new.

## 8. Licensing note

`androidx.health.connect:connect-client:1.1.0` — Apache-2.0, permissive, OK for
the distributed APK under MIT. No copyleft. Verified against the AndroidX
release (Apache-2.0 like all of Jetpack).
</content>
</invoke>
