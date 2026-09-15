# Health Connect — Play Console declaration

Source text for the Health Connect permissions declaration in Play Console. Keep this file and
the declaration in sync: Google re-reviews the declaration on every Health Connect permission
change.

**Approved use case claimed: "Fitness, wellness and coaching"** — *"apps designed to help users
track, monitor, analyze, manage, and improve their physical fitness."*

## Why the earlier declaration was rejected, and what changed

The first submission described the feature as "read finished workouts so the user can post them
to their social network." That is not one of the six approved use cases, and it reads as the
prohibited "publicly displaying or socially sharing sensitive data." It was rejected as *"Use of
permission is not a permitted/valid use case."*

The app changed, not just the wording. Amethyst now has **My Fitness**, a personal training
dashboard that summarises the user's own Health Connect workouts for them: weekly totals,
week-over-week movement, a per-activity breakdown, best efforts, active days and a training
streak. It is reached from the Workouts screen and works fully whether or not the user ever
publishes anything. Publishing a workout is now one optional action on a row of that dashboard.

**The permissions serve the dashboard.** If the declaration below is ever re-read against the
app, the test to apply is: every data type listed is rendered back to the user as their own
statistic, on a screen that has no posting requirement.

**The dashboard does not depend on Health Connect.** It builds the training log from two sources:
Health Connect, and the user's own published kind 1301 workout events. A user who never grants a
health permission still gets the full summary of everything they have logged — Health Connect adds
the device detail (heart rate, steps, climb) and the workouts they never posted. This matters for
the declaration's honesty: Health Connect *enriches* a tracking feature that exists on its own
rather than *being* the feature.

Related code:

- `service/workouts/health/WorkoutStats.kt` — all dashboard arithmetic; pure, unit-tested.
- `service/workouts/health/TrainingLog.kt` — merges Health Connect with the user's own published
  workouts, deduping the ones that appear in both.
- `ui/screen/loggedIn/workouts/fitness/MyFitnessScreen.kt` — the dashboard.
- `service/workouts/health/HealthConnectManager.kt` — the only place the app touches Health Connect.
- `ui/screen/loggedIn/workouts/health/HealthConnectRationaleActivity.kt` — the in-app rationale screen.
- `PRIVACY.md` § "Health and fitness data (Health Connect)".

---

## 1. App functionality

> Amethyst is a social client for Nostr, an open decentralized social protocol. It also includes
> a fitness feature, **My Fitness**, which is what uses Health Connect.
>
> My Fitness is a personal training dashboard. It builds a log of the user's own workouts — from
> the workouts their watch or fitness app has saved to Health Connect, and from the workout
> records they have logged in Amethyst itself — and turns it into a picture of how that person is
> training: how much they did this week and whether that is up or down on last week, how their
> time splits across running, cycling, walking, swimming and the gym, their best efforts, how
> many days they trained, and their current streak of consecutive active days.
>
> The purpose is to help the user track, monitor, analyze and improve their own physical
> fitness. Health Connect is not a precondition for it: the dashboard works from the user's own
> logged workouts alone, and Health Connect is what lets it also count the sessions their watch
> recorded and show the device metrics — heart rate, steps and elevation — that a hand-entered
> workout does not carry. The numbers are shown to the person who recorded them. No part of the dashboard
> requires posting anything, and nothing is transmitted anywhere to produce it — the summary is
> computed on the device from Health Connect data and displayed.
>
> Separately, and entirely optionally, a user who wants to tell their followers about a
> particular workout can tap "Share this workout" on a row of that dashboard. That opens a
> composer pre-filled with the workout's figures, which the user reviews and chooses to publish
> or discard. Sharing is a user-initiated action on top of the tracking feature, with per-post
> review and consent; it is never automatic, and the tracking feature is fully usable without it.

## 2. Reviewer walkthrough

> 1. Install and open Amethyst, and sign in (a key can be generated in-app).
> 2. Open the navigation drawer (hamburger, top-left), and under **Feeds** tap **Workouts**.
> 3. In the top bar, tap the **chart icon** to open **My Fitness**.
> 4. The screen explains what will be read and offers **What Amethyst reads** (the full rationale
>    screen) and **Connect**. Tap Connect and grant the permissions. (If the account has already
>    logged workouts in Amethyst, the dashboard is already populated from those and the Health
>    Connect offer appears as a banner above it instead — the feature does not gate on the
>    permission.)
> 5. The dashboard appears: "This week" totals with the change against last week; the streak,
>    active-days and workout-count tiles; the four-week weekly average; the per-activity
>    breakdown; best efforts; and the recent-workout list.
> 6. Everything above is the tracking feature. To see the optional sharing path, tap **Share this
>    workout** on any row — it opens a pre-filled composer that the user must confirm.
>
> Note for a fresh test device: the dashboard needs Health Connect to actually hold finished
> exercise sessions from the last four weeks, written by some fitness app or watch. With an empty
> Health Connect database it correctly reports that nothing has been recorded.

## 3. Per-permission justification

Each permission maps to a statistic the dashboard shows the user about their own training.

### READ_EXERCISE — ExerciseSession (and CyclingPedalingCadence)

> The workout itself, and the spine of the whole dashboard. Amethyst reads each session's
> activity type, start time and end time to produce: the count of workouts this week versus last
> week, total training time, the per-activity breakdown ("Cycling: 3×, 4h 10m"), the number of
> active days, and the consecutive-day training streak. Every other metric below is aggregated
> over the session's time window, so without this permission there is no notion of "a workout" to
> attach any statistic to and the feature cannot exist.
>
> Benefit to the user: they can see how consistently and how much they are actually training,
> which is the basic question a training summary answers.
>
> CyclingPedalingCadence is granted by Health Connect under this same permission. Amethyst does
> not read, store, or display cadence — it reads only ExerciseSessionRecord. There is no separate
> permission that grants one without the other.

### READ_DISTANCE — Distance

> Aggregated per workout and summed into the dashboard's distance figures: distance this week,
> the percentage change against last week, the four-week weekly average, distance per activity,
> and the "Longest distance" best effort.
>
> Benefit to the user: distance is the primary training-load measure for running, cycling,
> walking, hiking, rowing and swimming. Seeing this week's total against last week's is how a
> user knows whether they are building up or falling off.

### READ_ACTIVE_CALORIES_BURNED — ActiveCaloriesBurned

> Aggregated per workout and summed into the dashboard's energy figures: calories this week and
> the change against last week. Active calories — energy burned by the activity, excluding
> resting metabolism — are the correct measure of a workout's cost.
>
> Benefit to the user: a view of training energy expenditure over time, and whether it is rising
> or falling week to week.

### READ_TOTAL_CALORIES_BURNED — TotalCaloriesBurned

> Fallback for the same figure. Several widely used watches and fitness apps record only total
> energy for a session and never write ActiveCaloriesBurned. Where active calories are missing,
> Amethyst uses total calories so the energy statistics are not simply blank for those users;
> where active calories exist they are always preferred, because total calories include basal
> burn and would overstate the workout.
>
> Benefit to the user: the dashboard reports energy consistently regardless of which watch or app
> they use, instead of silently omitting the metric for a subset of devices.

### READ_HEART_RATE — HeartRate

> Aggregated per workout into the average and maximum heart rate, then combined across the window
> into a duration-weighted average heart rate (so a two-hour ride weighs more than a ten-minute
> walk), a maximum for the period, the per-workout effort shown on each row of the recent list,
> and the "Highest heart rate" best effort.
>
> Benefit to the user: heart rate is how a user distinguishes an easy week from a hard one at the
> same distance, and the standard signal for whether they are training too hard or too easily. It
> is the single most informative intensity measure the dashboard can show.

### READ_STEPS — Steps (and StepsCadence)

> Aggregated per workout and summed into the weekly step average and the "Most steps" best
> effort.
>
> Benefit to the user: for walking and hiking — and for many users the majority of their activity
> — step count is the metric they actually track, and a weekly average is how they judge whether
> they are keeping it up.
>
> StepsCadence is granted by Health Connect under this same permission. Amethyst does not read,
> store, or display cadence — it reads only the aggregated step count. There is no separate
> permission that grants one without the other.

### READ_ELEVATION_GAINED — ElevationGained

> Aggregated per workout and summed into the weekly climb average and the "Biggest climb" best
> effort, and shown per workout in the recent list.
>
> Benefit to the user: elevation is what separates a flat week from a hard hilly one at identical
> distance and time. Without it, a user training on hills sees no difference between a punishing
> week and an easy one, and the dashboard misrepresents their training load.

## 4. Scope and data handling

> - **Read-only.** Amethyst holds no Health Connect write permissions and never writes to it.
> - **Foreground only.** Reads happen only while the user has the My Fitness dashboard or the
>   workout composer on screen. Amethyst does not request READ_HEALTH_DATA_IN_BACKGROUND and has
>   no background worker, service or job that touches health data.
> - **Last four weeks only.** The dashboard reads a rolling 28-day window. Amethyst does not
>   request READ_HEALTH_DATA_HISTORY and cannot see anything older.
> - **No location.** Amethyst does not request READ_EXERCISE_ROUTE and never receives the GPS
>   track of a workout.
> - **No transmission to produce the feature.** The dashboard is computed on the device and
>   displayed. Nothing is uploaded to render it. The developer operates no server, so no health
>   data is ever received, stored or processed by the developer or any third party acting for the
>   developer.
> - **Sharing is separate, explicit and per-item.** A user may choose to publish one workout as a
>   post. That takes a deliberate tap, shows the user exactly what will be published, and requires
>   them to confirm. It is never automatic, never bulk, and never a condition of using the
>   tracking feature. Published posts go to the Nostr relays that user configured.
> - **No secondary use.** Health data is never used for advertising, analytics, profiling,
>   marketing or sale, never shared with data brokers, and never used to determine eligibility for
>   insurance, credit or employment.
> - **No persistence beyond the session.** The summary is held in memory while the screen is open.
>   The only thing stored is whatever the user chose to publish, as an ordinary post.
> - **Revocable and optional.** The user can revoke the permissions in Health Connect at any time —
>   the dashboard immediately returns to its prompt — or turn the composer suggestions off under
>   Settings → Compose Settings. The rest of the app is unaffected.
> - **In-app rationale.** Amethyst handles ACTION_SHOW_PERMISSIONS_RATIONALE (Android 13 and
>   below) and ACTION_VIEW_PERMISSION_USAGE + CATEGORY_HEALTH_PERMISSIONS (Android 14+), showing a
>   screen listing each data type, its purpose and a link to the privacy policy. The same screen
>   is reachable from "What Amethyst reads" before any permission is requested.
> - **Privacy policy:** https://github.com/vitorpamplona/amethyst/blob/main/PRIVACY.md — see
>   "Health and fitness data (Health Connect)".
