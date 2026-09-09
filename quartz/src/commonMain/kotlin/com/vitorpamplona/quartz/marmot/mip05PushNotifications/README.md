# Marmot push notifications

`features/push-notifications.md`. Optional: a group MUST keep working when no
member supports push, and nothing in this package can affect group state.

## The shape on the wire

Four kinds, three of them ordinary unsigned inner app payloads carried inside
group messages:

| kind | what it is | file |
|------|------------|------|
| 447 | token request (empty array) or self-update | `TokenRequestEvent` |
| 448 | list response, including other members' records | `TokenListEvent` |
| 449 | removal | `TokenRemovalEvent` |
| 446 | the trigger rumor to a notification server | `NotificationRequestEvent` |

Kind 446 is the odd one: it leaves the group entirely, so the Nostr binding
owns its seal, its recipient addressing and its publish targets.

All four carry `["v", "marmot-push-v1"]`. **This is not a rename of the earlier
`mip05-v1`.** That version carried tokens in `token` tags with empty content,
left the sender's leaf implicit, defined no removals and predated owner
authentication entirely. The two are not interoperable, and refusing the old
string is how they stay apart.

## Owner authentication is the whole design

A record's authority comes from `owner_sig` and current group membership —
never from who carried it. That is what lets one member relay another's records
in a kind 448 so a group converges without every owner being online, while
stopping the relayer from moving a record to another group, repointing it at a
different notification server or relay, swapping the token, or restamping it.

The proof (`PushOwnerProof`) is a BIP-340 signature over the id of an exact,
**unpublished** kind 451 Nostr event. An event id is a ready-made canonical
digest over precisely the tuple that needs binding, and an external signer can
produce it without ever being handed raw bytes. Only the 64-byte signature
travels.

`PushSignedRecord` is the other canonical encoding — a fixed-width byte string
whose SHA-256 is the ordering tie-breaker, and, in a legacy group only, the
preimage of the oldest accepted proof form. It deliberately uses `u16`/`u32`/
`u64` big-endian fields rather than the Marmot binary profile's QUIC varints;
the spec says so in as many words, and substituting a varint would shift every
subsequent field while still looking correct locally.

## Ordering, and why tombstones are durable

The record key is `(member_id_hex, leaf_index, platform, server_pubkey_hex)`.
`leaf_index` is in it because one account can hold several MLS leaves, and
collapsing them would let one device revoke a sibling's live token.

A write wins only when its `(owner_ts, SHA-256(SignedRecord))` is strictly
greater than the key's stored stamp. Never the carrying event's `created_at`,
arrival order, outer event ids, or local receive time — using `owner_ts` is
exactly what makes a relayed kind 448 safe, because the relayer cannot re-sign
it.

A winning removal writes a **tombstone** at its own stamp, and that tombstone is
durable. Any current member can re-emit a revoked-but-still-signed record in a
fresh kind 448 at any later epoch, so the relayed record's carrying epoch is
unbounded and the retained app-payload window cannot bound it. The per-key stamp
is the only thing that recognises such a record as stale. It is cleared on
exactly two events — a strictly-greater-stamped registration, or the owning leaf
leaving the group — and never on a wall clock, an `owner_ts` or an epoch count.
`MarmotPushStateStore` exists for that reason alone; the in-memory default lets
a stale relay win exactly once after a restart.

## Everything here is advisory

A malformed entry, a signature that does not verify, an entry naming a
non-member, a removal matching nothing, a list that loses an ordering race, a
replayed trigger — every one of them drops the offending datum and continues.
None may reject a group message, mutate MLS state, or change which commit wins.
The decoders return what they could read rather than throwing, and
`MarmotPushCoordinator` catches at its own boundary, so a surprise cannot reach
the ingest path that decides whether the carrying kind 445 was valid.

## What is wired, and what is not

Wired: `MarmotPushCoordinator` (in `commons`) produces and consumes 447/448/449
with real kind 451 proofs, assembles the 446 rumor, and persists records, stamps
and tombstones per group. Amethyst holds one per account, applies inbound gossip
in `DecryptAndIndexProcessor`, and answers a peer's request with a kind 448.

**Not wired: registering a token of our own.** `buildSelfUpdate` needs a device
token and Amethyst's notification-server public key. The server key is a
deployment decision — a notification server can only wake the application whose
platform push credentials it holds, so the spec defines no discovery for it and
each application ships its own. Until that key exists, this client is a correct
participant in other members' push routing and announces nothing of its own.

Nothing here publishes a kind 446 either. Selecting records, sealing, wrapping
and choosing publish targets belong to the Nostr binding; `buildTrigger` hands
back the rumor and stops.

## Interop

MDK implements the same adopted shape (`crates/marmot-app/src/notifications.rs`),
but its `wn` CLI exposes no push commands — `notifications` has only
`subscribe` — so there is no way to drive push through the interop harness the
way `amy`/`wn` drive messages, media and streams. Coverage is the spec's own
published removal fixture (event id and `owner_sig`, asserted in
`PushOwnerProofTest`), byte-layout assertions built independently of the encoder,
and the ordering and tombstone rules exercised in `PushRecordStoreTest`.
