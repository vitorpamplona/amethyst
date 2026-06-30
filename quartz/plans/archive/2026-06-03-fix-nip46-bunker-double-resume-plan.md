---
title: "fix(quartz): NIP-46 bunker double-resume + retry id-reuse races"
type: fix
status: completed
date: 2026-06-03
origin: docs/brainstorms/2026-06-03-nip46-bunker-double-resume-brainstorm.md
---

# fix(quartz): NIP-46 bunker double-resume + retry id-reuse races

> **Status:** shipped — `RemoteSignerManager` uses the Channel-per-request `pending` map and a retry test (`RemoteSignerManagerRetryTest`); the front-matter already marks it `completed`.
> _Audited 2026-06-30._

## Revision Note (2026-06-03, post-review)

Three reviews (simplicity / architecture / pattern-recognition) converged on
pivoting the approach. Original plan used atomic-remove + `tryResume` on a
cached `Continuation` map. **Revised approach: Channel-per-request (per
retry attempt) + fresh `request.id` per attempt**, matching the de facto
Quartz convention used in `NostrClientPublishExt.kt` and 4 sibling files
under `quartz/.../accessories/`.

### Why the pivot

| Driver | Detail |
|---|---|
| **Architecture review finding** | Quartz already uses Channel-per-request as house style (5+ files). The cached-`Continuation` pattern in `RemoteSignerManager` / `IntentRequestManager` is the outlier — only 2 files. |
| **4th failure mode discovered** | `RemoteSignerManager.kt:74-101` retry loop reuses the same `request.id` across attempts. Late response from attempt 1 can resume attempt 2's continuation with **stale data** (correctness bug, not just crash). Investigation verdict: HIGH probability on flaky relays. Fresh-id-per-retry naturally fixes this. |
| **Simplicity review** | Pivoting eliminates the `@InternalCoroutinesApi` opt-in surface entirely, kills the Plan C / SingleShotContinuation alternative discussions, removes the `TestLogCapture` test-infra invention, and removes the Strategy B stress-loop test. |
| **Pattern review** | "Aligns the existing outlier with the convention" — fewer correlation styles in the codebase, not more. |

### Scope unchanged from original plan

- Both managers fixed in the same PR (NIP-46 + NIP-55 sibling).
- Function-level concurrency primitives — no public API change to
  `launchWaitAndParse`. 17 call sites of `launchWaitAndParse` across
  `NostrSignerRemote.kt`, `ForegroundRequestHandler.kt`, and the retry test
  are untouched.
- `tryAndWait` (`ParallelUtils.kt:71-79`) is **retained** — still used by
  `collectSuccessfulOperationsReturning` (`ParallelUtils.kt:98`). Only its
  use inside the two managers is replaced.

---

## Overview

Fix two related correctness bugs in the NIP-46 bunker signer
(`RemoteSignerManager`) and its NIP-55 Android sibling
(`IntentRequestManager`):

1. **Double-resume crash** — `Continuation.resume(...)` called twice for
   the same id, throwing `IllegalStateException: Already resumed`.
   Triggered by (a) multi-relay delivery, (b) bunker echo/retry,
   (c) late response after `tryAndWait` timeout fires.
2. **Retry id-reuse → wrong-data bug** (NIP-46 only) — retry attempts
   reuse the same `request.id`, so a late response from attempt N can
   resume attempt N+1's continuation with attempt N's data.

The fix replaces the cached `Continuation<T>` map with a
**Channel-per-request** correlation pattern (mirroring the convention
in `quartz/.../accessories/NostrClientPublishExt.kt`), and regenerates
`request.id` per retry attempt.

## Problem Statement

### Bug 1 — Double-resume crash

```kotlin
// RemoteSignerManager.kt:46-50
suspend fun newResponse(responseEvent: NostrConnectEvent) {
    val decryptedJson = signer.decrypt(responseEvent.content, remoteKey)
    val bunkerResponse = OptimizedJsonMapper.fromJsonTo<BunkerResponse>(decryptedJson)
    awaitingRequests.get(bunkerResponse.id)?.resume(bunkerResponse)   // ← unsafe
}
```

Non-atomic `get(id)?.resume(value)` — three races trigger double-resume:

| Race | Sequence | Result |
|---|---|---|
| **Late response after timeout** | `tryAndWait`'s `withTimeoutOrNull` completes continuation with `null`; bunker's actual response arrives ms later; `newResponse` calls `resume` on already-completed continuation. | `IllegalStateException` at `RemoteSignerManager.kt:49` |
| **Multi-relay delivery (NIP-46 only)** | `NostrSignerRemote.kt:82` fires `scope.launch { manager.newResponse(event) }` per delivered event, no dedupe. Two relays delivering same response → two concurrent `newResponse` calls → both `get` same continuation → both `resume`. | First wins, second throws. |
| **Bunker echo / retry (NIP-46 only)** | Some bunker servers re-publish on relay reconnect. Same as multi-relay but with longer time gap. | Second resume throws. |

Stack trace:

```
Exception in thread "DefaultDispatcher-worker-42" java.lang.IllegalStateException:
  Already resumed, but proposed with update BunkerResponse@…
    at kotlinx.coroutines.CancellableContinuationImpl.alreadyResumedError(CancellableContinuationImpl.kt:556)
    at com.vitorpamplona.quartz.nip46RemoteSigner.signer.RemoteSignerManager.newResponse(RemoteSignerManager.kt:49)
    at com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote$subscription$2$1.invokeSuspend(NostrSignerRemote.kt:83)
```

### Bug 2 — Retry id-reuse → wrong data (NIP-46 only)

```kotlin
// RemoteSignerManager.kt:66-101  (paraphrased)
val request = buildRequest(...)             // ← request.id assigned ONCE
val event = signer.encrypt(request, ...)    // event id derived once
var attempt = 0
while (true) {
    val result = tryAndWait(timeout) { continuation ->
        continuation.invokeOnCancellation { awaitingRequests.remove(request.id) }
        awaitingRequests.put(request.id, continuation)   // ← SAME id every attempt
        client.publish(event, relayList = relayList)
    }
    when {
        result != null -> return parser(result)
        attempt >= maxRetries -> return SignerResult.RequestAddressed.TimedOut()
        else -> { attempt++; delay(2_000L) }
    }
}
```

Race sequence (default `timeout = 65_000L`):

```
T+0:    Attempt 1: put(continuation_1, "req123")
T+65s:  Attempt 1: timeout → invokeOnCancellation removes "req123"
T+67s:  Attempt 2: put(continuation_2, "req123")  ← same id
T+70s:  Bunker's late response for ATTEMPT 1 arrives
        newResponse() resumes continuation_2 with attempt_1's payload
        ⚠ wrong data delivered to caller
```

Likelihood: HIGH when relay RTT approaches timeout. Atomic-remove + `tryResume`
**does not fix this** — continuation_2 is genuinely live; `tryResume`
succeeds with stale data. Only fresh-id-per-attempt closes this race.

`IntentRequestManager.kt:119` already uses a fresh `RandomInstance.randomChars(32)` per
call and has no retry loop — Bug 2 does not apply there.

## Proposed Solution

### Mechanism: Channel-per-request

```kotlin
// after — RemoteSignerManager  (paraphrased shape)

private val pending = ConcurrentHashMap<String, Channel<BunkerResponse>>()

suspend fun newResponse(responseEvent: NostrConnectEvent) {
    val decryptedJson = signer.decrypt(responseEvent.content, remoteKey)
    val bunkerResponse = OptimizedJsonMapper.fromJsonTo<BunkerResponse>(decryptedJson)

    // Atomic remove. Multi-relay / bunker-echo duplicates: losers see null.
    val channel = pending.remove(bunkerResponse.id)
    if (channel == null) {
        Log.d("NIP46") { "no channel for bunker response id=${bunkerResponse.id} (duplicate or unknown)" }
        return
    }
    // capacity = 1: first delivery wins. trySend on a closed/full channel
    // is a no-op — late response after timeout cannot crash.
    channel.trySend(bunkerResponse)
}

private suspend fun <T : SignerResult.RequestAddressed> launchWaitAndParse(
    request: BunkerRequest,
    parser: (BunkerResponse) -> T,
): T {
    var attempt = 0
    while (true) {
        // Fresh id per attempt: each attempt is a brand-new request to the bunker.
        val attemptRequest = request.copy(id = RandomInstance.randomChars(32))
        val event = signer.encrypt(attemptRequest, ...)
        val channel = Channel<BunkerResponse>(capacity = 1)
        pending[attemptRequest.id] = channel
        try {
            client.publish(event, relayList = relayList)
            val response = withTimeoutOrNull(timeout) { channel.receive() }
            when {
                response != null -> return parser(response)
                attempt >= maxRetries -> return SignerResult.RequestAddressed.TimedOut()
                else -> { attempt++; delay(2_000L) }
            }
        } finally {
            pending.remove(attemptRequest.id)  // cleanup on both happy + timeout paths
            channel.close()
        }
    }
}
```

### Mechanism applied to `IntentRequestManager`

Same shape, no retry loop, single attempt — `IntentResult` instead of
`BunkerResponse`, `LruCache` becomes `ConcurrentHashMap` (which is also
the structure used by the existing Channel-per-request files), and the
log tag becomes `"NIP55"`.

### Why this fix

| Property | Cached `Continuation` (today) | Atomic `remove` + `tryResume` (original plan) | Channel-per-request (this plan) |
|---|---|---|---|
| Double-resume crash | ❌ | ✅ | ✅ |
| Late response after timeout | ❌ | ✅ (`tryResume` returns null) | ✅ (`trySend` on closed channel is a no-op) |
| Multi-relay delivery | ❌ | ✅ (atomic remove) | ✅ (atomic remove) |
| Bunker echo / retry | ❌ | ✅ | ✅ |
| **Retry id-reuse → wrong data** | ❌ | ❌ | ✅ (fresh id per attempt) |
| `@InternalCoroutinesApi` | n/a | required | not required |
| House style match | outlier | outlier (still cached map) | ✅ matches `accessories/` convention |
| Memory leak on success path | leaks | incidentally fixed | fixed (finally block) |

## Technical Considerations

### Channel capacity = 1

Each request has at most one valid response (NIP-46 `auth_url` flow not
implemented in Amethyst — see "NIP-46 spec" below). `Channel(capacity = 1)`:

- First `trySend` succeeds → `receive` resumes with the value.
- Concurrent / late `trySend` after the channel has been drained returns
  a `ChannelResult.Closed` once the `finally` block calls `close()`. No-op,
  no throw.
- `withTimeoutOrNull(timeout) { channel.receive() }` returns `null` on
  timeout; the `finally` block cleans up the map entry and closes the
  channel.

### Fresh `request.id` per retry attempt

NIP-46 has no idempotency contract — each retry is a new request from the
bunker's perspective. Generating a fresh id per attempt is consistent
with the spec and is also what `IntentRequestManager` already does for
single attempts.

The user-visible cost: a slow bunker that finishes processing attempt 1
mid-way through attempt 2 will not have its attempt-1 work "rescued" —
the response is discarded and attempt 2's response is the one we return.
This is the **correct** behaviour; the alternative (rescuing attempt 1
into attempt 2's slot) is the very bug we're fixing.

### `IntentRequestManager.LruCache` → `ConcurrentHashMap`

`androidx.collection.LruCache` was sized at 2000 for bounded growth.
With the `finally`-block cleanup the map shrinks on every completed call,
so an unbounded `ConcurrentHashMap` matches the Quartz `accessories/`
convention without leak risk. (If we ever want a safety cap, `Caffeine`
is in the dependency graph already, but YAGNI.)

### NIP-46 spec: `auth_url` (multi-response per id)

Spec allows a second response per id when the bunker emits an `auth_url`
challenge first. **Amethyst has zero `auth_url` handling code today**;
every parser maps any non-null `error` to `Rejected`. Channel(capacity=1)
mirrors that current contract: first response is terminal. If `auth_url`
support is added later, the right fix is at the parser /
`launchWaitAndParse` layer (e.g., keep the channel open until the parser
returns `SignerResult.AwaitingAuth`, then `receive` again). Not in scope
here.

### `tryAndWait` retained for `collectSuccessfulOperationsReturning`

`tryAndWait` is still used by `ParallelUtils.kt:98`
(`collectSuccessfulOperationsReturning`). We are not deleting it —
only its uses inside the two managers go away.

### Performance implications

None measurable. One `Channel(1)` allocation + one entry in
`ConcurrentHashMap` per request, both freed in the `finally` block.
NIP-46 throughput is < 10 req/s in practice; this is noise.

### Security considerations

None. Thread-safety + correctness only; no protocol surface change,
no new trust assumptions, no new data exposed.

## System-Wide Impact

- **Interaction graph:** Relay → `INostrClient.subscribe` →
  `NostrSignerRemote` callback → `scope.launch` → `manager.newResponse` →
  channel `trySend` → `launchWaitAndParse`'s `receive` unblocks →
  parser returns to Amethyst UI. The fix sits at the correlation layer;
  everything downstream is unchanged. Upstream (`withTimeoutOrNull`, the
  subscription callback) is unchanged.
- **`launchWaitAndParse` public signature unchanged.** All 17 call sites
  (8 in `NostrSignerRemote.kt`, 9 in `ForegroundRequestHandler.kt`,
  4 in tests) are untouched.
- **State lifecycle:** `pending` is the only mutable state. Atomic
  `ConcurrentHashMap.put / remove` semantics + `finally`-block cleanup
  guarantee no entries leak.
- **Error propagation:** Currently the `IllegalStateException` is thrown
  on a `Dispatchers.Default` worker inside `scope.launch { ... }`. After
  fix, no exception path remains — duplicates trigger a debug log line.
- **Integration test scenarios** (manual / amy):
  1. **NIP-46 cold-start with multi-relay delivery:** subscribe on N=5 relays → send request → all 5 deliver same response → only one resume, no crash.
  2. **NIP-46 late-response after timeout:** request with `timeout=100ms` against a bunker that responds at 200ms → returns `TimedOut`, debug log fires.
  3. **NIP-46 retry loop with mid-flight stale response:** force `timeout < network RTT` (`timeout=100ms`, RTT=300ms) → attempt 1 times out, attempt 2 in flight, attempt 1's actual response arrives → silently discarded; attempt 2's eventual response is what we return.
  4. **NIP-55 multi-result Intent:** Android signer returns Intent with multiple `results` entries that defensively repeat the same id → no crash.

## Acceptance Criteria

### Functional

- [x] `RemoteSignerManager.newResponse` never throws — late responses, duplicates from multiple relays, and bunker echoes are all silently dropped after a debug log.
- [x] `IntentRequestManager.newResponse` has the equivalent guarantee.
- [x] Each retry attempt in `RemoteSignerManager.launchWaitAndParse` uses a fresh `request.id`. A late response from attempt N cannot resume attempt N+1.
- [x] `pending` map entries are always removed in a `finally` block — no leaks on success, timeout, or thrown exception.
- [x] Caller-visible behaviour of `launchWaitAndParse` is unchanged in the happy path: same return value, same retry semantics, same `SignerResult.RequestAddressed` shapes.

### Non-functional

- [x] No `@OptIn(InternalCoroutinesApi::class)` introduced.
- [x] Channel capacity = 1; channel scoped to a single retry attempt (created inside loop, closed in `finally`).

### Quality gates

- [x] `./gradlew :quartz:compileKotlinJvm :quartz:compileKotlinAndroid` passes.
- [x] `./gradlew :quartz:jvmTest --tests "*RemoteSignerManager*"` passes, including new race-condition tests.
- [x] `./gradlew spotlessApply` clean before commit.
- [x] Existing `RemoteSignerManagerRetryTest` (5 tests) still passes.
- [x] Three new tests added covering: (a) duplicate response → no crash + one successful resume; (b) late response after timeout → no crash + caller sees `TimedOut`; (c) cross-attempt stale-response → attempt 1's late response does NOT corrupt attempt 2's result.

## Implementation Plan

### File-level changes

```
quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip46RemoteSigner/signer/
└── RemoteSignerManager.kt                                    # MODIFY

quartz/src/androidMain/kotlin/com/vitorpamplona/quartz/nip55AndroidSigner/api/foreground/
└── IntentRequestManager.kt                                   # MODIFY

quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/nip46RemoteSigner/signer/
└── RemoteSignerManagerRetryTest.kt                           # MODIFY (add three tests)
```

### Step 1 — `RemoteSignerManager.kt`

Replace `awaitingRequests` cache + `tryAndWait`-based loop with
Channel-per-attempt + fresh id:

- Cache type: `ConcurrentHashMap<String, Channel<BunkerResponse>>`
- `newResponse`: atomic `remove` + `trySend` on `Channel(1)` (no-op on closed/full).
- `launchWaitAndParse`: inside the retry loop, copy request with fresh
  `request.id` via `RandomInstance.randomChars(32)`, create
  `Channel<BunkerResponse>(capacity = 1)`, register under the new id,
  `client.publish`, then
  `withTimeoutOrNull(timeout) { channel.receive() }`. Cleanup in `finally`.

Imports added:
- `kotlinx.coroutines.channels.Channel`
- `kotlinx.coroutines.withTimeoutOrNull`
- `com.vitorpamplona.quartz.utils.RandomInstance`
- `java.util.concurrent.ConcurrentHashMap`
- `com.vitorpamplona.quartz.utils.Log`

Imports removed:
- `kotlin.coroutines.Continuation`
- `kotlin.coroutines.resume`
- `com.vitorpamplona.quartz.utils.cache.LargeCache`
- `com.vitorpamplona.quartz.utils.tryAndWait` (no longer used here; keep in `ParallelUtils.kt`)

### Step 2 — `IntentRequestManager.kt`

Same shape, no retry loop, single attempt. `LruCache` → `ConcurrentHashMap`,
`Continuation` → `Channel<IntentResult>(capacity = 1)`, atomic `remove` +
`trySend`. Log tag `"NIP55"`. The `forEach` over multi-result Intents
now does `pending.remove(id)?.trySend(result)` per entry.

### Step 3 — Tests (`RemoteSignerManagerRetryTest.kt`)

Three new tests. All use the existing `runTest`-based scaffolding —
no new test infra (no `TestLogCapture`, no `Dispatchers.Default` stress
loops). Each test must be observable via the public `launchWaitAndParse`
return value, not internal log state.

```kotlin
@Test
fun `duplicate response events do not crash and resume once`() = runTest {
    val client = TestClient()
    val manager = RemoteSignerManager(timeout = 5000L, client = client, ...)
    val resultDeferred = async { manager.launchWaitAndParse(...) }
    runCurrent()
    val response = client.captureRequestEvent().toResponse()
    // Three deliveries of the same response — only the first should reach the caller.
    launch { manager.newResponse(response) }
    launch { manager.newResponse(response) }
    launch { manager.newResponse(response) }
    advanceUntilIdle()
    val result = resultDeferred.await()
    assertIs<SignerResult.RequestAddressed.Result<*>>(result)
    // Bug exists on main: second/third launch throws IllegalStateException → fails test.
}

@Test
fun `late response after timeout is silently discarded`() = runTest {
    val client = TestClient(neverResponds = true)
    val manager = RemoteSignerManager(timeout = 100L, maxRetries = 0, client = client, ...)
    val resultDeferred = async { manager.launchWaitAndParse(...) }
    val response = client.captureRequestEvent().toResponse()
    advanceTimeBy(200L)  // timeout fires
    val result = resultDeferred.await()
    assertIs<SignerResult.RequestAddressed.TimedOut>(result)
    // Now the late response arrives — must not crash, must not affect caller.
    manager.newResponse(response)
    advanceUntilIdle()
    // No assertion needed: lack of crash + caller already got TimedOut is the success criterion.
}

@Test
fun `late response from attempt 1 does not corrupt attempt 2 result`() = runTest {
    val client = TestClient()
    val manager = RemoteSignerManager(timeout = 100L, maxRetries = 1, client = client, ...)
    val resultDeferred = async { manager.launchWaitAndParse(buildRequest("PAYLOAD_A")) }
    runCurrent()
    val attempt1Event = client.captureRequestEvent()  // captures id_1
    advanceTimeBy(150L)  // attempt 1 times out + delay(2000) begins
    advanceTimeBy(2000L) // retry kicks in
    val attempt2Event = client.captureRequestEvent()  // captures id_2 — must be different from id_1
    assertNotEquals(attempt1Event.requestId, attempt2Event.requestId)
    // Late response for attempt 1 arrives while attempt 2 is in flight
    manager.newResponse(attempt1Event.toResponse(payload = "STALE_A"))
    // Real response for attempt 2 arrives
    manager.newResponse(attempt2Event.toResponse(payload = "FRESH_B"))
    advanceUntilIdle()
    val result = resultDeferred.await()
    assertIs<SignerResult.RequestAddressed.Result<*>>(result)
    assertEquals("FRESH_B", (result as SignerResult.RequestAddressed.Result<*>).value)
    // On main: result would be "STALE_A" (Bug 2) — test fails. Also Bug 1 would crash.
}
```

### Step 4 — Verify on `main` first

Before applying Step 1, run the three new tests against `main`. Expected
failures:

- Test 1: `IllegalStateException: Already resumed` from second/third
  `launch { newResponse }`.
- Test 2: `IllegalStateException` from the late `newResponse` call.
- Test 3: either `IllegalStateException` (Bug 1) or `assertEquals` failure
  with `actual = "STALE_A"` (Bug 2). Both branches prove the test exercises
  the race.

Apply Step 1, re-run, all three pass.

### Step 5 — Format + final build

```bash
./gradlew spotlessApply
./gradlew :quartz:build
```

## Success Metrics

- Zero `IllegalStateException: Already resumed` log entries from
  `RemoteSignerManager.newResponse` or `IntentRequestManager.newResponse`
  after the fix lands.
- Zero stale-data correctness reports tied to retry id reuse.
- Three new tests in `RemoteSignerManagerRetryTest` pass; same tests fail
  on `main`.
- No regressions in the existing 5 retry tests.

## Dependencies & Risks

| Item | Risk | Mitigation |
|---|---|---|
| Channel migration changes the correlation primitive | If a future feature needs multi-response per id (e.g., NIP-46 `auth_url`), the channel must be re-`receive`'d after the parser yields `AwaitingAuth` | Not blocking today (no `auth_url` code). Documented as the right architectural seam. |
| Fresh id per retry attempt changes wire behaviour | Bunkers that cache responses by id will not see the second request as a duplicate | Acceptable — NIP-46 has no idempotency contract; this matches `IntentRequestManager`'s existing behaviour. |
| `LruCache` (Android) → `ConcurrentHashMap` change | `LruCache` had a 2000-entry cap; `ConcurrentHashMap` is unbounded | `finally`-block cleanup guarantees entries shrink on every completed call. Mirrors the unbounded `ConcurrentHashMap` already used in `quartz/.../accessories/`. |
| Existing 5 retry tests rely on the old continuation-cache API | Tests may not compile against the new `pending` map | Tests should only interact through the public `launchWaitAndParse` + `newResponse` surface (verified in deepen-plan research). If any directly inspect `awaitingRequests`, update to inspect `pending` or pivot to result-based assertion. |

## Alternative Approaches Considered

1. **Atomic `remove` + `tryResume` on cached `CancellableContinuation`** (original plan)
   Fixes Bug 1 cleanly but leaves Bug 2 (retry id-reuse) intact. Pulls in
   `@InternalCoroutinesApi`. Stays with the outlier pattern. Rejected
   after architecture + simplicity reviews.

2. **try/catch `IllegalStateException` around `resume`**
   Anti-pattern; doesn't fix Bug 2. Rejected.

3. **`SingleShotContinuation<T>` wrapper helper**
   Reusable abstraction for a problem already solved by Channel. Doesn't
   fix Bug 2. YAGNI; rejected.

4. **Subscription-level dedupe in `NostrSignerRemote.kt:82`**
   Bounded LRU of seen event ids. Suppresses duplicate work upstream. Not
   needed because Channel `trySend` on capacity-1 is already O(1) and
   guard at the receive side is atomic. Deferred unless field telemetry
   shows high duplicate volume.

5. **Fix only `RemoteSignerManager`, leave `IntentRequestManager`**
   Sibling bug is identical; bundling is cheaper than a follow-up.

6. **Keep `tryAndWait` for the managers**
   Rejected because `tryAndWait`'s `CancellableContinuation`-cache
   pattern is the source of both bugs. Keeping `tryAndWait` for
   `collectSuccessfulOperationsReturning` is fine — different use case
   (no shared id, no concurrent multi-source delivery).

## Sources & References

### Origin

- **Brainstorm:** [`docs/brainstorms/2026-06-03-nip46-bunker-double-resume-brainstorm.md`](../../docs/brainstorms/2026-06-03-nip46-bunker-double-resume-brainstorm.md)
- Decisions changed during deepen-plan + review pass:
  - Fix mechanism: atomic remove + `tryResume` → Channel-per-request
  - Scope: added Bug 2 (retry id-reuse) as in-scope after investigation
    showed HIGH probability of reaching it on flaky relays

### Internal references

- `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip46RemoteSigner/signer/RemoteSignerManager.kt:44,46-50,66-101` — the two bug sites
- `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip46RemoteSigner/signer/NostrSignerRemote.kt:69-86` — subscription handler (line 82 = source of multi-relay concurrent calls)
- `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/ParallelUtils.kt:71-79,98` — `tryAndWait` (retained for `collectSuccessfulOperationsReturning`)
- `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/client/accessories/NostrClientPublishExt.kt` — house-style reference for Channel-per-request
- `quartz/src/androidMain/kotlin/com/vitorpamplona/quartz/nip55AndroidSigner/api/foreground/IntentRequestManager.kt:63,80-97,119,126` — sibling bug + existing fresh-id usage at line 119
- `quartz/src/commonTest/kotlin/com/vitorpamplona/quartz/nip46RemoteSigner/signer/RemoteSignerManagerRetryTest.kt` — existing test scaffolding to extend

### External references

- [NIP-46 spec](https://github.com/nostr-protocol/nips/blob/master/46.md) — request/response shapes + the `auth_url` challenge flow
- [kotlinx.coroutines `Channel`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.channels/-channel/) — capacity, `trySend`, `receive`, `close` semantics
- [`runTest` docs](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/kotlinx.coroutines.test/run-test.html) — virtual time + single-threaded dispatcher

---

## Unanswered Questions

- `pending` final naming — `pending` vs `awaitingRequests` (retain old name for grep continuity)? — minor; leaning `pending` (matches `accessories/` convention)
- Whether the NIP-55 Intent multi-result branch is ever actually hit in practice — defensive fix either way; if telemetry confirms it's dead code we could collapse to single-result path in follow-up
- Whether to grep existing tests for direct `awaitingRequests` access before Step 1 — yes, do it at the start of /ce:work to flag breakage early
