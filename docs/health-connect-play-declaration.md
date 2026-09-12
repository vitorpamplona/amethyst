# Health Connect — Play Console declaration

Source text for the Health Connect permissions declaration in Play Console, written against what
`HealthConnectManager` actually does. Keep this file and the declaration in sync: Google re-reviews
the declaration on every Health Connect permission change.

Related code and copy:

- `amethyst/src/main/java/com/vitorpamplona/amethyst/service/workouts/health/HealthConnectManager.kt` — the only place the app touches Health Connect.
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/workouts/suggestion/DetectedWorkoutCarousel.kt` — the only UI that calls it.
- `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/workouts/health/HealthConnectRationaleActivity.kt` — the in-app rationale screen Health Connect links to.
- `PRIVACY.md` § "Health and fitness data (Health Connect)" — the public policy the declaration points at.

---

## 1. App functionality

> Amethyst is a social media client for the Nostr protocol — an open, decentralized social network.
> Users post updates and their followers read them. There is no Amethyst server and no Amethyst
> account: posts are signed on the device and sent to the public relay servers the user chooses.
>
> One of the things users post is a workout summary. Amethyst's Workouts section publishes a
> structured workout post (a NIP-101e "kind 1301" event) — the activity, when it happened, how long
> it lasted, and the metrics that describe the effort — so the people who follow the user can see
> what they did, congratulate them, and compare with their own. This is the fitness equivalent of
> sharing a run on Strava or a ride on Garmin Connect, except the post goes to the user's own
> chosen relays instead of a company's servers.
>
> Health Connect is used for exactly one thing: to fill that post in. Without it the user has to
> retype numbers their watch already recorded — activity, duration, distance, calories, heart rate,
> steps, climb — which is slow and error-prone enough that most people simply do not post. With it,
> the composer shows the workouts that finished in the last 7 days as one-tap cards; tapping one
> pre-fills the form, and the user then edits and decides whether to publish.

## 2. Reviewer walkthrough

> 1. Install and open Amethyst, and sign in (a new key can be generated in-app).
> 2. Open the navigation drawer (hamburger, top-left) and, under the **Feeds** section, tap
>    **Workouts**.
> 3. Tap the **+** button.
> 4. At the top of the composer is a card titled **"Share your workouts"**. Tap **What Amethyst
>    reads** to see the in-app rationale screen listing each data type and its purpose, then
>    **Connect** to trigger the Health Connect permission request.
> 5. Grant the permissions. The card is replaced by a horizontal list of the workouts Health Connect
>    holds from the last 7 days, labelled **"From Health Connect"**.
> 6. Tap any workout. The composer below is pre-filled with its activity type, title, duration,
>    distance, calories, heart rate, steps and elevation gain, ready to edit and publish.
>
> Note for testing on an emulator or a fresh device: the carousel only appears once Health Connect
> actually holds a finished exercise session from the last 7 days, written by some fitness app or
> watch. With an empty Health Connect database the composer correctly shows nothing.

## 3. Per-permission justification

Paste one row per permission into the corresponding field.

### READ_EXERCISE — ExerciseSession (and CyclingPedalingCadence)

> This is the workout itself and the anchor for everything else. Amethyst reads the exercise
> session's activity type, start time and end time, and turns them into the post's activity, date
> and duration: "Running, 42:15, yesterday". The session's time window is also what every other
> metric below is aggregated over, so without this permission the feature cannot exist at all — the
> app would have no notion of "a workout" to attach numbers to. The session's own title, when the
> source app set one, becomes the suggested post title.
>
> Benefit to the user: the workout their watch recorded appears as a one-tap suggestion instead of a
> blank form.
>
> Note on CyclingPedalingCadence: Health Connect grants that data type under the same
> READ_EXERCISE permission. Amethyst does not read, store, or publish cadence — it reads only
> ExerciseSessionRecord. There is no separate permission available to request one without the other.

### READ_DISTANCE — Distance

> Amethyst aggregates the distance recorded over the workout's time window and fills it into the
> distance field of the post. Distance is the single most important number in a running, cycling,
> walking, hiking, rowing or swimming post — "5.2 km" is what the post is about, and a shared
> workout without it is largely meaningless to the people reading it.
>
> Benefit to the user: they do not have to look up and retype the distance their watch already
> measured, and the figure published is the accurate recorded one rather than a remembered estimate.

### READ_ACTIVE_CALORIES_BURNED — ActiveCaloriesBurned

> Amethyst aggregates active calories over the workout's time window and fills in the post's energy
> field. Active calories (energy burned by the activity, excluding resting metabolism) are the
> correct figure for describing a workout, and the one other fitness apps and Nostr fitness clients
> publish, so using it keeps Amethyst's posts comparable with theirs.
>
> Benefit to the user: the effort figure in their post is the one their device computed, and it is
> filled in automatically.

### READ_TOTAL_CALORIES_BURNED — TotalCaloriesBurned

> Fallback for the field above. Not every source writes ActiveCaloriesBurned — several popular
> watches and fitness apps record only total energy for a session. When active calories are absent,
> Amethyst uses total calories for the same field so the energy figure is not simply blank for those
> users. When active calories are present they are always preferred, because total calories include
> basal burn and would over-report the workout.
>
> Benefit to the user: the feature works consistently regardless of which watch or fitness app they
> use, instead of silently dropping a metric for a subset of devices.

### READ_HEART_RATE — HeartRate

> Amethyst aggregates the average and maximum heart rate over the workout's time window and fills in
> the post's two heart-rate fields. Heart rate is the standard measure of how hard an effort was and
> is what makes two workouts of the same distance comparable — an easy recovery run and a hard
> tempo run look identical without it. It is a headline field of the NIP-101e workout post format
> Amethyst publishes.
>
> Benefit to the user: their followers can see how hard the session actually was, not just how far
> it went, without the user transcribing two more numbers by hand.

### READ_STEPS — Steps (and StepsCadence)

> Amethyst aggregates the step count over the workout's time window and fills in the post's steps
> field. For walking, running and hiking posts the step count is a primary metric — for a walk it is
> often the metric the user cares about most — and it is one of the fields of the workout post
> format.
>
> Benefit to the user: walk, run and hike posts carry the step count automatically.
>
> Note on StepsCadence: Health Connect grants that data type under the same READ_STEPS permission.
> Amethyst does not read, store, or publish cadence — it reads only the aggregated step count.
> There is no separate permission available to request one without the other.

### READ_ELEVATION_GAINED — ElevationGained

> Amethyst aggregates elevation gained over the workout's time window and fills in the post's climb
> field. Elevation is what distinguishes a flat ride or run from a hilly one — 30 km with 800 m of
> climbing is a completely different effort from 30 km on the flat — and it is the defining metric
> of a hiking post. It is one of the fields of the workout post format.
>
> Benefit to the user: hill and trail workouts are described accurately in the post rather than
> looking like flat ones.

## 4. Scope and data handling (state this alongside the table above)

> - **Read-only.** Amethyst holds no write permissions and never writes to Health Connect.
> - **Foreground only.** Reads happen only while the New Workout composer is on screen, in direct
>   response to the user opening it. Amethyst does not request READ_HEALTH_DATA_IN_BACKGROUND and
>   has no background worker, service or job that touches health data.
> - **Last 7 days only.** Only sessions finishing in the previous 7 days are read. Amethyst does not
>   request READ_HEALTH_DATA_HISTORY.
> - **No location.** Amethyst does not request READ_EXERCISE_ROUTE and never receives the GPS track
>   of a workout.
> - **No transmission without an explicit user action.** Health data is used to populate an on-screen
>   form. Nothing leaves the device unless the user taps a suggestion, reviews the pre-filled post,
>   and publishes it — at which point the post goes to the Nostr relays that user configured. The
>   developer operates no server, so no health data is ever received, stored or processed by the
>   developer or any third party on the developer's behalf.
> - **No secondary use.** Health data is never used for advertising, analytics, profiling, marketing
>   or sale, is never shared with data brokers or information-resellers, and is never used for
>   determining eligibility for insurance, credit or employment.
> - **No persistence beyond the session.** Suggestions are held in memory while the composer is open.
>   The only thing stored is whatever the user chose to publish, as an ordinary post.
> - **Revocable, and optional.** The user can turn the feature off at Settings → Compose Settings →
>   "Suggest workouts to share", or revoke the permissions in Health Connect, at any time; the rest
>   of the app is unaffected.
> - **In-app rationale.** Amethyst handles both ACTION_SHOW_PERMISSIONS_RATIONALE (Android 13 and
>   below) and ACTION_VIEW_PERMISSION_USAGE + CATEGORY_HEALTH_PERMISSIONS (Android 14+), showing a
>   screen that lists each data type, its purpose, and a link to the full privacy policy. The same
>   screen is reachable in-app from the "What Amethyst reads" link on the Connect card, before the
>   permission request.
> - **Privacy policy:** https://github.com/vitorpamplona/amethyst/blob/main/PRIVACY.md — see the
>   section "Health and fitness data (Health Connect)", which lists every data type, its purpose, and
>   each of the limits above.
