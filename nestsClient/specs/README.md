# EGGs — Extensible Gossip Guidelines

Wire-protocol specs for nostrnests-style audio rooms. Each EGG defines one
self-contained capability that a client or relay can implement. Two compliant
peers that implement the same set of EGGs round-trip without further
coordination.

Specs intentionally mirror the [Nostr NIP](https://github.com/nostr-protocol/nips)
and [Blossom BUD](https://github.com/hzrd149/blossom) formats: ASCII Markdown,
RFC-2119 keywords (MUST / SHOULD / MAY), one capability per file.

## Index

| #  | File           | Topic                                     | Category   |
|----|----------------|-------------------------------------------|------------|
| 01 | [EGG-01](./EGG-01.md) | Room event (`kind:30312`)         | required   |
| 02 | [EGG-02](./EGG-02.md) | Auth & WebTransport handshake      | required   |
| 03 | [EGG-03](./EGG-03.md) | Audio plane (moq-lite)             | required   |
| 04 | [EGG-04](./EGG-04.md) | Presence (`kind:10312`)            | required   |
| 05 | [EGG-05](./EGG-05.md) | In-room chat (`kind:1311`)         | optional   |
| 06 | [EGG-06](./EGG-06.md) | Reactions (`kind:7`)               | optional   |
| 07 | [EGG-07](./EGG-07.md) | Roles & moderation (`kind:4312`)   | optional   |
| 08 | [EGG-08](./EGG-08.md) | Scheduling (`status=planned`)      | optional   |
| 09 | [EGG-09](./EGG-09.md) | User server list (`kind:10112`)    | optional   |
| 10 | [EGG-10](./EGG-10.md) | Theming (`c`/`f`/`bg`)             | decorative |
| 11 | [EGG-11](./EGG-11.md) | Recording (`recording` tag)        | decorative |
| 12 | [EGG-12](./EGG-12.md) | Catalog track (`catalog.json`)     | optional   |

## Conformance levels

A peer claims **Listener compliance** when it implements EGG-01, EGG-02, EGG-03
and at least the read side of EGG-04.

A peer claims **Speaker compliance** when it implements all of the above plus
the publish side of EGG-03 and the write side of EGG-04.

A peer claims **Host compliance** when it implements Speaker compliance plus
EGG-07 and the write side of EGG-08 (if scheduled rooms are exposed in its UI).

EGG-05 through EGG-12 are independently optional. Lacking any of them MUST NOT
break interop on the EGGs a peer does implement.

## Versioning

Each spec carries a `status` line at the top: `draft` (subject to change),
`accepted` (frozen except for clarifications), or `replaced-by: EGG-XX`
(superseded). Breaking changes ship as a new EGG number; existing numbers are
never re-purposed.

## Naming

EGG = Extensible Gossip Guideline. The acronym is intentional: nostrnests
serves nests; nests hold eggs.

## Filing changes

Edit a single EGG per pull request. Include a wire-format example showing the
delta. If a change crosses two specs, file two PRs and reference each from the
other.
