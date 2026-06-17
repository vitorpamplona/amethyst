---
title: App Launch Optimization (Desktop, Foundation-first)
type: feat
status: active
date: 2026-06-17
origin: docs/brainstorms/2026-06-17-feat-app-launch-optimization-brainstorm.md
deepened: 2026-06-17
---

# App Launch Optimization (Desktop, Foundation-first)

## Progress Log

| Date       | Phase | Outcome                                                                                  | Commit      |
|------------|-------|------------------------------------------------------------------------------------------|-------------|
| 2026-06-17 | 1.1   | `AccountManagerLoadStateTransitionsTest` — 2 tests pass                                  | `ff55898ab` |
| 2026-06-17 | 1.2   | `LocalRelayStoreHydrationTest` — 5 tests pass                                            | `ff55898ab` |
| 2026-06-17 | 1.3   | `LocalRelayStore` gains `homeDir` ctor param (default unchanged)                         | `ff55898ab` |
| 2026-06-17 | 5.1   | `IconResources` collapses 4 sites + 2 `ImageIO.read` calls into one lazy each; 5 tests   | `b338d7db4` |

**Next on the critical path:** Phase 1.4 (App() Compose smoke test — needs DeckState / WorkspaceManager / TorManager mocks) → Phase 2 (in-process relay seam in `:quartz` testFixtures) → Phase 3 (benchmark harness) → Phase 4 (baseline) → Phase 5.2 (feed bootstrap gate fix). Phase 5.1 has already landed but its end-to-end delta will be measured once Phase 3 is in place.

## Enhancement Summary

**Deepened:** 2026-06-17 — 5 review agents (code-simplicity, architecture-strategist, performance-oracle, pattern-recognition, spec-flow-analyzer) plus repo-research-analyst.

**Key changes from initial plan:**

1. **Plan relocated** from `docs/plans/` (frozen per CLAUDE.md) to `desktopApp/plans/`.
2. **Phase 5.3 (sequential `remember` chain) cut** — spawn separate plan if Phase 4 baseline justifies. Avoids investigation scope creep.
3. **Warm-boot benchmark variant deferred** — all in-scope fixes target cold-boot; defer until a warm-path fix appears.
4. **Memory metric deferred** — `runComposeUiTest` heap isn't representative of real Swing/Skia; JVM `System.gc()` semantics unreliable. Revisit after baseline.
5. **Test fixtures placed in `:quartz` testFixtures** (not `:commons`) — `:quartz` already proves KMP + `java-test-fixtures` works (consumes `:geode` fixtures at `quartz/build.gradle.kts:352-358`). `:commons` KMP attempt was unnecessary risk.
6. **Use `createComposeRule()`** (not `runComposeUiTest`) — repo convention per `DesktopLaunchSmokeTest.kt:24`.
7. **Repo's existing test pattern**: `backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ... toList(states) } + advanceUntilIdle()` (per `AccountManagerStateTransitionTest.kt:73-95`).
8. **Cold benchmark forks JVM per sample** (shell-script driver) — same-JVM iterations measure JIT warmth, not cold boot.
9. **Renamed `t_compose_first_frame` → `t_first_composition_apply`** with explicit "not a real Skia frame" disclaimer.
10. **Event-count instrumentation via `Modifier.onPlaced` + `AtomicInteger`** — semantic-tree polling has 16ms quantization + tree-traversal bias.
11. **Pinned JVM flags** (`-Xms512m -Xmx512m -XX:+UseG1GC`); control benchmark (empty `Box`) measures harness floor.
12. **N=20 warm iterations, median + IQR + min** — Mann-Whitney U for fix delta significance, not t-test.
13. **`AccountManagerLoadStateTransitionsTest`** renamed to avoid collision with existing `AccountManagerStateTransitionTest`.
14. **Cut Internal/Remote AccountManager tests** — only ViewOnly drives the benchmark; deferred coverage is a separate scope.
15. **Cut `tools/launch-fixture/capture.sh`** — manual one-shot fixture commit; document `amy` commands in README.

### New Considerations Discovered
- `:quartz` already consumes `:geode` testFixtures across KMP boundary — KMP+testFixtures friction is overstated.
- `runComposeUiTest` skips real Swing/Skia surface; `t_first_composition_apply` measures composition cost, not paint.
- `RelayPool` queue-pre-connect behavior is the hinge for Phase 5.2 candidate (a) vs (b); must verify before refactor.
- Both home + DM subscriptions share the same gate (`Main.kt:1283-1326` + `1330`); fix must share a helper.
- `InProcessWebSocket` latency floor (~10-50ms) may swallow icon-decode delta on `t_n_events`; microbench the ImageIO.read cost directly.

---

## Overview

Build the **testing + benchmarking foundation** needed to safely refactor Amethyst Desktop's launch path, capture a quantitative baseline, then ship two targeted launch-path fixes guided by the resulting numbers. Android is explicitly deferred to a follow-up plan; sequential `remember` chain refactor (originally Phase 5.3) is deferred to a separate plan post-baseline.

Phased delivery (Approach A from brainstorm):

1. **Test pyramid foundation** — unit tests around `AccountManager.loadSavedAccount` (ViewOnly path only) + `LocalRelayStore.hydrate`; three `App()`-level Compose UI smoke tests. No launch-path code change beyond an optional `homeDir` ctor param on `LocalRelayStore`.
2. **Deterministic relay seam** — `InProcessWebsocketBuilder` + `FixtureNostrServer` in `:quartz` testFixtures. Real-world snapshot fixture (50 kind:1 + metadata for one well-known npub).
3. **Benchmark harness** — JVM-only harness driving `App()` via `createComposeRule` + onPlaced markers. Two harnesses: cold (fork-per-sample shell driver, N=10) and warm (single JVM, N=20). Memory + warm-boot DEFERRED.
4. **Baseline capture** — run benchmark on `main`, commit numbers as the reference point.
5. **Targeted fixes** — icon decode (cheap, microbench independent), feed bootstrap relay gate (medium refactor); each re-benchmarked after.

(see brainstorm: `docs/brainstorms/2026-06-17-feat-app-launch-optimization-brainstorm.md`)

---

## Problem Statement

Cold-boot perception is a primary UX signal and currently **unmeasured**. No timing markers exist in `Main.kt`, `App()`, or `AccountManager.loadSavedAccount`. The desktop launch path has identified bottlenecks (`Main.kt:218,302,988` triple icon decode on main thread; `Main.kt:1283-1330` home + DM subscriptions both gated on `connectedRelays.first { isNotEmpty() }` with a 30s timeout) — but refactoring boot code without tests is exactly how regressions ship.

The only existing test that touches launch wiring is `DesktopLaunchSmokeTest.kt:24` (drives `LoginScreen` only via `createComposeRule`); nothing covers `App()`, `MainContent`, or the `AccountState` transitions. We need the safety net **before** the refactors.

---

## Proposed Solution

Foundation-first: write tests before touching launch code. Inject a deterministic in-memory relay via the already-open `WebsocketBuilder` constructor seam on `RelayConnectionManager` (`Main.kt:834`). Drive the read-only npub flow (`AccountManager.loadReadOnlyAccount` — pure, no I/O, no keychain) as the headline benchmark scenario. Measure four time metrics (composition apply, account hydration done, first event, N=10 events; memory DEFERRED). Capture cold (fork-per-sample) and warm (single-JVM N=20) runs locally. Then fix one bottleneck at a time and prove the delta with numbers.

Key insight (deepen-plan): **most seams already exist.** `RelayConnectionManager` is `open class` taking `WebsocketBuilder`. `App()` is window-agnostic. Quartz already ships `InProcessWebSocket` + `NostrServer`. `:quartz` already consumes `:geode` testFixtures across the KMP boundary. The brainstorm's "FakeWebsocketBuilder" is a 20-line wrapper.

---

## Technical Approach

### Architecture

```
Test JVM (jvmTest)
 ├── LaunchMarkers (single-threaded, mutableMapOf<String, Duration>)
 ├── createComposeRule { setContent { MaterialTheme { App(...) } } }
 │    └── App() with injected deps:
 │         ├── AccountManager — temp homeDir, no keychain (ViewOnly only)
 │         ├── LocalRelayStore — temp homeDir param (new ctor arg)
 │         ├── DesktopRelayConnectionManager — InProcessWebsocketBuilder(fakeServer)
 │         └── LocalRelayMaintenance.start() suppressed in tests (no refactor)
 ├── InProcessWebsocketBuilder (:quartz testFixtures)
 │    └── wraps InProcessWebSocket → FixtureNostrServer
 │         └── loads fixtures/launch/<name>.jsonl, matches REQ via Filter.match
 └── Fixture: quartz/src/testFixtures/resources/fixtures/launch/fiatjaf-50.jsonl
```

### Module Placement

- `:quartz` testFixtures: `InProcessWebsocketBuilder`, `FixtureNostrServer`, fixture JSONL.
- `:desktopApp:jvmTest`: `AccountManagerLoadStateTransitionsTest`, `LocalRelayStoreHydrationTest`, `AppStateMachineTest`, `LaunchMarkers`, `LaunchBenchmark`.
- `:commons/commonMain`: `NoteCardTags` constants object (so Android can reuse).
- `:desktopApp:jvmMain`: `NoteCard` adds `Modifier.testTag(NoteCardTags.ROOT).onPlaced { ... }` — one-line change with negligible production cost.

Consumer wiring: `desktopApp/build.gradle.kts` adds `jvmTestImplementation(testFixtures(project(":quartz")))`. Pattern proven at `quartz/build.gradle.kts:352-358` consuming `testFixtures(project(":geode"))`.

### Marker Registry

```kotlin
// desktopApp/src/jvmTest/kotlin/.../benchmark/LaunchMarkers.kt
object LaunchMarkers {
    private val timestamps = mutableMapOf<String, Duration>()
    private var start: TimeMark? = null

    fun start() { timestamps.clear(); start = TimeSource.Monotonic.markNow() }
    fun mark(name: String) {
        if (name !in timestamps) timestamps[name] = start!!.elapsedNow()
    }
    fun snapshot(): Map<String, Duration> = timestamps.toMap()
}
```

Single-threaded — Gradle test parallelism disabled for benchmark suite (`maxParallelForks = 1` filtered to `*LaunchBenchmark*`).

Observation points (all in test code, zero production instrumentation):

- **`t_first_composition_apply`** — first `composeTestRule.waitForIdle()` returns after `setContent`. (Renamed from `t_compose_first_frame` — does NOT represent real Skia/GPU frame.)
- **`t_account_logged_in`** — observed via `backgroundScope.launch(UnconfinedTestDispatcher) { accountManager.accountState.toList(states) }` — marker recorded when `AccountState.LoggedIn` arrives.
- **`t_first_event`** — `Modifier.onPlaced` callback on `NoteCard` increments an `AtomicInteger`; marker recorded when counter goes 0→1.
- **`t_n_events`** — same counter reaches 10.

NoteCard production change is small:

```kotlin
// commons/src/commonMain/.../ui/note/NoteCardTags.kt (new)
object NoteCardTags {
    const val ROOT = "amethyst.note_card.root"
}

// desktopApp/src/jvmMain/.../ui/note/NoteCard.kt:97
fun NoteCard(...) {
    Surface(modifier = Modifier
        .testTag(NoteCardTags.ROOT)
        .onPlacedHook()        // no-op in production, calls LaunchInstrumentation in tests
        ...) { ... }
}
```

`onPlacedHook` is a `Modifier` extension defined in `commons/commonMain` that's a no-op by default; tests swap it via a CompositionLocal `LocalLaunchInstrumentation` (default = `LaunchInstrumentation.Noop`). One-line override in `AppStateMachineTest` and `LaunchBenchmark`.

### Fake Relay Wire Path

```
Test sets up: FixtureNostrServer(fixture = "fiatjaf-50.jsonl")
              ↓
Test constructs: DesktopRelayConnectionManager(InProcessWebsocketBuilder(server))
              ↓
App() → RelayConnectionManager → NostrClient → RelayPool → BasicRelayClient
              ↓ (uses InProcessWebsocketBuilder)
InProcessWebSocket connects to FixtureNostrServer
              ↓
Test sets account.relays to listOf(NormalizedRelayUrl("wss://test.invalid"))
              ↓
NostrClient sends REQ frames; FixtureNostrServer matches by Filter, replays EVENTs + EOSE
              ↓
DesktopLocalCache.consume(event, relay = "wss://test.invalid", wasVerified = true)
              ↓
LocalFeedProvider observes; FeedScreen LazyColumn composes NoteCards
              ↓
Modifier.onPlaced fires per NoteCard, AtomicInteger inc, LaunchMarkers records t_first_event then t_n_events
```

### Implementation Phases

#### Phase 1 — Test pyramid foundation

**1.1 — `AccountManagerLoadStateTransitionsTest` (ViewOnly only)**

New file: `desktopApp/src/jvmTest/kotlin/com/vitorpamplona/amethyst/desktop/account/AccountManagerLoadStateTransitionsTest.kt`

Renamed to avoid collision with existing `AccountManagerStateTransitionTest.kt`. Covers ViewOnly happy path + one decode-failure path (corrupt `accounts.json.enc`). Internal + Remote deferred — they are unrelated to the benchmark, and adding them is scope creep.

Pattern follows repo convention (`AccountManagerStateTransitionTest.kt:73-95`):

```kotlin
@Test
fun viewOnlyAccountTransitionsThroughLoadingToLoggedIn() = runTest {
    val storage = mockk<SecureKeyStorage>(relaxed = true)
    val tempDir = createTempDirectory("acctmgr-load-state").toFile()
    writeAccountsJsonEnc(tempDir, viewOnlyAccountInfo(testNpub))
    val mgr = AccountManager(storage, tempDir)

    val states = mutableListOf<AccountState>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        mgr.accountState.toList(states)
    }
    mgr.loadSavedAccount()
    advanceUntilIdle()

    assertTrue(states.size >= 3)
    assertTrue(states.first() is AccountState.Loading)
    assertTrue(states.last() is AccountState.LoggedIn)
    assertEquals(true, (states.last() as AccountState.LoggedIn).isReadOnly)
}
```

Acceptance: 2 tests pass (happy + decode failure).

**1.2 — `LocalRelayStoreHydrationTest`**

Covers `LocalRelayStore.hydrate` invariants (`LocalRelayStore.kt:115-161`). 5 tests:

- kind:3 (contact list) consumed before kind:0 (metadata).
- kind:0 author metadata before activity events.
- All consumed events tagged with `LOCAL_RELAY_URL` + `wasVerified=true`.
- Empty DB hydrates without throwing; cache receives no events.
- Replaceable (kind:0/3): only most recent kept.

**1.3 — `LocalRelayStore` `homeDir` ctor seam**

File: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/relay/LocalRelayStore.kt`

```kotlin
class LocalRelayStore(
    private val scope: CoroutineScope,
    private val homeDir: File = File(System.getProperty("user.home")),
) : AutoCloseable {
    private fun dbDir(pubKeyHex: String): File =
        File(homeDir, ".amethyst/accounts/${pubKeyHex.take(8)}")
    ...
}
```

`LocalRelayMaintenance` refactor **deferred** — tests just don't call `maintenance.start()`. If/when it becomes a blocker, refactor then.

**1.4 — `AppStateMachineTest` (Compose UI smoke)**

New file: `desktopApp/src/jvmTest/kotlin/com/vitorpamplona/amethyst/desktop/ui/AppStateMachineTest.kt`

Uses `createComposeRule()` (repo convention). Wraps `MaterialTheme {}` per `DesktopLaunchSmokeTest.kt:69` pattern. Three tests:

- `loggedOutShowsLoginScreen()` — no `accounts.json.enc`.
- `viewOnlyAccountReachesLoggedIn()` — pre-write ViewOnly account.
- `forceLogoutReasonShowsDialog()` — pre-populate `forceLogoutReason`.

Temporary minimal `WebsocketBuilder` fake (replaced by `InProcessWebsocketBuilder` in 2.4).

Acceptance: 3 tests pass, ≤ 5s each.

#### Phase 2 — Deterministic relay seam

**2.1 — `InProcessWebsocketBuilder` in `:quartz` testFixtures**

Add `java-test-fixtures` plugin + testFixtures source set to `:quartz` if not already. (Verify: research found `quartz/build.gradle.kts:352-358` already consumes `testFixtures(project(":geode"))`, so the plugin DSL is in use somewhere in the chain — may need to be enabled on `:quartz` for it to produce fixtures.)

New file: `quartz/src/testFixtures/kotlin/com/vitorpamplona/quartz/test/relay/InProcessWebsocketBuilder.kt`

```kotlin
class InProcessWebsocketBuilder(
    private val server: NostrServer,
) : WebsocketBuilder {
    override fun build(url: NormalizedRelayUrl, out: WebSocketListener): WebSocket =
        InProcessWebSocket(url, out, server)
}
```

Unit test verifies `RelayPool(builder).request(...)` round-trips REQ → EVENT + EOSE within 100ms.

**2.2 — `FixtureNostrServer`**

New file: `quartz/src/testFixtures/kotlin/com/vitorpamplona/quartz/test/relay/FixtureNostrServer.kt`

Spec (edge cases from spec-flow review baked in):

- Load fixture JSONL at construction; fail fast with `FixtureParseException(line, lineNumber, cause)` on malformed line.
- On REQ: match `Filter` per fixture event via `Filter.match(event)`. Send matching events in fixture order.
- **Always send EOSE** after replay, even if no events match (prevents benchmark hang).
- Per-connection `Mutex` to serialize REQ handling — safe under concurrent home + DM subs.
- Configurable response delay (default 5ms) to model relay RTT.

Tests: 50-event fixture + Filter for kinds=[1] authors=[pubKey] → 50 events + EOSE within 100ms; malformed line → `FixtureParseException`; empty match → bare EOSE.

**2.3 — Real-world snapshot fixture (manual capture)**

Per simplicity reviewer: no capture script. One-shot manual capture using `amy`. Document the exact commands in `quartz/src/testFixtures/resources/fixtures/launch/README.md`:

```bash
amy fetch --kinds 1 --author <fiatjaf-npub> --limit 50 --json > fiatjaf-50.jsonl
amy fetch --kinds 0 --author <fiatjaf-npub> --json >> fiatjaf-50.jsonl
# For each p-tag referenced pubkey:
amy fetch --kinds 0 --author <ref-npub> --json >> fiatjaf-50.jsonl
amy fetch --kinds 3 --author <fiatjaf-npub> --json >> fiatjaf-50.jsonl
amy fetch --kinds 10002 --author <fiatjaf-npub> --json >> fiatjaf-50.jsonl
```

Commit `fiatjaf-50.jsonl` as an immutable artifact. Re-capture only via explicit human action; PR that recaptures must include new baseline numbers.

Acceptance: fixture present, ≥ 50 events, all valid signatures, FixtureNostrServer loads it without error.

**2.4 — Replace temporary fake in 1.4 tests**

Update `AppStateMachineTest.viewOnlyAccountReachesLoggedIn()` to use `InProcessWebsocketBuilder(FixtureNostrServer.load("fiatjaf-50.jsonl"))`. Validates full subscription wiring end-to-end.

Acceptance: existing Phase 1.4 tests still pass with real fixture data, no time blow-up (≤ 8s).

#### Phase 3 — Benchmark harness

**3.1 — `LaunchMarkers` + instrumentation hooks**

- `LaunchMarkers` (simple, single-threaded, per above).
- `NoteCardTags` const object in `commons/commonMain`.
- `LocalLaunchInstrumentation` CompositionLocal in `commons/commonMain` (default = `Noop`).
- `Modifier.onPlacedHook()` extension reading the CompositionLocal.
- NoteCard adds `.testTag(NoteCardTags.ROOT).onPlacedHook()` to its root surface (one line each, prod-safe).

Acceptance: launching `App()` against fixture relay produces 4 named markers within 10s.

**3.2 — Two benchmark harnesses (cold + warm)**

Per perf reviewer: cold and warm need different statistical regimes.

**Cold harness** — `desktopApp/benchmarks/cold-launch.sh`:

```bash
#!/usr/bin/env bash
ITERATIONS=${1:-10}
mkdir -p desktopApp/build/benchmarks
out=desktopApp/build/benchmarks/cold-$(git rev-parse --short HEAD).tmp
echo "# cold-launch  $(date -u +%FT%TZ)  $(java --version | head -1)" > "$out"
echo "# host:        $(uname -a)" >> "$out"
echo "# jvm-flags:   -Xms512m -Xmx512m -XX:+UseG1GC" >> "$out"
for i in $(seq 1 "$ITERATIONS"); do
    ./gradlew --no-daemon :desktopApp:jvmTest \
        --tests "*ColdLaunchBenchmark.runOnce" \
        -Dorg.gradle.jvmargs="-Xms512m -Xmx512m -XX:+UseG1GC" \
        -Pbench.output="$out"
done
mv "$out" "${out%.tmp}.txt"
desktopApp/benchmarks/report.py "${out%.tmp}.txt"
```

Each iteration forks a fresh JVM (no daemon). `ColdLaunchBenchmark.runOnce` appends one row per metric (median is just the value; no in-JVM warmup). Output is moved atomically once complete (spec-flow gap #12 — atomic write).

**Warm harness** — `WarmLaunchBenchmark` JUnit test, single JVM:

- N=20 iterations within one test method.
- Discard first 5 iterations (JIT warmup).
- Report median + IQR + min on remaining 15.
- Atomic write to `desktopApp/build/benchmarks/warm-${git_sha}.txt`.

Both harnesses include a **control benchmark**: `setContent { Box {} }` with onPlaced + LaunchMarkers, to measure harness floor. Reported alongside; reviewers can read SNR.

`desktopApp/benchmarks/report.py` parses output, computes Mann-Whitney U vs. baseline if present, prints summary table.

Acceptance:
- Cold harness completes in ≤ 4 min (10 × ~25s/fork) on a developer laptop.
- Warm harness completes in ≤ 2 min.
- Output files include git SHA, JVM version, OS, host info in header.
- Control benchmark < 50ms on warm; documented as the harness floor.

**3.3 (DEFERRED) — Memory metric.** Not part of this plan. JVM heap post-`System.gc()` is unreliable; `TestComposeWindow` heap not representative of real Swing/Skia; out of scope.

**3.4 (DEFERRED) — Warm-boot variant (pre-seeded events.db).** Not part of this plan. None of the in-scope fixes target warm-path code; revisit when a warm-path fix appears.

#### Phase 4 — Baseline capture

Run `desktopApp/benchmarks/cold-launch.sh 10` and `./gradlew :desktopApp:jvmTest --tests "WarmLaunchBenchmark"` on a clean `main`. Commit both output files as:

- `desktopApp/benchmarks/baseline-main-cold.txt`
- `desktopApp/benchmarks/baseline-main-warm.txt`

Append a "Baseline" section to this plan with numbers. No code change.

Acceptance: baselines committed, plan updated, deltas on re-run ≤ 15% (median-to-median).

#### Phase 5 — Targeted fixes

Each lands in its own PR with before/after numbers in the description.

**5.1 — Icon decode**

Files: `desktopApp/src/jvmMain/.../Main.kt:218, 302, 988`.

Top-level shared lazy:

```kotlin
// desktopApp/src/jvmMain/.../IconResources.kt (new)
val DesktopAppIcon: BufferedImage by lazy {
    requireNotNull(IconResources::class.java.getResourceAsStream("/icon.png")) {
        "icon.png not found"
    }.use { ImageIO.read(it) }
}
```

Replace all three sites. Default `lazy` mode (`SYNCHRONIZED`) is fine — three call sites span main + AWT threads.

**Microbench (separate from end-to-end):** add `IconResourcesBenchmark` running `ImageIO.read` N=1000 times to measure absolute decode cost. Report savings independently in case `InProcessWebSocket` floor (perf reviewer #6) hides the delta on `t_n_events`.

Unit test: `IconResourcesTest` asserts `ImageIO.read` invoked once per process (counter wrapper around `getResourceAsStream`).

Re-run benchmark: expected delta = (decode_cost × 2). Microbench delta = decode_cost × 999.

**5.2 — Feed bootstrap relay gate**

Files: `desktopApp/src/jvmMain/.../Main.kt:1283-1326, 1330` (home + DM subs).

**Investigation step (in the PR):** check whether `RelayPool` already queues REQs pre-connect (`quartz/.../RelayPool.kt`). Two outcomes:

- **(a) Pool queues:** delete the `connectedRelays.first { isNotEmpty() }` gate entirely. Subscriptions fire eagerly; pool flushes on connect. **Preferred.**
- **(b) Pool does not queue:** change predicate to `connectedRelays.first { any { it.state in setOf(CONNECTING, CONNECTED) } }`. Lower-risk fallback.

Reject (c) `combine + debounce(50ms)` — adds artificial 50ms to the critical path we're shortening (arch reviewer).

**Single shared helper** between home + DM gate (arch reviewer flag) — both must point at the same logic to avoid drift.

New tests (spec-flow + perf): all use Phase 2 fixture relay infrastructure.

- `slowRelayDoesNotStallFeed()` — `FixtureNostrServer(responseDelay = 1.seconds)`; bootstrap REQ flushes within 1.1s, not 30s.
- `noRelaysAvailableShowsErrorState()` — empty relay list; assert UI reports error within ≤ 10s.
- `relayListArrivesLateStartsSubscriptionThen()` — relay list emitted after `loadSavedAccount` returns; sub fires post-emit, not pre-emit.
- `relaysAddedMidLoadDoNotDoubleSubscribe()` — flip relay list during boot; verify single REQ.

Re-run benchmark: expected delta = large on `t_first_event` and `t_n_events` (gate currently blocks critical path).

**5.3 (CUT)** — sequential `remember` chain refactor. Out of scope this plan. If Phase 4 baseline shows `MainContent` composition cost > 50ms, spawn `desktopApp/plans/<date>-feat-mainContent-state-holder-refactor-plan.md` separately. Reason: scope creep risk + composite-holder choice depends on consumer audit.

---

## Alternative Approaches Considered

**B — Vertical slice per fix.** Per-bottleneck test+bench+fix. Rejected: contradicts "testing before refactor" priority; sequential `remember` lacks broader safety net. (see brainstorm § "Approach B")

**C — Instrument-first, fix-later.** Markers behind a flag; tests + refactors split into follow-ups. Rejected: same reason. (see brainstorm § "Approach C")

**Macrobenchmark / Android-first.** `androidx.benchmark.macro.StartupTimingMetric`. Deferred to a separate plan. (see brainstorm § "Q1 Platform Priority")

**Real WebSocket loopback to a test relay (Docker).** Rejected: network jitter + container startup; in-process is deterministic.

**JMH for benchmarks.** Rejected: doesn't compose with `createComposeRule`. Used independently for the icon microbench in Phase 5.1.

**Fakes in `:commons` testFixtures (original plan).** Rejected per arch review: `:commons` is KMP, unproven with `java-test-fixtures` here; `:quartz` already proves the pattern works.

**`testFixtures` for fakes vs hand-rolled constants.** Hand-rolled would re-invent `Filter.match`; `FixtureNostrServer` reuses existing logic. Keep testFixtures route.

**CompositionLocal `Instrumentation` interface.** Rejected per arch review: would add production API surface for a single test concern.

---

## System-Wide Impact

### Interaction Graph

Phase 1-4: production interaction graph **unchanged**. Tests observe production flows.

Phase 5 changes:

- **5.1 icon decode**: three `ImageIO.read` calls collapse to one `lazy`. `Coil`, Compose `Image`, `DesktopImageLoaderSetup` untouched. Thread safety: `lazy` SYNCHRONIZED default.
- **5.2 feed bootstrap gate**: `RelayConnectionManager` → `NostrClient` → `RelayPool` chain unchanged. The gating predicate in `Main.kt:1283-1326` shifts (or is removed). DM sub at `Main.kt:1330` uses same helper.

### Error & Failure Propagation

- Test errors: `LaunchMarkers.snapshot()` exposes recorded markers so far on any test failure for diagnostic clarity.
- Fixture errors: `FixtureNostrServer` throws on unknown filter and on malformed line at load — no silent empty EOSE.
- Production 5.2: REQ queued pre-connect (case a) relies on `RelayPool` retry semantics; verify before merge.

### State Lifecycle Risks

- Test isolation: each iteration uses fresh temp `homeDir` (cleaned via `@After` + `Files.walk(...).sorted(reverseOrder()).forEach(Files::delete)`).
- `Preferences.userRoot()` write in `LocalRelayMaintenance`: tests don't call `start()`. Production code path unchanged.
- Marker registry: single-threaded; benchmark suite serialized via Gradle `maxParallelForks = 1` filter on `*LaunchBenchmark*`.
- Fixture file stale: re-capture is manual; PR includes new numbers.
- Phase 5.2 risk: subscription firing before relay list emit — explicit `relayListArrivesLateStartsSubscriptionThen` test pins behavior.

### API Surface Parity

- `WebsocketBuilder` already stable across `RelayConnectionManager`, `NostrClient`, `RelayPool`. Test substitution touches no production code.
- `LocalRelayStore` ctor change is additive (default param). Existing callers compile unchanged.
- `NoteCardTags`/`testTag` additive; KDoc states "stable identifier for UI tests; do not key behavior off this."

### Integration Test Scenarios

Five scenarios unit tests with mocks won't catch:

1. **Cold boot ViewOnly → 10 events visible against fixture** — primary benchmark; end-to-end through AccountManager + LocalRelayStore + RelayConnectionManager + NostrClient + LocalCache + FeedScreen.
2. **Slow relay** (Phase 5.2 test) — fixture delays REQ response by 1s; assert subscription completion within 1.1s post-fix.
3. **Relay list arrives late** — exposes ordering bug if Phase 5.2 case (a) fires REQ before list populated.
4. **No relays available** — empty list; UI reports error rather than hang.
5. **Logged-out → LoginScreen** — no `accounts.json.enc`.

---

## Acceptance Criteria

### Functional Requirements

- [x] **Phase 1.1**: 2 tests (ViewOnly happy + decode failure) pass using repo's existing pattern (`backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))` + `advanceUntilIdle()`). _Landed 2026-06-17 — `AccountManagerLoadStateTransitionsTest` (commit `ff55898ab`)._
- [x] **Phase 1.2**: 5 `LocalRelayStoreHydrationTest` cases pass. _Landed 2026-06-17 (commit `ff55898ab`)._
- [x] **Phase 1.3**: `LocalRelayStore` accepts optional `homeDir`; existing callers unchanged; `LocalRelayMaintenance` untouched. _Landed 2026-06-17 (commit `ff55898ab`)._
- [ ] **Phase 1.4**: 3 `AppStateMachineTest` Compose UI tests pass via `createComposeRule()`, ≤ 5s each, with `MaterialTheme {}` wrap.
- [ ] **Phase 2.1**: `InProcessWebsocketBuilder` in `quartz/src/testFixtures/`; round-trip test passes.
- [ ] **Phase 2.2**: `FixtureNostrServer` correctly matches `Filter`, always emits EOSE (incl. empty match), fails fast on malformed JSONL via `FixtureParseException`, per-connection Mutex.
- [ ] **Phase 2.3**: `fiatjaf-50.jsonl` committed under `quartz/src/testFixtures/resources/fixtures/launch/`, ≥ 50 events, all valid signatures; README documents recapture commands.
- [ ] **Phase 2.4**: 1.4 tests still pass using fixture-driven relay.
- [ ] **Phase 3.1**: `LaunchMarkers` produces 4 named markers within 10s of a fixture cold boot. `NoteCardTags.ROOT` in `commons/commonMain`. NoteCard `Modifier.testTag().onPlacedHook()` added; production cost ≤ 1 SemanticsModifier allocation per card.
- [ ] **Phase 3.2**: Cold harness (shell-script, fork per sample, N=10) and warm harness (single JVM, N=20, discard 5 warmup) both run reproducibly. Pinned JVM flags. Output headers include git SHA, JVM/OS/arch. Control benchmark (`setContent { Box {} }`) reports harness floor.
- [ ] **Phase 4**: `desktopApp/benchmarks/baseline-main-cold.txt` and `-warm.txt` committed; plan updated with numbers. Re-run delta ≤ 15% median-to-median.
- [x] **Phase 5.1**: icon decoded exactly once per process (unit test); microbench delta reported; end-to-end delta reported. _Code + unit test landed 2026-06-17 (commit `b338d7db4`). Delta numbers pending Phase 3 benchmark harness._
- [ ] **Phase 5.2**: bootstrap subscription fires before any relay reaches CONNECTED state OR Pool queues pre-connect (whichever investigation shows). 4 new tests: `slowRelayDoesNotStallFeed`, `noRelaysAvailableShowsErrorState`, `relayListArrivesLateStartsSubscriptionThen`, `relaysAddedMidLoadDoNotDoubleSubscribe`. Home + DM share single helper.

### Non-Functional Requirements

- [ ] Warm benchmark variance ≤ 15% on `t_n_events` (median-to-median across 5 runs of the suite).
- [ ] `InProcessWebsocketBuilder` adds ≤ 50ms RTT vs direct in-process call.
- [ ] Cold harness total wall clock ≤ 4 min, warm ≤ 2 min, on a developer M1/M2/M3 laptop.

### Quality Gates

- [ ] All new tests pass `./gradlew :desktopApp:jvmTest` and `./gradlew :quartz:test`.
- [ ] Existing tests unaffected.
- [ ] `./gradlew spotlessApply` clean. `.spotless/copyright.kt` header on every new `.kt`.
- [ ] Pre-commit hook passes without `--no-verify`.
- [ ] Each Phase 5 PR includes before/after numbers table in description.
- [ ] No new `runBlocking { ... }` calls in launch path.
- [ ] No production code references `LaunchMarkers`.

---

## Success Metrics

Per brainstorm (Q4 + Q6) with deepen-plan adjustments:

| Metric | What | Reporting |
|--------|------|-----------|
| `t_first_composition_apply` | First `setContent` reaches idle (NOT real Skia frame) | ms, cold + warm |
| `t_account_logged_in` | `AccountState.LoggedIn` emitted | ms, cold + warm |
| `t_first_event` | First `NoteCard.onPlaced` fires | ms, cold + warm |
| `t_n_events` (N=10) | 10th `NoteCard.onPlaced` fires | ms, cold + warm — **headline** |

(Memory + warm-boot variant DEFERRED.)

Cold = shell-script, fork per sample, N=10, no warmup. Warm = single JVM, N=20, discard first 5. Report **median + IQR + min**. For fix deltas, **Mann-Whitney U** with Cliff's delta > 0.33 threshold (perf reviewer).

**Phase 5 success:** each fix produces positive delta on `t_n_events` (or for 5.1, on the microbench if end-to-end is below the `InProcessWebSocket` floor). Sum target ≥ 20% reduction in cold `t_n_events` — aspirational, actual depends on baseline.

---

## Dependencies & Risks

### Risks (prioritized)

**Risk #1 (mitigated) — `:quartz` + `java-test-fixtures` setup.** `:quartz` already consumes geode testFixtures (`quartz/build.gradle.kts:352-358`). Producing fixtures from `:quartz` requires the `java-test-fixtures` plugin on `:quartz` itself, which is a separate enablement. **Mitigation**: time-box to 1 hour; fall back to `:desktopApp:jvmTest` directly if Gradle friction blocks.

**Risk #2 (acknowledged) — `runComposeUiTest` / `createComposeRule` doesn't capture real Swing/Skia render time.** Headline metric `t_first_composition_apply` measures composition only, not paint. **Mitigation**: explicit "out-of-scope: GPU/paint timing" disclosure in benchmark output header. Real-window benchmark named as follow-up: `desktopApp/plans/<future>-real-window-launch-benchmark.md`.

**Risk #3 — Fixture staleness.** Commit as immutable; recapture explicit human action only; PR includes new numbers.

**Risk #4 — Phase 5.2 eager subscription causes double-subscribe on relay list reorder.** Test: `relaysAddedMidLoadDoNotDoubleSubscribe`.

**Risk #5 — `InProcessWebSocket` floor swallows fix delta.** Microbench for icon decode in 5.1; Mann-Whitney U test for significance.

**Risk #6 — Cold harness fork cost (~25s/sample × N=10 = ~4 min).** Acceptable for local-manual runs; document in README.

**Risk #7 — JVM/OS/arch variation across developers.** Output header records env; cross-machine numbers informational only.

### Dependencies

- `quartz`: `InProcessWebSocket`, `NostrServer`, `Filter`, `WebsocketBuilder` — all exist.
- `commons`: `EventStore`, `DesktopLocalCache` — exist.
- `desktopApp`: `AccountManager`, `LocalRelayStore`, `RelayConnectionManager`, `App()` — exist with needed seams post 1.3.
- Compose UI test: `compose.desktop.uiTestJUnit4` on classpath (`desktopApp/build.gradle.kts:87`).
- Gradle `java-test-fixtures` plugin (proven in `:geode`; needs enablement on `:quartz`).

---

## Resource Requirements

Solo engineer, rough sizing:

| Phase | Effort |
|-------|--------|
| 1.1 | ~half day |
| 1.2 | ~half day |
| 1.3 | ~hour |
| 1.4 | ~day |
| 2.1 | ~hour |
| 2.2 | ~half day |
| 2.3 | ~hour (manual fixture capture + README) |
| 2.4 | ~hour |
| 3.1 | ~half day |
| 3.2 | ~day (two harnesses + report.py) |
| 4 | ~hour |
| 5.1 | ~hour + microbench |
| 5.2 | ~day (investigation + refactor + 4 tests) |

---

## Future Considerations

- **Real-render benchmark** (`desktopApp/plans/<future>-real-window-launch-benchmark.md`) — `application { Window { App() } }` with file-marker IPC. Catches GPU regressions.
- **Phase 5.3 follow-up** (`desktopApp/plans/<future>-feat-maincontent-state-holder-refactor-plan.md`) — only if Phase 4 baseline shows `MainContent` cost > 50ms.
- **Android port** (brainstorm Q14) — separate brainstorm, `androidx.benchmark.macro.StartupTimingMetric`.
- **Memory metric** — add after baseline shows it's worth measuring; pin GC mode, parse JFR.
- **Larger fixture for stress (`fiatjaf-5000.jsonl`)** — catches O(n²) regressions; defer until needed.
- **CI integration** — track baseline on `main` for trend visibility (no PR gating).
- **Tor cold boot** — measure separately; out of scope (Tor splash gate is shutdown-shape, not boot-shape).

---

## Documentation Plan

- This plan — primary reference.
- `quartz/src/testFixtures/resources/fixtures/launch/README.md` — fixture recapture commands.
- `desktopApp/benchmarks/README.md` — how to run benchmarks, interpret numbers, known caveats.
- KDoc on `LaunchMarkers`, `InProcessWebsocketBuilder`, `FixtureNostrServer`, `NoteCardTags`.
- Plan update after Phase 4 with baselines.
- Plan update after Phase 5.1 and 5.2 with deltas.

---

## Sources & References

### Origin

- **Brainstorm:** [`docs/brainstorms/2026-06-17-feat-app-launch-optimization-brainstorm.md`](../../docs/brainstorms/2026-06-17-feat-app-launch-optimization-brainstorm.md). Key decisions carried forward: Approach A (foundation-first); Desktop-first / Android deferred; layered pyramid; in-process fake relay; fresh-boot fixture; N=10 events visible as headline; local-only manual runs; cold + warm reported separately; real-world fixture; 3 in-scope fixes (now 2 in-scope, 1 deferred).

### Internal References

- `desktopApp/src/jvmMain/.../Main.kt:218,302,988` — 3× ImageIO.read of /icon.png.
- `desktopApp/src/jvmMain/.../Main.kt:1283-1330` — home + DM subscriptions gated on first connected relay.
- `desktopApp/src/jvmMain/.../Main.kt:834` — production `WebsocketBuilder` injection site.
- `desktopApp/src/jvmMain/.../account/AccountManager.kt:71-86` — `AccountState`.
- `desktopApp/src/jvmMain/.../account/AccountManager.kt:240-266` — `loadSavedAccount`.
- `desktopApp/src/jvmMain/.../account/AccountManager.kt:676-695` — `loadReadOnlyAccount`.
- `desktopApp/src/jvmMain/.../relay/LocalRelayStore.kt:38-103,115-161` — store ctor + hydrate.
- `desktopApp/src/jvmMain/.../network/RelayConnectionManager.kt:56-58` — `open class` taking `WebsocketBuilder`.
- `desktopApp/src/jvmMain/.../network/DesktopRelayConnectionManager.kt:30-34`.
- `quartz/src/commonMain/.../sockets/WebsocketBuilder.kt:25-30`.
- `quartz/src/commonMain/.../relay/server/inprocess/InProcessWebSocket.kt:55`.
- `quartz/src/commonMain/.../relay/client/NostrClient.kt:80-82`.
- `quartz/src/commonMain/.../relay/filters/Filter.kt:51-61`.
- `desktopApp/src/jvmTest/.../ui/DesktopLaunchSmokeTest.kt:24,57,58,69` — existing UI test pattern.
- `desktopApp/src/jvmTest/.../account/AccountManagerStateTransitionTest.kt:73-95` — existing state-transition test pattern.
- `desktopApp/src/jvmMain/.../ui/note/NoteCard.kt:97` — testTag/onPlaced injection site.
- `quartz/build.gradle.kts:352-358` — `:quartz` consuming `testFixtures(project(":geode"))`.
- `geode/build.gradle.kts:7,35-37,81-83` — `java-test-fixtures` precedent.
- `.spotless/copyright.kt` — required header for new files.

### Related Work

- `docs/brainstorms/2026-04-29-feed-metadata-loading-optimization-brainstorm.md` — adjacent viewport-aware metadata.
- `desktopApp/plans/2026-05-09-embedded-local-relay-plan.md` — `LocalRelayStore` infrastructure this plan reuses.

### External References

- Compose UI test: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
- kotlinx-coroutines test: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/
- Gradle `java-test-fixtures`: https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures
