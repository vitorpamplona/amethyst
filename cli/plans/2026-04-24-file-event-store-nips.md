# File-backed event store for `amy` — Part 3: NIPs, Enforcement, Scrub, Testing

**Status:** plan · **Date:** 2026-04-24 · **Part 3 of 3**
(see `2026-04-24-file-event-store-overview.md`,
`2026-04-24-file-event-store-pipelines.md`)

Per-NIP behavior, slot enforcement details, tag indexing, scrub, and
test plan.

---

## NIP-01 — Replaceable events (kinds 0, 3, 10000–19999)

### Enforcement

- Slot path: `replaceable/<kind>/<pubkey>.json`
- Slot is a **hardlink** into `events/<aa>/<bb>/<id>.json` — same
  inode, two names.
- Directory-entry uniqueness is the `UNIQUE(kind, pubkey)` constraint
  (parity with `ReplaceableModule.kt:25-60`).

### Insert flow (inside transaction T2/T5 from Part 2)

```
guard = ReplaceableGuard
   if slot exists:
       Told = read slot JSON → createdAt
       if event.createdAt <= Told: REJECT   # blocks older from reinsert
install (at T5):
   slotTmp = <slot>.tmp.<rand>
   ln events/<new_id>.json slotTmp
   Files.move(slotTmp, slot, ATOMIC_MOVE, REPLACE_EXISTING)
   # Old slot's inode loses one link. If it had a canonical hardlink too,
   # drop it — mirror SQLite trigger that deletes the old row.
   if oldId present:
       runDeleteLinksFor(oldId)  # same code path as delete()
```

### Reading the current version

```
cat replaceable/3/<pubkey>.json        # latest kind-3 for pubkey
```

Direct path open. No scan, no MAX — the slot is the winner.

### Edge cases

- **Race between two newer events.** Both pass guard, both try to
  install; last `rename` wins atomically. Losing event's canonical
  exists orphaned until scrub or until someone queries by its id.
  Semantically valid: both were newer than the previous slot.
- **Slot deleted by user.** Next insert of (kind, pubkey) has no
  guard and wins. This matches "files come and go" contract.
- **Canonical deleted but slot survives.** Slot still resolves —
  hardlink keeps inode alive. Queries work. Scrub re-creates the
  canonical from slot.

---

## NIP-01 — Addressable events (kinds 30000–39999)

### Enforcement

- Slot path: `addressable/<kind>/<pubkey>/<sha256(d_tag)>.json`
- SHA-256 of d-tag value avoids filesystem-unsafe characters and
  collisions. Parity with `AddressableModule.kt:25-63`.
- Empty d-tag is valid Nostr — hashed as `sha256("")`.

### Flow

Same as replaceable, keyed by (kind, pubkey, d_tag). Guard reads
slot's createdAt; install via `rename REPLACE_EXISTING`; cascade-
unlink old canonical and its index hardlinks.

### Query shortcut

If a filter supplies `kinds`, `authors`, and a d-tag, the planner
bypasses the driver indexes and opens the slot directly. O(1).

---

## NIP-09 — Event deletion (kind 5)

### Deletion event insert

When a kind-5 event is inserted (inside T6 of the pipeline):

```
for target in event.deleteEventIds():
    # NIP-09: same author as the deletion
    targetPath = events/<aa>/<bb>/<target>.json
    if targetPath exists AND read(targetPath).pubkey == event.pubkey:
        runDeleteLinksFor(target)     # cascade unlink

    # Install tombstone (hardlink to the kind-5 event):
    tomb = tombstones/id/<target>
    ln event_canonical <tomb>.tmp
    Files.move(<tomb>.tmp, tomb, ATOMIC_MOVE, REPLACE_EXISTING_IF_NEWER)

for addr in event.deleteAddresses():
    hash = sha256(addr.dTag)
    slot = addressable/<addr.kind>/<addr.pubkey>/<hash>.json
    if slot exists AND read(slot).pubkey == event.pubkey:
        Told = read slot.createdAt
        if Told <= event.createdAt:
            oldId = read slot.id
            unlink slot
            runDeleteLinksFor(oldId)

    tomb = tombstones/addr/<addr.kind>/<addr.pubkey>/<hash>
    ln event_canonical <tomb>.tmp
    Files.move(<tomb>.tmp, tomb, ATOMIC_MOVE, REPLACE_EXISTING_IF_NEWER)

for replaceable_kind in event.deleteReplaceableKinds():
    # kinds 0,3,10000-19999 — no d-tag
    slot = replaceable/<kind>/<event.pubkey>.json
    similar cascade + tombstone
```

`REPLACE_EXISTING_IF_NEWER` is a helper we implement: if an older
kind-5 already owns the tombstone, and the new one has a later
createdAt, swap it in; otherwise keep the existing. Ensures
tombstone holds the strongest (latest) deletion cutoff, matching
SQLite's "OR" of all deletion events
(`DeletionRequestModule.kt:102-184`).

### Blocking re-insertion

Part of T2 guards:

```
TombstoneIdCheck:
    if tombstones/id/<event.id> exists → REJECT

TombstoneAddrCheck (addressable only):
    if tombstones/addr/<k>/<pk>/<hash(d)> exists:
        Tdel = read tombstone.createdAt
        if event.createdAt <= Tdel → REJECT
```

Parity with `DeletionRequestModule.kt:61-75` (BEFORE INSERT trigger
rejecting events whose etag_hash/atag_hash match a kind-5 entry).

### Gift-wrap deletion by p-tag

`EventIndexesModule.kt:161-166` uses `pubkey_owner_hash` that, for
`GiftWrapEvent`, is the recipient's p-tag (not the outer pubkey).
We mirror this: `idx/owner/<owner_hash>/<ts>-<id>` points at every
event where `ownerHash = TagNameValueHasher.hash(recipient)`.

A kind-5 from the recipient deleting by `e`-tag still resolves via
`runDeleteLinksFor(id)`, which walks all hardlinks (including
`idx/owner/`) and unlinks them.

### Security: author check

NIP-09 requires deletions be authored by the same pubkey as the
deleted event. SQLite enforces this via `pubkey_owner_hash` in the
DELETE WHERE clause (`DeletionRequestModule.kt:107-115`). We mirror
by reading the target's JSON and comparing `pubkey` — done inside
`runDeleteLinksFor`.

---

## NIP-40 — Expiration

### Read at insert

`event.expiration()` returns `Long?` (tag value as unix seconds).

```
if exp != null && exp > 0:
    if exp <= now: throw ExpiredEventException  # T1 guard, mirrors trigger
    ln canonical idx/expires_at/<zero_padded(exp)>-<id>
```

Parity with `ExpirationModule.kt:27-95`.

### Sweep

`deleteExpiredEvents()` listed in Part 2. Uses the sorted
`idx/expires_at/` — early termination when `<exp>` ≥ now.

Recommended caller: cron or shell loop.

```sh
while :; do amy store sweep-expired; sleep 900; done
```

Matches the `ExpirationWorker` WorkManager pattern from the SQLite
README.

### Query-time filtering

Belt-and-suspenders: queries also filter out events where
`exp != null && exp <= now`. Covers sweep-missed windows and
clock skew between writer and reader.

---

## NIP-62 — Right to vanish (kind 62)

### Insert

```
if !event.shouldVanishFrom(relayUrl): return   # same scoping as SQLite

ownerHash = TagNameValueHasher.hash(event.pubkey)

# Install vanish tombstone (one per pubkey; latest wins)
tomb = tombstones/vanish/<ownerHash>
existing = read tomb if exists
if existing == null || existing.createdAt < event.createdAt:
    ln canonical <tomb>.tmp
    Files.move(<tomb>.tmp, tomb, ATOMIC_MOVE, REPLACE_EXISTING)

# Cascade: delete every event from this owner with createdAt < tomb.createdAt
for entry in idx/owner/<ownerHash>/:
    (ts, id) = parse entry filename
    if ts < event.createdAt:
        runDeleteLinksFor(id)
```

Parity with `RightToVanishModule.kt:46-71`.

### Blocking future inserts

Part of T2 guards (VanishCheck):

```
tomb = tombstones/vanish/<TagNameValueHasher.hash(event.pubkey)>
if tomb exists:
    Tvanish = read tomb.createdAt
    if event.createdAt <= Tvanish → REJECT
```

For GiftWraps, owner is the recipient — a vanish request from the
recipient cascades their received gift-wraps too, matching SQLite's
behaviour via `pubkey_owner_hash`.

---

## NIP-45 — Count

`count(filter)` / `count(filters)` runs the exact query plan from
Part 2 and returns the number of surviving entries. Parity with
SQLite `SELECT count(*)`.

Optimisation: if the filter is driver-only (no post-driver code
filter), we just count directory entries — `Files.list().count()` —
without opening any JSON. Linear in the driver's listing size.

---

## NIP-50 — Full-text search

### Tokenisation

Reuse Quartz's `SearchableEvent.indexableContent()` (per
`FullTextSearchModule.kt:66-78`). Tokeniser:

- Lowercase.
- Split on Unicode word boundaries.
- Filter tokens shorter than 3 chars (FTS5 default).
- Keep ASCII hex ids and bech32 identifiers whole (no splitting on
  underscores/digits).

Match the Android FTS5 tokeniser behaviour; a shared helper lives
in `commons/` and is called by both backends to guarantee identical
search results.

### Index

```
idx/fts/<token>/<id>       # empty hardlink into canonical
```

One empty hardlink per (token, event). For a 1000-word note with
500 unique tokens, 500 hardlinks — each costs one directory entry
(~~50 bytes on ext4), no inode. At scale this is the biggest
directory count; `idx/fts/` alone may hold millions of entries.
Still fine — each token dir stays small.

### Search query

```
tokens = tokenize(filter.search)
streams = tokens.map { idx/fts/<it>/ }
candidates = k-way intersection of streams by <id>
for candidate in candidates:
    if passes all other filter predicates:
        emit
```

Same semantics as SQLite `MATCH` in FTS5 AND-mode. Matches
`QueryBuilder.kt:590-657`.

### Maintenance

- On insert: write token hardlinks (T4).
- On delete (any path — direct, replaceable replace, vanish,
  expiration): `runDeleteLinksFor(id)` enumerates every possible
  token and unlinks. We don't store the token list, so we re-derive
  it by reading the canonical content *before* unlinking the
  canonical. If the canonical is already gone, we fall back to
  `find -samefile` to enumerate remaining hardlinks.

### Without an index

`amy store search --raw "keywords"` can fall back to
`ripgrep events/` for forensic use. Always works, even if indexes
are corrupt.

---

## Tag indexing

### Which tags get indexed

Matches `DefaultIndexingStrategy`
(`IndexingStrategy.kt:99-102`): tag arrays with at least 2 elements
where `tag[0].length == 1` (single-letter tag names). Non-single-
letter tags are stored in the JSON and visible to queries but not
reverse-indexed.

### Hash scheme

Reuse `TagNameValueHasher`
(`quartz/.../sqlite/TagNameValueHasher.kt:30-67`). Reads `.seed`
into a `Long` salt identically to SQLite's `seeds` table. The hash
becomes a 16-char hex string that names the directory:

```
idx/tag/<name>/<hex(hash(name, value))>/<ts>-<id>
```

Parity with `event_tags.tag_hash`. d-tag, e-tag, a-tag, p-tag,
owner: see below.

### Special tags

| Tag | Where indexed | Notes |
|---|---|---|
| `d` | Only as part of `addressable/<k>/<pk>/<sha256(d)>.json` slot | Not in `idx/tag/d/` |
| `e` | `idx/tag/e/<hash>/` | Normal |
| `a` | `idx/tag/a/<hash>/` + addressable slot look-aside | Normal |
| `p` | `idx/tag/p/<hash>/` | Recipient for GiftWrap also goes to `idx/owner/` |
| owner (synthetic) | `idx/owner/<owner_hash>/` | Event pubkey for normal events, recipient for GiftWrap — matches `pubkey_owner_hash` (`EventIndexesModule.kt:161-166`) |

---

## Seed file

`.seed` is 8 random bytes generated on store creation. Read into a
`Long` salt for `TagNameValueHasher`, matching SQLite's `seeds` table
(`SeedModule.kt:26-84`).

- Written atomically (tmp-rename) on first open if absent.
- Never updated. Corrupt or mismatched seeds invalidate `idx/` —
  scrub rebuilds from canonical.
- Permissions `400`.

---

## Scrub / rebuild

`FsEventStore.scrub()`:

```
1. Lock .lock exclusively.
2. Delete .staging/ leftovers.
3. Enumerate all events/<aa>/<bb>/<id>.json.
4. For each, re-derive:
      idx/kind/, idx/author/, idx/owner/, idx/tag/*/,
      idx/fts/*/, idx/expires_at/
   using the same code as T4. createLink is idempotent — NOACT on
   collision (or unlink + recreate).
5. Walk idx/ trees; drop entries whose target inode count is zero
   (dangling — canonical was deleted).
6. Rebuild replaceable/ and addressable/ slots from canonicals by
   grouping (kind,pubkey[,d]) and picking max createdAt.
7. Keep tombstones as-is. (Removing a tombstone file is a user-
   intended action; scrub does not resurrect them.)
8. Release lock.
```

Cost: O(N) disk reads once per scrub. Acceptable as a manual
`amy store scrub` command. Not run automatically.

### `compact()`

Lighter: walk `idx/` only, drop dangling entries. No canonical
reads. O(size of idx trees).

---

## Testing strategy

### Location

`quartz/src/jvmTest/kotlin/com/vitorpamplona/quartz/nip01Core/store/fs/`
(sibling to the SQLite reference tests). Uses kotlin.test (matches
the rest of the quartz `jvmTest` source set). Temp-dir fixture
creates a fresh store per test.

### Test categories

1. **Parity tests.** Drive both `SQLiteEventStore` and `FsEventStore`
   with identical event streams and filters; assert query results
   match. Existing SQLite tests under
   `quartz/src/jvmTest/.../sqlite/` become the golden set. This is
   the highest-value tier.

2. **Per-module tests.** One file per enforcement area:
   - `ReplaceableSlotTest` — newer wins, older rejected, race
     deposes both cleanly.
   - `AddressableSlotTest` — same, plus d-tag hash collisions
     cannot occur (SHA-256).
   - `DeletionTombstoneTest` — by id, by address, by replaceable
     kind; re-insert blocked; gift-wrap by owner.
   - `VanishTest` — cascade + block, GiftWrap recipient.
   - `ExpirationTest` — sweep, early termination, belt-and-
     suspenders query filter.
   - `SearchTest` — tokeniser parity with SQLite FTS, AND of tokens,
     maintenance across replace/delete/expire/vanish.
   - `TagAndTest` — NIP-91 intersection, three-tag filters.
   - `EphemeralTest` — never written.
   - `TransactionTest` — all-or-nothing batch insert, rollback on
     exception.

3. **Crash-safety tests.** Inject failures after each pipeline step
   (T1–T8) via a testing hook; reopen; assert scrub converges.

4. **Concurrency tests.** Two threads / two processes hitting the
   same store: assert no corruption, writes serialise, readers see
   consistent snapshots.

5. **External edit tests.** Mid-run, delete a canonical file / a
   slot / a tombstone; assert store behaves as spec'd (dangling
   index skipped; slot rebuilds; tombstone stops enforcing).

6. **Property tests.** For a random sequence of (insert, delete,
   query, vanish, expire, replace) operations, assert the file
   store and SQLite store produce identical `query()` results.
   Seed = test parameter.

### Running

```
./gradlew :commons:jvmTest --tests "*.store.fs.*"
```

Target: all tests pass before the CLI starts depending on the new
store.

---

## Rollout plan

1. **Step 1 — skeleton.** `FsEventStore` class, directory layout,
   `insert` + `query(ids only)` + `delete(id)`. Round-trip test.
2. **Step 2 — indexes.** kind/author/owner/tag hardlinks; query by
   kind, author, tag; `count`.
3. **Step 3 — slots.** Replaceable + addressable enforcement +
   tests.
4. **Step 4 — tombstones.** NIP-09 deletion by id and address;
   block re-insert; gift-wrap via owner.
5. **Step 5 — expirations.** `idx/expires_at/`, `deleteExpiredEvents`.
6. **Step 6 — vanish.** NIP-62 insert + cascade + block.
7. **Step 7 — FTS.** Tokeniser in `commons/`, `idx/fts/`, search
   queries.
8. **Step 8 — transactions, concurrency, scrub.**
9. **Step 9 — wire into `amy`.** `Context` gains a `store:
   IEventStore` property; default = `FsEventStore` rooted at
   `$AMY_HOME/events/`.
10. **Step 10 — parity test matrix** driven by the existing SQLite
    golden tests.

Each step is a separate commit. Each is behind `AMY_EXPERIMENTAL_
STORE=1` env guard until step 10 passes.

---

## Out of scope for this plan

- Android integration — stays on SQLite.
- Desktop opt-in — separate plan once CLI is stable.
- Cross-host sync — users can rsync the directory; that's it.
- Encryption at rest — future plan; would wrap each event file.

---

## Summary of plan split

| File | Covers |
|---|---|
| `2026-04-24-file-event-store-overview.md` | Goals, layout, feature matrix, API |
| `2026-04-24-file-event-store-pipelines.md` | Insert, query, delete, transaction, concurrency |
| `2026-04-24-file-event-store-nips.md` (this file) | Per-NIP enforcement, slots, tombstones, FTS, scrub, testing |
