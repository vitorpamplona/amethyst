# File-Backed Event Store (`fs`)

A pure-filesystem `IEventStore` implementation. Each Nostr event is
written as a single JSON file under a sharded directory tree;
hardlinks form every secondary index, slot, and tombstone. No
database engine, no JNI, no schema migration — just `events/`,
`idx/`, `replaceable/`, `addressable/`, `tombstones/`.

JVM-only (uses `java.nio.file.Files.createLink` and
`FileChannel.lock`). For Android the SQLite store at
[`../sqlite`](../sqlite/README.md) is still the right choice; for the
JVM CLI (`amy`) and for human-inspectable scratch stores, this
implementation is the default.

## Why a filesystem store?

| Goal | Outcome |
|---|---|
| Human-inspectable | Every event is a JSON file. `ls`, `cat`, `jq`, `grep`, `find`, `rsync`, `git` all work directly. |
| Tolerant of external edits | Delete an event file by hand → store converges. Dangling `idx/` entries are skipped at read time and cleaned up by `compact()`. |
| No JNI / no JDBC | Runs on any JVM. The code is `expect`/`actual`-friendly so it could be ported to native. |
| Same `IEventStore` contract | Drop-in replacement for `EventStore` (SQLite). Same insert / query / delete / count semantics, same NIP support. |
| Filesystem primitives = invariants | Directory-entry uniqueness IS the `UNIQUE(kind,pubkey[,d])` constraint. `rename(2)` IS the atomic commit. Hardlink refcount IS the FK cascade. `flock(2)` IS the writer serialisation. |

The trade-off vs. SQLite: filtered queries are O(*directory listing
size*) instead of O(*log n*) and there's no cost-based query planner.
For tens-of-thousands of events on a single host this is fine; for a
full Amethyst-scale cache (hundreds of thousands of events with hot
queries) SQLite still wins.

## Feature parity with the SQLite store

Everything documented in [`../sqlite/README.md`](../sqlite/README.md):

| SQLite feature | This store |
|---|---|
| Insert + retrieve by Nostr filter | ✓ |
| Replaceable events (kinds 0, 3, 10000-19999) — newer wins, older blocked | ✓ via `replaceable/<kind>/<pubkey>.json` slot |
| Addressable events (kinds 30000-39999) — `(kind,pubkey,d)` uniqueness | ✓ via `addressable/<kind>/<pubkey>/<sha256(d)>.json` slot |
| Ephemeral events never stored | ✓ rejected pre-write |
| NIP-09 deletions — by id, by address, gift-wrap by `p`-tag, blocks re-insert | ✓ via `tombstones/id/` and `tombstones/addr/` (each tombstone is a hardlink to the kind-5 event) |
| NIP-40 expirations — reject expired-on-insert, periodic sweep | ✓ via `idx/expires_at/` index + `deleteExpiredEvents()` |
| NIP-45 counts | ✓ same planner, just count results |
| NIP-50 full-text search — content tokenisation, AND of tokens | ✓ via `idx/fts/<token>/` |
| NIP-91 multi-tag AND | ✓ k-way intersection of `idx/tag/<name>/<hash>/` listings |
| NIP-62 right-to-vanish — cascade + block-future-insert | ✓ via `tombstones/vanish/<owner_hash>` (latest cutoff wins) |
| Immutable canonical (no UPDATE) | ✓ event files are write-once; only indexes/tombstones mutate |
| Transactions | ✓ `transaction { … }` holds the flock for the whole batch |

A parity test
([`FsParityTest`](../../../../../../jvmTest/kotlin/com/vitorpamplona/quartz/nip01Core/store/fs/FsParityTest.kt))
drives both this store and the SQLite reference with identical event
streams and asserts query results agree.

## On-disk layout

```
<root>/
├── .lock                                          # flock target (cross-process serialisation)
├── .seed                                          # 8 random bytes; salts every Murmur hash
├── .staging/                                      # tmp files awaiting atomic rename
│
├── events/<aa>/<bb>/<id>.json                     # canonical event; mtime = event.createdAt
│
├── idx/
│   ├── kind/<kind>/<ts>-<id>                      # hardlink → events/.../<id>.json
│   ├── author/<pubkey>/<ts>-<id>                  # hardlink
│   ├── owner/<owner_hex>/<ts>-<id>                # hardlink; recipient for GiftWrap
│   ├── tag/<name>/<value-or-_h_hash>/<ts>-<id>    # hardlink; single-letter tag names only
│   ├── expires_at/<exp>-<id>                      # hardlink; for NIP-40 sweep
│   └── fts/<token>/<ts>-<id>                      # hardlink; for NIP-50 search
│
├── replaceable/<kind>/<pubkey>.json               # hardlink to current winner (kinds 0,3,10000-19999)
├── addressable/<kind>/<pubkey>/<sha256(d)>.json   # hardlink to current winner (kinds 30000-39999)
│
└── tombstones/
    ├── id/<id>.json                               # hardlink to the kind-5 event that deleted <id>
    ├── addr/<kind>/<pubkey>/<sha256(d)>.json      # hardlink to the kind-5 event for an address
    └── vanish/<owner_hex>.json                    # hardlink to the kind-62 event for an owner
```

### Filename conventions

- **Event files**: `<event_id>.json`, raw NIP-01 event JSON.
- **Sharding**: first 4 hex chars of the event id give the path
  `<aa>/<bb>/`, capping any one leaf directory at a few hundred
  entries even at million-event scale and piggybacking on ext4 htree
  / APFS B-tree / NTFS index for O(log n) lookup.
- **Index entry filenames**: `<padded_ts>-<id>` where `<padded_ts>`
  is the event's `created_at` left-padded to 10 digits. Lexicographic
  sort of a directory listing == chronological sort.
- **Hashes** (only when needed):
  - `sha256Hex(d-tag)` — collision-resistant, always used wherever the
    path IS the uniqueness constraint (addressable slots, address
    tombstones).
  - `idx/tag/<name>/<value-or-_h_hash>/` — for the tag indexes the
    directory name is the **raw tag value** when filesystem-safe
    (printable ASCII, no path separators or shell-hostile chars,
    ≤180 bytes). Otherwise it falls back to `_h_<hashHex(murmurLong)>`
    (16 hex chars after the sentinel). Pubkey p-tags, hex e-tags,
    ASCII hashtags, kind numbers, and geohashes all keep their raw
    values — `ls idx/tag/p/<your_pubkey>/` works directly. Emojis,
    URLs, free-form `alt` text, and `:`-bearing `a`-tags route through
    the `_h_` bucket so the filesystem stays portable.
  - `hashHex(murmurLong)` — 16 hex chars, used for owner-hash dirs
    and (after the `_h_` prefix) the hash bucket inside tag indexes.
- **mtime**: every canonical write does
  `Files.setLastModifiedTime(canonical, FileTime.from(event.createdAt, SECONDS))`
  so `ls -t`, `find -newermt`, `rsync -a` all behave naturally.

## Architecture

```
FsEventStore                ← orchestrator; implements IEventStore
├── FsLayout                ← path helpers + .seed file
├── FsLockManager           ← flock(.lock) with per-thread reentry
├── FsIndexer               ← link / unlink the idx/ + fts hardlinks for an event
├── FsSlots                 ← install / evict replaceable + addressable slots
├── FsTombstones            ← NIP-09 id / addr + NIP-62 vanish tombstones
├── FsQueryPlanner          ← pick driver index, stream candidates DESC by createdAt
└── FsSearchTokenizer       ← lowercase + Unicode-letter-or-digit split (≈ FTS5 unicode61)
```

Every mutating call (`insert`, `delete`, `transaction`,
`deleteExpiredEvents`, `scrub`, `compact`) acquires the flock once;
nested calls on the same thread re-enter for free. Reads take no
lock — atomic-rename writes mean readers see either the pre- or
post-mutation state, and `NoSuchFileException` on a just-unlinked
candidate is silently skipped.

## Usage

### Initialisation

```kotlin
import com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore
import java.nio.file.Path

val store = FsEventStore(
    root = Path.of(System.getProperty("user.home"), ".amy", "events-store"),
    // optional NIP-62 relay scoping; null = only ALL_RELAYS vanish requests cascade
    relay = null,
)
```

The directory is created on first use. The `.seed` file (8 random
bytes) is generated atomically on first open and read on every
subsequent open — it salts every tag / owner hash, so it must persist
or every previously-written index entry becomes unreachable.

`FsEventStore` is `open` and exposes a `protected open fun now(): Long
= TimeUtils.now()`. Tests override it with a subclass to drive NIP-40
expiration at exact timestamps without relying on the wall clock; see
`FsExpirationTest.ClockedStore`.

### Insert

```kotlin
store.insert(event)
```

Pre-checks (in order, parity with the SQLite triggers):

1. **Ephemeral kinds** (20000-29999) — silently dropped.
2. **NIP-40 expiration** — rejected if `event.expiration() <= now`.
3. **NIP-09 / NIP-62 tombstones** — id-tombstone always blocks;
   address-tombstone blocks if `event.createdAt <= cutoff`; vanish
   tombstone blocks if `event.createdAt <= vanish.createdAt`.
4. **Replaceable / addressable slot** — blocks if existing slot's
   `createdAt >= event.createdAt`.

Then writes canonical via `events/<id>.json.tmp` → atomic rename,
links every `idx/` hardlink, installs the slot atomically (evicting
the old winner's canonical + indexes), and applies any NIP-09 / NIP-62
side effects if the event is a kind-5 / kind-62.

### Query

```kotlin
val notes = store.query<TextNoteEvent>(
    Filter(authors = listOf(pubKey), kinds = listOf(1), limit = 50),
)

// Streaming variant (no intermediate List)
store.query<TextNoteEvent>(filter) { event ->
    println(event.content)
}

// Multi-filter union (deduped by id)
store.query<Event>(listOf(filterA, filterB))
```

Driver selection:

| Filter contents | Driver |
|---|---|
| `ids`              | direct canonical opens (mtime gives ordering) |
| `search`           | `idx/fts/<token>/` intersection |
| `tags`/`tagsAll`   | `idx/tag/<name>/<hash>/` (first key wins; rest are post-filter) |
| `kinds`            | union of `idx/kind/<k>/` |
| `authors`          | union of `idx/author/<pk>/` |
| nothing            | full scan via every `idx/kind/<k>/` |

Whatever the driver picks, the orchestrator opens each candidate's
JSON and runs `Filter.match(event)` — so any driver is
correctness-safe; choice only affects how much we scan.

### Count

```kotlin
val total = store.count(filter)
```

Same plan as `query`, just doesn't materialise events.

### Delete

```kotlin
store.delete(eventId)              // by id; returns 1 if removed, 0 otherwise
store.delete(filter)               // delete all matching
store.delete(listOf(filterA, ...)) // delete union
```

Unlinks every index hardlink, clears the slot if this event was the
current winner, then unlinks the canonical.

### Transactions

```kotlin
store.transaction {
    insert(event1)
    insert(event2)
    insert(event3)
}
```

Holds the flock for the whole body. Inside the body each `insert()`
re-enters the lock for free. Exceptions propagate; events written
before the exception are kept (atomic-per-event, serialised across
writers — not all-or-nothing, since canonical files are individually
atomic).

### NIP-40 expiration sweep

```kotlin
store.deleteExpiredEvents()
```

Walks `idx/expires_at/`, deletes everything with `exp < now`. Run on
a timer (cron, scheduled coroutine) — every 15 minutes is a sane
default; matches the SQLite store's recommendation.

### Maintenance

```kotlin
store.scrub()    // rebuild every idx/ entry from the canonical events
store.compact()  // drop dangling idx/ entries (where canonical is gone)
```

`scrub()` recovers from external edits or partial-write crashes;
slots and tombstones are left alone (a tombstone removed by hand is
treated as deliberate "un-forget"). `compact()` is cheap — never
opens an event JSON, only stats canonicals.

### Close

```kotlin
store.close()
```

Releases the flock channel. Reopen is free — the store is stateless
on disk; first action after open is sweeping leftover `.staging/` files.

## Failure modes

The store is engineered to converge under the kinds of damage a
filesystem-backed cache will actually see:

| What happened | Effect |
|---|---|
| User deleted an event JSON | Dangling `idx/` entries are skipped at read time; `compact()` cleans them up. Slot- or tombstone-pinned data is preserved via the hardlink. |
| User deleted a slot file | Next insert at that `(kind, pubkey[, d])` wins unopposed. |
| User deleted a tombstone | Enforcement stops for that target — treated as deliberate. |
| Crash mid-insert (canonical written, indexes not) | `scrub()` rebuilds the missing `idx/` entries. |
| Crash mid-rename (.staging tmp leftover) | Cleared on next open. |
| Two `amy` processes write at once | Cross-process flock serialises them. |
| Two threads in one process write at once | Same flock plus per-thread re-entry counter. |
| Disk fills up | Insert throws; the store is consistent at the canonical it wrote (or didn't). Replaceable slot replacement is atomic via `rename(2)`. |

## Testing

Tests live under
[`quartz/src/jvmTest/kotlin/.../store/fs/`](../../../../../../jvmTest/kotlin/com/vitorpamplona/quartz/nip01Core/store/fs/):

| File | Coverage |
|---|---|
| `FsEventStoreTest`   | round-trip, sharding, ephemeral, staging cleanup |
| `FsQueryTest`        | author / kind / tag drivers, tag OR / tagsAll AND, since/until, limit, count, reopen |
| `FsSlotsTest`        | replaceable + addressable winner / eviction / canonical-survives-via-slot |
| `FsDeletionTest`     | NIP-09 by id / address, block re-insert, cutoff semantics, non-author no-cascade |
| `FsExpirationTest`   | NIP-40 future / past / equal-now / sweep / non-positive |
| `FsVanishTest`       | NIP-62 cascade / block / strongest-cutoff-wins / per-relay scoping |
| `FsSearchTest`       | tokenizer behaviour, single-token / AND-of-tokens, ordering, reopen |
| `FsMaintenanceTest`  | flock, transaction commit + propagated exceptions, re-entrant lock, scrub, compact, two-thread concurrency |
| `FsParityTest`       | drive both this store and SQLite with identical streams and assert results match |

```bash
./gradlew :quartz:jvmTest --tests "com.vitorpamplona.quartz.nip01Core.store.fs.*"
```

113 tests, ~5 s on a laptop.

## Design notes & references

- Plan documents live in
  [`cli/plans/2026-04-24-file-event-store-*.md`](../../../../../../../../cli/plans/)
  (overview, pipelines, NIP-by-NIP).
- The CLI integration that makes this the source of truth for `amy`
  is documented under "Local event store — the source of truth" in
  [`cli/README.md`](../../../../../../../../cli/README.md).
- The SQLite reference implementation that this store achieves
  parity with: [`../sqlite/README.md`](../sqlite/README.md).
