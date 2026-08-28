# Trusted Lists (kinds 30392-30395)

A **Trusted List** is an addressable event that publishes a curated set of
**members** computed under a point of view — "the pubkeys trusted-tagged with
tag X under observer O", "the notes trusted-tagged with X", "the tags that
apply to events". It is the *aggregate* analog of a
[NIP-85](https://nips.nostr.com/85) Trusted Assertion: where an assertion
states a computed result about **one** subject, a list enumerates **many**
members of one type.

This is a pre-NIP wire format drafted by Tapestry
([spec](https://github.com/nous-clawds4/tapestry/blob/main/protocols/drafts/trusted-lists.md)).
It does not modify NIP-85; it defines the list analog and binds its kinds to
NIP-85's subject-type convention.

## Kinds — the `+10` rule

NIP-85 encodes the *subject* type in the last digit of `3038x`. Trusted Lists
mirror that with `TL kind = TA kind + 10`, so the last digit denotes the
**member** type and a reader can tell what a list contains from the kind alone,
without inspecting the tags.

| Kind | = TA + 10 | Members | Member tag | Quartz class |
|---|---|---|---|---|
| 30392 | 30382 | pubkeys | `p` | `users.UserTrustedListEvent` |
| 30393 | 30383 | events | `e` | `events.EventTrustedListEvent` |
| 30394 | 30384 | addressable events | `a` | `addressables.AddressableTrustedListEvent` |
| 30395 | 30385 | external identifiers (NIP-73) | `i` | `externalIds.ExternalIdTrustedListEvent` |

A list of addressable events therefore **must** be 30394. Publishing
a-coordinate members on 30393 (the event-id kind) would tell a conformant
reader "these are event ids" when they are coordinates — a category error that
breaks kind-keyed dispatch and federation.

All four extend `TrustedListEvent`, which carries everything the family shares.
`members()` is narrowed per kind but always satisfies `TrustedListMemberTag`,
so a kind-agnostic reader can take `memberValue` and `score` without branching.
`memberValues()` and `memberCount()` read the tags directly rather than going
through `members()`, so callers that only need the membership never pay to
build the member objects — these lists run to thousands of entries.

## Wire shape

```json
{
  "kind": 30392,
  "tags": [
    ["d", "tl-pin-2efaa715-e5272de9-podcaster"],
    ["title", "Podcaster"],
    ["metric", "pinned-tag-membership"],
    ["observer", "2efaa715…"],
    ["source-tag", "2f6a8652…", "e5272de9…", "podcaster"],
    ["cutoff", "1"],
    ["min-rank", "2"],
    ["p", "b83a28b7…", "", "99"]
  ],
  "content": "{\"members\":[{\"pubkey\":\"b83a28b7…\",\"endorsements\":4,\"disputes\":0,\"score\":99}]}"
}
```

- `d` identifies the **list**, not a subject, so it replaces in place as it is
  recomputed (`listId()`).
- `title` / `metric` are the label and the name of the computation.
- Member tags carry `[<tag>, <value>, <hint>, <score>]`. The score sits at
  index 3 for every kind in the family — right after the relay hint — so a
  publisher with a score but no relay hint pads index 2 with an empty string,
  as in the `p` tag above. It is a **percentage: an integer 0–100 inclusive**
  (`MemberTagFields.SCORE_RANGE`). The fixed scale is the point — it is what
  lets a consumer compare members across two publishers, or across two metrics
  of one publisher, without knowing either computation. `assemble` clamps into
  the range, so we never emit what we would refuse to read; a parsed value
  outside it is dropped rather than clamped, because a 950 pinned to 100 would
  rank a member from some other scale above every honestly-scored peer. Such a
  member is simply unscored, exactly as if the tag carried no score at all.
  On `e` members index 3 is the score, **not** a NIP-10 marker: these lists
  enumerate membership, they do not thread. Index 2 is only read as a relay
  hint when it looks like one (`MemberTagFields.relayHint`): the normalizer
  turns any bare word into `wss://<word>/`, so an unguarded parse would index a
  petname as a relay. On `i` members that slot is NIP-73's URL hint instead.
- `observer`, `source-tag`, `cutoff`, `min-rank` are provenance.
- Single-letter tags that are *not* the kind's member tag are
  relay-filterable **discovery** metadata — what the list is about — and are
  read through `aboutAddresses()` / `aboutPubKeys()`, never through
  `members()`. The pinned-tag note list (30393) carries
  `["a", "39999:<tagAuthor>:<slug>"]` so consumers can find every note list for
  a tag (`{"kinds":[30393],"#a":[coord]}`) and `["p", <observer>]` to find
  every note list for an observer.
- `content` is an optional JSON echo of the members with their computed values
  (`contentEcho()` → `TrustedListContent`).

## Treasure Map advertisement (kind 10040)

A NIP-85 Treasure Map delegates each assertion kind+metric to a publisher with
`["30382:rank", <pubkey>, <relay>]`. Trusted Lists extend it with a **generic
bare-kind entry** (Tapestry ADR `tl-treasure-map/0001`):

```json
["30392", "<publisher-pubkey>", "wss://nip85.brainstorm.world"]
```

One entry delegates *all* lists of that kind — the ones computed under the Map
owner's point of view, discoverable at the relay hint. List names are never
enumerated, which is the point of the bare-kind form: the Map stays a fixed
size however many lists the publisher computes.

Parse rule — split the first element on `:`. A single all-digits segment is a
generic entry; two segments are either NIP-85's `3038x:<metric>` or a **named**
TL entry (`3039x:<name>`, reserved). Named entries parse so a reader can
display them as Trusted List entries, but drive no behavior until the spec
defines them — `isGeneric` is the guard, and `trustedListProvider(kind)`
returns only the generic one.

This lives in `treasureMap/`, outside `nip85TrustedAssertions/`, even though it
rides on that kind: `ServiceProviderTag` models NIP-85's own delegation, and a
NIP-85 consumer should stay unaware of this family. That separation is load
bearing in both directions — `ServiceProviderTag.parse` is bounded to NIP-85's
own assertion kinds (30382–30385), so a `30392:podcaster` entry, which splits
into two segments exactly like `30382:rank`, is never handed to code looking
for a rank or follower-count service.

Two things the reader must not do:

- **Drop an entry with an empty relay hint.** A publisher with no relay
  configured still writes the three-element shape with `""` in the slot. The
  pubkey is the part a consumer cannot do without, so `relayUrl` is nullable
  and the delegation stands without it.
- **Resolve duplicates arbitrarily.** At most one generic entry per kind is the
  *writer's* invariant; where duplicates appear in the wild the **first
  occurrence wins**, so two readers of one Map resolve the same publisher.

### Both halves of the Map

A 10040 keeps half its delegations NIP-44 encrypted in `content` — who you
trust to rank the network is itself sensitive — so a Trusted List entry has to
work in both halves, and the one-entry-per-kind invariant spans them.

The parsing is `TagArray`-level and half-agnostic: hand
`trustedListProviders()` an already-merged array (commons'
`PrivateTagArrayEventCache`, which caches the decryption, is how the app reads
NIP-85 providers) and private entries come out with no extra work. The
event-level accessors are the convenience layer on top:

| Accessor | Sees |
|---|---|
| `publicTrustedListProviders()` / `publicTrustedListProvider(kind)` | the public tags alone, no signer |
| `trustedListProviders(signer)` / `trustedListProvider(kind, signer)` | both halves, merged |

With anyone else's signer, or a private half that will not decrypt, the merged
accessors fall back to the public half rather than failing — the same contract
as `TrustProviderListEvent.privateTags`. Public tags are searched first, so
where a Map violates the invariant *across* halves the public entry wins.

Writing goes through `replaceTrustedListProvider(provider, isPrivate, signer)`,
which swaps the generic entry for its kind **in place** in the half `isPrivate`
selects, and drops it from the other one — moving a delegation between public
and private is a single call rather than a two-step that strands a twin,
shadowed on read and republished forever after. Every other tag in both halves
survives verbatim: 10040 is replaceable, so the update republishes the whole
event and anything dropped is gone from the Map for good.

The cost of the cross-half invariant is that a Map *with* a private half must
be decryptable even for a public write — we cannot drop a private twin we
cannot read, so that write throws `UnauthorizedDecryptionException` rather than
publishing a Map that breaks the invariant. A Map with no private half (blank
`content`) needs no decryption either way.

```kotlin
val updated =
    treasureMap.replaceTrustedListProvider(
        kind = UserTrustedListEvent.KIND,
        pubkey = publisherHex,
        relayUrl = RelayUrlNormalizer.normalizeOrNull("wss://nip85.brainstorm.world"),
        isPrivate = false,
        signer = signer,
    )
```

## Completeness and retraction

A list an integrator relies on must be complete, or say that it isn't. The
**absence** of a `truncated` tag means the membership is
authoritative-complete; its **presence** means the list is not exhaustive and
its value is the true total. `isTruncated()` keys off presence, so a
`truncated` tag with a missing or unparseable total still reads as incomplete;
`truncatedTotal()` returns the total when it parses. The content echo mirrors
this with `partial` + `total`.

A list that no longer has members, or that is migrating off a kind, is replaced
in place by an empty-membership event carrying `["status", "retracted"]`
(`isRetracted()`) rather than being deleted.

## Building

```kotlin
val template =
    UserTrustedListEvent.build(
        listId = "tl-pin-2efaa715-e5272de9-podcaster",
        members = listOf(PubKeyMemberTag(authorHex, score = 99)),
        content = TrustedListContent(listOf(TrustedListContentMember(pubkey = authorHex, score = 99))).toContent(),
    ) {
        title("Podcaster")
        metric("pinned-tag-membership")
        observer(observerHex)
        sourceTag(tagEventId, tagAuthorHex, "podcaster")
        cutoff(1)
        minRank(2)
    }
```

## Search

`TrustedListEvent` implements `SearchableEvent`, so all four kinds are indexed
for NIP-50 — and they index **`title` alone**:

```kotlin
override fun indexableContent() = title() ?: ""
```

Nothing else in the family is human-authored prose. `metric` is the name of a
computation and `d` is the list identifier — machine ids, kept out so a search
for a common word in one of them doesn't return every list that ran the same
job. The member tags are hex ids and `content` is a JSON echo of the same
membership, so indexing either would put thousands of identifiers into the
full-text index for no lookup a `#p`/`#e`/`#a`/`#i` filter doesn't already
serve better. A list with no `title` indexes the empty string rather than
throwing — `indexableContent()` runs inside the store's insert transaction.

Stores built before this was added keep their old rows unindexed until
`IEventStore.reindexFullTextSearch()` runs.
