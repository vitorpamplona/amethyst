# A link vocabulary: what each kind's references MEAN

Status: **draft for review** (2026-09-29). Nothing is implemented yet. Decided in review: links
always start at an event (no user-to-user shortcuts), the author of acted-on content gets its OWN
relation, and the kind stays on the source event only. **Naming is still open** (the style below
is a placeholder).

## Why

Quartz already knows which values in an event are references: the hint providers
(`EventHintProvider`, `PubKeyHintProvider`, `AddressHintProvider`) name the linked ids, kind by
kind. They do not say what a link MEANS. They return `List<HexKey>`, so a consumer cannot tell a
reply's parent from its thread root, a reaction's target from a `p` it notifies, or a report
about a person from a report about their note.

Every consumer that needs the meaning re-derives it:
- Amethyst, in its feed filters;
- neo4j-eventstore, the graph projection of the relay's store. Its `RoleTable`, `LinkRules` and
  report extractors are per-kind interpretation written one repository downstream of the kinds.

That graph named its relationships mechanically, `<tag>_<kind>` (`p_3`, `e_1111`, `z_39999`). The
names are complete without curation, but they push the per-kind knowledge onto every query
author: to find a comment's parent you must know kind 1111 puts it in `e`, and a newer kind may
put an address in `z` or `c`. The knowledge is needed either way. It belongs next to the kind,
written once, where the tags are already parsed — the pattern `SearchFieldExtractor` /
`IndexableFields` already follows for search.

## The model

A **link** is one statement an event makes about something else:

```kotlin
@JvmInline value class Relation(val name: String)   // an open vocabulary: constants below, extensible

sealed interface LinkTarget {
    data class Event(val id: HexKey) : LinkTarget
    data class User(val pubkey: HexKey) : LinkTarget
    data class Address(val value: String) : LinkTarget      // kind:pubkey:d
    data class Tag(val name: String, val value: String) : LinkTarget  // a topic, url, label value…
}

data class Link(
    val relation: Relation,
    val target: LinkTarget,
    val via: String? = null,          // "content" for a nostr: URI in the text, else the tag name
    val props: Map<String, Any>? = null,  // relation-specific values (a report's type, a rank)
)

interface LinkProvider { fun links(): List<Link> }
```

Rules the vocabulary follows:

1. **Every link starts at the event that makes the statement.** The event is the provenance: its
   author, its time, and the version that superseded it all hang off it. The one exception is
   `OWNED_BY` (address → user), which no event states.
2. **One relation per action, across kinds.** `REPLIES_TO` is a kind 1 reply, a NIP-22 comment, a
   git reply and a chat reply. The source event's `kind` says which; a query that cares filters
   on it (`(c:Event {kind: 1111})-[:REPLIES_TO]->(x)`). Kinds are not repeated in the name.
3. **The author of acted-on content gets a relation of its own.** A reaction `REACTS_TO` the
   note, and names the note's author through `REACTS_TO_AUTHOR`, not through a second
   `REACTS_TO`. "Reactions to my notes" and "reactions to anything by me" stay one hop, and each
   is its own constant-time count.
4. **Split a relation when queries separate its meanings on the same target type.** Counting a
   relation per node is constant-time in Neo4j, but filtering on a property reads every edge.
   So a distinction that is filtered all the time becomes two relations: `REPORTS_USER` (a
   complaint about the person) is not `REPORTS_AUTHOR` (the author of reported content), and
   `FOLLOWS` (the kind 3 social graph) is not `SUBSCRIBES_TO` (every other follow-like list).
5. **Nothing is invisible before it is classified.** A class that implements no `LinkProvider`
   gets a default derived from its hint providers: every linked id becomes a `REFERENCES` link
   with `via` = the tag it came from. Classifying a kind later is an additive change.
6. **Values that qualify a link ride on it** (`props`): a report's type, an assertion's rank, a
   zap request's amount. They are what a query filters on after choosing the relation.

## The vocabulary

Targets: **E** event, **A** address, **U** user, **T** tag value. "Kinds" lists the Quartz classes
the relation comes from today; each row is a golden test when implemented.

### Authorship and identity

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `AUTHORED_BY` | U | The event's signer | every kind |
| `VERSION_OF` | A | The addressable event's own address | 30000–39999 |
| `OWNED_BY` | U | address → its pubkey (not stated by an event) | every address |

### Conversation

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `REPLIES_TO` | E, A | The direct parent | 1 (NIP-10, `replyingTo()`), 1111 (`e`/`a`/`p`), 1244, 1622, 2004, 30818, 14, 42, 1311, and 9 — whose reply parent is a **`q`** tag (NIP-C7), the case that shows why tag letters cannot be the schema |
| `THREAD_ROOT` | E, A | The thread's root | 1 (`root()`), 1111 (`E`/`A`), 1622, 42 |
| `REPLIES_TO_AUTHOR` | U | The direct parent's author | 1111 (`p`), 1244 |
| `THREAD_ROOT_AUTHOR` | U | The root's author | 1111 (`P`), 1244 |
| `MENTIONS` | E, A, U | Named in passing: a NIP-10 `mention` marker, a `p` that notifies, a `nostr:` URI in the text (`via: content`) | 1, 1111, 9, 24, 42, 1311, 1621, 1622, 9802, 30023, 30817, 30818, … |
| `QUOTES` | E, A | A `q` tag (except kind 9, where `q` is the reply parent) | 1, 42, 1111, 1311, 1621, 30023, … |
| `FORK_OF` | E | NIP-10 `fork` marker | 1 |
| `EDITS` | E | A later edit of that event | 1010 (TextNoteModification), 3302 |
| `POSTED_IN` | E, A | The container a message belongs to: a channel, live activity, community, repository | 42 (channel `root`), 1311 (`a`), 1617–1622 (repo `a`), posts tagging a 34550 |
| `SENT_TO` | U | A direct or gift-wrapped message's recipients | 4, 14, 15, 24, 1059, 21059 |

### Reactions, reposts, zaps

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `REACTS_TO` | E, A, T | The reacted-to content (the last `e`/`a`, `originalPost()`); kind 17 reacts to a URL / external id (T) | 7, 17 |
| `REACTS_TO_AUTHOR` | U | Its author (`originalAuthor()`) | 7 |
| `REPOSTS` | E, A | The reposted content (`boostedEventId()` / `boostedAddress()`) | 6, 16 |
| `REPOSTS_AUTHOR` | U | Its author | 6, 16 |
| `ZAPS` | E, A | The zapped content. Props: `msats` | 9734, 9735, 9733, 9321, 8333, 9736, 9737 |
| `ZAP_RECIPIENT` | U | Who is paid (NIP-57 `p`). Props: `msats` | same |
| `ZAP_SENDER` | U | Who paid (NIP-57 `P`, the embedded request's author) | 9735 |
| `HIGHLIGHTS` | E, A | The highlighted source | 9802 |
| `HIGHLIGHTS_AUTHOR` | U | Its author | 9802 |
| `RATES` | E, A, U | The rated entity | 34259 |

### Moderation

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `DELETES` | E, A | NIP-09 targets | 5 |
| `REPORTS_USER` | U | A report about the PERSON: it names no event, address or blob | 1984 |
| `REPORTS` | E, A, T | Reported content (T: a blob hash) | 1984 |
| `REPORTS_AUTHOR` | U | The author of reported content | 1984 |
| `LABELS` | E, A, U, T | NIP-32 targets. Props: `labels` (the `l` values, with namespace) | 1985 |
| `MUTES` | U, E, T | A user's own mutes: people, threads, words/hashtags | 10000, 30007 |
| `HIDES` | E, U | A channel moderator hides a message (43) or a user (44) in the channel — moderation, not a personal mute | 43, 44 |
| `APPROVES` | E, A | A community moderator approves a post | 4550 |
| `MODERATOR` | U | A community's moderators | 34550 |

Report props (all three report relations): `report` (the category, Quartz's `ReportType` code),
`report_raw` (the type as written, lowercased). Splitting the relations replaces the `scope`
property of the current graph schema: "user-wide reports of X" is
`COUNT { (x)<-[:REPORTS_USER]-() }`, constant-time.

### Social graph and lists

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `FOLLOWS` | U | The kind 3 follow list — the social graph | 3 |
| `SUBSCRIBES_TO` | U, E, A, T | Every other "follow this" list: media follows, communities, public chats, interests (hashtags and interest sets) | 10020, 10004, 10005, 10015 |
| `LISTS` | U, E, A | Membership in a named set or directory: follow sets, starter packs, author lists, trusted lists, calendars, publications, emoji sets | 30000, 39089, 39092, 10017, 10101, 10064, 30392–30395, 31924, 30040, 30045, 10030 |
| `RECOMMENDS` | A | An app-handler recommendation | 31989 |
| `BOOKMARKS` | E, A | Private-ish saves | 10003, 30001, 30003 |
| `CURATES` | E, A | Published curation sets | 30004, 30005, 30006, 30063, 30267, 37517 |
| `PINS` | E | Pinned to a profile or a live stream | 10001, 30311 / 30313 (`pinned`) |

### Badges

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `AWARDS` | U | A badge award's recipients | 8 |
| `BADGE` | A | The badge definition an award or a profile refers to | 8, 30008, 10008 |
| `ACCEPTS` | E | A profile accepting an award | 30008, 10008 |

### Trust (NIP-85)

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `ASSERTS` | U, E, A | The assertion's subject (`d`). Props: `rank`, `followers`, … | 30382, 30383, 30384 |
| `TRUSTS_PROVIDER` | U | A 10040's service for one assertion. Props: `service` (`30382:rank`) — one link per service entry | 10040 |

### Events, calendars, live activities, markets

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `PARTICIPANT` | U | Listed participants / speakers / hosts | 30311, 30312, 30313, 31922, 31923 |
| `RSVPS` | A, E | A calendar RSVP's event | 31925 |
| `PRESENT_IN` | A | Presence in a meeting room | 10312 |
| `RAIDS` | A | A live-activity raid target | 1312 |
| `CLIPS` | A, U | A clip of a stream | 1313 |
| `VOTES_IN` | E | A poll response's poll | 1018 |
| `BIDS_ON` / `CONFIRMS_BID` | E | Marketplace bids | 1021 / 1022 |
| `TIMESTAMPS` | E | An OpenTimestamps proof's target | 1040 |
| `STATUS_OF` | E | A NIP-34 status for a patch / issue | 1630–1633 |
| `UPDATES` | E | A later statement about that event: a PR update's pull request, a channel's new metadata | 1619, 41 |
| `REDIRECTS_TO` | A | A wiki redirect | 30819 |

### Topics and plain tags

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `TOPIC` | T | A hashtag (`t`) | any |
| `TAGGED` | T | Any other allowlisted value tag: `i` (external id), `k`, `l`/`L`, `r` (url), `g` (geohash). The target's name says which | any |

### Fallback

| Relation | Targets | Meaning |
|---|---|---|
| `REFERENCES` | E, A, U | A link a provider names (or a value shaped like an id) that no relation above claims. Props: `tag` |

Until classified, these stay `REFERENCES`:
- NIP-90 DVM requests, results and feedback (5000–7000);
- NIP-29 group events;
- experimental kinds (workouts, geocaching, roadstr, attestations, zap polls);
- wiki merge requests (818 / 819);
- user status (30315);
- zap goals (9041);
- classifieds (30402);
- video collaboration (34238).

Each is a small, additive classification when someone needs it.

## Open questions for review

Decided:
- **No user-to-user shortcuts.** `FOLLOWS` runs from the kind 3 event, like every other list. There
  are more than twenty people lists, and a shortcut for one invites one for each.
- **The author of acted-on content has its own relation** (rule 3).
- **`kind` stays on the source event only.** A property on billions of links would cost tens of GB,
  and the source node is one hop away. A relation whose counts are needed per kind is split
  instead (as `FOLLOWS` is).

Open:

4. **Naming style.** UPPER_SNAKE, verbs in the present tense, as Neo4j convention has it.
   Alternatives welcome on any row: `THREAD_ROOT` vs `IN_THREAD`, `LISTS` vs `LISTS_MEMBER`,
   `AUTHORED_BY` vs `BY`.
5. **Where it lives.** `nip01Core/links/` (the interface, the value classes, the relation
   constants) plus one `links()` per class, beside its tags. The default (rule 5) sits on `Event`
   and reads the hint providers.
6. **Vocabulary stability.** Adding a relation or classifying a kind is additive. Renaming or
   re-splitting one breaks graph queries, so this review is the cheap moment.

## What changes downstream

- **neo4j-eventstore:** the relationship type is the relation name, and the link's `props` are
  its properties. Its `RoleTable`, most of `LinkRules` and the report logic move here. It keeps
  the curated node values (names, reaction symbol, title), the nsec rule, and the key bounds.
  Graph schema 2.0; nothing is in production yet.
- **Amethyst:** feed filters can read the same links instead of re-deriving roles (optional,
  incremental).

## Upstream fixes found on the way

These were already catalogued in neo4j-eventstore's `docs/appendix-providers.md`:
- `ListEntityExt.pubKeys()` maps an `nsec` to its hex (a private key) as a "linked pubkey";
- `QTag.parseAddressId` rejects every address;
- `ChannelCreateEvent.linkedEventIds()` returns its own id;
- `ZapReceiptEvent` omits the zap sender.

New: two classes claim kind **1010**, `experimental/edits/TextNoteModificationEvent` and
`nip51Lists/goodWikiRelayList/GoodWikiRelayListEvent`. `EventFactory` can only type one of them.

## Plan

1. This review: the vocabulary, the model, the open questions.
2. Quartz: `nip01Core/links/` and the default from the hint providers; then `links()` for the
   kinds the graph already interprets (NIP-10, 18, 22, 25, 56, 57, 85, 51, 58, 72, 09), each with
   a golden test. The upstream fixes above land with them.
3. neo4j-eventstore: derive from `links()`, schema 2.0, rewrite `docs/schema.md` and the reference
   queries.
4. The remaining kinds, as someone needs them.
