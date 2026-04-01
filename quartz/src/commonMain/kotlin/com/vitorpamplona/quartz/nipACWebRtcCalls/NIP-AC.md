NIP-AC
======

WebRTC Calls
------------

`draft` `optional`

This NIP defines a protocol for establishing peer-to-peer voice and video calls between Nostr users using WebRTC, with Nostr relays serving as the signaling transport.

## Motivation

Nostr users currently lack a way to make real-time voice or video calls without relying on centralized services. By using Nostr relays for WebRTC signaling and public STUN servers for NAT traversal, calls can be established in a fully decentralized manner — no custom server infrastructure is required. Once a WebRTC peer connection is established, the relay is no longer involved in the media stream.

## Overview

The protocol works as follows:

1. **Caller** creates a signed call offer event containing an SDP offer
2. The event is **gift-wrapped** ([NIP-59](https://github.com/nostr-protocol/nips/blob/master/59.md)) and published to relays
3. **Callee** unwraps the event, verifies the signature, and decides whether to accept
4. If accepted, callee sends back a gift-wrapped call answer event containing an SDP answer
5. Both parties exchange **ICE candidates** as gift-wrapped events for NAT traversal
6. A **direct WebRTC peer connection** is established for audio/video

All signaling events MUST be gift-wrapped using [NIP-59](https://github.com/nostr-protocol/nips/blob/master/59.md) for metadata privacy. Events are signed by the sender's key and wrapped directly (without the seal layer) — the gift wrap's random ephemeral key already hides the sender from relay operators.

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
| `p`           | Hex pubkey of the recipient                           | YES      |
| `call-id`     | UUID identifying the call session                     | YES      |
| `expiration`  | Unix timestamp ([NIP-40](https://github.com/nostr-protocol/nips/blob/master/40.md)), SHOULD be ~5 minutes from `created_at` | YES      |
| `alt`         | Human-readable description ([NIP-31](https://github.com/nostr-protocol/nips/blob/master/31.md)) | YES      |

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
    ["expiration", "1234568190"],
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
    ["expiration", "1234568195"],
    ["alt", "WebRTC call answer"]
  ],
  "id": "<event-id>",
  "sig": "<signature>"
}
```

### ICE Candidate (kind 25052)

The `content` field contains the ICE candidate as a JSON string with the fields `candidate`, `sdpMid`, and `sdpMLineIndex`.

```json
{
  "kind": 25052,
  "pubkey": "<sender-hex-pubkey>",
  "created_at": 1234567896,
  "content": "{\"candidate\":\"candidate:842163049 1 udp 1677729535 203.0.113.1 44323 typ srflx raddr 0.0.0.0 rport 0 generation 0\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}",
  "tags": [
    ["p", "<peer-hex-pubkey>"],
    ["call-id", "550e8400-e29b-41d4-a716-446655440000"],
    ["expiration", "1234568196"],
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
    ["expiration", "1234568300"],
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
    ["expiration", "1234568193"],
    ["alt", "WebRTC call rejection"]
  ],
  "id": "<event-id>",
  "sig": "<signature>"
}
```

### Call Renegotiate (kind 25055)

Used for mid-call changes such as toggling video on/off. The `content` field contains a new SDP offer.

```json
{
  "kind": 25055,
  "pubkey": "<sender-hex-pubkey>",
  "created_at": 1234568100,
  "content": "v=0\r\no=- 4611731400430051338 3 IN IP4 127.0.0.1\r\n...",
  "tags": [
    ["p", "<peer-hex-pubkey>"],
    ["call-id", "550e8400-e29b-41d4-a716-446655440000"],
    ["expiration", "1234568400"],
    ["alt", "WebRTC call renegotiation"]
  ],
  "id": "<event-id>",
  "sig": "<signature>"
}
```

## Encryption and Delivery

All signaling events MUST be delivered using [NIP-59](https://github.com/nostr-protocol/nips/blob/master/59.md) Gift Wraps:

1. **Sign** the signaling event with the sender's key
2. **Gift-wrap** the signed event directly using `GiftWrapEvent` (kind 1059) with NIP-44 encryption
3. **Publish** the gift wrap to the recipient's relay list

The seal layer (`SealedRumorEvent`) is NOT used. The gift wrap already provides:

- **NIP-44 encryption** — content is unreadable to relay operators
- **Random ephemeral pubkey** — the relay cannot identify the sender
- **`p` tag** — reveals only the recipient (necessary for delivery)

Recipients unwrap the gift, verify the inner event's signature against the sender's pubkey, and then process the signaling message.

## Protocol Flow

### Initiating a Call

```
Caller                          Relay                           Callee
  |                               |                               |
  |-- GiftWrap(CallOffer) ------->|                               |
  |                               |-- GiftWrap(CallOffer) ------->|
  |                               |                               |
  |                               |         [Callee unwraps, verifies signature]
  |                               |         [Checks: is caller followed?]
  |                               |         [YES → ring / NO → ignore]
  |                               |                               |
  |<-- GiftWrap(CallAnswer) ------|<-- GiftWrap(CallAnswer) ------|
  |                               |                               |
  |<-> GiftWrap(IceCandidate) <-->|<-> GiftWrap(IceCandidate) <-->|
  |                               |                               |
  |============= WebRTC P2P Connection Established ===============|
  |                 (relay no longer involved)                     |
```

### Ending a Call

Either party may send a `CallHangup` (kind 25053) at any time. The recipient SHOULD close the WebRTC peer connection and release media resources upon receiving it.

### Rejecting a Call

The callee may send a `CallReject` (kind 25054) instead of a `CallAnswer`. The caller SHOULD stop ringing and display a "call rejected" state.

## Spam Prevention

Clients SHOULD implement call filtering:

- **Follow-gated ringing**: Only display incoming call notifications for users in the recipient's follow list. Calls from non-followed users SHOULD be silently ignored.
- **Rate limiting**: Clients SHOULD ignore duplicate call offers from the same pubkey within a short window.
- **Expiration enforcement**: Clients MUST check the `expiration` tag and discard signaling events that have expired.

## NAT Traversal

This NIP does not mandate specific STUN or TURN servers. Clients SHOULD:

- Ship with a default set of public STUN servers (e.g., `stun:stun.l.google.com:19302`)
- Allow users to configure custom TURN servers for restrictive network environments
- Use trickle ICE (sending candidates as they are discovered) rather than waiting for all candidates before sending the offer/answer

## Implementation Notes

- The `call-id` tag MUST be a UUID that is unique per call session. All signaling events for the same call share the same `call-id`.
- Events SHOULD have short expiration times (~5 minutes) since signaling data is ephemeral and has no long-term value.
- Clients SHOULD implement a ringing timeout (e.g., 60 seconds). If no answer is received, the call transitions to a "timed out" state.
- Clients SHOULD use a foreground service or equivalent mechanism to keep calls active when the app is backgrounded.
- The WebRTC `PeerConnection` SHOULD use Unified Plan SDP semantics.
- Clients MAY support call renegotiation (kind 25055) for toggling video on/off mid-call without tearing down the connection.

## References

- [NIP-01: Basic Protocol](https://github.com/nostr-protocol/nips/blob/master/01.md) — Event structure
- [NIP-31: Alt Tag](https://github.com/nostr-protocol/nips/blob/master/31.md) — Human-readable event descriptions
- [NIP-40: Expiration](https://github.com/nostr-protocol/nips/blob/master/40.md) — Event expiration timestamps
- [NIP-44: Encryption](https://github.com/nostr-protocol/nips/blob/master/44.md) — XChaCha20-Poly1305 encryption
- [NIP-59: Gift Wraps](https://github.com/nostr-protocol/nips/blob/master/59.md) — Encrypted event delivery
- [WebRTC Specification](https://www.w3.org/TR/webrtc/) — Peer-to-peer real-time communication
- [RFC 8445: ICE](https://datatracker.ietf.org/doc/html/rfc8445) — Interactive Connectivity Establishment
- [nostr-protocol/nips#771](https://github.com/nostr-protocol/nips/issues/771) — WebRTC signaling discussion
