# Account StateFlow Catalog

`Account.kt` exposes dozens of `StateFlow` properties that mirror different facets of the current user. This is a map from flow → Nostr kind → model package.

(Flow names are exact as of the current `Account.kt`; if a flow has been renamed, grep `Account.kt` for the old name.)

## Identity & Contacts

| Flow | Kind(s) | Source | Model package |
|------|---------|--------|---------------|
| `userProfile().liveMetadata` | 0 MetadataEvent | relay | `model/nip01UserMetadata/` |
| `followListFlow` | 3 ContactListEvent | relay | `model/nip02FollowLists/` |
| `followersFlow` | derived | LocalCache scan | — |
| `muteListFlow` | 10000 NIP-51 | relay | `model/nip51Lists/` |
| `blockListFlow` | 10000 list variant | relay | `model/nip51Lists/` |

## Relays & Connectivity

| Flow | Kind | Package |
|------|------|---------|
| `relayListFlow` | 10002 RelayList (NIP-65) | `model/nip65RelayList/` |
| `dmRelayListFlow` | 10050 | `model/nip65RelayList/` |
| `searchRelayListFlow` | 10007 | `model/nip65RelayList/` |
| `nip86RelayListFlow` | NIP-86 relay management | `model/nip86RelayManagement/` |
| `proxyFlow`, `torStateFlow` | local preferences | `model/torState/`, `AccountSyncedSettings` |

## Content Lists

| Flow | Kind | Package |
|------|------|---------|
| `bookmarkListFlow` | 10003 | `model/nip51Lists/` |
| `privateBookmarksFlow` | encrypted list | `model/nip51Lists/` |
| `topNavFeedsFlow` | custom | `model/topNavFeeds/` |
| `customEmojisFlow` | 10030 NIP-30 | `model/nip30CustomEmojis/` |
| `marmotGroupsFlow` | NIP-29 (marmot variant) | `model/marmot/` |
| `nip72CommunitiesFlow` | 34550 (NIP-72) | `model/nip72Communities/` |
| `nip64ChessFlow` | NIP-64 chess games | `model/nip64Chess/` |

## Messaging

| Flow | Kind | Package |
|------|------|---------|
| `dmInboxFlow` | 14 / 1059 (NIP-17 / gift-wrap) | `model/nip17Dms/` |
| `nwcSettingsFlow` | NIP-47 wallet connect | `model/nip47WalletConnect/` |
| `paymentTargetsFlow` | NIP-A3 | `model/nipA3PaymentTargets/` |
| `blossomServersFlow` | NIP-B7 blossom | `model/nipB7Blossom/` |

## Settings & UI

| Flow | Source | Package |
|------|--------|---------|
| `uiSettingsFlow` | local | `model/UiSettings.kt`, `UiSettingsFlow.kt` |
| `antiSpamFilter` | local | `model/AntiSpamFilter.kt` |
| `privacyOptionsFlow` | local | `model/privacyOptions/` |
| `trustedAssertionsFlow` | derived | `model/trustedAssertions/` |
| `defaultZapAmountsFlow`, `theme`, `language` | local preferences | `AccountSettings.kt`, `AccountSyncedSettings.kt` |

## Advanced / Derived

| Flow | Purpose | Package |
|------|---------|---------|
| `accountsCacheFlow` | multi-account switcher | `model/accountsCache/` |
| `algoFeedsFlow` | custom algorithmic feeds | `model/algoFeeds/` |
| `vanishFlow` | NIP-62 account vanish requests | `model/nip62Vanish/` |
| `nip78AppSpecificFlow` | NIP-78 app-specific data | `model/nip78AppSpecific/` |
| `serverListFlow` | media/upload servers | `model/serverList/` |

## Publishing Mutations

Every flow has a corresponding mutation method on `Account` that:

1. Constructs the updated event using a `TagArrayBuilder`.
2. Signs through the injected `NostrSigner` (see `auth-signers` skill).
3. Publishes to the appropriate relay set.
4. Updates the local StateFlow *before* relay round-trip (optimistic).
5. Rolls back / reconciles on failure.

Examples of mutation methods (names may vary slightly in current code):
- `follow(pubKey)` / `unfollow(pubKey)`
- `addBookmark(noteId)` / `removeBookmark(noteId)`
- `mute(pubKey)` / `unmute(pubKey)`
- `updateRelayList(...)`, `updateDmRelayList(...)`
- `sendPost(...)`, `sendReaction(...)`, `sendZap(...)`

## When a Flow Doesn't Exist Yet

If you're adding a new NIP that's user-scoped, follow the pattern:

1. Create `model/nipXX…/` with an optional `ExtState`/builder class.
2. Add `private val _xFlow = MutableStateFlow(initial)` + `val xFlow: StateFlow<T> = _xFlow.asStateFlow()` to `Account`.
3. Wire the relay subscription (see `relay-client` skill).
4. Add the mutation method that builds, signs, and publishes.
5. Update persistence if the setting is local-only (`AccountSettings.kt`).
