# Account State-Object Catalog

`Account.kt` composes ~50 **feature state objects** (not raw StateFlow
properties). Each object pins its backing addressable note in `LocalCache`,
exposes `val flow: StateFlow<…>` (decrypted + backup-merged + `stateIn`), and
offers suspend mutation helpers that return signed events. Consumers read
`account.<property>.flow`.

(Property and class names are exact as of the current `Account.kt`; if one has
been renamed, grep `Account.kt` for the class name.)

## Identity & Contacts

| Account property | State class | Kind | Package |
|------------------|-------------|------|---------|
| `userMetadata` | `UserMetadataState` | 0 | `amethyst/.../model/nip01UserMetadata/` |
| `kind3FollowList` | `Kind3FollowListState` | 3 | `model/nip02FollowLists/` |
| `muteList` (+ `muteListDecryptionCache`) | `MuteListState` | 10000 | `model/nip51Lists/muteList/` |
| `blockPeopleList`, `peopleLists` | `BlockPeopleListState`, `PeopleListsState` | NIP-51 people sets | `model/nip51Lists/peopleList/` |
| `followLists` | `FollowListsState` | NIP-51 follow sets | `model/nip51Lists/peopleList/` |
| `hiddenUsers` | `HiddenUsersState` — derived from `muteList.flow` + `blockPeopleList.flow` | — | `model/nip51Lists/` |
| `allFollows` | `MergedFollowListsState` — merges kind3 + people/follow/hashtag/geohash/community lists | — | `model/serverList/` |

## Relay Lists

| Account property | State class | Kind | Package |
|------------------|-------------|------|---------|
| `nip65RelayList` | `Nip65RelayListState` | 10002 | `model/nip65RelayList/` |
| `dmRelayList` | `DmRelayListState` | 10050 | `model/nip17Dms/` |
| `searchRelayList` | `SearchRelayListState` | 10007 | `model/nip51Lists/searchRelays/` |
| `blockedRelayList` | `BlockedRelayListState` | 10006 | `model/nip51Lists/blockedRelays/` |
| `localRelayList` | `LocalRelayListState` | local | `model/localRelays/` |
| `privateStorageRelayList` | `PrivateStorageRelayListState` | private storage | `model/edits/` |
| `keyPackageRelayList`, `trustedRelayList`, `proxyRelayList`, `broadcastRelayList`, `indexerRelayList`, `relayFeedsList` | per-feature `…RelayListState` classes, each with a `DecryptionCache` sibling | custom relay sets | `model/nip51Lists/…` |

Derived relay views (merge several of the above): `homeRelays`
(`AccountHomeRelayState`), `outboxRelays`, `dmRelays`, `notificationRelays`,
`trustedRelays`, `followPlusAllMineWithIndex`, `followPlusAllMineWithSearch`,
`defaultGlobalRelays`.

## Content Lists

| Account property | State class | Kind | Package |
|------------------|-------------|------|---------|
| `bookmarkState` (and legacy `oldBookmarkState`) | `BookmarkListState` | 10003 | `model/nip51Lists/` |
| `labeledBookmarkLists` | `LabeledBookmarkListsState` | NIP-51 bookmark sets | `model/nip51Lists/labeledBookmarkLists/` |
| `pinState` | `PinListState` | NIP-51 | `model/nip51Lists/` |
| `interestSets` | `InterestSetsState` | NIP-51 interest sets | `model/nip51Lists/interestSets/` |
| `hashtagList` / `geohashList` | `HashtagListState` / `GeohashListState` | NIP-51 | `model/nip51Lists/hashtagLists/`, `…/geohashLists/` |
| `communityList` | `CommunityListState` | NIP-72 communities | `model/nip72Communities/` |
| `favoriteAlgoFeedsList` | `FavoriteAlgoFeedsListState` | NIP-51 | `model/nip51Lists/` |
| `emoji`, `ownedEmojiPacks` | `EmojiPackState`, `OwnedEmojiPacksState` | 10030 | `commons/.../commons/model/nip30CustomEmojis/` |
| `publicChatList` | `PublicChatListState` | NIP-28 | `commons/.../commons/model/nip28PublicChats/` |
| `ephemeralChatList` | `EphemeralChatListState` | ephemeral chats | `commons/.../commons/model/emphChat/` |
| `blossomServers` | `BlossomServerListState` | Blossom (BUD) | `model/nipB7Blossom/` |

## Other Feature State

| Account property | State class | Purpose | Package |
|------------------|-------------|---------|---------|
| `vanish` | `VanishRequestsState` | NIP-62 vanish requests | `model/nip62Vanish/` |
| `appSpecific` | `AppSpecificState` | NIP-78 app data | `model/nip78AppSpecific/` |
| `otsState` | `OtsState` | NIP-03 OpenTimestamps | `model/nip03Timestamp/` |
| `live*FollowListsPerRelay` | `OutboxLoaderState(...).flow` — already a flow | per-feed outbox routing | `model/topNavFeeds/` |
| `privateDMDecryptionCache`, `draftsDecryptionCache` | `PrivateDMCache`, `DraftEventCache` | NIP-44 decryption caches | — |

Note the migration direction: newer/extracted state classes live in
`commons/src/commonMain/.../commons/model/`, the rest still in
`amethyst/src/main/java/.../model/`. Check both when looking for one.

## Publishing Mutations

State objects' mutation helpers (e.g. `MuteListState.hideUser(pubkey)`,
`BookmarkListState` add/remove) **build and sign** the updated replaceable
event via the quartz event class (`XEvent.add / remove / create`) and return
it. The caller (usually a method on `Account`) is responsible for sending it
through the client. Decryption results are cached in the paired
`…DecryptionCache` so re-renders don't re-decrypt.

## When a State Object Doesn't Exist Yet

If you're adding a new NIP that's user-scoped, follow the pattern (full recipe
in `SKILL.md`):

1. Create `model/nipXX…/XState.kt` modeled on `MuteListState` (encrypted) or
   `BookmarkListState` (plain).
2. Pin the note with `cache.getOrCreateAddressableNote(...)`, expose
   `val flow: StateFlow<…>` via `stateIn(scope, Eagerly, default)`.
3. Instantiate it in `Account.kt` (plus a `DecryptionCache` sibling if
   private), and wire the relay subscription (see `relay-client` skill).
4. Add mutation helpers that build, sign, and return the event; publish from
   the calling site.
5. Use `AccountSettings` for the local backup copy if the list must survive
   relay loss.
