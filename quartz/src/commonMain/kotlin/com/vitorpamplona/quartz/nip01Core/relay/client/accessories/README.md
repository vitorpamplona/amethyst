# `INostrClient` accessories

One-shot / high-level relay operations, written as **extension functions** on
`INostrClient`. They live here (and in `../reqs/`) rather than on the client class,
so they don't show up under "usages of `NostrClient`" or in method completion — you
only find them by knowing this package exists.

**Before writing a new subscribe / REQ / publish loop, look here first.** Most of what
a caller needs (fetch a set, fetch one, page past the relay cap, publish-and-confirm,
count, negentropy sync/reconcile) already exists.

Import as `com.vitorpamplona.quartz.nip01Core.relay.client.accessories.<name>` (or
`...client.reqs.<name>` for the flow/subscribe helpers).

## Timeout convention

Every wait in this package is an **idle window measured from the relay's most recent
progress**, never a wall-clock deadline: real progress resets it, so an actively
streaming relay is never cut off mid-delivery — the operation only gives up after a
full window of silence. The shared primitives are in `IdleWatchdog.kt` (`IdleClock` +
`receiveWithinIdle`); use them in a new accessory.

**The parameter is named `idleTimeoutMs`, never `timeoutMs`** — the name is the
contract, so a caller can't mistake it for a deadline. The sole exception is
`publishAndConfirm`'s `timeoutInSeconds`, which genuinely *is* a fixed window (see
below); the differing name is the tell.

**Progress, not merely traffic.** A message that tells us nothing new — a relay
re-CLOSEing after we already recorded it as done, a duplicate COUNT — must not
restart the window, or a flapping relay keeps the call alive indefinitely. This is
the rule the negentropy watchdog already applies to `NOTICE`/`CLOSED` chatter, and
it is what makes `fetchFirst` and multi-relay `count` self-bounding: at most one
window per relay.

**No accessory takes a wall-clock ceiling parameter.** A hard bound composes at the
call site — `withTimeoutOrNull(ms) { fetchFirst(...) }` — so duplicating it in every
signature buys nothing. Prefer the idle window inside (the caller cannot implement
it; it needs the message stream) and the wall clock outside. Two consequences worth
knowing:

- `fetchAllPages` has no ceiling and could not usefully have one. A per-page cap
  bounds a *page*, not the call: the loop reacts to a page ending by advancing the
  cursor and issuing the next `REQ`, so an endless trickle is just re-paged (a
  400 ms cap measured 8 `REQ`s and no return). It also makes truncation unsafe —
  cutting a page mid-stream advances `until` to the oldest event received *so far*,
  which only preserves the set if the relay streams strictly newest-first, which
  NIP-01 recommends but does not require. Bound a paged download with the filter's
  `limit`, or by cancelling.
- `fetchAll` / `fetchAllWithHooks` keep a pre-existing `maxTotalMs`, and it earns
  its place: an endless *event* trickle there is genuine progress, so the call never
  self-terminates, and the internal cap returns the events collected so far where an
  external `withTimeoutOrNull` would discard them.

The write side is its own case: `publishAndConfirm`'s `timeoutInSeconds` is a fixed
window to collect the `OK`s — a bounded confirmation round-trip, not a stream.

## One-shot reads (subscribe → collect → return)

| Function | File | Use when |
| --- | --- | --- |
| `fetchAll(relay, filter, idleTimeoutMs)` | `NostrClientFetchAllExt` | Get every event matching a filter in one REQ, deduped by id, until EOSE or a full idle window of silence. **No verify, no store** — just the events. |
| `fetchFirst(relay, filter, idleTimeoutMs)` | `NostrClientFetchFirstExt` | Get the first matching event and stop (returns `null` on none/timeout). |
| `fetchAllPages(relay, filters, idleTimeoutMs)` | `NostrClientFetchAllPagesExt` | Fully retrieve a result set larger than the relay's per-REQ cap (strfry `limit`, ~500) by walking a `created_at` cursor. Bound it with the filter's `limit`. |
| `fetchAllPagesFromPool(filters, ...)` | `NostrClientFetchAllPagesPoolExt` | Same paging, across several relays at once. No cross-relay dedup — the `WithHooks` variant below dedups. |
| `fetchAllWithHooks(filters, ...)` | `NostrClientFetchAllWithHooksExt` | `fetchAll` with a suspending per-`(relay, event)` accept hook (verify+store as events arrive), per-relay terminal-reason tracking, optional dead-relay collection (`deadOut` + `classifyDrainFailure`), keep-pending-on-`auth-required` CLOSED (NIP-42 re-fire), and a timeout diagnostic hook. |
| `fetchAllPagesFromPoolWithHooks(filters, ...)` | `NostrClientFetchAllWithHooksExt` | `fetchAllPagesFromPool` with the same suspending accept hook, run single-threaded in one consumer; deduped across relays by `SeenIds` before the hook. |

## Streaming (`Flow`)

| Function | File | Use when |
| --- | --- | --- |
| `fetchAsFlow(relay, filter)` | `../reqs/NostrClientFetchAsFlowExt` | Emit the accumulating list on each arrival; completes on EOSE. One-shot query as a flow. |
| `subscribeAsFlow(relay, filter)` | `../reqs/NostrClientSubscribeAsFlowExt` | Live subscription as a flow (stays open past EOSE; re-sends the REQ on reconnect). |
| `subscribe(subId, filters, listener)` | `../reqs/StaticSubscription`, `DynamicSubscription` | Raw live subscription with a `SubscriptionListener`. The lowest-level primitive the above build on. |

## Publish

| Function | File | Use when |
| --- | --- | --- |
| `publishAndConfirm(event, relays, timeout)` | `NostrClientPublishExt` | Send an EVENT and wait for `OK`; returns whether any relay accepted it. |
| `publishAndConfirmDetailed(event, relays, timeout)` | `NostrClientPublishExt` | Same, but returns the per-relay accepted/rejected map. |

## Count (NIP-45)

| Function | File | Use when |
| --- | --- | --- |
| `count(relay, filter, idleTimeoutMs)` | `NostrClientCountExt` | NIP-45 `COUNT` against one relay (`null` on timeout / no support). |
| `countMerged(relays, filter, ...)` | `NostrClientCountExt` | Merged count across relays. |

## Negentropy (NIP-77)

| Function | File | Use when |
| --- | --- | --- |
| `negentropySync(relay, filter, ...)` | `NostrClientNegentropySyncExt` | Download everything a relay holds for a filter, diffing against `localEntries` and by-id downloading only the diff. Throws `NegentropySyncException` if the relay can't reconcile (no fallback). |
| `negentropySyncOrFetch(relay, filter, ...)` | `NostrClientNegentropySyncExt` | Same, but transparently falls back to `fetchAllPages` when the relay can't reconcile. The "just get the events" combinator. |
| `negentropySyncEvents` / `negentropySyncOrFetchEvents` | `NostrClientNegentropySyncEventsExt` | The two above as an O(1)-memory `Flow<Event>`. |
| `negentropyReconcile(relay, filter, localEntries, onNeedIds, onHaveIds)` | `NostrClientNegentropySyncExt` | **Pure diff, no I/O** — streams the two directions (`need` = relay has & we lack; `have` = we have & relay lacks) to callbacks. Compose your own download/upload on top. |
| `negentropyReconcileIds(relay, filter, localEntries)` | `NostrClientNegentropySyncExt` | Same diff, materialized into `needIds` / `haveIds` lists (small sets only). |
| `negentropySettleDeletions(relay, filter, store, sendUp, applyDown)` | `NostrClientNegentropyDeletionSettleExt` | Second pass of a two-pass sync: after a content sync settles, re-reconcile and resolve only the residual — send our covering deletions up (`sendUp`) and/or apply the relay's kind-5 down (`applyDown`), looping until stable. Cost is O(residual), not O(db). Pairs with `IEventStore.deletionsCovering`. |

`fetchByIds`, `reconcileStreaming`, `syncPipeline` in `NostrClientNegentropySyncExt`
are `internal` implementation details — not part of the public surface.

---

_Keep this table in sync when you add a public `INostrClient` extension here._
