# NIP Catalog: 60 Standard + 8 Experimental NIPs in Quartz

## Standard NIPs by Category

### Core/Basic Protocol
| NIP | Directory | Key Files | Description |
|-----|-----------|-----------|-------------|
| 01 | `nip01Core/` | Event.kt, Kind.kt, Tag.kt | Core protocol, event structure, kinds, tags |
| 02 | `nip02FollowList/` | ContactListEvent.kt | Follow/contact lists (kind 3) |
| 03 | `nip03Timestamp/` | OpenTimestampsAttestation.kt | Timestamps |
| 04 | `nip04Dm/` | EncryptedDmEvent.kt | Legacy encrypted DMs (deprecated for NIP-17) |
| 05 | `nip05DnsIdentifiers/` | Nip05Verifier.kt | DNS-based verification |
| 06 | `nip06KeyDerivation/` | Mnemonic-related | BIP-39 key derivation |
| 09 | `nip09Deletions/` | DeletionEvent.kt | Event deletion requests (kind 5) |
| 11 | `nip11RelayInfo/` | RelayInformation.kt | Relay metadata |
| 13 | `nip13Pow/` | ProofOfWork.kt | Proof of work |
| 14 | `nip14Subject/` | Subject tags | Subject tags for text notes |
| 17 | `nip17Dm/` | GiftWrapEvent.kt, SealedGossipEvent.kt | Private DMs (replacem

ent for NIP-04) |
| 21 | `nip21UriScheme/` | URI scheme (`nostr:`) | URI scheme parsing |
| 42 | `nip42RelayAuth/` | RelayAuthEvent.kt | Relay authentication (kind 22242) |
| 44 | `nip44Encryption/` | Nip44.kt, Nip44v2.kt | Modern encryption (ChaCha20) |
| 49 | `nip49PrivKeyEnc/` | NIP-49Ncryptsec.kt | Private key encryption format |

### Content Types
| NIP | Directory | Key Files | Description |
|-----|-----------|-----------|-------------|
| 10 | `nip10Notes/` | TextNoteEvent.kt | Text notes with threading (kind 1) |
| 18 | `nip18Reposts/` | RepostEvent.kt, GenericRepostEvent.kt | Reposts (kind 6, 16) |
| 22 | `nip22Comments/` | CommentEvent.kt | Comments (kind 1111) |
| 23 | `nip23LongContent/` | LongTextNoteEvent.kt | Long-form content (kind 30023) |
| 25 | `nip25Reactions/` | ReactionEvent.kt | Reactions (kind 7) |
| 31 | `nip31Alts/` | Alt tags | Alt description tags |
| 36 | `nip36SensitiveContent/` | Content warnings | Content warning tags |
| 37 | `nip37Drafts/` | DraftEvent.kt | Drafts (kind 31234) |
| 50 | `nip50Search/` | Search filters | Full-text search |

### Encoding & Standards
| NIP | Directory | Key Files | Description |
|-----|-----------|-----------|-------------|
| 19 | `nip19Bech32/` | Nip19.kt | Bech32 encoding (npub, nsec, note, nevent, nprofile, naddr) |
| 40 | `nip40Expiration/` | Expiration tags | Event expiration |
| 48 | `nip48ProxyTags/` | Proxy tags | Proxy tags for delegation |
| 62 | `nip62RequestToVanish/` | RequestToVanishEvent.kt | Request to vanish (kind 12) |
| 98 | `nip98HttpAuth/` | HTTP authorization | HTTP auth header |

### Lists & Management
| NIP | Directory | Key Files | Description |
|-----|-----------|-----------|-------------|
| 51 | `nip51Lists/` | 18 list types | Named lists (mute, bookmarks, pins, communities, etc.) (kinds 10000-30004) |
| 65 | `nip65RelayList/` | AdvertisedRelayListEvent.kt | Relay lists (kind 10002) |

### Social & Identity
| NIP | Directory | Key Files | Description |
|-----|-----------|-----------|-------------|
| 39 | `nip39ExtIdentities/` | External identities | External identity claims |
| 46 | `nip46RemoteSigner/` | NostrConnectEvent.kt | Remote signer protocol (bunker) |
| 47 | `nip47WalletConnect/` | Nostr Wallet Connect | Wallet connection protocol |
| 56 | `nip56Reports/` | ReportEvent.kt | Reports (kind 1984) |
| 57 | `nip57Zaps/` | LnZapEvent.kt, LnZapRequestEvent.kt | Lightning zaps (kinds 9734, 9735) |
| 58 | `nip58Badges/` | Badge events | Badge definitions & awards (kinds 30009, 8) |
| 59 | `nip59Giftwrap/` | GiftWrapEvent.kt | Gift-wrapped events for privacy |
| 75 | `nip75ZapGoals/` | ZapGoalEvent.kt | Zap goals (kind 9041) |

### Specialized Content
| NIP | Directory | Key Files | Description |
|-----|-----------|-----------|-------------|
| 28 | `nip28PublicChat/` | ChannelCreateEvent.kt, ChannelMessageEvent.kt | Public chat channels (kinds 40-44) |
| 30 | `nip30CustomEmoji/` | EmojiUrl.kt | Custom emoji |
| 34 | `nip34Git/` | Git patch/issue events | Git repository tracking (kinds 30617, 30618, 1617, 1621, 1622, 1630, 1633) |
| 35 | `nip35Torrents/` | Torrent events | Torrent tracking |
| 52 | `nip52Calendar/` | Calendar events | Calendar time-based/date-based (kinds 31922-31925) |
| 53 | `nip53LiveActivities/` | LiveActivitiesEvent.kt | Live events/streaming (kind 30311) |
| 54 | `nip54Wiki/` | WikiNoteEvent.kt | Wiki pages (kind 30818) |
| 68 | `nip68Picture/` | Picture metadata | Picture metadata |
| 71 | `nip71Video/` | 7 video event types | Video events (kinds 34235, 35235, 1234, 1235) |
| 72 | `nip72ModCommunities/` | Community events | Moderated communities (kinds 34550, 34551, 9041) |
| 84 | `nip84Highlights/` | HighlightEvent.kt | Highlights (kind 9802) |
| 89 | `nip89AppHandlers/` | AppDefinitionEvent.kt | App recommendations (kinds 31990, 31989) |
| 90 | `nip90Dvms/` | DVM job events | Data Vending Machines (DVMs) (kinds 5000-7000) |
| 92 | `nip92IMeta/` | IMeta tags | Image metadata tags |
| 94 | `nip94FileMetadata/` | FileHeaderEvent.kt, FileStorageEvent.kt | File metadata (kind 1063) |
| 96 | `nip96FileStorage/` | HTTP file storage | HTTP-based file storage |
| 99 | `nip99Classifieds/` | ClassifiedsEvent.kt | Classifieds/marketplace (kind 30402) |
| A0 | `nipA0VoiceMessages/` | Voice messages | Voice message events |
| B7 | `nipB7Blossom/` | Blossom server URLs | Blossom file storage |

### Web/Storage/Other
| NIP | Directory | Key Files | Description |
|-----|-----------|-----------|-------------|
| 38 | `nip38UserStatus/` | StatusEvent.kt | User status (kind 30315) |
| 60 | `nip60Payment/` | Wallet events | Wallet info (kind 13194) |
| 61 | `nip61PaymentRequest/` | Nut zaps | Cashu payment requests |
| 64 | `nip64Chess/` | Chess moves | Chess move events |
| 66 | `nip66Monitoring/` | Relay monitor events | Relay monitoring |
| 67 | `nip67Invoices/` | Invoice tags | Lightning invoice tags |
| 69 | `nip69Offers/` | BOLT-12 offers | BOLT-12 offer tags |
| 70 | `nip70ProtectedEvts/` | Protected events | Protected event types |
| 73 | `nip73ExternalIds/` | External content IDs | External content identifiers |
| 78 | `nip78AppData/` | AppDataEvent.kt | Application data (kind 30078) |
| 79 | `nip79Labels/` | Label events | Labeling (kinds 1985, 1986) |
| 80-88 | Various | Various protocols | Relationship, preferences, polls, surveys, social graphs, etc. |
| 91 | `nip91Feed/` | Feed display events | Feed definitions |
| 93 | `nip93Gallery/` | Gallery events | Gallery collections |
| 95 | `nip95Storage/` | Storage event tags | Storage events |
| 97 | `nip97Nests/` | Audio rooms | Audio room events |

## Experimental NIPs (18 packages)

Located at `/quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/experimental/`:

| Package | Description |
|---------|-------------|
| `audio/` | Audio content, track events |
| `bounties/` | Bounty/funding events |
| `decoupling/` | Decoupling setup |
| `edits/` | Event edit tracking |
| `ephemChat/` | Ephemeral encrypted chat |
| `forks/` | Fork tracking |
| `inlineMetadata/` | Inline metadata |
| `interactiveStories/` | Interactive story events |
| `limits/` | Limit enforcement |
| `medical/` | Medical data |
| `nip95/` | File storage support |
| `nipA3/` | A3 protocol extension |
| `nns/` | Nostr Name System |
| `profileGallery/` | Profile gallery lists |
| `publicMessages/` | Public message lists |
| `relationshipStatus/` | Relationship status events |
| `trustedAssertions/` | Trust/assertion events |
| `zapPolls/` | Zap-based polling |

## Quick Lookup by Kind

| Kind | Event Type | NIP |
|------|------------|-----|
| 0 | Metadata | 01 |
| 1 | Text Note | 01, 10 |
| 3 | Follow List | 02 |
| 4 | Encrypted DM (legacy) | 04 |
| 5 | Deletion | 09 |
| 6 | Repost | 18 |
| 7 | Reaction | 25 |
| 8 | Badge Award | 58 |
| 16 | Generic Repost | 18 |
| 40-44 | Channel Events | 28 |
| 1063 | File Metadata | 94 |
| 1111 | Comment | 22 |
| 1617, 1621, 1622, 1630, 1633 | Git | 34 |
| 1984 | Report | 56 |
| 1985, 1986 | Label | 79 |
| 9734 | Zap Request | 57 |
| 9735 | Zap Receipt | 57 |
| 9802 | Highlight | 84 |
| 10000-20000 | Replaceable Lists | 51 |
| 10002 | Relay List | 65 |
| 13194 | Wallet Info | 60 |
| 22242 | Relay Auth | 42 |
| 23194, 23195 | NWC Payment | 47 |
| 30000-40000 | Addressable Events | Various |
| 30009 | Badge Definition | 58 |
| 30023 | Long-Form Content | 23 |
| 30078 | App Data | 78 |
| 30311 | Live Event | 53 |
| 30315 | User Status | 38 |
| 30402 | Classifieds | 99 |
| 30818 | Wiki | 54 |
| 31234 | Draft | 37 |
| 31922-31925 | Calendar | 52 |
| 31989, 31990 | App Handlers | 89 |
| 34235, 34550-34551 | Video/Communities | 71, 72 |
| 5000-7000 | DVM Jobs | 90 |

## File Location Pattern

All NIPs located at: `/quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip<NN><Name>/`

Example: NIP-57 → `/quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip57Zaps/`
