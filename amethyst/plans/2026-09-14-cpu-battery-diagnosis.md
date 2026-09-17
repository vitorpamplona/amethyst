# CPU & Battery Diagnosis Plan — attributing the app's CPU, foreground and background

**Date:** 2026-09-14
**Goal:** Find out *where* Amethyst's CPU time actually goes — foreground and
background — with evidence, then cut the waste. Constraint: **no feature may be
removed and no user-visible performance may regress.** Every change this plan
licenses must be either (a) work that produced no observable result, or (b) the
same result computed more cheaply or less often.

**Non-goal:** shipping optimizations on a hunch. The repo has already been
burned by this once — the ping-interval study
(`2026-07-12-relay-ping-interval-study.md`) found that a "obviously saves
battery" change (120s → 240s pings) saved nothing and broke relay tiers.
Nothing in Phase 3 ships without a Phase 1/2 measurement behind it.

---

## 0. What we already have (reuse, don't rebuild)

The battery groundwork is largely built. Read these before writing any code:

| Asset | What it already answers |
| --- | --- |
| `service/resourceusage/` (the ledger, `2026-07-12-resource-usage-ledger.md`) | Per-day, production, on-device counters: `cpu.ms`, `app.fgms`, `battery.drain.fg|bg`, `relay.connms.*`, `crypto.verify.count/us`, `pow.ms`, `app.starts`, per-role HTTP bytes/requests, `screen.<Name>.ms`. Shipped, privacy-reviewed, user-sendable over NIP-17. |
| `ResourceUsageScreen` + `ResourceUsageReportAssembler` | The UI and the Markdown report a user sends us. Any new counter lands here for free. |
| `service/priority/WorkerThreadPriorityGovernor` | Already walks `/proc/self/task`, reads each thread's `comm`, and keeps a live tid set. **This is the injection point for per-thread CPU attribution** — see §2.1. Its class doc is also the best existing writeup of the cold-start thread storm (~190 relays, ~650 threads). |
| `benchmark` build type (`amethyst/build.gradle.kts:223`) | `isProfileable = true` on top of `release` (R8 + baseline profile). The only valid vehicle for CPU measurement — the governor's doc proves a debug build inverts the answer. |
| `:benchmark` / `:baselineprofile` modules | Existing microbenchmarks (crypto, parsing, cache) and a `startupAndIngest` macrobenchmark journey to extend. |
| `composeCompiler { metricsDestination / reportsDestination }` (`amethyst/build.gradle.kts:377`) | Compose stability/skippability reports are already configured; they just need to be generated and read. |
| `ForegroundTracker` (`AppModules.kt:366`) | A `StateFlow<Boolean>` for process foreground, already plumbed to location, always-on service, screen time. **Nothing in the ingest or feed path consumes it.** |
| `logTime()` (`commons/.../util/DebugUtils.kt`) | Wraps every `FeedFilter.loadTop()` and `AdditiveFeedFilter.updateListWith()` — but only when `isDebug`. Useless for the release-only effects we care about. |

**The single biggest gap:** `cpu.ms` is *whole-process and undimensioned*. The
ledger plan says so explicitly ("Deliberately not tracked (v1): per-coroutine or
per-dispatcher CPU ... `cpu.ms` answers whether CPU matters at all first"). We
have now reached the point where it matters, so the answer to "whether" must
become "where". That is Phase 1.

---

## 1. The measurement questions

Ordered so each answer narrows the next.

1. **How much CPU, and when?** What is `cpu.ms / app.fgms` vs `cpu.ms` accrued
   while backgrounded? A backgrounded app that still burns foreground-rate CPU
   is the whole problem statement.
2. **Which threads?** Relay sockets (OkHttp), the kotlinx default dispatcher
   (parse/verify/feeds), the main thread (recomposition), RenderThread, Arti's
   tokio pool, Coil, media codecs. These are separable by thread name.
3. **Which call stacks inside those threads?** Only once (2) names a pool is it
   worth a sampling profiler.
4. **Is the work load-bearing?** For each hot path: does a user-visible result
   change because it ran? If not, it is waste and can go without touching
   features.

---

## 2. Phase 1 — make the app explain itself (production-safe instrumentation)

Everything here is release-safe, allocation-light, and lands in the existing
ledger so it reaches us through the existing consented report path. No new
permissions, no new network traffic.

### 2.1 Per-thread-group CPU attribution (`ThreadCpuSampler`)

**Mechanism.** `/proc/self/task/<tid>/stat` fields 14/15 (`utime`, `stime`) give
per-thread CPU in clock ticks. A process can always read its own; no root, no
`PACKAGE_USAGE_STATS`, works on a release build.

**Why it's cheap here.** `WorkerThreadPriorityGovernor.sweepOnce()` already
enumerates `/proc/self/task` and reads each `comm`. Adding a `stat` read to the
threads it already visits makes attribution a marginal cost on a sweep that
already runs (5s steady-state cadence). Sample into the ledger from the
accountant's **pre-flush hook**, exactly like `ProcessCpuSampler` — one pass per
30s flush, nothing scheduled while idle.

**Bucketing.** Thread names are stable enough to classify by prefix, and the
bucket set must be compile-time bounded (the accountant's key space must not be
keyed on runtime strings — see its class doc):

| Bucket | `comm` prefixes |
| --- | --- |
| `main` | the main tid |
| `render` | `RenderThread`, `hwuiTask`, `GPU completion` |
| `net` | `OkHttp`, `TaskRunner`, `Okio Watchdog` |
| `dispatch` | `DefaultDispatch`, `kotlinx.coroutin` |
| `io` | `pool-`, `IO-`, `Thread-` |
| `gc` | `HeapTaskDaemon`, `ReferenceQueueD`, `Finalizer*` |
| `jit` | `Jit thread pool`, `Runtime worker` |
| `tor` | Arti's tokio workers |
| `media` | `ExoPlayer`, `codec`, `AudioTrack`, `Opus` |
| `image` | Coil's dispatcher threads |
| `other` | anything else |

Key grammar follows the ledger's existing convention:
`cpu.<bucket>.<fg|bg>.ms`. Threads that exit between sweeps lose their tail —
accept that (it biases *against* short-lived work, and the governor already
prunes dead tids, so the bias is visible and bounded).

**Also fix the existing counter while here:** split `cpu.ms` into
`cpu.fg.ms` / `cpu.bg.ms` using `ForegroundTracker`. `UsageKeys.CPU_MS` stays as
the total so existing reports keep parsing.

**Validation:** Σ buckets must track `Process.getElapsedCpuTime()` deltas within
a few percent. A unit test with a fake `/proc` reader and a fake clock pins the
parse (field offsets after the `comm` field's parenthesised name are the classic
bug — `comm` can contain spaces and `)`, so parse from the **last** `)`).

### 2.2 Release-safe ingest/feed timing

`logTime` compiles to a bare call in release, so the feed pipeline is invisible
exactly where it matters. Add counters — not logs — on the paths that run per
event bundle:

- `ingest.bundle.count`, `ingest.notes.count` — how much firehose we actually take.
- `feeds.fanout.us` — total time in `AccountFeedContentStates.updateFeedsWith()`.
- `feeds.matched.count` / `feeds.nomatch.count` — how many of the ~48 feed
  updates per bundle produced a changed list vs produced nothing.

The last pair is the decisive metric for hypothesis H1 below, and it is three
counters, not a profiler session.

Timing with `System.nanoTime()` around a whole fan-out (one pair of reads per
bundle, ~1/s) is free. Do **not** time each of the 48 feeds individually in
production — that is 96 `nanoTime` calls/s for a number Phase 2 can get from a
profiler.

### 2.3 Background-work tripwires

Counters that make "something is running that shouldn't be" self-evident from a
user's report, without asking the user anything:

- `bg.recompose.count` — frames composed while `isForeground == false` (should
  be ~0; anything else is an invisible animation or a `while(true)` UI loop —
  the codebase has 21 of them in composables).
- `bg.feedfanout.count` — feed fan-outs that ran while backgrounded.
- `bg.imageload.count` — image decodes while backgrounded.

Each is one `add()` on a path that already exists.

### 2.4 A `dumpsys`-style on-device dump

Extend `ResourceUsageScreen` (or add a debug-menu action) with "copy CPU
attribution", emitting the bucket table for today + 7 days. This is what turns a
user's "my phone is hot" into an actionable bug report, and it is the same
consented path the ledger already uses — no new privacy surface, since bucket
names are compile-time constants and carry no URL, pubkey or content.

---

## 3. Phase 2 — off-device profiling protocol

Run on `:amethyst:installPlayBenchmark` (R8 + baseline profile + profileable).
**Never** conclude anything from a debug build or an emulator — the governor's
measurements document both traps in detail.

### 3.1 Scenarios (each 10 min, logged-in account with a real follow list)

| # | Scenario | What it isolates |
| --- | --- | --- |
| S1 | Cold start → Home feed loaded → idle 10 min, screen on, no touch | Steady-state foreground cost with zero user input. Any CPU here is pure background machinery wearing a foreground hat. |
| S2 | Home feed scroll (macrobenchmark, existing journey) | Recomposition, image decode, text layout. |
| S3 | Screen off, always-on notification service **on**, 30 min | The battery complaint most users actually file. |
| S4 | Screen off, always-on **off**, 30 min | Separates the service's cost from the app's own. |
| S5 | S3 with Tor on | Arti's share. |
| S6 | S3 with a 5k-follow account | Does cost scale with follows (i.e. with firehose volume)? |

### 3.2 Tools, in the order they earn their keep

1. **`batterystats` + `dumpsys cpuinfo`** — coarse, first. `adb shell dumpsys
   batterystats --reset`, run the scenario, `--charged <pkg>`. Gives partial
   wakelock time, alarm counts, wakeup counts, cpu foreground/background split.
   Frequently identifies the culprit outright.
2. **Perfetto** with `linux.process_stats` (per-thread CPU), `sched` and
   `android.power` (ODPM rails on Pixels ≥ 6). A 30-minute background trace with
   `ftrace` limited to `sched_switch`/`sched_waking` is the ground truth for
   "which thread wakes up how often". Cross-check it against §2.1's buckets —
   if they disagree, §2.1 is wrong.
3. **`simpleperf record -g`** on the thread the above named, and only then.
   `simpleperf` on a profileable release build gives real symbols via the
   mapping file.
4. **Compose compiler reports** (already configured) + Layout Inspector
   recomposition counts for S2. See the `compose-stability-diagnostics` and
   `compose-recomposition-performance` skills.
5. **`:benchmark` microbenchmarks** for any single function the above blames —
   the module already has the harness for parse/verify/cache work.

### 3.3 Protocol discipline

Borrowed from the governor's methodology, which is the house standard:

- Paired/round-robin A-B runs, ≥5 rounds, same device, same battery band, same
  network. Report medians and spread, not a single run.
- One variable per arm. A flag or `Settings.Global` key per hypothesis so an arm
  can be toggled without a rebuild (the governor's `amethyst_worker_nice`
  pattern).
- Record the noise floor first (two identical arms). A result inside the noise
  floor is not a result.

---

## 4. Named hypotheses (each falsifiable, each with its cheap test)

These came out of reading the ingest path. They are **suspects, not findings** —
the point of Phases 1–2 is to confirm or kill each one.

### H1 — Feed fan-out does O(feeds × feed-length) work per bundle, mostly for nothing

`AccountViewModel.init` (`AccountViewModel.kt:2382`) collects
`LocalCache.live.newEventBundles` and calls
`AccountFeedContentStates.updateFeedsWith()` (`AccountFeedContentStates.kt:334`),
which fans out to **48** feed states — every home tab, every discover tab,
polls, badges, articles, podcasts, workouts, git repos, calendars… — on every
bundle, regardless of which feed is on screen and regardless of whether the app
is visible at all.

For each feed, `FeedContentState.refreshFromOldState()`
(`FeedContentState.kt:148`) runs even when the filter matched nothing:
`updateListWith()` returns the *same* `oldList` instance, and the caller still
pays `.distinctBy { it.idHex }` (a HashSet of up to `limit()` strings plus an
ArrayList) `.toImmutableList()` (another copy) and `equalImmutableLists()` (an
O(n) identity walk) before discovering nothing changed. Typical `limit()` is
**200**. So a bundle that matches nothing anywhere still costs roughly
48 × 200 element visits plus ~96 collection allocations — once per bundle,
forever, including with the screen off.

Also: a feed that has never been opened sits in `FeedState.Loading`, which
fails the `Loaded || Empty` guard in `updateFeedWith()` and routes to
`invalidateData()` → `refreshSuspended()` → `localFilter.loadTop()` — a **full
`LocalCache` scan**. That self-heals after the first bundle (the state becomes
Loaded/Empty), but it means startup pays ~48 full cache scans.

- **Test:** §2.2's `feeds.matched.count` / `feeds.nomatch.count`. If no-match
  dominates by an order of magnitude, H1 is real.
- **Fix if confirmed (feature-neutral):** short-circuit when
  `newList === oldList` before the `distinctBy`/`toImmutableList`/compare; and
  gate the fan-out on "this feed has a live collector or is the visible tab".
  `FeedContentState` already owns a `MutableStateFlow`, so `subscriptionCount`
  is available — a feed nobody collects can mark itself dirty and rebuild on
  next collection instead of maintaining itself eagerly. Same rows, same
  freshness on screen, none of the invisible work.
- **Risk to guard:** "rebuild on next collection" must not add a visible stall
  when switching tabs. Measure tab-switch latency as a paired arm; if it
  regresses, keep eager maintenance for the tabs of the *current* screen only.

### H2 — Background ingest runs at full foreground cost

In always-on mode the `NotificationRelayService` deliberately keeps the pool
connected and relies on the Compose-tree subscriptions surviving
(`NotificationRelayService.kt:295-310`). That is correct for *notifications* —
but it means the full ingest pipeline (parse → Schnorr verify → `LocalCache`
mutation → `newEventBundles` → 48-way feed fan-out → StateFlow emissions) runs
at foreground rate with the screen off. Nothing in that chain reads
`ForegroundTracker`, which already exists.

- **Test:** S3 vs S4 Perfetto traces, plus §2.1's `cpu.*.bg.ms` buckets and
  §2.3's `bg.feedfanout.count`.
- **Fix if confirmed:** keep ingest (notifications need it) but suspend the
  parts whose only consumer is an invisible UI — the feed fan-out above all.
  Notifications are produced by `EventNotificationConsumer` /
  `NotificationDispatcher`, not by feed states, so this removes no feature.

### H3 — ~190 relay connections set a floor that no client-side tuning can lower

The governor documents ~190 simultaneous relay dials on cold start; the ping
study documents that 90% of relays server-ping every 30–70s and OkHttp must
pong each one. At 190 connections that is a few wakeups *per second*,
continuously, each with a TLS record to decrypt — before any event arrives.
Connection **count**, not ping interval, is the lever the ping study left
untested.

- **Test:** correlate `relay.connms.*` and `relay.connects.*` (already in the
  ledger) against `cpu.net.bg.ms` (new) across reports; and an S3 arm with the
  outbox fan-out capped.
- **Fix if confirmed:** this is the one with real feature risk (fewer relays =
  fewer events = a worse feed), so it needs its own study before anything
  ships. Candidate directions that are *not* feature-reducing: don't hold
  connections open to relays that have delivered nothing in N minutes while
  backgrounded (reconnect on foreground — the DNS cache and
  `SurgeDnsStore` already make redial cheap); prefer negentropy catch-up on
  resume over an always-open socket for low-yield relays.

### H4 — Signature verification volume

Every accepted event pays a Schnorr verify. The ledger *already* counts
`crypto.verify.count` and `crypto.verify.us`, so this is answerable **today from
existing reports** — no code needed.

- **Test:** read the counters from a real report. If `verify.us` is a small
  fraction of `cpu.ms`, close this and stop speculating about crypto.

### H5 — Invisible recomposition / animation loops

There are 21 `while (true)` loops inside composables (timestamp tickers, typing
indicators, live-status blinkers, `NowProvider`). Compose stops recomposing an
invisible window, but a `LaunchedEffect` loop keeps running and keeps writing
state as long as its composable stays in the tree — and the Activity is not
destroyed when backgrounded.

- **Test:** §2.3's `bg.recompose.count`; then Perfetto for main-thread wakeups
  during S3.
- **Fix if confirmed:** these loops should observe lifecycle (or
  `ForegroundTracker`) and pause; a clock that stops ticking while the screen is
  off loses nothing, since it re-reads the real time when it resumes.

### H6 — Thread count itself

~650 threads at nice 0→10. Each carries a stack and a scheduler entity; the
governor's own sweep exists because of this. Worth measuring whether the OkHttp
`TaskRunner` pool and the connection-per-relay model can share fewer threads —
but only after H1–H3, since it is the most invasive and the least likely to
dominate.

---

## 5. Phase 3 — the fix loop

For each hypothesis that survives Phase 2:

1. Write the paired A-B arm behind a runtime toggle (`Settings.Global`, the
   governor's pattern) so the fix can be measured without a rebuild.
2. Measure on the `benchmark` build type, ≥5 paired rounds, report median +
   spread.
3. **Prove feature-neutrality**, not just speed: the affected feed shows the
   same rows in the same order; notifications still fire in S3; tab-switch
   latency does not regress. State the check in the PR.
4. Land the toggle's winning default, keep the toggle for a release so a
   regression can be bisected in the field.
5. Add the counter that would have caught the regression to the ledger's alert
   thresholds (`ResourceUsageAlerts`).

---

## 6. Order of work

| Step | Work | Gate | Status |
| --- | --- | --- | --- |
| 1 | Read `crypto.verify.*` + `cpu.ms` + `app.fgms` from existing reports | Answers H4 for free; sizes the whole problem | **needs a device** |
| 2 | §2.1 `ThreadCpuSampler` + `cpu.fg/bg` split | Ships in the next release; every later step reads its output | **shipped** |
| 3 | §2.2 ingest/feed counters | Answers H1, H2 from production data | **shipped** |
| 3a | Wake-up counters: device awake-vs-asleep, relay wake cadence | Covers the energy term CPU counters structurally cannot | **shipped** |
| 3b | §2.3 background tripwires (frames drawn, image decodes) | Answers H5 | **shipped** |
| 4 | §3 S1/S3/S4 Perfetto + batterystats on a real device | Confirms or kills what step 3 suggested; catches anything the counters are blind to | **needs a device** |
| 5 | H1 fix (the cheap half: `newList === oldList` short-circuit) | Lowest risk, measurable immediately | **shipped** |
| 6 | H1 fix (collector-gated fan-out) + H2 background gating | Needs the tab-switch-latency guard | todo — blocked on step 4 |
| 7 | H5 lifecycle-aware loops | Mechanical once located | todo — blocked on step 3b |
| 8 | H3 relay-count study | Own plan; do not touch before 1–7 | todo |

### What has landed so far

Two commits on `claude/cpu-usage-battery-optimization-2sbqhh`:

- **`feat(ledger): attribute CPU time by thread subsystem and visibility`** —
  `ProcessCpuSampler` now also writes `cpu.fg.ms` / `cpu.bg.ms`; a new
  `ThreadCpuSampler` diffs `/proc/self/task` per-thread counters into
  `cpu.<bucket>.<fg|bg>.ms` over a fixed bucket vocabulary
  (`ThreadCpuBuckets`), with the unattributed remainder booked to `gone` so
  the buckets sum to the process total. Surfaced on the resource-usage screen
  and in the sendable report. Thread names never leave the classifier —
  OkHttp's carry relay hostnames.
- **`feat(feeds): measure the per-bundle feed fan-out, and skip the work it
  wastes`** — `FeedUpdateMeter` (commons) + `FeedUsageMeter` (Android) record
  `ingest.bundles/notes.<vis>.count`, `feeds.fanout.<vis>.us|count` and
  `feeds.{skipped,changed,unchanged,rebuilt}.<vis>.count`; plus the H1
  short-circuit, which skips the `distinctBy` + copy + identity-walk when an
  additive filter returned the on-screen list unchanged.

- **`fix(ledger): correct four counters that were measuring the wrong thing`** —
  an audit found `MAIN` unreachable (UI-thread CPU was landing in `misc`), the
  fan-out timer measuring coroutine enqueue rather than work, `feeds.rebuilt.*`
  never firing from the path that causes rebuilds, and a tid recycled between
  sweeps inheriting the dead thread's bucket.
- **wake-up counters** — `device.awake.<vis>.ms` / `device.sleep.<vis>.ms`
  (`DeviceSleepSampler`) and `relay.wakes.<net>.<vis>`
  (`RelayWakeEstimator`).
- **wake cost and background tripwires** — `wakework.windows.<vis>.count`,
  `wakework.<vis>.ms` and a `wakework.span.*` histogram (`WakeWorkTracker`);
  `ui.frames.<vis>.*` / `ui.slowframes.<vis>.count` (`FrameMetricsCollector`);
  `coil.decodes.<vis>.count` / `coil.fetches.<vis>.count`
  (`ImageUsageListener`).

### Why "frames drawn" replaced "recompositions while backgrounded"

§2.3 originally asked for `bg.recompose.count`. Recomposition turns out not to
be **passively** observable: the in-process hooks that can see it
(`withFrameNanos`, a self-reposting `Choreographer` callback) keep the frame
clock running by asking, so on a battery investigation the measurement would
manufacture the work it claims to measure.
`Window.OnFrameMetricsAvailableListener` fires only when a frame really was
produced, asks for nothing, and reports the half that actually costs energy —
GPU and display pipeline rather than the composition upstream of it. A
backgrounded window is not drawn, so `ui.frames.bg.count` reading zero closes
that half of H5 with evidence, and anything else names a surface still drawing
unseen.

Everything is behaviour-preserving. The `/proc` field offsets, the short-circuit,
each audit fix, and the two wake counters are pinned by tests verified by
mutation.

### What these counters can and cannot settle

Worth stating plainly, because it bounds what Phase 2 still has to do on a real
device.

**They can** say whether the app burns CPU with the screen off and which
subsystem burns it; whether the feed fan-out does useful work or maintains ~48
feeds nobody is watching; and whether signature verification is material.

**They cannot** measure energy. Three specific gaps:

1. **CPU ms is not joules.** A millisecond on a big core at 2.8 GHz is roughly
   an order of magnitude more energy than one on a little core at 600 MHz. The
   buckets are unweighted milliseconds.
2. **Radio tail energy is invisible to CPU accounting.** The 2026-07-12 ping
   study already found the dominant proxy is connection-time, because ~90% of
   relays server-ping every 30-70s. That energy costs almost no CPU.
   `relay.wakes.*` is the closest available proxy and is why it was added.
3. **Wake-ups, not milliseconds, dominate background energy.** Covered now by
   `device.awake/sleep` (how much the device ran at all), `relay.wakes` (how
   often relay traffic pulled it out of idle) and `wakework.*` (how long it
   stayed busy each time — a wake is not a fixed price, since the device cannot
   suspend again until the work settles). Still device-wide for the first: the
   ledger can say the device never slept, not that Amethyst is what kept it
   awake.

So the ledger is built for **correlation across a corpus of reports** ("devices
with high `cpu.bg.ms` also show high `battery.drain.bg`"), not for attribution
from a single one. `adb shell dumpsys batterystats` gives per-app wake-up counts,
wakelock attribution and radio state directly, on one device, today — which is
why step 4 remains the gate and not a formality.

**Stop condition for Phase 1/2:** we can state, from data, what fraction of
`cpu.ms` each bucket owns in S1 and in S3, and name the top three call sites.
Until then, no optimization ships.

---

## 7. Explicitly out of scope

- Reducing the number of features, feeds, or screens.
- Lowering refresh rates, animation quality, or feed freshness *on screen*.
- Changing the WebSocket ping interval — settled by the 2026-07-12 study;
  reopening it needs new evidence, not a new opinion.
- Desktop (`desktopApp`) CPU. The ledger is Android-module today; extraction to
  `commons` is mechanical but is not this plan.
- Any telemetry that leaves the device without the existing explicit-consent DM
  flow.
