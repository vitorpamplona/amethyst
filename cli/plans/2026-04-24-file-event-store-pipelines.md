# File-backed event store for `amy` — Part 2: Pipelines

**Status:** plan · **Date:** 2026-04-24 · **Part 2 of 3**
(see `2026-04-24-file-event-store-overview.md`,
`2026-04-24-file-event-store-nips.md`)

Covers insert, query, delete, transaction, and concurrency. NIP-
specific enforcement (09/40/50/62) and replaceable/addressable slot
mechanics are in Part 3.

---

## Insert pipeline

Mirrors `SQLiteEventStore.insertEvent()`
(`quartz/.../sqlite/SQLiteEventStore.kt:189-196`) and
`innerInsertEvent()` (lines 178-187). Module ordering is identical
— only the backing representation changes.

### Pre-transaction guards (no files touched)

```
1. if (event.kind.isEphemeral()) return               // EphemeralModule
2. if (event.isExpired()) throw ExpiredEventException // ExpirationModule
```

Both match SQLite behaviour exactly — rejected before any I/O.

### Transaction body

Acquire `FileChannel.lock(.lock)` for the whole body. Release only
on COMMIT or ROLLBACK.

```
T1. Stage event file
    stagingDir = .staging/<random>/
    write stagingDir/event.json   (event bytes, fsync if AMY_FSYNC=1)

T2. Pre-insert veto checks (read-only)
    • TombstoneIdCheck:  tombstones/id/<event.id> exists?           → REJECT
    • TombstoneAddrCheck: (addressable only) tombstones/addr/<k>/<pk>/<hash(d)>
                          exists AND Tdel >= event.createdAt?       → REJECT
    • VanishCheck:       tombstones/vanish/<owner_hash> exists AND
                         Tvanish >= event.createdAt?                → REJECT
    • ReplaceableGuard:  (kinds 0,3,10000-19999) replaceable/<k>/<pk>.json
                         exists AND its createdAt >= event.createdAt? → REJECT
    • AddressableGuard:  (kinds 30000-39999) addressable/<k>/<pk>/<hash(d)>.json
                         exists AND its createdAt >= event.createdAt? → REJECT
    Any REJECT → discard staging dir, release lock, return.

T3. Canonical write
    canonical = events/<id[0:2]>/<id[2:4]>/<id>.json
    Files.move(stagingDir/event.json, canonical, ATOMIC_MOVE)
    Files.setLastModifiedTime(canonical, event.createdAt seconds)
    Files.setPosixFilePermissions(canonical, r--r--r--)

T4. Derived-index hardlinks (all ln(canonical → X))
    • idx/kind/<k>/<ts>-<id>
    • idx/author/<pk>/<ts>-<id>
    • idx/owner/<owner_hash>/<ts>-<id>        # recipient for GiftWrap
    • for each indexed tag (name, value):
         idx/tag/<name>/<murmur_hex>/<ts>-<id>
    • if event.expiration() > 0:
         idx/expires_at/<exp_ts>-<id>
    • if event implements SearchableEvent:
         for each token in tokenize(event.indexableContent()):
             idx/fts/<token>/<id>

T5. Replaceable / Addressable slot (if applicable)
    slotTmp = <slot>.tmp.<random>
    ln canonical slotTmp
    Files.move(slotTmp, slot, ATOMIC_MOVE, REPLACE_EXISTING)
    # The rename atomically deposes the old winner and installs new.
    if oldSlot had different event id:
         unlink events/<old_id>.json                       # drop canonical
         walk idx/... and unlink entries for old id         # reuses same logic as delete()

T6. Deletion-event side effects (if event.kind == 5)
    Apply NIP-09 actions — see Part 3. Executes *inside* the lock.

T7. Vanish-event side effects (if event.kind == 62 && shouldVanishFrom(relay))
    Apply NIP-62 cascade — see Part 3.

T8. Commit
    Release .lock. Remove emptied staging dir.
```

### Crash safety at every step

| Crash between | Post-crash state | Scrub action |
|---|---|---|
| T1 and T3 | Only staging dir exists | Delete `.staging/` on next open |
| T3 and T4 | Canonical written, no indexes | Rebuild indexes by walking `events/` |
| T4 (partial) | Some indexes written | Rebuild indexes (idempotent) |
| T5 (partial) | Slot tmp hardlink exists | Remove `<slot>.tmp.*` on next open |
| T5 (after rename) | Old winner still has canonical | Slot takes precedence; old canonical GC'd by scrub |
| T6/T7 (partial) | Some targets unlinked, some not | Re-apply kind-5/62 during scrub |

The kernel guarantees `rename(2)` atomicity; `link(2)` either
succeeds fully or fails. All failure modes are forward-recoverable
by scrub — never corrupting.

### Ordering vs SQLite

SQLite runs triggers *before* our code inserts. We run checks
before writing the canonical (T2), then indexes (T4), then slot
enforcement (T5), then kind-5 / kind-62 side effects (T6/T7). Same
overall guarantees, different sequencing because we have no trigger
mechanism.

---

## Transaction API

```kotlin
override fun transaction(body: ITransaction.() -> Unit) {
    withLock {
        val txn = FsTransaction(this)
        try {
            txn.body()           // accumulates staged events
            txn.commit()         // all T3..T7 for every staged event, then one unlock
        } catch (e: Throwable) {
            txn.rollback()       // delete staging dirs, do nothing else
            throw e
        }
    }
}
```

Semantics:

- Single `flock` for the whole transaction.
- Staging dirs collected; canonical moves happen at commit time.
- If any event in the batch is rejected by T2, the whole txn aborts.
- Matches SQLite's all-or-nothing behaviour
  (`SQLiteConnectionExt.kt:71-81`).

No readers are blocked: they don't hold the lock and see either
pre-commit or post-commit state (atomic rename).

---

## Query pipeline

Matches the planner structure of `QueryBuilder`
(`quartz/.../sqlite/QueryBuilder.kt:110-172`) but emits directory
walks instead of SQL.

### Filter normalisation

Same as SQLite: `filter.toFilterWithDTags()` splits the d-tag out of
the generic tag map, flags `isSimpleQuery` / `isSimpleSearch`.

### Plan selection

For each filter, choose one driver index based on cardinality and
availability:

| Filter contents | Driver index | Rationale |
|---|---|---|
| Only `ids` | direct `events/<aa>/<bb>/<id>.json` opens | O(1) per id |
| `kinds` present, no tags | `idx/kind/<k>/` | Smallest per-kind fanout |
| `authors` present, no kinds, no tags | `idx/author/<pk>/` | Author-scoped |
| `kinds` + `authors` + no tags | Pick the smaller of `idx/kind/` vs `idx/author/` by listing size | Parity with SQLite's `query_by_kind_pubkey_created` choice |
| Any `#tag` filter | `idx/tag/<n>/<h>/` — intersect multiple if AND | Matches SQLite's per-tag INNER JOINs |
| Only `search` | `idx/fts/<token>/` — intersect tokens | Parity with FTS MATCH |
| Only `since`/`until` / `limit` | `idx/kind/*/` or full `events/` walk ordered by mtime | See below |
| `d_tag` present (addressable) | `addressable/<k>/<pk>/<hash(d)>.json` direct open | Slot lookup — O(1) |

Chosen driver produces a **sorted stream of `(createdAt, id)` pairs**
in DESC order (lexicographic sort over zero-padded filenames reversed).
Filenames are `<ts>-<id>`, so `Files.list(dir).sorted(reverseOrder())`
is the plan.

### Secondary filters (post-driver)

Anything not covered by the driver is applied in code, event by
event, after opening the JSON:

- `since <= createdAt <= until` (already a prefix filter on the driver)
- `authors` / `kinds` / `ids` if not the driver
- d-tag match (addressable)
- additional tag filters
- search tokens (if driver wasn't FTS)
- tombstone exclusions (id + address)
- vanish exclusions (by owner_hash)
- expiration exclusions (`createdAt < now < expiration`)

### Tag AND (NIP-91)

For `filter.tagsAll = {#e = [a, b], #p = [c]}`:

```
streamA = idx/tag/e/<hash(a)>/  (sorted by <ts>-<id> DESC)
streamB = idx/tag/e/<hash(b)>/
streamC = idx/tag/p/<hash(c)>/
result  = k-way intersection by <id> suffix, preserving DESC order
```

Implemented as a sorted-list intersection (Java `Iterator<String>`
merge). Equivalent to SQLite's INNER JOIN per tag
(`QueryBuilder.kt:434-449`). Bounded memory.

### Multi-filter UNION

`query(filters: List<Filter>)` runs each filter's plan, then merges
the resulting streams in DESC order with dedup by id. Per-filter
`limit` applied before merge, as SQLite does
(`QueryBuilder.kt:577-583`).

### Streaming mode

`query(filter, onEach: (T) -> Unit)` calls `onEach` as each event is
read and parsed. No intermediate list. Matches SQLite's cursor
callback.

### `count()`

Same plan, but open nothing — just count filenames that survive the
post-driver code filters. For filters that require JSON inspection
(tag exact match on non-indexed tags, search tokens), we still open
files; cost scales with matched set size, not store size.

### `planQuery()` output

Plain text, like `EXPLAIN QUERY PLAN`:

```
FILTER 0
  DRIVER: idx/kind/1/ (12,400 entries)
  POST: authors=[<pk>], since >= 1713960000, limit 100
FILTER 1
  DRIVER: idx/tag/e/ab12…/  ∩  idx/tag/p/cd34…/ (est 37 hits)
  POST: kinds=[30023], limit 10
MERGE: 2 filters, DESC by created_at, overall limit 110
```

---

## Delete pipeline

### `delete(filter)` / `delete(filters)`

Runs the query pipeline to enumerate matching events, then for each:

```
1. Read canonical, parse tags (needed to know all hardlink paths).
2. Unlink every derived hardlink:
   idx/kind/<k>/<ts>-<id>
   idx/author/<pk>/<ts>-<id>
   idx/owner/<owner_hash>/<ts>-<id>
   idx/tag/<n>/<h>/<ts>-<id>  (for each indexed tag)
   idx/expires_at/<exp>-<id>  (if exp present)
   idx/fts/<token>/<id>        (for each FTS token)
3. If event is the winner of a replaceable/addressable slot:
   unlink the slot path.
4. Unlink canonical events/<aa>/<bb>/<id>.json.
5. kernel GC's the inode (no other refs).
```

All inside `flock`. Matches SQLite's
`DELETE FROM event_headers WHERE row_id IN (...)` with FK cascade.

### `delete(id: HexKey): Int`

Direct shortcut. Opens `events/<aa>/<bb>/<id>.json`, runs the same
steps 1-5. Returns `1` on success, `0` if the file didn't exist —
matching the SQLite return value from `changes()`
(`SQLiteEventStore.kt:261-264`).

### `deleteExpiredEvents()`

```
now = clock.now().epochSecond
for entry in idx/expires_at/ where <exp> < now:
    delete(entry.id)
```

Optional optimisation: because filenames are `<exp>-<id>`, listing is
already sorted, so we can stop at the first entry with `<exp> >= now`.

---

## Concurrency model

### Single writer, many readers

- Writers hold an exclusive `flock(.lock)` via `FileChannel.tryLock()`.
- Readers take a shared lock only while listing top-level directory
  contents that may be mid-rename; most reads hold no lock (kernel-
  atomic `open` / `readdir` is enough).
- Two concurrent `amy` invocations serialise cleanly on the flock;
  the second one blocks on `FileChannel.lock()` (blocking) or fails
  fast with `tryLock()` (configurable).

### Reader–writer interaction

Because every writer mutation is a rename-over-tmp, readers see
either the pre-mutation file or the post-mutation file, never a
torn view. Dangling reads (writer unlinks a file between
`readdir` and `open`) manifest as `NoSuchFileException` and are
treated as "event not in store anymore" — we skip it and keep going.

### Watch / subscribe (future)

`java.nio.file.WatchService` on `events/` gives per-platform
inotify/FSEvents/ReadDirectoryChangesW. Lets a future
`amy store tail` stream newly arriving events to stdout without
polling. Not part of this plan but the layout supports it
natively.

---

## Close / reopen

- `close()` releases any held `FileChannel` and flushes nothing
  (writes are already durable as of each rename, modulo the
  `AMY_FSYNC` option).
- Reopen is free: the store is stateless. No header page, no WAL
  checkpoint. First action after open: scan `.staging/` and delete
  leftovers (crash from previous run).

---

Next: NIP specifics and enforcement details
(`2026-04-24-file-event-store-nips.md`).
