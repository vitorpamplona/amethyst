# Concord event layer → per-kind Event classes (nip88Polls structure)

## Why

The Concord quartz layer currently centralizes wire kinds in a single
`concord/events/ConcordKinds.kt` constant object and hand-rolls rumors with raw
string tags (see `cord03Channels/ChannelChat.kt`:
`RumorAssembler.assembleRumor(kind = ConcordKinds.MESSAGE, tags = arrayOf(arrayOf("q", …)))`).
Two problems:

1. **Duplicates standard Nostr kinds.** `ConcordKinds.MESSAGE=9`, `REACTION=7`,
   `DELETE=5`, `COMMENT=1111`, `EDIT=3302` shadow `ChatEvent.KIND`,
   `ReactionEvent.KIND`, `DeletionEvent.KIND`, `CommentEvent.KIND`. Concord chat
   rumors *are* those standard events (they already parse back as `ChatEvent`
   etc. on read) — the build side should reuse the classes, not re-derive by
   number.
2. **No per-event structure.** Every other protocol in quartz gives each event
   kind a package: `XxxEvent.kt` (the `Event` subclass + `build`), plus
   `TagArrayBuilderExt.kt` / `TagArrayExt.kt` and a `tags/` folder of typed tag
   classes (`nip88Polls/poll/…` is the reference). Concord instead has loose
   builders (`ChannelChat`, `ControlEditionBuilder`) and stringly-typed tags.

Target: match the `nip88Polls` shape. Reuse standard events where the protocol
uses standard kinds; give each genuinely-Concord kind its own package.

## Reuse standard events (chat plane, CORD-03)

A Concord chat rumor is a standard event + a `["channel", id]` + `["epoch", n]`
binding. Introduce a binding-tag package and reuse the standard builders:

- `cord03Channels/tags/ChannelTag.kt`, `tags/EpochTag.kt` — typed tags.
- `cord03Channels/TagArrayBuilderExt.kt` — `channel(id)`, `epoch(n)` on the DSL.
- `cord03Channels/TagArrayExt.kt` — `channelId()`, `epoch()`, `isBoundTo(...)`.

| rumor | reuse | binding |
|---|---|---|
| message (9) | `ChatEvent.build` | `channel` + `epoch` |
| reply (9 + q) | `ChatEvent.build` + `q`/`p` | `channel` + `epoch` |
| reaction (7) | `ReactionEvent.build` | `channel` + `epoch` |
| delete (5) | `DeletionEvent.build` | `channel` + `epoch` |

`ChannelChat` keeps its public API (returns unsigned rumors via `RumorAssembler`)
but builds tags from the standard event's DSL + the binding ext. Delete
`ConcordKinds.MESSAGE/REACTION/DELETE/COMMENT/EDIT`.

**Interop guard:** the exact on-wire tags must stay byte-identical to today's
output (Armada compat). Keep `["q", parentId]` + `["p", parentAuthor]` for
replies and `["e"/"p"/"k"]` for reactions — do not switch to `QEventTag`'s
3-element form. Verify with `ConcordPlaneRegistryTest` + an amy↔Armada round-trip.

## New per-kind Event classes (genuinely Concord)

Each gets `concord/<cordXX>/<name>/XxxEvent.kt` + `TagArrayBuilderExt` +
`TagArrayExt` + `tags/`:

| kind | event | CORD |
|---|---|---|
| 3308 | `ControlEditionEvent` (from `ControlEdition`/`ControlEditionBuilder`) | 02/04/06 |
| 3303 | `RekeyEvent` | 06 |
| 3306 / 3309 / 3312 | `GuestbookJoinLeaveEvent` / `KickEvent` / `SnapshotEvent` | 02 |
| 3313 | `DirectInviteEvent` | 05 |
| 23311 / 23313 | `TypingEvent` / `VoicePresenceEvent` | 03/07 |
| 3310 | `WebxdcEvent` | 03 |
| 33301 | `InviteBundleEvent` (from `ConcordInviteBundle`) | 05 |
| 13303 | `InviteListEvent` | 05 |
| 20013 / 20014 / 1059 / 21059 | envelope seals + inverted wrap | 01 |

`ConcordCommunityListEvent` (13302) is already an `Event` class (now a
`BaseReplaceableEvent`) — keep, just move under a per-kind package if desired.

After the move, `ConcordKinds` retains only the truly-Concord kinds (envelope,
control, rekey, guestbook, invites, voice), not the standard-Nostr aliases.

## Sequencing (incremental, compile + interop-test each)

1. Chat plane reuse (this doc's first section) — smallest blast radius (4 refs),
   highest clarity. **← start here.**
2. Control plane 3308 → `ControlEditionEvent` package.
3. Invites 33301 / 3313 / 13303.
4. Guestbook + voice + rekey.
5. Envelope seals/wrap.
6. Shrink `ConcordKinds`, delete dead constants, update `EventFactory`.
