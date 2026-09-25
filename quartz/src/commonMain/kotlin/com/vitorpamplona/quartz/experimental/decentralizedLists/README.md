# Decentralized Lists (kinds 9998, 39998, 9999, 39999)

Lists that anyone can add to. Where a NIP-51 list is fully controlled by its
author, a Decentralized List only has its *header* controlled by its author;
the *items* are contributed by the community, one event each, and curation is
left to the reader's trust metric (typically fed by NIP-25 `+`/`-` reactions
to the item events).

This is a pre-NIP wire format drafted by Tapestry
([spec](https://github.com/nous-clawds4/tapestry/blob/main/protocols/nips/decentralized-lists.md)).
The package mirrors `nip88Polls`: one sub-package per role, each with its
events, `TagArrayExt` (readers), `TagArrayBuilderExt` (writers) and `tags/`.

| Kind | Role | Editable | Quartz class |
|---|---|---|---|
| 9998 | list header | no | `header.ListHeaderEvent` |
| 39998 | list header | yes (addressable) | `header.AddressableListHeaderEvent` |
| 9999 | list item | no | `item.ListItemEvent` |
| 39999 | list item | yes (addressable) | `item.AddressableListItemEvent` |

All four implement `DecentralizedListEvent`, whose `listPointer()` is what a
child writes in its `z` tag: the event id for 9998/9999, the
`kind:pubkey:d` coordinate for 39998/39999.

## Header tags

- `["names", <singular>, <plural>]` — required. `titles` and `slugs` have the
  same two-form shape (`SingularPlural`); a tag missing either form is dropped.
- `["required" | "allowed" | "recommended" | "disallowed", <tag name>, <description>?]`
  — one tag per constrained name, with an optional human-readable description
  at index 2 (`TagRule`).
- `["description", <text>]`.

## Item tags

- `["z", <pointer>]` — required, one per parent list. `ParentListTag.classify`
  tells the three allowed shapes apart by form: a 64-hex event id
  (`ParentList.EventId`), a `kind:pubkey:d` coordinate
  (`ParentList.Coordinate`), or anything else, the singular name of an
  undeclared list such as `"dog"` (`ParentList.Name`).
- The items themselves: `p` (pubkeys), `e` (events), `t` (strings, kept in
  their original case — they are values like "Fido", not hashtags) and `a`
  (addressables; the spec's own example uses an `naddr1…`, which parses too).
- Optional `name`, `title`, `slug`, `description`, `comments`.

## The nonstandard method

The spec also allows declaring a list with an *item*: a 9999 on the list of
lists (`["z", "list"]` or `["z", <id of the list of lists>]`) that carries the
header tags. Item events therefore expose the header accessors too
(`names()`, `requiredTags()`, …), and `declaresList()` says whether one is
doing so. The header builder extensions are typed on the whole family for the
same reason.

## Building

```kotlin
val header =
    ListHeaderEvent.build("dog name", "dog names", "Commonly used dog names.") {
        required("t")
        allowed("comments", "Why this name")
    }

// once `header` is signed:
val item =
    ListItemEvent.build(signedHeader) {
        itemString("Fido")
        comments("A classic")
    }
```

## Retrieval

```kotlin
Filter(kinds = listOf(ListHeaderEvent.KIND, AddressableListHeaderEvent.KIND))

// every item on a list, across redundant declarations of it
Filter(
    kinds = listOf(ListItemEvent.KIND, AddressableListItemEvent.KIND),
    tags = mapOf("z" to listOf("dog", header.listPointer())),
)
```

## Search

All four kinds are `SearchableEvent`s sharing one walk
(`forEachSearchableListField`): `names` and `titles` (singular then plural),
`name`, `title`, `description`, `comments`, then every `t` item value.
`content` is not part of the spec, and ids, pubkeys and coordinates are
served by `#p`/`#e`/`#a`/`#z` filters, so none of them are indexed.

## Tapestry extensions

The Tapestry drafts
([`protocols/drafts`](https://github.com/nous-clawds4/tapestry/tree/main/protocols/drafts))
add tags and conventions on these same four kinds. None of them adds a kind.

Concept addresses such as `39998:<assistant>:tag` or `nostr-user-tag` are
published by each deployment's own assistant key, and the drafts forbid
hardcoding them, so every builder and parser below takes them as parameters.

### Tags

| Tag | Draft | Kinds | Quartz |
|---|---|---|---|
| `["b", <kind:pk:d>, "pointer"\|"inherit"\|"inherit-items"]` | Inherit-From | 39998, 39999 | `tags.InheritFromTag`, `inheritFrom()` |
| `["b", "b-tag-deferred"]` | Shared Concepts | 39998, 39999 | `isDeliberatelyUnaffiliated()` |
| `["n", <kind:pk:d>]` / `["s", <kind:pk:d>]` | Class Thread Relationships | 39999 | `item.tags.ElementOfTag` / `SubsetOfTag` |
| `["json", "<json>"]` | Tapestry Concepts | 39998, 39999 | `tags.JsonTag`, `wordWrapper()` → `concepts.WordWrapper` |
| `["concept-graph", "39999:<pk>:<d>-concept-graph"]` | Tapestry Concepts | 39998 | `conceptGraph()`, falls back to the computed address |
| `["item-kind", <kind>, <description>?]` | Cross-NIP Compatibility | headers | `itemKinds()`, `acceptedItemKinds()` |

An unknown or missing `b` type reads as `pointer`, and code that acts on
deference must check for `inherit` / `inherit-items` explicitly.

Events of other NIPs can list themselves by carrying `z` tags (Cross-NIP
Compatibility "Method 3"); `Event.dListParents()` reads them on any kind.

### Taggings (`taggings/`)

All kind 39999 items, told apart by their `z` tags:

- `TagElement`: a tag ("Podcaster"), `d` = slug, JSON in `content`, plus
  optional `tag-for-nostr-pubkey` / `tag-for-nostr-event` hint `z`s.
- `PubKeyTagging`: "Avi is a Podcaster", with the deterministic
  `profile-tag-<slug>-<target8>-<asserter8>` `d`.
- `TagPin`: a viewer's pin with a `curation-method` JSON tag. Unpin with a
  NIP-09 deletion.
- `TaggingHeader` + `EventTagging`: tagging events. The target sits in
  `a`/`e`, so the tag is reached through a `z` to a per-tag header.
- `polarity()`: no tag means apply, `≥ 0.5` applied, `≤ -0.5` disputed, and
  anything in between is not counted in v1.

### Assistant designation (`assistant/`)

- Kind 10040 entries next to NIP-85's: the blanket
  `["39998:dlist-header", <assistant>, <relay>]` and one
  `["<kind>:<d>", <assistant>, <relay>]` per curated list. Read with
  `dListAssistant()` / `dListCurations()`; write with the `replace…` /
  `remove…` helpers, which keep every other tag verbatim. The first
  occurrence wins on duplicates.
- `CurationCopy`: the assistant's copy of an accepted item, with
  `d` = `copy-<sha256(header + "\n" + original ref)>`, two `q` tags back to
  the original, and only the tags the spec lists. `buildRemoval` is the
  matching NIP-09 deletion.
- `HeaderResolution`: which header governs a user's concept. Their own
  header wins, then their assistant's, and recency never decides.
