---
title: "feat: WeakReference Cache + State Class Extraction"
type: feat
status: active
date: 2026-03-24
origin: docs/brainstorms/2026-03-24-weakref-cache-architecture-brainstorm.md
---

# feat: WeakReference Cache + State Class Extraction

## Enhancement Summary

**Deepened on:** 2026-03-24
**Agents used:** kotlin-expert, kotlin-multiplatform, kotlin-coroutines, gradle-expert, architecture-strategist, performance-oracle, code-simplicity-reviewer, pattern-recognition-specialist

### Key Improvements from Deepening
1. **Source set correction:** `commons/jvmAndroid` not `jvmMain` — both KMP + Gradle agents confirmed
2. **Massive simplification:** Extract 5 State classes now (not 22) — only what Desktop needs today
3. **Per-feature repositories** instead of single `IAccountSettings` — matches existing commons pattern (`EphemeralChatRepository`, `PublicChatListRepository`)
4. **Fix ICacheProvider `Any?` returns directly** — `User`/`Note` already in commons, no new methods needed
5. **DecryptionCaches are pure Kotlin** — move as-is, no interfaces needed
6. **Performance fixes:** `AtomicInteger` size tracker, single-pass `cleanUp()`, debounced `findUsersStartingWith`
7. **Heap monitoring:** `MemoryMXBean` check every 30s, cleanup at >75% or every 5min
8. **2 phases not 4** — cache swap + note pinning

### Critical Issues Discovered
1. **Static `LocalCache.justConsumeMyOwnEvent()` calls** in State classes bypass injected cache param — must fix during extraction
2. **`findUsersStartingWith` becomes O(n) per keystroke** with no cap — needs debouncing
3. **`size()` is O(n)** on ConcurrentSkipListMap — add AtomicInteger tracker
4. **Note.replies unbounded growth** for pinned notes — pruning is NOT optional for long-running Desktop sessions

---

## Overview

Replace `BoundedLargeCache` (strong refs, 50k cap, arbitrary eviction) with Android's `LargeSoftCache` (WeakReference-based, GC-driven). Extract 5 critical note-pinning State classes from `amethyst/` to `commons/` so Desktop gets the same retention logic. All in existing PR #1905 on branch `feat/desktop-cache-v2`.

(see brainstorm: `docs/brainstorms/2026-03-24-weakref-cache-architecture-brainstorm.md`)

## Problem Statement

Vitor's feedback on PR #1905: `BoundedLargeCache` causes notes to "just disappear" because eviction is by lowest hex key order — ignoring whether notes are displayed, replied-to, or part of a thread. Android solved this with `LargeSoftCache<K, WeakReference<V>>` where GC respects the reference graph.

Desktop's `DesktopIAccount` has **zero State objects** — it stubs everything. The State classes that pin important notes (bookmarks, follow list, mute list, relay lists, metadata) are Android-only.

## Proposed Solution

Two-phase approach (simplified from original 4 phases):

1. **Phase 1: Cache swap** — Move `LargeSoftCache` to `commons/jvmAndroid`, fix `ICacheProvider` types, replace `BoundedLargeCache`, add cleanup loop
2. **Phase 2: Pin critical notes** — Extract 5 State classes to `commons/commonMain`, wire in `DesktopIAccount`

Remaining 17 State classes extracted incrementally as Desktop builds features that need them.

## Technical Approach

### Architecture

```
┌──────────────────────────────────────────────────────┐
│              LargeSoftCache<K, WeakRef<V>>            │
│              (commons/jvmAndroid/ — shared)           │
│                                                      │
│  notes: LargeSoftCache<HexKey, Note>                 │
│  users: LargeSoftCache<HexKey, User>                 │
│  addressables: LargeSoftCache<String, AddressableNote>│
└───────────────────────┬──────────────────────────────┘
                        │ WeakRef (GC-managed)
    ┌───────────────────▼───────────────────┐
    │         Note Reference Graph           │
    │  (strong refs: replies, reactions,     │
    │   zaps, boosts, replyTo, author)       │
    │                                        │
    │  Clusters survive together.            │
    │  GC collects entire cluster when       │
    │  no external strong ref remains.       │
    └───────────────────┬───────────────────┘
                        │ strong refs (pinning)
    ┌───────────────────▼───────────────────┐
    │      State Classes (commons/)          │
    │  (hold strong refs to important notes) │
    │                                        │
    │  BookmarkListState → bookmarkList note │
    │  Kind3FollowListState → contact list   │
    │  MuteListState → mute list note        │
    │  Nip65RelayListState → relay list      │
    │  UserMetadataState → metadata note     │
    │                                        │
    │  Live on Account (Android) or          │
    │  DesktopIAccount (Desktop) — both      │
    │  implement IAccount                    │
    └───────────────────────────────────────┘
```

### Implementation Phases

---

#### Phase 1: Cache Swap (~1 day)

**Goal:** Replace BoundedLargeCache with LargeSoftCache, fix ICacheProvider types, add cleanup.

##### 1a. Fix ICacheProvider return types (prerequisite)

**File:** `commons/src/commonMain/kotlin/.../model/cache/ICacheProvider.kt`

`User`, `Note`, `AddressableNote` all live in `commons/commonMain/` already. The `Any?` returns are a legacy artifact. Change directly:

```kotlin
interface ICacheProvider {
    // Change these from Any? to proper types
    fun getUserIfExists(pubkey: HexKey): User?        // was Any?
    fun getNoteIfExists(hexKey: HexKey): Note?         // was Any?
    fun getOrCreateUser(pubkey: HexKey): User?          // was Any?
    fun findUsersStartingWith(prefix: String, limit: Int = 50): List<User>  // was List<Any>

    // Already typed correctly — no change
    fun checkGetOrCreateNote(hexKey: HexKey): Note?
    fun getOrCreateAddressableNote(key: Address): AddressableNote
    fun justConsumeMyOwnEvent(event: Event): Boolean
    // ...
}
```

Callers that currently cast `as? User` just drop the cast. No breakage.

**Research Insight (kotlin-expert):** `User`/`Note` are already in commons — the `Any?` returns were from an earlier extraction. Safe to fix now.

##### 1b. Move LargeSoftCache to commons/jvmAndroid

**From:** `amethyst/src/main/java/.../model/LargeSoftCache.kt`
**To:** `commons/src/jvmAndroid/kotlin/.../model/cache/LargeSoftCache.kt`

**Research Insight (KMP + Gradle agents):** Must be `jvmAndroid`, NOT `jvmMain`. The `jvmAndroid` source set in commons already exists (line 85-90 of `commons/build.gradle.kts`). Both `jvmMain` and `androidMain` depend on it. Zero Gradle changes needed. `CacheOperations` from `quartz/jvmAndroid` is visible transitively.

**Changes:**
- Move file, update package to `com.vitorpamplona.amethyst.commons.model.cache`
- Update Android's `LocalCache.kt` import (only file that imports it)

**Performance improvements to LargeSoftCache:**

```kotlin
class LargeSoftCache<K : Any, V : Any> : CacheOperations<K, V> {
    private val cache = ConcurrentSkipListMap<K, WeakReference<V>>()

    // NEW: O(1) approximate size tracking (performance-oracle recommendation)
    private val _size = AtomicInteger(0)
    fun approximateSize(): Int = _size.get()

    fun put(key: K, value: V) {
        val prev = cache.put(key, WeakReference(value))
        if (prev == null) _size.incrementAndGet()
    }

    // IMPROVED: Single-pass cleanUp (performance-oracle: ~40% faster, zero alloc)
    fun cleanUp() {
        val iter = cache.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.get() == null) {
                iter.remove()
                _size.decrementAndGet()
            }
        }
    }
    // ... rest unchanged
}
```

##### 1c. Replace BoundedLargeCache on Desktop

**File:** `desktopApp/.../cache/DesktopLocalCache.kt`

```kotlin
// Before
val users = BoundedLargeCache<HexKey, User>(MAX_USERS)
val notes = BoundedLargeCache<HexKey, Note>(MAX_NOTES)
val addressableNotes = BoundedLargeCache<String, AddressableNote>(MAX_ADDRESSABLE)

// After
val users = LargeSoftCache<HexKey, User>()
val notes = LargeSoftCache<HexKey, Note>()
val addressableNotes = LargeSoftCache<String, AddressableNote>()
```

**Remove:** `BoundedLargeCache.kt`, `MAX_NOTES`/`MAX_USERS`/`MAX_ADDRESSABLE` constants.

**API differences:**
- `BoundedLargeCache.values()` → use `forEach` + collect
- `BoundedLargeCache.count(predicate)` → available via `CacheOperations`
- `userCount()`/`noteCount()` → use `approximateSize()` (O(1) vs O(n))

**Performance fix for `findUsersStartingWith`:**

```kotlin
// Before: O(n) per keystroke with no cap
// After: debounce at call site + yield during iteration
override fun findUsersStartingWith(prefix: String, limit: Int): List<User> {
    val results = mutableListOf<User>()
    users.forEach { _, user ->
        if (results.size >= limit) return@forEach
        if (user.anyNameStartsWith(prefix)) results.add(user)
    }
    return results
}
```

Call sites must debounce to 300ms. Acceptable because search already uses debounced state.

##### 1d. Add Desktop cleanup loop

**Research Insight (kotlin-coroutines):** Inline in coordinator, no abstraction needed. `Dispatchers.Default` for CPU-bound work. `Mutex.tryLock()` if heap trigger added. `yield()` every 1000 items.

```kotlin
// In Coordinator or Main.kt — inline, no CacheCleanupService needed
private val cleanupScope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t ->
        println("Cleanup failed, will retry: ${t.message}")
    }
)

private val memoryBean = ManagementFactory.getMemoryMXBean()
private var lastCleanupTime = 0L

val cleanupJob = cleanupScope.launch {
    delay(2.minutes) // Grace period for startup
    while (isActive) {
        delay(30.seconds) // Check frequently, clean infrequently
        val heapPct = memoryBean.heapMemoryUsage.let { it.used.toDouble() / it.max }
        val elapsed = System.currentTimeMillis() - lastCleanupTime

        if (heapPct > 0.75 || elapsed > 5.minutes.inWholeMilliseconds) {
            val ops = listOf(
                "cleanMemory" to { localCache.cleanMemory() },
                "cleanObservers" to { cleanObserversWithYield() },
                "pruneExpired" to { localCache.pruneExpiredEvents() },
                "pruneReplaceables" to { localCache.prunePastVersionsOfReplaceables() },
            )
            ops.forEach { (name, op) ->
                try { op() } catch (e: Exception) {
                    println("Cleanup $name failed: ${e.message}")
                }
            }
            lastCleanupTime = System.currentTimeMillis()
        }
    }
}

private suspend fun cleanObserversWithYield() {
    var count = 0
    localCache.notes.forEach { _, note ->
        note.clearFlow()
        if (++count % 1000 == 0) yield()
    }
}
```

##### Phase 1 Acceptance Criteria

- [ ] `ICacheProvider` methods return typed `User?`/`Note?` (not `Any?`)
- [ ] `LargeSoftCache` in `commons/src/jvmAndroid/kotlin/.../model/cache/`
- [ ] `LargeSoftCache` has `AtomicInteger` size tracker + single-pass `cleanUp()`
- [ ] Android's `LocalCache` imports from commons (one import change)
- [ ] Desktop's `DesktopLocalCache` uses `LargeSoftCache`
- [ ] `BoundedLargeCache.kt` deleted
- [ ] Desktop cleanup loop: periodic + heap-pressure triggered
- [ ] `findUsersStartingWith` debounced at call sites
- [ ] `./gradlew :commons:compileKotlinJvm :amethyst:compileDebugKotlin :desktopApp:compileKotlin` passes

---

#### Phase 2: Pin Critical Notes (~1-2 days)

**Goal:** Extract 5 essential State classes to commons so Desktop pins important notes.

##### Why only 5 (not 22)?

**Research Insight (code-simplicity-reviewer):** Desktop currently has zero State classes and zero UI for most of the 22 features. Only 5 State classes serve features Desktop has today:

| State Class | Why Desktop needs it now |
|-------------|------------------------|
| BookmarkListState | Bookmarks screen exists |
| Kind3FollowListState | Follow list drives home feed |
| Nip65RelayListState | Relay list drives subscriptions |
| MuteListState | Content filtering |
| UserMetadataState | Profile display |

The other 17 (GeohashList, ProxyRelay, IndexerRelay, etc.) pin notes for features Desktop hasn't built yet. Extract them when needed.

##### 2a. Per-feature repository interfaces (not IAccountSettings)

**Research Insight (pattern-recognition-specialist):** The existing commons State classes use **per-feature repository interfaces**, not a shared settings interface:

- `EphemeralChatRepository` — 2 methods: `ephemeralChatList()`, `updateEphemeralChatListTo()`
- `PublicChatListRepository` — 2 methods: `publicChatList()`, `updatePublicChatListTo()`

Follow the same pattern. Each State class gets a narrow repository interface:

```kotlin
// commons/commonMain — one per State class that uses settings
interface Kind3FollowListRepository {
    val backupContactList: ContactListEvent?
    fun updateContactListTo(event: ContactListEvent)
}

interface MuteListRepository {
    val backupMuteList: MuteListEvent?
    fun updateMuteList(event: MuteListEvent)
}

interface Nip65RelayListRepository {
    val backupNIP65RelayList: AdvertisedRelayListEvent?
    fun updateNIP65RelayList(event: AdvertisedRelayListEvent)
}
// UserMetadataState — audit for specific settings usage
```

Android's `AccountSettings` implements all 5 interfaces. Desktop implements with stubs initially (no persistence yet), returning null for backup events.

**Naming convention (pattern-recognition):** `I` prefix for broad abstractions (`IAccount`, `ICacheProvider`). No prefix for narrow feature-scoped interfaces (`Kind3FollowListRepository`, `MuteListRepository`).

##### 2b. Move DecryptionCaches as-is

**Research Insight (kotlin-expert):** DecryptionCaches are pure Kotlin wrappers around `PrivateTagArrayEventCache<T>` from `quartz/commonMain`. No interfaces needed, no Android deps:

```kotlin
// Already works in commons/commonMain — no changes needed
class MuteListDecryptionCache(val signer: NostrSigner) {
    val cachedPrivateLists = PrivateTagArrayEventCache<MuteListEvent>(signer)
}
```

Only `MuteListState` uses a DecryptionCache among the initial 5 State classes. Move `MuteListDecryptionCache` alongside it.

##### 2c. Fix static LocalCache calls

**Research Insight (pattern-recognition + architecture):** Several State classes call `LocalCache.justConsumeMyOwnEvent(event)` as a **static singleton call**, not through the injected `cache` parameter. Found in:
- `Kind3FollowListState` (line 168)
- `MuteListState` (line 147)
- `SearchRelayListState` (line 108)

During extraction, change to `cache.justConsumeMyOwnEvent(event)` — the method already exists on `ICacheProvider`.

##### 2d. Extraction order

1. **BookmarkListState** — no settings, no decryptionCache, simplest. Verify imports: change `amethyst.model.Note` → `amethyst.commons.model.Note`
2. **Nip65RelayListState** — needs `Nip65RelayListRepository` interface
3. **Kind3FollowListState** — needs `Kind3FollowListRepository`, fix static `LocalCache` call
4. **MuteListState** — needs `MuteListRepository` + move `MuteListDecryptionCache`, fix static call
5. **UserMetadataState** — audit settings usage, create repository interface

##### 2e. Wire State classes on DesktopIAccount

```kotlin
class DesktopIAccount(
    private val accountState: AccountState.LoggedIn,
    val localCache: DesktopLocalCache,
    // ...
) : IAccount {
    // Stub repositories — no persistence yet, return null for backups
    private val kind3Repo = object : Kind3FollowListRepository {
        override val backupContactList: ContactListEvent? = null
        override fun updateContactListTo(event: ContactListEvent) { /* no-op for now */ }
    }
    // ... similar stubs for other repos

    val bookmarkList = BookmarkListState(signer, localCache, scope)
    val kind3FollowList = Kind3FollowListState(signer, localCache, scope, kind3Repo)
    val nip65RelayList = Nip65RelayListState(signer, localCache, scope, nip65Repo)
    val muteList = MuteListState(signer, localCache, MuteListDecryptionCache(signer), scope, muteRepo)
    val userMetadata = UserMetadataState(signer, localCache, scope, metadataRepo)
}
```

##### Phase 2 Acceptance Criteria

- [ ] 5 State classes in `commons/commonMain/`
- [ ] Per-feature repository interfaces (not single IAccountSettings)
- [ ] `MuteListDecryptionCache` moved alongside `MuteListState`
- [ ] All static `LocalCache.` calls changed to `cache.` (injected)
- [ ] Android's `Account` imports State classes from commons (no behavior change)
- [ ] Desktop's `DesktopIAccount` creates 5 State objects with stub repos
- [ ] Pinned AddressableNotes survive GC on Desktop
- [ ] `@Stable`/`@Immutable` annotations compile on all targets
- [ ] `./gradlew spotlessApply` + all modules compile

---

## System-Wide Impact

### Interaction Graph

```
Event arrives from relay
  → Coordinator.consumeEvent()
    → DesktopLocalCache.consume()
      → notes.getOrCreate(id) [LargeSoftCache — creates WeakRef]
        → Note populated with event, author, relationships
          → Note.addReply/addReaction/addZap [strong refs to related notes]
            → eventStream.emit()
              → FeedViewModels receive update

State class created on DesktopIAccount
  → State.init { cache.getOrCreateAddressableNote(address) }
    → AddressableNote pinned via strong ref on State object
      → Note's reference graph (replies, reactions) also pinned transitively
        → Cluster survives GC

Periodic cleanup (every 5 min or heap > 75%)
  → LargeSoftCache.cleanUp() sweeps stale WeakRef entries (single-pass)
  → cleanObservers() clears unused NoteFlowSets (yield every 1000)
  → pruneExpiredEvents() removes NIP-40 expired notes
  → prunePastVersionsOfReplaceables() keeps only latest replaceable
```

### Error Propagation

- **getOrCreate race:** Two threads create same Note → `putIfAbsent` ensures one wins. Note constructor is pure (just stores `idHex`), no side effects. Acceptable.
- **GC during render:** Compose holds strong ref during composition. Cache eviction can't crash.
- **State class missing:** If a State class isn't created, that note type has no pin → GC can collect. Mitigated by creating all 5 critical ones at init.

### State Lifecycle Risks

- **Note.replies unbounded growth:** Pinned notes keep all replies alive via strong refs. For long-running Desktop sessions, this grows monotonically. `pruneRepliesAndReactions` is account-dependent — tracked as follow-up work (not deferred indefinitely).
- **NoteFlowSet leak:** `cleanObservers()` with `yield()` handles this.
- **`SharingStarted.Eagerly` on State classes:** All 5 State classes use `stateIn(scope, SharingStarted.Eagerly)`. Desktop has no process lifecycle to bound this. Consider `WhileSubscribed(5000)` for non-critical states when expanding beyond 5.
- **Replaceable event swap:** AddressableNote stays pinned, `.event` swaps. FlowSet emits. No orphaned refs.

### Performance Characteristics

| Operation | Before (BoundedLargeCache) | After (LargeSoftCache) |
|-----------|---------------------------|----------------------|
| `size()` | O(1) via AtomicInteger | O(1) via AtomicInteger (new) |
| `get(key)` | O(log n), strong ref | O(log n), WeakRef deref |
| `forEach` | O(n), no GC checks | O(n), GC check per entry |
| `cleanUp()` | N/A (evict by key order) | O(n) single-pass iterator |
| `findUsersStartingWith` | O(n) capped at 25k | O(n) uncapped — **debounce required** |
| Eviction | Arbitrary (by hex key) | GC-driven (respects refs) |
| Memory cap | Hard (50k/25k/10k) | None (GC-managed) |

## Acceptance Criteria

### Functional

- [ ] Desktop cache uses WeakReferences — no hard size limits
- [ ] Notes only disappear when nothing references them (not arbitrarily)
- [ ] Bookmarks, contact list, mute list, relay lists, metadata pinned on Desktop
- [ ] Navigation persistence maintained (existing behavior)
- [ ] Android behavior unchanged (same code, new import path)

### Non-Functional

- [ ] `BoundedLargeCache` deleted
- [ ] 5 critical State classes in commons
- [ ] Periodic + heap-triggered cleanup on Desktop
- [ ] `LargeSoftCache` shared from `commons/jvmAndroid/`
- [ ] `ICacheProvider` returns typed `User?`/`Note?`
- [ ] `./gradlew spotlessApply` + compile on all modules

## Dependencies & Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Static `LocalCache.` calls in State classes | High | High | Audit each class. Found in Kind3FollowList, MuteList, SearchRelay. Change to `cache.` |
| `findUsersStartingWith` O(n) jank | High | Medium | Debounce 300ms at call sites. Already debounced in search UI. |
| `size()` O(n) in logging paths | Medium | Low | `AtomicInteger` tracker on LargeSoftCache. Use `approximateSize()`. |
| Note.replies unbounded growth | Medium | Medium | Follow-up: implement non-account-dependent reply cap (e.g., 500 per note) |
| Import path changes break Android | Low | Medium | Only `LocalCache.kt` changes import. One-line diff. |
| BookmarkListState imports `amethyst.model.Note` (wrong package) | Low | Low | Change to `amethyst.commons.model.Note` during extraction |

## Future Work (tracked, not deferred indefinitely)

1. **Extract remaining 17 State classes** — as Desktop builds features that need them
2. **`pruneRepliesAndReactions`** — non-account-dependent reply/reaction cap (e.g., 500 per note) to bound memory for popular threads
3. **Desktop persistence** — implement real `Kind3FollowListRepository` etc. with disk-backed storage
4. **`SharingStarted.WhileSubscribed`** — for non-critical State classes when expanding beyond 5

## Success Metrics

- BoundedLargeCache eviction bugs → eliminated (WeakRef model)
- Desktop note retention → matches Android behavior for 5 critical types
- Android behavior → unchanged (regression-free)
- Desktop memory stability → heap monitoring confirms GC collects unpinned notes

## Sources & References

### Origin

- **Brainstorm:** [docs/brainstorms/2026-03-24-weakref-cache-architecture-brainstorm.md](docs/brainstorms/2026-03-24-weakref-cache-architecture-brainstorm.md) — Key decisions: LargeSoftCache shared, extract State classes, same pinning as Android, extensible for Desktop-specific pins later, one PR in existing #1905

### Internal References

- Prior clean-cache plan: `docs/plans/2026-03-22-feat-clean-cache-single-source-of-truth-plan.md`
- Cache architecture brainstorm: `docs/brainstorms/2026-03-17-desktop-cache-architecture-brainstorm.md`
- Android LargeSoftCache: `amethyst/src/main/java/.../model/LargeSoftCache.kt`
- Android LocalCache: `amethyst/src/main/java/.../model/LocalCache.kt`
- Android Account (State objects): `amethyst/src/main/java/.../model/Account.kt`
- Android MemoryTrimmingService: `amethyst/src/main/java/.../service/eventCache/MemoryTrimmingService.kt`
- Desktop DesktopLocalCache: `desktopApp/.../cache/DesktopLocalCache.kt`
- Desktop BoundedLargeCache: `desktopApp/.../cache/BoundedLargeCache.kt`
- Desktop DesktopIAccount: `desktopApp/.../model/DesktopIAccount.kt`
- ICacheProvider: `commons/.../model/cache/ICacheProvider.kt`
- CacheOperations: `quartz/src/jvmAndroid/.../utils/cache/CacheOperations.kt`
- Note model: `commons/.../model/Note.kt`
- Existing pattern: `EphemeralChatRepository` / `PublicChatListRepository` in commons

### Research Insights Applied

- **kotlin-expert:** DecryptionCaches are pure Kotlin (move as-is), fix `Any?` returns directly, normalize static LocalCache calls, only 3 files use `@Stable`/`@Immutable`
- **kotlin-multiplatform:** `commons/jvmAndroid` (not `jvmMain`), zero Gradle changes, `CacheOperations` visible transitively
- **kotlin-coroutines:** `SupervisorJob` + `Dispatchers.Default`, `Mutex.tryLock()` for heap trigger, `yield()` every 1000 items, 2-min startup grace period
- **gradle-expert:** Zero build.gradle.kts changes needed, no circular deps introduced
- **architecture-strategist:** Fix ICacheProvider types as Phase 0 prerequisite, `pruneRepliesAndReactions` is NOT optional for Desktop, consider `SharingStarted.WhileSubscribed` for non-critical states
- **performance-oracle:** `AtomicInteger` size tracker, single-pass `cleanUp()`, `findUsersStartingWith` O(n) jank needs debouncing, non-account-dependent reply cap needed
- **code-simplicity-reviewer:** 5 State classes not 22, per-feature repos not IAccountSettings, inline cleanup not CacheCleanupService, 2 phases not 4
- **pattern-recognition-specialist:** Per-feature repository interfaces match `EphemeralChatRepository` pattern, `I` prefix for broad abstractions only, static `LocalCache.` calls are extraction blockers

### Resolved Questions (from original plan)

1. **AccountSettings audit** → Resolved: use per-feature repository interfaces, not single IAccountSettings. Each State class gets a narrow 2-method interface.
2. **DecryptionCache complexity** → Resolved: pure Kotlin wrappers around quartz's `PrivateTagArrayEventCache`. Move as-is, no interfaces.
3. **ICacheProvider Any? methods** → Resolved: `User`/`Note` already in commons. Change return types directly. Callers drop casts.
4. **Account-dependent prune timing** → Resolved: follow-up work, not deferred. Implement non-account-dependent reply cap (500 per note) as next step.
