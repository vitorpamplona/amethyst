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

## One-shot reads (subscribe → collect → return)

| Function | File | Use when |
| --- | --- | --- |
| `fetchAll(relay, filter, timeoutMs)` | `NostrClientFetchAllExt` | Get every event matching a filter in one REQ, deduped by id, until EOSE or timeout. **No verify, no store** — just the events. |
| `fetchFirst(relay, filter, timeoutMs)` | `NostrClientFetchFirstExt` | Get the first matching event and stop (returns `null` on none/timeout). |
| `fetchAllPages(relay, filters, timeoutMs)` | `NostrClientFetchAllPagesExt` | Fully retrieve a result set larger than the relay's per-REQ cap (strfry `limit`, ~500) by walking a `created_at` cursor. Bound it with the filter's `limit`. |
| `fetchAllPagesFromPool(filters, ...)` | `NostrClientFetchAllPagesPoolExt` | Same paging, across several relays at once, deduped across them. |

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
| `count(relay, filter, timeoutMs)` | `NostrClientCountExt` | NIP-45 `COUNT` against one relay (`null` on timeout / no support). |
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
