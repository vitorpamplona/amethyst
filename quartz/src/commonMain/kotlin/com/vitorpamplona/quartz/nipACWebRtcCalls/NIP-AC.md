NIP-AC
======

WebRTC Calls
------------

`draft` `optional`

This NIP defines a protocol for establishing private peer-to-peer voice and video calls between Nostr
users using WebRTC, with Nostr relays serving as the signaling transport and public STUN servers for
NAT traversal — no custom server infrastructure is required.

## Overview

The protocol works as follows:

1. **Caller** creates a signed call offer event containing an SDP offer
2. The event is wrapped in an **Ephemeral Gift Wrap** (kind `21059`) and published to relays
3. **Callee** unwraps the event, verifies the signature, and decides whether to accept
4. If accepted, callee sends back a gift-wrapped call answer event containing an SDP answer
5. Both parties exchange **ICE candidates** as gift-wrapped events for NAT traversal
6. A **direct WebRTC peer connection** is established for audio/video

All signaling events MUST be delivered using **Ephemeral Gift Wraps** (kind `21059`), an ephemeral
variant of [NIP-59](https://github.com/nostr-protocol/nips/blob/master/59.md) Gift Wraps. Events are
signed by the sender's key and wrapped directly (without the seal layer) — the ephemeral gift wrap's
random ephemeral key already hides the sender from relay operators. Relays SHOULD treat kind `21059`
events as ephemeral and not persist them to long-term storage.

## Event Kinds

| Kind  | Name                | Description                                  |
|-------|---------------------|----------------------------------------------|
| 25050 | Call Offer          | SDP offer initiating a call                  |
| 25051 | Call Answer         | SDP answer accepting a call                  |
| 25052 | ICE Candidate       | ICE candidate for NAT traversal              |
| 25053 | Call Hangup         | Terminates an active or pending call         |
| 25054 | Call Reject         | Rejects an incoming call                     |
| 25055 | Call Renegotiate    | New SDP offer for mid-call changes           |

## Tags

All signaling events MUST include:

| Tag           | Description                                           | Required |
|---------------|-------------------------------------------------------|----------|
| `p`           | Hex pubkey of the recipient (group calls: one per member) | YES  |
| `call-id`     | UUID identifying the call session                     | YES      |
| `alt`         | Human-readable description ([NIP-31](https://github.com/nostr-protocol/nips/blob/master/31.md)) | YES |

Additional tags for **Call Offer** (kind 25050):

| Tag           | Description                                           | Required |
|---------------|-------------------------------------------------------|----------|
| `call-type`   | `"voice"` or `"video"`                                | YES      |

## Event Structures

### Call Offer (kind 25050)

The `content` field contains the SDP offer string.

```json
{
  "kind": 25050,
  "pubkey": "<caller-hex-pubkey>",
  "created_at": 1234567890,
  "content": "v=0\r\no=- 4611731400430051336 2 IN IP4 127.0.0.1\r\n...",
  "tags": [
    ["p", "<callee-hex-pubkey>"],
    ["call-id", "550e8400-e29b-41d4-a716-446655440000"],
    ["call-type", "video"],
    ["alt", "WebRTC call offer"]
  ],
  "id": "<event-id>",
  "sig": "<signature>"
}
```

### Call Answer (kind 25051)

The `content` field contains the SDP answer string.

```json
{
  "kind": 25051,
  "pubkey": "<callee-hex-pubkey>",
  "created_at": 1234567895,
  "content": "v=0\r\no=- 4611731400430051337 2 IN IP4 127.0.0.1\r\n...",
  "tags": [
    ["p", "<caller-hex-pubkey>"],
    ["call-id", "550e8400-e29b-41d4-a716-446655440000"],
    ["alt", "WebRTC call answer"]
  ],
  "id": "<event-id>",
  "sig": "<signature>"
}
```

### ICE Candidate (kind 25052)

The `content` field contains the ICE candidate as a JSON string with the fields `candidate`, `sdpMid`, and `sdpMLineIndex`. Special characters in the SDP string MUST be properly JSON-escaped.

```json
{
  "kind": 25052,
  "pubkey": "<sender-hex-pubkey>",
  "created_at": 1234567896,
  "content": "{\"candidate\":\"candidate:842163049 1 udp 1677729535 203.0.113.1 44323 typ srflx raddr 0.0.0.0 rport 0 generation 0\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}",
  "tags": [
    ["p", "<peer-hex-pubkey>"],
    ["call-id", "550e8400-e29b-41d4-a716-446655440000"],
    ["alt", "WebRTC ICE candidate"]
  ],
  "id": "<event-id>",
  "sig": "<signature>"
}
```

### Call Hangup (kind 25053)

The `content` field MAY contain a human-readable reason or be empty.

```json
{
  "kind": 25053,
  "pubkey": "<sender-hex-pubkey>",
  "created_at": 1234568000,
  "content": "",
  "tags": [
    ["p", "<peer-hex-pubkey>"],
    ["call-id", "550e8400-e29b-41d4-a716-446655440000"],
    ["alt", "WebRTC call hangup"]
  ],
  "id": "<event-id>",
  "sig": "<signature>"
}
```

### Call Reject (kind 25054)

The `content` field MAY contain a reason or be empty.

```json
{
  "kind": 25054,
  "pubkey": "<callee-hex-pubkey>",
  "created_at": 1234567893,
  "content": "",
  "tags": [
    ["p", "<caller-hex-pubkey>"],
    ["call-id", "550e8400-e29b-41d4-a716-446655440000"],
    ["alt", "WebRTC call rejection"]
  ],
  "id": "<event-id>",
  "sig": "<signature>"
}
```

### Call Renegotiate (kind 25055)

Used for mid-call changes such as toggling video on/off. The `content` field contains a new SDP offer. The recipient MUST respond with a `Call Answer` (kind 25051) containing the SDP answer for the renegotiation.

```json
{
  "kind": 25055,
  "pubkey": "<sender-hex-pubkey>",
  "created_at": 1234568100,
  "content": "v=0\r\no=- 4611731400430051338 3 IN IP4 127.0.0.1\r\n...",
  "tags": [
    ["p", "<peer-hex-pubkey>"],
    ["call-id", "550e8400-e29b-41d4-a716-446655440000"],
    ["alt", "WebRTC call renegotiation"]
  ],
  "id": "<event-id>",
  "sig": "<signature>"
}
```

## Encryption and Delivery

All signaling events MUST be delivered using **Ephemeral Gift Wraps** (kind `21059`):

1. **Sign** the signaling event with the sender's key
2. **Wrap** the signed event directly using `EphemeralGiftWrapEvent` (kind `21059`) with NIP-44 encryption
3. **Publish** the ephemeral gift wrap to the recipient's relay list

The seal layer (`SealedRumorEvent`) is NOT used. The ephemeral gift wrap already provides:

- **NIP-44 encryption** — content is unreadable to relay operators
- **Random ephemeral pubkey** — the relay cannot identify the sender
- **`p` tag** — reveals only the recipient (necessary for delivery)
- **Ephemeral kind (`21059`)** — signals to relays that this event is transient and SHOULD NOT be persisted to long-term storage

No `expiration` tag is needed on the inner signaling events or the outer wrap. The ephemeral kind itself communicates the transient nature of the data. Clients MUST still perform staleness checks (see Spam Prevention) to discard old events.

Recipients unwrap the ephemeral gift, verify the inner event's signature against the sender's pubkey, and then process the signaling message.

## Protocol Flow

### Initiating a Call

```
Caller                          Relay                           Callee
  |                               |                               |
  |-- EphemeralGiftWrap(CallOffer) ------->|                               |
  |                               |-- EphemeralGiftWrap(CallOffer) ------->|
  |                               |                               |
  |                               |         [Callee unwraps, verifies signature]
  |                               |         [Checks: is caller followed?]
  |                               |         [YES → ring / NO → ignore]
  |                               |                               |
  |<-- EphemeralGiftWrap(CallAnswer) ------|<-- EphemeralGiftWrap(CallAnswer) ------|
  |                               |                               |
  |<-> EphemeralGiftWrap(IceCandidate) <-->|<-> EphemeralGiftWrap(IceCandidate) <-->|
  |                               |                               |
  |============= WebRTC P2P Connection Established ===============|
  |                 (relay no longer involved)                     |
```

### ICE Candidate Buffering

ICE candidates may arrive before the WebRTC peer connection is ready (e.g., the callee is still ringing). Clients MUST buffer incoming ICE candidates and apply them after `setRemoteDescription()` succeeds. Candidates buffered while ringing MUST NOT be cleared when accepting the call.

### Mid-Call Renegotiation

Either party may send a `CallRenegotiate` (kind 25055) during an active call to change media parameters (e.g., toggling video on/off). The recipient responds with a `CallAnswer` (kind 25051):

```
Party A                         Relay                           Party B
  |                               |                               |
  |-- EphemeralGiftWrap(Renegotiate) ----->|                               |
  |                               |-- EphemeralGiftWrap(Renegotiate) ----->|
  |                               |                               |
  |                               |  [Party B creates SDP answer] |
  |                               |                               |
  |<-- EphemeralGiftWrap(CallAnswer) ------|<-- EphemeralGiftWrap(CallAnswer) ------|
  |                               |                               |
  |========= Updated WebRTC P2P Connection ========================|
```

### Ending a Call

Either party may send a `CallHangup` (kind 25053) at any time. The recipient SHOULD close the WebRTC peer connection and release media resources upon receiving it.

### Rejecting a Call

The callee may send a `CallReject` (kind 25054) instead of a `CallAnswer`. The caller SHOULD stop ringing and display a "call rejected" state.

## Group Calls

Group calls (calls with more than two participants) use the same event kinds but differ in how `p` tags and gift wraps are structured.

### P-Tag Convention

In a group call, all signaling events (except ICE candidates, kind 25052) MUST include a `p` tag for **every** group member. This allows each recipient to know the full group composition from any signaling event.

ICE candidates (kind 25052) remain addressed to a single peer because WebRTC connections are peer-to-peer — each ICE candidate is relevant only to the specific connection it belongs to.

### Sign Once, Wrap Per Recipient

For events whose content is identical for all recipients (hangup, reject), the event is **signed once** and then gift-wrapped individually for each recipient:

1. **Build** the signaling event with `p` tags for all group members
2. **Sign** the event once with the sender's key
3. **Gift-wrap** the same signed event separately for each member (each wrap encrypted to that member's pubkey)
4. **Publish** each gift wrap to the corresponding member's relay list

This is more efficient than signing a separate event per recipient and ensures cryptographic consistency — every member receives the exact same signed inner event.

### Per-Peer SDP with Group P-Tags

Events carrying SDP payloads (offer, answer, renegotiate) contain session descriptions that are specific to a single `PeerConnection`. In a full-mesh group call, each participant maintains a separate `PeerConnection` per peer, so SDP content differs per connection.

For these events, the inner event still includes `p` tags for **all** group members (so any recipient can see the full group), but:

1. **Build** the event with `p` tags for all group members and the per-peer SDP content
2. **Sign** the event (signed per peer, since the SDP content differs)
3. **Gift-wrap** and send **only to the specific peer** the SDP is intended for

This means offer, answer, and renegotiate events in group calls are signed per-peer but still carry the full group membership in their `p` tags.

### Group Call Offer

The Call Offer (kind 25050) initiating a group call contains multiple `p` tags:

```json
{
  "kind": 25050,
  "pubkey": "<caller-hex-pubkey>",
  "tags": [
    ["p", "<callee-1-hex-pubkey>"],
    ["p", "<callee-2-hex-pubkey>"],
    ["p", "<callee-3-hex-pubkey>"],
    ["call-id", "550e8400-e29b-41d4-a716-446655440000"],
    ["call-type", "video"],
    ["alt", "WebRTC call offer"]
  ]
}
```

Recipients detect a group call by the presence of multiple `p` tags. The full group is the union of all `p`-tagged pubkeys plus the event's `pubkey` (the caller).

### Inviting New Peers

To invite a new peer into an active group call, send a Call Offer (kind 25050) with `p` tags listing **all** existing group members plus the new invitee. This allows the invitee to immediately see the full group composition. The SDP in the offer is specific to the new PeerConnection being established, so the wrap is addressed only to the invitee.

## Spam Prevention

Clients SHOULD implement call filtering:

- **Follow-gated ringing**: Only display incoming call notifications for users in the recipient's follow list. Calls from non-followed users SHOULD be silently ignored.
- **Staleness check**: Clients MUST discard signaling events older than 20 seconds (based on `created_at`). This prevents old cached events from triggering phantom calls on app restart or relay reconnect.
- **Deduplication**: Clients MUST track processed event IDs to prevent the same signaling event (delivered by multiple relays) from being processed twice.
- **Rate limiting**: Clients SHOULD ignore duplicate call offers from the same pubkey within a short window.

## NAT Traversal

This NIP does not mandate specific STUN or TURN servers. Clients SHOULD:

- Ship with a default set of public STUN servers (e.g., `stun:stun.l.google.com:19302`)
- Ship with default TURN servers for relay fallback when direct P2P fails (~20% of cases, including devices on the same WiFi network where hairpin NAT is not supported)
- Allow users to configure custom TURN servers for restrictive network environments
- Use trickle ICE (sending candidates as they are discovered) rather than waiting for all candidates before sending the offer/answer
- Use `GATHER_CONTINUALLY` policy for ongoing ICE candidate discovery

## Implementation Notes

### Event Lifecycle

- The `call-id` tag MUST be a UUID that is unique per call session. All signaling events for the same call share the same `call-id`.
- Signaling data is ephemeral and has no long-term value. Using kind `21059` (Ephemeral Gift Wrap) signals to relays that these events are transient and SHOULD NOT be persisted.
- Clients SHOULD implement a ringing timeout (e.g., 60 seconds). If no answer is received, the call transitions to a "timed out" state.
- After a call ends, the call state SHOULD auto-reset to idle after a brief display period (e.g., 2 seconds) to ensure the client is ready for subsequent calls.

### WebRTC Configuration

- The WebRTC `PeerConnection` SHOULD use Unified Plan SDP semantics.
- Clients MAY support call renegotiation (kind 25055) for toggling video on/off mid-call without tearing down the connection. When a `Call Renegotiate` event is received, the recipient creates a new SDP answer for the renegotiated session and sends it back as a `Call Answer` (kind 25051) with the same `call-id`. The initiator applies this answer via `setRemoteDescription()` to complete the renegotiation.
- ICE candidate JSON content MUST be properly escaped — SDP strings can contain quotes and backslashes that break naive string interpolation.

### Multi-Device Support

When a user is logged in on multiple devices, all devices will receive and ring for incoming calls. To prevent all devices from continuing to ring after one device handles the call:

- When **accepting** a call, the callee SHOULD gift-wrap and publish an additional `Call Answer` (kind 25051) addressed to their **own pubkey** (the `p` tag set to self). Other devices of the same user that receive this self-addressed answer SHOULD stop ringing and transition to an "answered elsewhere" state.
- When **rejecting** a call, the callee SHOULD gift-wrap and publish an additional `Call Reject` (kind 25054) addressed to their **own pubkey**. Other devices SHOULD stop ringing.

These self-notification events use the same `call-id` as the original call and follow the same gift-wrapping rules. Clients receiving a self-addressed answer or reject MUST verify the `call-id` matches the currently ringing call before acting on it.

**Group calls**: In group calls, the sender's own pubkey SHOULD be included in the set of recipients when gift-wrapping answer and reject events. This means the self-notification is implicit — no separate self-addressed event is needed. The same signed inner event (with all group member `p` tags) is simply wrapped to the sender's own pubkey along with all other members.

### Audio and Media

- Clients SHOULD switch `AudioManager` to `MODE_IN_COMMUNICATION` when a call connects and restore to `MODE_NORMAL` when the call ends.
- Clients SHOULD support audio routing between earpiece, speaker, and Bluetooth SCO headsets. If a Bluetooth headset disconnects mid-call, the client SHOULD fall back to earpiece automatically.
- Clients SHOULD play a ringback tone (e.g., `TONE_SUP_RINGTONE`) for the caller while waiting for the callee to answer.
- Clients SHOULD play the device's default ringtone and vibrate when an incoming call arrives from a followed user.

### Platform Integration

- Clients SHOULD use a foreground service (type `microphone`) to keep calls alive when the app is backgrounded.
- Clients SHOULD acquire a proximity wake lock during active calls to turn off the screen when held to the ear.
- Clients SHOULD keep the screen on during active calls.
- Clients MAY enter Picture-in-Picture mode when the user navigates away from the call screen during an active call.
- Clients SHOULD request `RECORD_AUDIO` permission (and `CAMERA` for video calls) at runtime before initiating or accepting a call.

### Error Handling

- If WebRTC session creation fails, the client SHOULD display an error to the user and transition to an ended state.
- If SDP offer/answer creation fails, the client SHOULD surface the error instead of hanging silently.
- Clients SHOULD handle `ICE_CONNECTION_FAILED` state by ending the call and notifying the user of a connection failure.

## References

- [NIP-01: Basic Protocol](https://github.com/nostr-protocol/nips/blob/master/01.md) — Event structure
- [NIP-31: Alt Tag](https://github.com/nostr-protocol/nips/blob/master/31.md) — Human-readable event descriptions
- [NIP-44: Encryption](https://github.com/nostr-protocol/nips/blob/master/44.md) — XChaCha20-Poly1305 encryption
- [NIP-59: Gift Wraps](https://github.com/nostr-protocol/nips/blob/master/59.md) — Encrypted event delivery
- [WebRTC Specification](https://www.w3.org/TR/webrtc/) — Peer-to-peer real-time communication
- [RFC 8445: ICE](https://datatracker.ietf.org/doc/html/rfc8445) — Interactive Connectivity Establishment
- [nostr-protocol/nips#771](https://github.com/nostr-protocol/nips/issues/771) — WebRTC signaling discussion
