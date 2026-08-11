# Shared Metadata Loading — Upstream Reconciliation Plan
*Redo the commons extraction of the per-user metadata finder and per-note event finder on top of current `upstream/main`, abandoning the stale rebase of `worktree-plan-shared-metadata-loading` (`682c6000f5`).*

## 0. Executive summary of what changed under us

Upstream re-architected the same subsystem but **also completed the model-sharing prerequisite we depended on**:

- `amethyst.model.User`, `UserContext`, `Note` are now **typealiases** to `commons.model.*` (`amethyst/model/User.kt`). `SincePerRelayMap`, `MutableTime`, `EOSERelayList` are typealiases to `commons.relays.*` (`amethyst/service/relays/EOSE.kt`). Finder bodies are already type-compatible with commonMain.
- `LocalCache` is an `object` implementing `commons.model.cache.ICacheProvider` (exposes `getUserIfExists`, `getNoteIfExists`, `getOrCreateUser`, `relayHints`, …). The finders call exactly these.
- **Two new cross-cutting mechanisms**:
  1. `AccountScopedQuery { val account: amethyst.model.Account }` — implemented by `UserFinderQueryState`/`EventFinderQueryState` so base managers can attribute subscriptions to an account.
  2. `ExplainedFilter`/`SubPurpose` (already in commons) — every emitted filter is tagged with its purpose.

**Most important finding:** the base **commons** managers never read `.account`. Attribution lives in *amethyst-side* managers (`PerUserEoseManager`, `PerUniqueIdEoseManager`, …) and each reads exactly `(key as? AccountScopedQuery)?.account?.userProfile()?.pubkeyHex` — **only a pubkey hex**. AND the four finder sub-assemblers extend the **commons** base managers, not the amethyst attribution managers — they attribute *inline* by computing `soleAccountPubKey` and passing `accountPubKeys` into their `ExplainedFilter`s. So the finders do not depend on the amethyst attribution managers; they need only a pubkey.

## A. Map of upstream's current subsystem

### User side — `amethyst/.../reqCommand/user/`
- `UserFinderFilterAssembler.kt` — `UserFinderQueryState(user, override val account: Account) : AccountScopedQuery`; groups `UserOutboxFinderSubAssembler`, `UserWatcherSubAssembler`, `UserReportsSubAssembler`, `UserCardsSubAssembler`.
- `UserFinderFilterAssemblerSubscription.kt` — composable entry (`(user, accountViewModel)`) + `UserFinderByParentFilterAssemblerSubscription`; uses `AccountViewModel.account`, `dataSources().userFinder`, commons `LifecycleAwareKeyDataSourceSubscription`.
- `UserObservers.kt` — `observeUser*`.
- `loaders/UserOutboxFinderSubAssembler.kt` — extends commons `BaseEoseManager`; reads `it.account` → `pickRelaysToLoadUsers(...)`; emits `ExplainedFilter(purpose = RELAY_LISTS, accountPubKeys = listOfNotNull(soleAccountPubKey))`.
- `watchers/UserWatcherSubAssembler.kt` — commons `BaseEoseManager`; reads `account.indexerRelayList.flow.value`; calls `filterUserMetadataForKey(...)`.
- `watchers/FilterUserMetadataForKey.kt` — emits `ExplainedFilter(PROFILE_METADATA, …)`; reads `LocalCache.relayHints.hintsForKey(...)` **statically** (the one non-injected cache ref).
- `watchers/UserReportsSubAssembler.kt` — commons `SingleSubEoseManager`; reads `account.declaredFollowsPerOutboxRelay.value`, `account.userProfile().pubkeyHex`.
- `watchers/UserCardsSubAssembler.kt` — commons `SingleSubEoseManager`; reads `account.homeRelays.flow.value`, `account.trustProviderList.liveUserRankProvider`, `…liveUserFollowerCount`, `account.userProfile().pubkeyHex`; emits `filterContactCardsToTargetKeysFromTrustedAccountsInTheRelay(...)` (already in commons `assemblers/ContactCardFilters.kt`).

### Event side — `amethyst/.../reqCommand/event/`
- `EventFinderFilterAssembler.kt` — `EventFinderQueryState(note, override val account: Account) : AccountScopedQuery`; groups `NoteEventLoaderSubAssembler`, `EventWatcherSubAssembler`, `AddressableAuthorRelayLoaderSubAssembler(cache, ::allKeys, userFinder)`.
- `EventFinderFilterAssemblerSubscription.kt` — reads `accountViewModel.account`, `dataSources().eventFinder`.
- `EventObservers.kt` — `observeNote*` (one UI-only spot reads `accountViewModel.account.userProfile().pubkeyHex` for a moderator check).
- `loaders/NoteEventLoaderSubAssembler.kt`; `loaders/FilterMissingEvents.kt` (reads `key.account.followPlusAllMineWithSearch.flow.value`, `key.account.searchRelayList.flow.value`; `SubPurpose.REFERENCED_EVENTS`); `loaders/FilterMissingAddressables.kt` (`REFERENCED_EVENTS`).
- `loaders/AddressableAuthorRelayLoaderSubAssembler.kt` — constructs `UserFinderQueryState(author, key.account)`.
- `watchers/EventWatcherSubAssembler.kt` — commons `SingleSubEoseManager`; reads `it.account.userProfile().pubkeyHex` (attribution only); calls `filterRepliesAndReactionsToNotes/Addresses(...)`.
- `watchers/FilterRepliesAndReactionsToNotes.kt`, `FilterRepliesAndReactionsToAddresses.kt` — emit `ExplainedFilter(ENGAGEMENT, …)`.

### Base managers and attribution
- commons `BaseEoseManager`, `PerKeyEoseManager`, `SingleSubEoseManager` — **account-agnostic**.
- amethyst `PerUserEoseManager`, `PerUniqueIdEoseManager`, `PerUserAndFollowListEoseManager`, `SingleSubNoEoseCacheEoseManager` — do `f.attributedTo(pk)` where `pk = (key as? AccountScopedQuery).account.userProfile().pubkeyHex`. The finder sub-assemblers do NOT use these; they attribute inline.
- `ExplainedFilter.attributedTo(accountPubKey: HexKey)`; `SubPurpose`/`SubPurposeGroup` — commonMain.

## B. Account-seam decision — CHOSEN

**Keep `UserFinderAccount` as the narrow commons seam, carrying the attribution pubkey via `userFinderPubkeyHex`. Do NOT move `AccountScopedQuery` to commons. Drop `AccountScopedQuery` from the two finder query states.**

Justification (grounded in code):
1. Attribution reduces to `.userProfile().pubkeyHex` — a `HexKey`, already on `UserFinderAccount.userFinderPubkeyHex`.
2. The loaders' account reads are all snapshot relay-hint / follow-graph getters — exactly the `UserFinderAccount` surface (+2 additions).
3. `AccountScopedQuery` is declared over `amethyst.model.Account` and implemented by ~66 amethyst query states — can't move without dragging `Account`.
4. The finder query states never flow through the amethyst attribution managers, so they don't need `AccountScopedQuery` at all → dropping it removes the collision.

Seam shape (commons `UserFinderAccount`), = our old branch + 2 additions:
```kotlin
interface UserFinderAccount {
    val userFinderPubkeyHex: HexKey            // attribution pubkey → ExplainedFilter.accountPubKeys
    fun indexRelays(): Set<NormalizedRelayUrl>
    fun outboxHomeRelays(): Set<NormalizedRelayUrl>
    fun searchRelays(): Set<NormalizedRelayUrl>
    fun followPlusAllMineWithSearchRelays(): Set<NormalizedRelayUrl>
    fun commonRelays(): Set<NormalizedRelayUrl>
    fun cardHomeRelays(): Set<NormalizedRelayUrl>
    fun trustProvider(): ServiceProviderTag?
    fun followerCountProvider(): ServiceProviderTag?   // NEW (UserCards reads liveUserFollowerCount)
    fun declaredFollowsByOutboxRelay(): Map<NormalizedRelayUrl, Set<HexKey>>
}
```
`EventFinderQueryState` reuses `UserFinderAccount` (needs only follow+search relays + pubkey). Attribution: `soleAccountPubKey = keys.map { it.account.userFinderPubkeyHex }.singleOrNull()` → `accountPubKeys = listOfNotNull(soleAccountPubKey)`.

## C. SubPurpose mapping (preserve upstream tags across the move)

| Moved helper | SubPurpose |
|---|---|
| `FilterUserMetadataForKey` (kind 0 bundle) | `PROFILE_METADATA` (runsInBackground) |
| `UserOutboxFinderSubAssembler` | `RELAY_LISTS` |
| `UserCardsSubAssembler` → `ContactCardFilters` (already commons) | unchanged |
| `UserReportsSubAssembler` → `filterReportsToKeysFromTrusted` | `MODERATION` |
| `EventWatcherSubAssembler` → replies/reactions | `ENGAGEMENT` |
| `NoteEventLoader` → `FilterMissingEvents/Addressables` | `REFERENCED_EVENTS` |

`ExplainedFilter`/`SubPurpose`/`attributedTo`/`ContactCardFilters` are already commonMain + iOS-pure. Only change: `accountPubKeys` sourced from `userFinderPubkeyHex`.

## D. Portable-unchanged vs must-rework

**Unchanged (body identical after package move + seam swap):** the filter builders (`FilterUserMetadataForKey`, `FilterReportsToKey`, `FilterMissingEvents/Addressables`, `FilterRepliesAndReactionsToNotes/Addresses`), `observeUser*`/`observeNote*`, `UserFinderAccount` + CompositionLocals, `pickRelaysToLoadUsers` inner overload.

**Must rework:**
1. **Move `EOSEAccountFast<T>`** (`amethyst/service/relays/EOSE.kt`) to commons `relays/` + amethyst typealias — prereq for the loaders. (Purity risk: verify no `System.currentTimeMillis`/`Thread.sleep`.)
2. **`LocalCache.relayHints` static ref** in `FilterUserMetadataForKey` → injected `cache.relayHints` (add `val relayHints: HintIndexer` to `ICacheProvider`; LocalCache already has it).
3. **Account-seam swap** across the four user sub-assemblers + event loaders/watchers (getter-for-flow mapping listed in §B).
4. **`pickRelaysToLoadUsers`** — feed commons inner overload from `UserFinderAccount` getters (amethyst keeps a thin `Account`→relay-set wrapper).
5. **Drop `AccountScopedQuery`** from the two finder query states (verified no amethyst attribution manager consumes them).
6. `AddressableAuthorRelayLoaderSubAssembler` — `UserFinderQueryState(author, key.account)` now takes `UserFinderAccount`. Clean.

**Desktop wiring (Phase 3/3b old plan) — unaffected:** `observeUser*`, `EventFinderFilterAssemblerSubscription(note)`, Locals, DM/search/notifications adoption, fast index-relay warm-up. `DesktopIAccount` adds `followerCountProvider() = null` (already degrades trust/declaredFollows).

## E. Phase / commit sequence (fresh branch `feat/shared-metadata-loading-v2` off `upstream/main`)

Cherry-pick *content*, not commits. Gates after each commons/amethyst commit: `:commons:verifyKmpPurity`, `:amethyst:compilePlayDebugKotlin`, `:commons:compileKotlinJvm` + `:desktopApp:compileKotlinJvm`.

- **Phase 0 — prereqs:** (0a) move `EOSEAccountFast` → commons + typealias; (0b) add `relayHints` to `ICacheProvider`.
- **Phase 1 — seam:** (1a) add commons `UserFinderAccount` (+`followerCountProvider()`); (1b) `Account implements UserFinderAccount`.
- **Phase 2 — user finder → commons:** (2a) filter builders + `pickRelaysToLoadUsers` inner; (2b) the 4 sub-assemblers; (2c) `UserFinderFilterAssembler`+`UserFinderQueryState` (drop `AccountScopedQuery`) + amethyst typealias shim + commons `UserFinderSubscription`/Locals; keep composable subscription in amethyst delegating.
- **Phase 3 — event finder → commons:** (3a) filter builders; (3b) loaders/watchers + addressable bridge; (3c) assembler+state (drop `AccountScopedQuery`) + shim + `LocalEventFinder` + `observeNote*` to commons.
- **Phase 4 — Desktop wiring:** (4a) `DesktopIAccount implements UserFinderAccount`; (4b) provide Locals in `Main.kt`, wire DM/search/notifications + warm-up onto `observeUser*`/`EventFinderFilterAssemblerSubscription`.
- **Phase 5 — cleanup & tests:** delete dead originals; run `ExplainedFilterTest` + finder tests + full `:commons:check`.

### Risk callouts
- `EOSEAccountFast` purity is the likeliest `verifyKmpPurity` tripwire.
- Preserve inline `soleAccountPubKey` attribution so "Active Relay Subscriptions" still files single-account REQs correctly (and shows "not attributed" when accounts pool relays — don't regress to per-account splitting).
- Do NOT re-introduce `AccountScopedQuery` on the two finder states; if a future upstream manager consumes them, add a thin amethyst adapter instead of widening the commons seam.
