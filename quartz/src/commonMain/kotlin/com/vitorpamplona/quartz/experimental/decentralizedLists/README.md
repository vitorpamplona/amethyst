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
