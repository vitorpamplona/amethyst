---
title: Desktop Profile — Phase 2 (Six Viewing Tabs)
type: feat
status: drafting
date: 2026-07-27
parent: docs/plans/2026-07-27-feat-desktop-profile-parity-plan.md
---

# Desktop Profile — Phase 2: Six Viewing Tabs

Sub-plan expanding Phase 2 of the [parent parity plan](../../docs/plans/2026-07-27-feat-desktop-profile-parity-plan.md).
Adds Followers, Following, Zaps received, Relays, Bookmarks, Mutual tabs to
`UserProfileScreen.kt`, mirroring the verified Android ViewModels/filters.

> **Depends on `main` only** — this phase does NOT need moderation-safety. It does need
> the six tab filters to respect the unified hidden-set once Phase 3 lands; until then they
> use `DesktopIAccount.isHidden` (returns false today), so no user is wrongly hidden.

## Established Desktop tab pattern (reuse — no new infra)

**Key finding:** the 5 existing tabs do **not** use Android's `observeEvents`. Each is a
`DesktopFeedViewModel(filter, localCache)` where the filter is an `AdditiveFeedFilter` that
**scans the cache**, rendered via `feedState.feedContent.collectAsState()`, with relays fed by a
compose-scoped `rememberSubscription { SubscriptionConfig(...) }`. New tabs follow this pattern.

```kotlin
// SubscriptionUtils.kt:67 — the compose-scoped subscription primitive
@Composable fun rememberSubscription(vararg keys: Any?, relayManager: RelayConnectionManager,
    config: () -> SubscriptionConfig?): SubscriptionHandle?
// SubscriptionConfig(subId, filters: List<Filter>, relays: Set<NormalizedRelayUrl>, onEvent, onEose, onClosed)

// UserProfileScreen.kt tab wiring (~L229–490):
val vm = remember(pubKeyHex) { DesktopFeedViewModel(DesktopProfileFeedFilter(pubKeyHex, localCache), localCache) }
val feed by vm.feedState.feedContent.collectAsState()   // FeedState.Loaded → items
```

## Prerequisite infra (do first)

### A. Followers/Zaps event access — prefer the cache-scan filter over new infra
Followers (kind 3) and Zaps (kind 9735/8333) filters scan `localCache.notes`/`users` like the
existing tabs, driven by a `rememberSubscription` that fetches the events (`Filter(kinds=..., tags=
mapOf("p" to listOf(pubKeyHex)))` → `subscriptionsCoordinator.consumeEvent`).
- **Only if the scan proves insufficient**, add Android-parity
  `DesktopLocalCache.observeEvents<T>(filter): Flow<List<T>>` — but that needs a new inverted
  **filter index** (Android's `observables: FilterIndex`); Desktop's `DesktopCacheEventStream` only
  emits bundles today. Treat this as a fallback, not the default (avoids a real infra lift).
- Following/Bookmarks/Relays/Mutual read pinned addressable notes / iterate the cache map /
  `user.relayState().flow()` — no streaming accessor needed.

### B. Wire `PrivateZapCache(signer)` into `DesktopIAccount` (one-line stub replacement)
`DesktopIAccount.kt:163-168` has a null-returning `IPrivateZapsDecryptionCache` stub. Replace:
```kotlin
override val privateZapsDecryptionCache: IPrivateZapsDecryptionCache = PrivateZapCache(signer)
```
`PrivateZapCache(signer: NostrSigner)` (quartz `nip57Zaps/PrivateZapCache.kt`) is an LRU(1000) that
lazily decrypts per event via `signer.decryptZapEvent`. Gate on `isWriteable()`; decrypt
**own-profile only** (Android `UserProfileZapsViewModel:71-85`: `if (user.pubkeyHex == account.pubKey)`
decrypt else fall back to `zapRequest.pubKey`). Lazy per row — a NIP-46 bunker does one round-trip
per zap. Kinds: `LnZapEvent.KIND=9735`, `OnchainZapEvent.KIND=8333`.

## Per-tab implementation

Each tab = a desktop-local filter (extends the shared `AdditiveFeedFilter` base, over
`DesktopLocalCache`) + a row composable + a header + loading/empty/populated states
(Followers & Zaps add a 4th: **partial/cache-limited**).

| Tab | Android class (verified) | Data logic | New filter | Row composable |
|---|---|---|---|---|
| Followers | `UserProfileFollowersUserFeedViewModel` | `observeEvents(Filter(kind 3, p=user))` → unique authors, `!isHidden`, sort by isFollowing then hex | `DesktopFollowersFeedFilter` | reuse user-row ⟨agent a1d3686896f7c2db5⟩ |
| Following | `UserProfileFollowsUserFeedViewModel` | `getOrCreateAddressableNote(ContactListEvent.createAddress(user))` → `verifiedFollowKeySet()` → load users | `DesktopFollowingFeedFilter` | same user-row |
| Zaps | `UserProfileZapsViewModel` | `observeEvents(Filter(kinds 9735+onchain, p=user))` → `mapRequest` (decrypt if self) → `sumAmountsByUser`, sort by amount desc | `DesktopZapsReceivedFeedFilter` | `ProfileZapRow` (new) |
| Relays | `RelayFeedViewModel` | `user.nip65RelayListNote.flow()` write/read + `user.dmRelayListNote.flow()` (10050) + `user.relayState().flow()` counters | `DesktopRelaysFeedFilter` | reuse RelaySettings row ⟨agent a1d3686896f7c2db5⟩ |
| Bookmarks | `UserProfileBookmarksFeedFilter` | `getOrCreateAddressableNote(BookmarkListEvent.createBookmarkAddress(user))` → `publicBookmarks()` (+ legacy 30001) → resolve notes | `DesktopBookmarksFeedFilter` | `FeedNoteCard` |
| Mutual | `UserProfileMutualFeedFilter` | iterate `notes`+`addressableNotes`; author==`userProfile()` AND `event.isTaggedUser(user)`; limit 200 | `DesktopMutualFeedFilter` | `FeedNoteCard` (notes I authored tagging them — **not** a user list) |

### Reuse-vs-build (verified)
| Need | Decision | Signature / location |
|---|---|---|
| User row (Followers/Following) | **REUSE** | `UserSearchCard(user, onClick, modifier, badge)` — `commons/.../ui/components/UserSearchCard.kt` (avatar+name+nip05; has a `badge` slot) |
| Relay row (Relays) | **BUILD** `RelayRowCard` | none exists on Desktop (`LocalRelaySettingsScreen` only has a `StatRow`); model on UserSearchCard: url + read/write icons + counter |
| Zapper row (Zaps) | **BUILD** `ProfileZapRow` | avatar + name + sats amount |
| Note row (Bookmarks/Mutual) | **REUSE** | `FeedNoteCard(note, relayManager, localCache, account, …, forceReveal)` — `desktopApp/.../ui/FeedScreen.kt:186`; already wrapped by `SpamCheckedNoteRender` (`ui/note/SpamCheckedNoteRender.kt:59`) |
| Tab header + count | **REUSE** | `PrimaryTabRow` + `Tab { Text("Followers (${count})") }` — pattern in `UserProfileScreen.kt:849` |
| Loading / empty states | **REUSE** | `LoadingState(message)` + `EmptyState(title, description?, onRefresh?, refreshLabel?)` — `commons/.../ui/components/LoadingState.kt` |
| LazyColumn + states | **REUSE** | `items(list, key={it.id})`; `when { isLoading && !eose → LoadingState; empty && eose → EmptyState; else → LazyColumn }` (pattern in `BookmarksScreen.kt:290-330`) |

## Per-tab states
- **Loading vs empty** distinct on every tab.
- **Followers/Following count is cache-limited** → label "N found" OR use NIP-45 `count`. Never a
  false exact total.
- **10k lists** → LazyColumn windowing.
- **Bookmarks/Mutual** notes not yet cached → loading placeholder, not blank.
- **Zaps** on a third-party profile → label "public zaps only".

## Wiring into `UserProfileScreen.kt`
- Add 6 entries to the `PrimaryTabRow` + `when(selectedTab)` block (existing 5-tab pattern at
  `L849` header / `L229–490` subscriptions / feed render).
- Each tab = `DesktopFeedViewModel(<newFilter>, localCache)` + a `rememberSubscription` keyed on
  `(connectedRelays, pubKeyHex, retryTrigger)` producing a `SubscriptionConfig` (subId via
  `generateSubId`, `onEvent → subscriptionsCoordinator.consumeEvent`). Subscriptions are already
  lifecycle-scoped by `rememberSubscription`.
- Do NOT use `rememberSubscription` inside any popup/dialog (broken in AlertDialog).

## Acceptance criteria
- [ ] Followers + Zaps populate via cache-scan filter + `rememberSubscription` (or `observeEvents` fallback if scan insufficient).
- [ ] `PrivateZapCache(signer)` wired (stub replaced); own-profile private zaps summed; other-profile public-only + labeled.
- [ ] All six tabs populated with correct headers; counts honest (labeled partial or NIP-45).
- [ ] Distinct loading/empty states; large lists scroll windowed.
- [ ] Tab subscriptions lifecycle-scoped (no leaks).
- [ ] Filters consult the unified hidden-set (post-Phase-3) — placeholder respects `isHidden` now.
- [ ] spotless clean; `:commons:compileKotlinJvm` + `:desktopApp:compileKotlin`; new filter unit tests green.

## Open questions
1. Ship Zaps as a fast-follow (parent Open Q #5)? It carries the decryption wiring + `ProfileZapRow`.
2. Followers count: label-as-partial vs NIP-45 `count` query — which for v1?
3. Can the cache-scan filter cover Followers/Zaps, or is `observeEvents` (new filter index) actually needed?

## Sources
Parent plan + verified Android tab classes. Desktop signatures verified: `UserSearchCard`,
`FeedNoteCard`/`SpamCheckedNoteRender`, `LoadingState`/`EmptyState`, `rememberSubscription`/
`SubscriptionConfig`, `DesktopFeedViewModel`, `PrivateZapCache(signer)`, `DesktopLocalCache` (no
`observeEvents`).
