---
name: nip85-trusted-assertions
description: The NIP-85 trusted-assertions model in Quartz (`nip85TrustedAssertions/`) — kind 10040 trust-provider lists, kind 30382 contact cards / user assertions, 30383 event assertions, 30384 addressable assertions, 30385 external-id assertions. Use when building or parsing these events, working with the typed tags (RankTag, HopsTag, FollowerCountTag, ServiceProviderTag/ServiceType, …), wiring a consumer that resolves a 10040 provider entry to the 30382s it signs, ranking on assertion values, or touching the GrapeRank publisher, contact-card nicknames, or the trust projection of an external store.
---

# NIP-85 Trusted Assertions — the Quartz model

Package: `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip85TrustedAssertions/`.
NIP-85 is still an evolving spec; **this package is the operative definition** of what
Amethyst-family software writes and reads. This skill states the model (who signs what about
whom), the exact kind/d-tag/tag vocabulary, and what consumers may — and may not — assume.

## The model in one paragraph

An **assertion is signed by the asserting party** (a trust provider service, or the user
themself) **about a subject named in the d-tag**. All assertion kinds are addressable, so
"latest card by provider P about subject S" is just the addressable coordinate
`(kind, P, S)` and supersession is standard NIP-01 latest-wins. Discovery is the observer's
**kind 10040 list**: each entry says *"for metric M on kind K, I trust provider P — fetch
their assertions at relay R"*. Quartz enforces none of this cryptographically beyond normal
event signatures; the 10040→assertion link is **consumer-side convention** (see
"Authorization" below).

## Kind map

| Kind | Class | Kind class | d-tag = the subject | Content |
|---|---|---|---|---|
| 10040 | `list/TrustProviderListEvent` | replaceable | *(none — always `""`)* | NIP-44 private provider entries (optional) |
| 30382 | `users/ContactCardEvent` | addressable | **target user's pubkey** (hex) | NIP-44 private tags (petname/summary/emoji) |
| 30383 | `events/EventAssertionEvent` | addressable | **target event id** (hex) | `""` |
| 30384 | `addressables/AddressableAssertionEvent` | addressable | **target coordinate** `kind:pubkey:dtag` | `""` |
| 30385 | `externalIds/ExternalIdAssertionEvent` | addressable | **external identifier** (e.g. `isbn:978-0-13-468599-1`) | `""` |

Addresses: `ContactCardEvent.createAddress(owner, target)` → `Address(30382, owner, target)`
(owner = signer, target = subject). `TrustProviderListEvent.createAddress(pubKey)` uses
`FIXED_D_TAG = ""`. `AssertionEventTest.eventKindsAreCorrect` pins all five numbers.

`ContactCardEvent` is also a `SearchableEvent` — it indexes only the **public** petname/summary
tags plus topics; the encrypted card content is intentionally never indexed.

## The 10040 provider entry (`ServiceProviderTag` / `ServiceType`)

There is **no fixed tag name**: `tag[0]` *is* the service string.

```json
["30382:rank", "<provider pubkey, 64 hex>", "wss://nip85.brainstorm.world"]
```

- `ServiceType(kind, type)` parses/renders `"<kind>:<type>"` — kind must be an int, the first
  `:` splits, colons in the remainder stay in `type`. `ServiceType.isOfKind` is the
  allocation-free prefix check.
- `ServiceProviderTag.parse` requires ≥3 elements, non-empty service, 64-char pubkey
  (length-only check), and a **normalizable relay URL** (`RelayUrlNormalizer.normalizeOrNull`) —
  entries failing any check are silently dropped, which is what keeps foreign tags like
  `["client","nostria"]` out (regression-tested in `ServiceTypeParserTest`).
- Entries may be **public** (tag array) or **private** (NIP-44 content); `create`/`add` take
  `isPrivate`. `remove` always needs decryption and strips from both sides by parsed-value
  equality.
- `object ProviderTypes` (`list/tags/ServiceType.kt`) enumerates the *known* service types —
  `30382:rank`, `30382:followers`, `30382:first_created_at`, per-metric `30383:*`/`30384:*`/
  `30385:*`, etc. It is an **open vocabulary**: real 10040s in the wild (see the fiatjaf →
  brainstorm fixture in `commonTest/.../nip85TrustedAssertions/ServiceParser.kt`) carry types
  Quartz doesn't enumerate (`30382:personalizedGrapeRank_influence`, `30382:hops`,
  `30382:verifiedFollowersCount`, …). Parse any `kind:type`; special-case only what you rank on.

## Authorization — what a consumer may assume

- **A 30382 (or 30383/…) is meaningful to an observer only if its author is listed in the
  observer's 10040 for a matching service type.** Quartz does not enforce this; the consuming
  code does. The in-repo pattern is `commons/.../model/nip85TrustedAssertions/UserCardsCache.kt`:
  `rankFlow(trustProviderList)` picks the received card whose **author pubkey equals the
  provider entry's pubkey** and reads `rank()` from it. Assertions from unlisted signers are
  simply ignored for trust purposes (they may still be stored; dropping them — as an external
  store's orphan sweep does — is a legitimate storage policy, not a protocol rule).
- What an entry authorizes is scoped by its `ServiceType`: `30382:rank` authorizes that
  provider's user-rank cards, nothing else. Amethyst models this as one provider slot per
  metric (`liveUserRankProvider`, `liveUserFollowerCount` in
  `amethyst/.../model/trustedAssertions/TrustProviderListState.kt`).
- **Multi-provider combination is unprescribed.** When two listed providers assert different
  ranks, there is no spec'd merge; Amethyst avoids the question by selecting one provider per
  metric slot. Consumers choose their own policy — document it.
- The relay URL in the entry is a **fetch hint, and it is honored**:
  `amethyst/.../UserCardsSubAssembler.kt` subscribes for cards at the provider's declared relay
  (`kinds=[30382], authors=[provider], #d=[targets]`).

### The dual use of kind 30382

The same kind serves two roles, distinguished **by author**:

1. **Provider WoT cards** — signed by a trust provider; public metric tags (`rank`,
   `followers`, `hops`, …); this is what 10040 discovery points at.
2. **The account's own contact cards (nicknames, NIP-81-style)** — signed by the account,
   one per target user. The petname, summary, and their NIP-30 emoji mappings **always live in
   the NIP-44 encrypted content, never in public tags** (`ContactCardEvent.build`/
   `updatePetNameAndSummary` strip stray public copies; asserted by `ContactCardPetNameTest`).
   `commons/.../ContactCardsState.kt` keys everything on `author == account` and ignores
   provider cards.

## Tag vocabulary and value semantics

All tag classes share one shape: `TAG_NAME` + `parse(tag)` (null on wrong name/empty/non-numeric
value — a bad tag is *dropped*, never an error) + `assemble(value)` → `[name, value.toString()]`.
**A missing tag means "unknown" (`null` accessor), never zero.** There is deliberately no range
validation (rank isn't clamped, hours aren't checked against 0–23, counts may be negative) —
consumers must defend.

**On 30382** (`users/tags/`, accessors on `ContactCardEvent` and as `TagArray` extensions in
`users/TagArrayExt.kt` so they also work on decrypted private arrays):

| Tag name | Accessor | Type | Semantics |
|---|---|---|---|
| `rank` | `rank()` | Int | Provider-relative score; higher is better. GrapeRank publishes `round(score × 100)` (so 0–100 in practice), but nothing enforces a scale — treat it as comparable only *within one provider*. |
| `followers` | `followerCount()` | Int | Follower count as the provider computes it (cumulative, provider-defined). |
| `hops` | `hops()` | Int | Shortest follow-path length **from the observer the provider computed for** to the subject (1 = directly followed). Mirrors Brainstorm GrapeRank's `hops`. The only tag with KDoc. |
| `first_created_at` | `firstCreatedAt()` | Long | Unix seconds of subject's earliest known event. |
| `post_cnt` / `reply_cnt` / `reactions_cnt` | `postCount()` etc. | Int | Activity counts. |
| `zap_amt_recd` / `zap_amt_sent` | `zapAmountReceived()`/`…Sent()` | Long | Sats. |
| `zap_cnt_recd` / `zap_cnt_sent` | `zapCountReceived()`/`…Sent()` | Int | Counts. |
| `zap_avg_amt_day_recd` / `zap_avg_amt_day_sent` | `zapAvgAmountDay…()` | Long | Sats/day averages. |
| `reports_cnt_recd` / `reports_cnt_sent` | `reportsCount…()` | Int | NIP-56 report counts. |
| `t` (repeatable) | `topics()` | List\<String> | Subject's topics/interests. |
| `active_hours_start` / `active_hours_end` | `activeHours…()` | Int | Hour-of-day; **no timezone is specified in code** — treat as provider-defined (UTC in practice) and unclamped. |
| `petname` / `summary` | `petName()`/`summary()` | String | Nickname fields — conventionally private (see dual use above). |

**On 30383/30384** (`tags/`, shared): `rank`, `comment_cnt`, `quote_cnt`, `repost_cnt`,
`reaction_cnt`, `zap_cnt` (Int) and `zap_amount` (Long, sats).
**On 30385**: only `rank`, `comment_cnt`, `reaction_cnt`.

## Building and parsing (use the typed helpers, not raw `arrayOf`)

```kotlin
// Provider list: declare a rank provider (this is what `amy graperank register` does)
val tag = ServiceProviderTag(ProviderTypes.rank, providerPubkeyHex, relayUrl)
val list = TrustProviderListEvent.create(tag, isPrivate = false, signer)
// or append to an existing one:
val updated = TrustProviderListEvent.add(existing, tag, isPrivate = false, signer)
val providers: List<ServiceProviderTag> = updated.serviceProviders()          // public
val private = updated.privateTags(signer)?.serviceProviders()                 // private side

// Provider-style contact card (public metrics) — the GrapeRankPublisher pattern:
val card = ContactCardEvent.create(
    targetUser = subjectPubkey,
    signer = providerSigner,
    publicInitializer = {
        rank(87)
        followers(1234)
        hops(2)
    },
)
card.aboutUser()   // d-tag → subject pubkey
card.rank()        // 87

// Event assertion: unsigned template only (30383/84/85 have build(), no create())
val template = EventAssertionEvent.build(targetEventId) {
    rank(12)
    reactionCount(40)
    zapAmount(2100)
}
val signed = signer.sign(template)
```

## Worked end-to-end example

Observer `O` trusts provider `P` for user ranks (kind 10040, replaceable, by `O`):

```json
{ "kind": 10040, "pubkey": "<O>",
  "tags": [
    ["30382:rank",      "<P>", "wss://nip85.brainstorm.world"],
    ["30382:followers", "<P>", "wss://nip85.brainstorm.world"]
  ],
  "content": "" }
```

Provider `P` asserts about subject `S` (kind 30382, addressable at `30382:<P>:<S>`):

```json
{ "kind": 30382, "pubkey": "<P>",
  "tags": [
    ["d", "<S>"],
    ["rank", "87"], ["followers", "1234"], ["hops", "2"]
  ],
  "content": "" }
```

`P` asserts about an event `E` (kind 30383, addressable at `30383:<P>:<E>`):

```json
{ "kind": 30383, "pubkey": "<P>",
  "tags": [["d", "<E>"], ["rank", "12"], ["reaction_cnt", "40"], ["zap_amount", "2100"]],
  "content": "" }
```

Consumption chain: read `O`'s 10040 → entry matching `ServiceType(30382, "rank")` → subscribe
`{kinds:[30382], authors:["<P>"], "#d":["<S>", …]}` at the hinted relay → newest card per
address wins → `rank()`.

Literal fixtures: `quartz/src/commonTest/.../nip85TrustedAssertions/ServiceParser.kt` (a real
10040 — fiatjaf's, pointing at the Brainstorm provider) and `AssertionEventTest.kt` (all four
assertion kinds with every tag populated).

## Freshness / supersession

Assertions are addressable: **latest per `(kind, author, d-tag)` wins**; there is no expiry tag
convention and **no prescribed refresh cadence** — staleness policy is the consumer's.
Writers should avoid churn: `GrapeRankPublisher` re-signs a card only when
`(rank, followers, hops)` actually changed, and retracts with a NIP-09 kind-5 carrying the
card's `a`-tag (`30382:<provider>:<target>`).

## Stability notes (as of 2026-08)

- **Settled** (shipped consumers on both ends): the kind map; `ServiceProviderTag` entry shape;
  `rank`/`followers`/`hops` on 30382; petname/summary-in-encrypted-content; 10040 relay-hint
  consumption.
- **Written but lightly consumed** (parse, but gate ranking features carefully): the activity/
  zap/report count tags, `active_hours_*` (no timezone semantics), 30383/30384/30385 (builders +
  tests exist; no in-repo publisher yet).
- **Known warts**: `ServiceProviderTag.assemble(id: ServiceProviderTag)` infers `Array<Any>` —
  dead code, don't use it; `SummaryTag.assemble(ip:)`/`ActiveHours*Tag.assemble(count:)` params
  are misnamed; the tests live under `commonTest/.../experimental/nip85TrustedAssertions/`
  (stale path); `TrustProviderListEvent` extends the addressable base, so a stray on-wire `d`
  tag is reflected by `dTag()` even though the convention is `""`.

## Where it's consumed (reading list)

- **Publisher**: `quartz/.../experimental/graperank/GrapeRankPublisher.kt` (canonical 30382
  writer), `cli/.../graperank/` (`amy graperank register|unregister|providers|publish`).
- **Client model**: `commons/.../model/nip85TrustedAssertions/` (`ContactCardsState`,
  `UserCardsCache`, `ContactCardDecryptionCache`, `TrustProviderListDecryptionCache`),
  `amethyst/.../model/trustedAssertions/TrustProviderListState.kt`.
- **Relay plumbing**: `commons/.../relayClient/assemblers/ContactCardFilters.kt`,
  `amethyst/.../reqCommand/user/watchers/UserCardsSubAssembler.kt`.
