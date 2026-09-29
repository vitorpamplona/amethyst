# A link vocabulary: what each kind's references MEAN

Status: **draft for review** (2026-09-29). Nothing is implemented yet. Decided in review: links
always start at an event (no user-to-user shortcuts); the author of acted-on content gets its OWN
relation; the kind stays on the source event only; names follow Nostr's own words (rule 7).

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
   `AUTHOR` from an address to its pubkey, which no event states.
2. **One relation per role, across kinds.** `PARENT` is a kind 1 reply's parent, a NIP-22
   comment's parent item, a git reply's and a chat reply's. The source event's `kind` says which;
   a query that cares filters on it (`(c:Event {kind: 1111})-[:PARENT]->(x)`). Kinds are not
   repeated in the name.
3. **The author of acted-on content gets a relation of its own.** A reaction points at the note
   through `REACTED` and at the note's author through `REACTED_AUTHOR`. "Reactions to my notes"
   and "reactions to anything by me" stay one hop, and each is its own constant-time count.
4. **Split a relation when queries separate its meanings on the same target type.** Counting a
   relation per node is constant-time in Neo4j, but filtering on a property reads every edge.
   So a distinction that is filtered all the time becomes two relations: `REPORTED_USER` (a
   complaint about the person) is not `REPORTED_AUTHOR` (the author of reported content), and
   `FOLLOW` (the kind 3 social graph) is not `SUBSCRIBED` (every other follow-like list).
5. **Nothing is invisible before it is classified.** A class that implements no `LinkProvider`
   gets a default `REFERENCE` link (with `via` = the tag it came from) for every id its hint
   providers name AND every generic reference tag whose value has the right shape: `e`/`E`/`q`
   with a 64-hex id, `p`/`P` with a 64-hex key, `a`/`A` with a valid address. The shape half is
   not optional: most kinds Quartz types implement no hint provider (measured below), and without
   it their references would vanish. Classifying a kind later is an additive change.
6. **Values that qualify a link ride on it** (`props`): a report's type, an assertion's rank, a
   zap request's amount. They are what a query filters on after choosing the relation.
7. **Names are Nostr's own words for the slot.** A relation names what the TARGET is to the
   event: its `AUTHOR`, its `ROOT`, its `PARENT`, the `ZAP_RECIPIENT`. Where a NIP has a word for
   the slot, that word is the name: NIP-10's markers (`root`), NIP-22's "root scope" and
   "parent item" (`ROOT`, `PARENT`, `ROOT_AUTHOR`, `PARENT_AUTHOR`), NIP-57's "sender" and
   "recipient", NIP-85's "subject", NIP-58's "badge definition" and "badge award", NIP-18's
   "quote". Where a NIP uses a marker, the marker wins over a friendlier noun: a NIP-28 channel
   message's channel is its `ROOT`, as NIP-28 tags it. Where a NIP has no word, or only a
   generic one ("target"), the name is the past participle of the NIP's action: `REACTED`,
   `REPOSTED`, `REPORTED`, `DELETED`, `TIMESTAMPED`. Lists name their entries the way the list
   names them: a follow list holds `FOLLOW`s, a bookmark list `BOOKMARK`s. Casing is
   UPPER_SNAKE, the Cypher convention, which also keeps relations apart from properties
   (`r.report`).
   - `PARENT`, not NIP-10's `reply` marker: `REPLY_AUTHOR` would read as the author of the
     reply, and `(c)-[:REPLY]->(p)` as if `p` were the reply.

## The vocabulary

Targets: **E** event, **A** address, **U** user, **T** tag value. "Kinds" lists the Quartz classes
the relation comes from today; each row is a golden test when implemented.

### Authorship and identity

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `AUTHOR` | U | The event's signer; from an address, its pubkey (the only link no event states) | every kind; every address |
| `ADDRESS` | A | The addressable event's own address (NIP-01) | 30000–39999 |

### Conversation

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `ROOT` | E, A | The root: NIP-10 `root` (`root()`), NIP-22 root scope (`E`/`A`), and every NIP that reuses the `root` marker — a NIP-28 message's channel (41, 42), a NIP-53 chat's activity (1311) and a presence's room (10312), a NIP-34 status's or PR update's patch/issue/PR (1630–1633, 1619 `E`) | 1, 1111, 1244, 1622, 41, 42, 1311, 10312, 1619, 1630–1633 |
| `PARENT` | E, A | The direct parent: NIP-10 `replyingTo()`, NIP-22 parent item (`e`/`a`), NIP-53's parent space (30313 → 30312), a NIP-34 status's accepted revision. Kind 9 (NIP-C7) puts its parent in a **`q`** tag — the case that shows why tag letters cannot be the schema | 1, 1111, 1244, 1622, 2004, 30818, 14, 42, 1311, 9, 30313, 1630–1633 |
| `ROOT_AUTHOR` | U | The root scope's author (NIP-22 `P`) | 1111, 1244 |
| `PARENT_AUTHOR` | U | The parent item's author (NIP-22 `p`) | 1111, 1244 |
| `MENTION` | E, A, U | Named in passing: a `p` that notifies, a NIP-10 `mention` marker, a `nostr:` URI in the text (NIP-27, `via: content`) | 1, 1111, 9, 24, 42, 1311, 1621, 1622, 9802, 30023, 30817, 30818, … |
| `QUOTE` | E, A | A NIP-18 `q` (except kind 9, where `q` is the parent) | 1, 42, 1111, 1311, 1621, 30023, … |
| `FORK` | E | The event a note forks (the `fork` marker) | 1 |
| `EDITED` | E | The event this one edits | 1010 (TextNoteModification), 3302 |
| `RECIPIENT` | U | A direct or gift-wrapped message's recipients | 4, 14, 15, 24, 1059, 21059 |
| `COMMUNITY` | A | A NIP-72 community a post is submitted to (and an approval's community) | posts tagging a 34550, 4550 |
| `REPOSITORY` | A | A NIP-34 patch's, PR's or issue's repository | 1617, 1618, 1621 |

### Reactions, reposts, zaps

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `REACTED` | E, A, T | The reacted-to content (the last `e`/`a`, `originalPost()`); kind 17 reacts to a URL / external id (T) | 7, 17 |
| `REACTED_AUTHOR` | U | Its author (`originalAuthor()`) | 7 |
| `REPOSTED` | E, A | The reposted content (`boostedEventId()` / `boostedAddress()`) | 6, 16 |
| `REPOSTED_AUTHOR` | U | Its author | 6, 16 |
| `ZAPPED` | E, A | The zapped content. Props: `msats` | 9734, 9735, 9733, 9321, 8333, 9736, 9737 |
| `ZAP_RECIPIENT` | U | Who is paid (NIP-57 `p`, the "recipient"). Props: `msats` | same |
| `ZAP_SENDER` | U | Who paid (NIP-57 `P`, the "sender": the embedded request's author) | 9735 |
| `HIGHLIGHTED` | E, A | The highlighted source | 9802 |
| `HIGHLIGHTED_AUTHOR` | U | Its author | 9802 |
| `RATED` | E, A, U | The rated entity | 34259 |

### Moderation

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `DELETED` | E, A | NIP-09 deletion request targets | 5 |
| `REPORTED_USER` | U | A report about the PERSON: it names no event, address or blob | 1984 |
| `REPORTED` | E, A, T | Reported content (T: a blob hash) | 1984 |
| `REPORTED_AUTHOR` | U | The author of reported content | 1984 |
| `LABELED` | E, A, U, T | NIP-32 label targets. Props: `labels` (the `l` values, with namespace) | 1985 |
| `MUTE` | U, E, T | A mute list's entries: people, threads, words/hashtags | 10000, 30007 |
| `HIDDEN` | E | A NIP-28 "hide message" | 43 |
| `CHANNEL_MUTED` | U | A NIP-28 "mute user": channel moderation, not a personal mute | 44 |
| `APPROVED` | E, A | A NIP-72 approval's post | 4550 |
| `MODERATOR` | U | A community's moderators | 34550 |

Report props (all three report relations): `report` (the category, Quartz's `ReportType` code),
`report_raw` (the type as written, lowercased). Splitting the relations replaces the `scope`
property of the current graph schema: "user-wide reports of X" is
`COUNT { (x)<-[:REPORTED_USER]-() }`, constant-time.

### Social graph and lists

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `FOLLOW` | U | A kind 3 follow list's entries — the social graph | 3 |
| `SUBSCRIBED` | U, E, A, T | Every other "follow this" list: media follows, communities, public chats, interests (hashtags and interest sets) | 10020, 10004, 10005, 10015 |
| `MEMBER` | U, E, A | Membership in a named set or directory: follow sets, starter packs, author lists, trusted lists, calendars, publications, emoji sets | 30000, 39089, 39092, 10017, 10101, 10064, 30392–30395, 31924, 30040, 30045, 10030 |
| `RECOMMENDED` | A | A NIP-89 recommendation's app handler | 31989 |
| `BOOKMARK` | E, A | Bookmark lists' and sets' entries | 10003, 30001, 30003 |
| `CURATED` | E, A | Published curation sets' entries | 30004, 30005, 30006, 30063, 30267, 37517 |
| `PIN` | E | Pinned to a profile or a live stream | 10001, 30311 / 30313 (`pinned`) |

### Badges (NIP-58)

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `AWARDED` | U | A badge award's recipients ("each pubkey the issuer wishes to award") | 8 |
| `BADGE_DEFINITION` | A | The badge definition an award or a profile refers to | 8, 30008, 10008 |
| `BADGE_AWARD` | E | The badge award a profile displays | 30008, 10008 |

### Trust (NIP-85)

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `SUBJECT` | U, E, A | The assertion's subject (`d`). Props: `rank`, `followers`, … | 30382, 30383, 30384 |
| `SERVICE_PROVIDER` | U | A 10040's provider for one assertion. Props: `service` (`30382:rank`) — one link per entry | 10040 |

### Events, calendars, live activities, markets

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `PARTICIPANT` | U | Listed participants / speakers / hosts | 30311, 30312, 30313, 31922, 31923 |
| `CALENDAR_EVENT` | A, E | A calendar RSVP's calendar event | 31925 |
| `RAIDED` | A | A live-activity raid's target | 1312 |
| `CLIPPED` | A | A clip's stream | 1313 |
| `CLIPPED_AUTHOR` | U | The clipped stream's host | 1313 |
| `POLL` | E | A poll response's poll | 1018 |
| `AUCTION` | E | A bid's (and a bid confirmation's) auction | 1021, 1022 |
| `BID` | E | The bid a confirmation confirms | 1022 |
| `TIMESTAMPED` | E | An OpenTimestamps proof's target (NIP-03 says "target", too generic to name a relation) | 1040 |
| `REDIRECT` | A | A wiki redirect's destination | 30819 |

### Topics and plain tags

| Relation | Targets | Meaning | Kinds |
|---|---|---|---|
| `HASHTAG` | T | A `t` tag | any |
| `TAG` | T | Any other allowlisted value tag: `i` (external id), `k`, `l`/`L`, `r` (url), `g` (geohash). The target's name says which | any |

### Fallback

| Relation | Targets | Meaning |
|---|---|---|
| `REFERENCE` | E, A, U | A link a provider names (or a value shaped like an id) that no relation above claims. Props: `tag` |

Until classified, these stay `REFERENCE`:
- NIP-90 DVM requests, results and feedback (5000–7000);
- NIP-29 group events;
- experimental kinds (workouts, geocaching, roadstr, attestations, zap polls);
- wiki merge requests (818 / 819);
- user status (30315);
- zap goals (9041);
- classifieds (30402);
- video collaboration (34238).

Each is a small, additive classification when someone needs it.

## Reading it back

```cypher
// a whole reply tree
MATCH (:Event {id: $root})<-[:PARENT*]-(r) RETURN r

// reactions to my posts by people I follow
MATCH (me:User {pubkey: $me})<-[:AUTHOR]-(:Event {kind: 3})-[:FOLLOW]->(f),
      (f)<-[:AUTHOR]-(r)-[:REACTED_AUTHOR]->(me)
RETURN r

// user-wide reports against X, by category
MATCH (:User {pubkey: $x})<-[r:REPORTED_USER]-() RETURN r.report, count(*)

// who zapped whom, from one sender
MATCH (:User {pubkey: $x})<-[:ZAP_SENDER]-(z)-[:ZAP_RECIPIENT]->(u) RETURN u, sum(z.msats)
```

## Open questions for review

Decided:
- **No user-to-user shortcuts.** `FOLLOW` runs from the kind 3 event, like every other list. There
  are more than twenty people lists, and a shortcut for one invites one for each.
- **The author of acted-on content has its own relation** (rule 3).
- **`kind` stays on the source event only.** A property on billions of links would cost tens of GB,
  and the source node is one hop away. A relation whose counts are needed per kind is split
  instead (as `FOLLOW` is).
- **Names follow Nostr's words** (rule 7), with `PARENT` for the direct parent.

Open:

1. **Where it lives.** `nip01Core/links/` (the interface, the value classes, the relation
   constants) plus one `links()` per class, beside its tags. The default (rule 5) sits on `Event`
   and reads the hint providers.
2. **Vocabulary stability.** Adding a relation or classifying a kind is additive. Renaming or
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

## Coverage

Measured on 2026-09-29: `EventFactory` types **410** classes. The measurement is a text scan, so
the split below is approximate; an exact per-class table is plan step 2a.
- **~150 are classified above.** That covers the NIPs the graph already interprets.
- **~110 carry no references.** Settings, metadata, relay and server lists, key packages,
  ephemeral auth. They need nothing beyond `AUTHOR` (and `ADDRESS`).
- **~150 carry references and are not classified yet.** Only 12 of them implement a hint
  provider; the rest reach the graph only through the shape half of rule 5. The largest groups:
  - `buzz/` (~65 kinds: streams, workflows, jobs, huddles, forums, DMs, moderation);
  - NIP-29 groups (9000–9010, 39000–39005);
  - NIP-43 and buzz relay membership;
  - NIP-47 wallet connect and NIP-46 remote signer traffic;
  - WebRTC calls (25050–25055);
  - NIP-71 videos (21, 22, 34235, 34236);
  - file metadata (1063, 1065);
  - chess (64, Jester);
  - clink, cashu, contextvm, marmot;
  - app data and handlers (78, 30078, 31990);
  - music playlists, interactive stories, attestations, workouts, geocaching, list items
    (9999 / 39999), torrents (2003).

**Enforced, not hoped for:** a Quartz test walks every `EventFactory` kind and fails unless the
class is one of: classified (implements `LinkProvider`), explicitly `REFERENCE`-only, or
explicitly link-free. A new kind then cannot land without a decision about its links.

## Plan

1. This review: the vocabulary, the model, the open questions. Done except the two open points.
   - 2a. The exact per-class coverage table (all 410), generated, as an appendix to this plan.
2. Quartz: `nip01Core/links/` and the default from the hint providers; then `links()` for the
   kinds the graph already interprets (NIP-10, 18, 22, 25, 56, 57, 85, 51, 58, 72, 09), each with
   a golden test. The upstream fixes above land with them.
3. neo4j-eventstore: derive from `links()`, schema 2.0, rewrite `docs/schema.md` and the reference
   queries.
4. The remaining kinds, as someone needs them.
