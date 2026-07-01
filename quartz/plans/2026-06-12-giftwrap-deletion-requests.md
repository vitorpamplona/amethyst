# TODO: Deletion Requests (kind 5) for Gift Wraps

> **Status:** queued — none of the four steps landed: `HostStub` still lacks a `recipient` field and `DeletionIndex` has no gift-wrap recipient-authored handling; header itself reads "Open — not implemented".
> _Audited 2026-06-30._

**Date:** 2026-06-12
**Status:** Open — design agreed, not implemented
**Modules:** quartz (`DeletionIndex`), amethyst (`LocalCache`)

## The special case

Gift wraps (kind 1059) are signed by a discarded throwaway key, so the
normal NIP-09 rule — a deletion only applies when its author equals the
target's author — can never match a wrap. The intended rule: **a kind-5
authored by the wrap's `p` tag (the recipient) may delete it.** A
recipient deleting their own received wrap is also the only deletion a
client can express without leaking the private rumor id (the rumor id
must never appear in a public kind-5).

## Current behavior (verified 2026-06-12)

`DeletionIndex` is strictly author-keyed (`DeletionRequest(targetId,
deleterPubkey)`), the live-delete path requires `deleteNote.author ==
deletion.pubKey`, and no downward cascade (wrap → seal → rumor) exists —
`deleteEnvelopes` only walks upward via `Note.rumorHost`.

| Deletion e-tags | authored by | live message deleted? | blocks later insert? |
|---|---|---|---|
| wrap id | recipient (p tag) | no (key mismatch + no cascade + wrap note usually GC'd) | no (tombstone keyed `(wrapId, recipient)`, check uses `(wrapId, throwawayKey)`) |
| seal id | sender | seal note only; message survives (no cascade) | **yes** — accidental: seals are sender-signed, and `GiftWrapEventHandler` gates the unwrap on `justConsume(seal)` |
| rumor id | sender | yes (private un-react path; `deleteEnvelopes` cascades upward) | yes |

Only the rumor-id direction works; the wrap-id direction — the one the
special case describes — does nothing.

## Implementation sketch

1. **Insertion blocking (quartz, small):** in `DeletionIndex.hasBeenDeleted`,
   when the event is a `GiftWrapEvent`, additionally check
   `DeletionRequest(event.id, event.recipientPubKey())`. A
   recipient-authored tombstone then blocks the wrap in `justConsume`
   before it is ever unwrapped, which blocks the message.

2. **Recipient on the stub (commons, tiny):** add `recipient: HexKey?` to
   `HostStub` (one shared-string reference per rumor), populated from
   `GiftWrapEvent.recipientPubKey()` at unseal time, so the validation
   below works after the wrap note is GC'd.

3. **Live cascade (amethyst `LocalCache.consume(DeletionEvent)`):** the
   wrap note that knew its `innerEventId` is GC'd by the time a deletion
   arrives, so find the rumor by reverse lookup: scan notes for
   `note.rumorHost?.id == deletedId` (precedent: the addressable pass in
   the same function already does a full `notes.forEach`; deletions are
   rare). Validate `deletion.pubKey == note.rumorHost.recipient`, then
   `deleteNote(rumor)` — envelope cleanup and chatroom removal already
   follow from the existing deleted-notes pipeline.

4. **Tests:** block-before-unwrap (tombstone first, wrap second → message
   never materializes) and delete-after-unwrap (message in a chatroom,
   recipient-authored kind-5 for the wrap id → rumor and envelopes gone).

Note: only wraps addressed to the local user are ever in the cache, so
the live-cascade case in practice means "another of my devices retracted
a DM" — rare, not a hot path.
