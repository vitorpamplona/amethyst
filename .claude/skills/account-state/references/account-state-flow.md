# Account State-Object Catalog

`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/Account.kt` composes ~50
feature state objects. Packages below are relative to `commons/…/commons/model/`. Names were checked
against `Account.kt`; if one has moved, grep `Account.kt` for the class name — the import tells you
where it lives.

## Identity and contacts

| `Account` property | Class | Kind | Package |
|---|---|---|---|
| `userMetadata` | `UserMetadataState` | 0 | `nip01UserMetadata/` |
| `kind3FollowList` | `Kind3FollowListState` | 3 | `nip02FollowLists/` |
| `muteList` (+ `muteListDecryptionCache`) | `MuteListState` (+ `MuteListDecryptionCache`) | 10000 | `nip51Lists/muteList/` |
| `blockPeopleList` | `BlockPeopleListState` | NIP-51 people set | `nip51Lists/blockPeopleList/` |
| `followSets` (+ `followSetDecryptionCache`) | `FollowSetsState` | NIP-51 follow sets | `nip51Lists/followSets/` |
| `starterPacks` | `StarterPacksState` | NIP-51 starter packs | `nip51Lists/followSets/` |
| `hiddenUsers` | `HiddenUsersState` — derived from `muteList.flow` + `blockPeopleList.flow` | — | `nip51Lists/` |
| `allFollows` | `MergedFollowListsState` — kind3 + follow sets + starter packs + interest/geohash/community lists | — | `serverList/` |

## Relay lists

| `Account` property | Class | Kind | Package |
|---|---|---|---|
| `nip65RelayList` | `Nip65RelayListState` | 10002 | `nip65RelayList/` |
| `dmRelayList` | `DmRelayListState` | 10050 | `nip17Dms/` |
| `searchRelayList` | `SearchRelayListState` | 10007 | `nip51Lists/searchRelays/` |
| `blockedRelayList` | `BlockedRelayListState` | 10006 | `nip51Lists/blockedRelays/` |
| `localRelayList` | `LocalRelayListState` | local only | `localRelays/` |
| `privateStorageRelayList` | `PrivateStorageRelayListState` | private | `edits/` |
| `keyPackageRelayList` | `KeyPackageRelayListState` | Marmot | `marmot/` |
| `trustedRelayList`, `proxyRelayList`, `broadcastRelayList`, `indexerRelayList`, `favoriteRelayList` | `TrustedRelayListState`, `ProxyRelayListState`, `BroadcastRelayListState`, `IndexerRelayListState`, `FavoriteRelayListState` — each with a `…DecryptionCache` sibling | NIP-51 relay sets | `nip51Lists/trustedRelays/`, `…/proxyRelays/`, `…/broadcastRelays/`, `…/indexerRelays/`, `…/favoriteRelays/` |

Derived relay views (merge several of the above, no event of their own): `homeRelays`
(`AccountHomeRelayState`), `outboxRelays` (`AccountOutboxRelayState`), `dmRelays`
(`DmInboxRelayState`), `notificationRelays` (`NotificationInboxRelayState`), `trustedRelays`
(`TrustedRelayListsState`), `followOutboxesOrProxy` (`FollowListOutboxOrProxyRelays`),
`followPlusAllMineWithIndex`, `followPlusAllMineWithSearch`.

## Content lists

| `Account` property | Class | Kind | Package |
|---|---|---|---|
| `bookmarkState` (legacy `oldBookmarkState`) | `BookmarkListState` (`OldBookmarkListState`) | 10003 | `nip51Lists/` |
| `bookmarkSets` | `BookmarkSetsState` | NIP-51 bookmark sets | `nip51Lists/bookmarkSets/` |
| `pinState` | `PinListState` | NIP-51 pins | `nip51Lists/` |
| `interestSets` | `InterestSetsState` | NIP-51 interest sets | `nip51Lists/interestSets/` |
| `interestList` / `geohashList` | `InterestListState` / `GeohashListState` | NIP-51 | `nip51Lists/interestLists/`, `nip51Lists/geohashLists/` |
| `communityList` | `CommunityListState` | NIP-72 | `nip72Communities/` |
| `favoriteAlgoFeedsList` | `FavoriteAlgoFeedsListState` | NIP-51 | `nip51Lists/favoriteAlgoFeedsLists/` |
| `emoji`, `ownedEmojiPacks` | `EmojiPackState`, `OwnedEmojiPacksState` | NIP-30 | `nip30CustomEmojis/` |
| `publicChatList` | `PublicChatListState` | NIP-28 | `nip28PublicChats/` |
| `ephemeralChatList` | `EphemeralChatListState` | ephemeral chats | `emphChat/` |
| `relayGroupList` | `RelayGroupListState` | 10009 (NIP-29 groups) | `nip29RelayGroups/` |
| `blossomServers` | `BlossomServerListState` | Blossom | `nipB7Blossom/` |

## Other feature state

| `Account` property | Class | Purpose | Package |
|---|---|---|---|
| `vanish` | `VanishRequestsState` | NIP-62 vanish requests | `nip62Vanish/` |
| `appSpecific` | `AppSpecificState` | NIP-78 app data | `nip78AppSpecific/` |
| `otsState` | `OtsState` | NIP-03 OpenTimestamps | `nip03Timestamp/` |
| `liveHomeFollowListsPerRelay`, `liveStoriesFollowListsPerRelay`, `liveDiscoveryFollowListsPerRelay`, … | `OutboxLoaderState(...).flow` — already a flow | per-feed outbox routing | `topNavFeeds/` |
| `privateDMDecryptionCache`, `draftsDecryptionCache` | `PrivateDMCache`, `DraftEventCache` (quartz) | NIP-04/NIP-37 decryption caches | quartz `nip04Dm/`, `nip37Drafts/` |

## Publishing mutations

Mutation helpers build and sign the replaceable event with the quartz event class and **return** it;
an `Account` method sends it. Real pairs from `Account.kt`:

```kotlin
suspend fun follow(user: User) = sendMyPublicAndPrivateOutbox(kind3FollowList.follow(user))
suspend fun follow(community: AddressableNote) = sendMyPublicAndPrivateOutbox(communityList.follow(community))
```

The send helpers (`sendAutomatic`, `sendMyPublicAndPrivateOutbox`, `sendLiterallyEverywhere`)
delegate to `EventBroadcaster` (`model/EventBroadcaster.kt`). Pick the one that matches where the
event must land; for an encrypted list that is almost always `sendMyPublicAndPrivateOutbox`.

## Local backups

`AccountSettings` (`model/AccountSettings.kt`) keeps `backupX` copies of the lists the account
cannot afford to lose (`backupContactList`, `backupMuteList`, `backupNIP65RelayList`,
`backupDMRelayList`, …). The state object falls back to it when the relay copy is absent, so a new
list that must survive relay loss needs its own `backup…` field.
