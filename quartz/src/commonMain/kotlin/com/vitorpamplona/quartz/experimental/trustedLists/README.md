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
  index 3 for every kind in the family, so a publisher with a score but no
  relay hint pads index 2 with an empty string — as in the `p` tag above.
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
