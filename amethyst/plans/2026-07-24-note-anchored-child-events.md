# Migrating a "child event" from cache-scan to Note-anchored

A reusable playbook for the refactor we did to message **edits**, so it can be
re-applied to **OTS attestations** (NIP-03, kind 1040) — or any other event that
_attaches to a target note and changes how it is displayed_ (reactions, replies,
zaps, reports, labels already follow this shape).

## The pattern (and why)

A "child event" `C` targets an existing note `T` via an `e` tag and overlays
extra state on it (edit → new text; OTS → an earliest-verified timestamp).

Two ways to answer *"what children does T have?"*:

1. **Cache scan (old):** on demand, walk **all** of `LocalCache.notes` filtering
   `event is C && event.isTaggedEvent(T.id)`. This is O(all notes) per lookup and
   is memoized behind an LRU. It "just works" for deletion (a deleted child leaves
   the cache, so the scan stops finding it) but is slow, and every child kind grows
   its own scan + LRU.

2. **Anchored on the target (new):** when `C` is consumed, attach it to a
   hard-referenced `List<Note>` **on `T` itself** (`T.children`). Reads then fold
   `T.children` — O(children of T), no scan, no LRU. `LocalCache.notes` is a
   `LargeSoftCache` (evictable), so anchoring also **keeps the child alive exactly
   as long as its target** — critical for children that can't be re-downloaded
   (e.g. decrypted Concord rumors) and a nice-to-have for everything else.

The catch the new model must handle explicitly: **deletion**. Because the child
attaches through the collection (not `replyTo`), the normal delete cascade won't
reach it — you must unlink it on delete yourself (the old scan got this for free).

## Reference implementation — the edits migration

The whole edits migration lives on branch `claude/chat-updates-concord-75jx1g`
(PR #3698). The child collection is `Note.edits`; the resolvers are in
`amethyst/.../model/NoteEditOverlays.kt`. Read these commits in order:

- `98d8f88c` — anchor edits on `Note.edits`, drop the scan + `modificationCache` LRU + the Buzz side-store.
- `f9ad60c4` — move the per-kind resolvers off `LocalCache` into `Note` extensions (`NoteEditOverlays.kt`).
- `893a270c` — the audit fixes: **unlink deleted edits**, one collector per row, ordering tie-breaks.

## The recipe (generic)

### 1. Add the hard collection to `Note` (`commons/.../model/Note.kt`)

```kotlin
var children = listOf<Note>()
    private set

fun addChild(note: Note)    { if (note !in children) { children = children + note; flowSet?.childrenFlow?.invalidateData() } }
fun removeChild(note: Note) { if (note in children) { children = children - note; flowSet?.childrenFlow?.invalidateData() } }
```

- Reuse the note's existing reactive flow if it already has one (edits reused
  `flowSet.edits`; **OTS already has `flowSet.ots`** — reuse it, don't add a new one).
- Wire `children` into **`clearChildLinks()`**: add it to `toBeRemoved`, reset it to
  `listOf()`, and invalidate the flow — so it's released when the message is pruned.
- Add `removeChild(note)` to **`Note.removeNote()`** (the "unlink this child from all
  my collections" funnel), for symmetry with `removeReaction`/`removeReply`.

### 2. Attach on consume (`LocalCache.consume(CEvent)`)

After `loadEvent`, resolve the target by the `e` tag and attach:

```kotlin
event.targetId()?.let { getOrCreateNote(it).addChild(childNote) }
refreshNewNoteObservers(childNote)   // wakes observeEvents-style observers
```

- Use **`getOrCreateNote`** (not `checkGetOrCreateNote`) so it attaches even when the
  target hasn't arrived yet (placeholder), then survives once the target loads.

### 3. Unlink on delete (`LocalCache.unlinkAndRemove`)

The delete cascade walks `replyTo`; these children have none, so add an explicit
resolve-and-unlink, plus a small helper that maps each child kind → its target id:

```kotlin
childTargetIdOf(noteEvent)?.let { getNoteIfExists(it)?.removeChild(note) }
```
```kotlin
private fun childTargetIdOf(event: Event?): HexKey? =
    when (event) { is CEvent -> event.targetId(); else -> null }
```

### 4. Replace the scan with a pure `Note` extension resolver

New file `NoteXOverlays.kt` in `com.vitorpamplona.amethyst.model` (`Note` is a
typealias there). Fold `note.children`, filtering by type + ordering **at read
time** — no `LocalCache` access:

```kotlin
fun Note.latestChild(): Note? =
    children.filter { it.event is CEvent }.maxWithOrNull(compareBy({ it.createdAt() ?: 0L }, { it.idHex }))
```

- **Keep author/validity checks at READ time**, never at attach time: a child can be
  consumed before its target loads, so `T.author` may be unknown at attach — an
  attach-time gate would wrongly drop early-arriving valid children. (For edits this
  was the author check; **OTS has no author check** — see below.)
- Always add an **`idHex` tie-break** to `maxWith`/`minWith` so ties resolve
  identically on every client.

### 5. One reactive observer per surface

```kotlin
@Composable fun observeX(note: Note): T? {
    val v by produceState<T?>(null, note.idHex) {
        note.flow().childrenFlow.stateFlow.collect { value = note.latestChild() }
    }
    return v
}
```
- If a row can host more than one child kind, use **one** observer that resolves both
  (we merged Concord+Buzz edits into a single `observeChatEdit`) — one flow collector
  per visible row, not one per kind.

### 6. Delete the old machinery

The whole-cache scan, its LRU/memo, any thin `cached…` alias, and any per-kind
side-store.

### 7. Tests

Attaches on consume; correct ordering/selection; **deletion unlinks & un-overlays**;
prune releases. (See `BuzzWorkspaceChannelTest` for the shapes.)

## Applying it to OTS — concrete mapping

OTS is already at the "old" stage: a scan (`LocalCache.findEarliestOtsForNote`,
~L3204) + `flowSet.ots` invalidation in `consume(OtsEvent)` (~L1446), read through
`note.flow().ots` and `Loaders.kt`. Map the recipe:

| Recipe piece            | OTS specifics |
|-------------------------|---------------|
| Child event `CEvent`    | `OtsEvent` (`nip03Timestamp.OtsEvent`, `KIND = 1040`) |
| Target id `targetId()`  | `OtsEvent.digestEventId()` (its `e`/target tag) |
| Reactive flow           | **reuse the existing** `note.flow().ots` — do **not** add a new flow |
| New collection on Note  | `Note.ots: List<Note>` + `addOts` / `removeOts` (mind the name vs the `flow().ots` flow) |
| `consume(OtsEvent)`      | after `loadEvent`, `getOrCreateNote(event.digestEventId()).addOts(otsNote)` instead of invalidating `version`'s own flow |
| `unlinkAndRemove`       | add `is OtsEvent -> event.digestEventId()` to the target-id helper → `removeOts` |
| Resolver                | replace the scan with `fun Note.earliestOts(cache): Long?` folding `note.ots` |
| Old machinery to delete | the `notes.mapNotNull { … OtsEvent … }` scan body in `findEarliestOtsForNote` |

**Three OTS-specific differences from edits — do NOT copy edits blindly:**

1. **No author gate.** An OTS proof is cryptographic; the attester's identity is
   irrelevant. Accept OTS from **any** author. (So the read-time author filter that
   edits use simply isn't there — OTS is *simpler*.)
2. **Fold is "earliest verified time," not "latest edit."** Keep **all** OTS on the
   note and take the **minimum** verified timestamp — don't reduce to one.
3. **Resolution is async + cache-backed, not a pure sync fold.** `findEarliestOtsForNote`
   is `suspend` and takes an `otsVerifCacheBuilder` (verify against a resolver, cache
   the `VerificationState`). **Keep all of that** — only swap the *candidate source*
   from "scan `notes`" to "`note.ots`". The verification/caching logic is orthogonal to
   this migration and must not change.

## Gotchas we actually hit

- **ktlint `no-consecutive-comments`:** a file-level `/** … */` KDoc directly above the
  first declaration's KDoc fails the build. Make the file overview a plain `/* … */`
  block, and don't insert a new KDoc'd function *between* an existing KDoc and the
  function it documents (it silently orphans the KDoc and trips the rule).
- **Gradle task names use product flavors:** `:amethyst:compileDebugKotlin` is
  *ambiguous* — use `:amethyst:compilePlayDebugKotlin` and
  `:amethyst:testPlayDebugUnitTest`.
- **`| tail` hides gradle's exit code** — check `BUILD SUCCESSFUL`/`EXIT=` explicitly,
  don't trust the pipeline status.
- **Unused imports** after deleting the scan/side-store fail `spotlessCheck`; run
  `./gradlew spotlessApply` and re-scan for `LruCache`, the old event imports, etc.
- **`Note` is a typealias** (`com.vitorpamplona.amethyst.model.Note` →
  `commons.model.Note`); put the extensions in `com.vitorpamplona.amethyst.model` and
  same-package callers/tests need no import.
