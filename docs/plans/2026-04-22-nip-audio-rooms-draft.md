# NIP-XX — Interactive Audio Rooms Join Protocol

`draft` `optional`

## Abstract

This NIP specifies the client/server control plane and real-time audio
transport for joining a NIP-53 Meeting Space (kind `30312`) as a listener
or speaker. It closes the gap between NIP-53's room discovery (which
defines only *what* a room is) and what an audio-capable client must do
to actually hear and speak in it.

A standard, vendor-neutral profile lets multiple server implementations
interoperate with multiple clients. A client that follows this NIP will
work against any server that advertises compliance, and vice versa.

## Dependencies

- [NIP-53: Live Activities](https://github.com/nostr-protocol/nips/blob/master/53.md)
  defines kind `30312` (Meeting Space), kind `10312` (Room Presence),
  and kind `1311` (Live Activity Chat).
- [NIP-98: HTTP Auth](https://github.com/nostr-protocol/nips/blob/master/98.md)
  defines kind `27235` and the `Authorization: Nostr <base64-event>` header.
- [IETF `moq-transport`](https://datatracker.ietf.org/doc/draft-ietf-moq-transport/)
  and [IETF `webtransport-http3`](https://datatracker.ietf.org/doc/draft-ietf-webtrans-http3/)
  provide the audio transport substrate.

## Terminology

- **Host** — the pubkey that signed the kind `30312` event.
- **Speaker** — any pubkey listed in the `30312` event's `p` tags with role
  `host` or `speaker`.
- **Listener** — any other participant (including un-tagged members of the
  audience).
- **MoQ endpoint** — the WebTransport URL a server returns from its room-info
  HTTP endpoint; where audio flows once the session is established.

## Event kinds used

### Kind 30312 (already defined by NIP-53)

Clients and servers compliant with this NIP MUST honour these tags:

| Tag | Meaning | Required |
|---|---|---|
| `d` | Room identifier. The "room id" everywhere else in this spec. | yes |
| `room` | Human-readable display name. | recommended |
| `summary` | Short description. | optional |
| `image` | Room cover URL. | optional |
| `status` | `open` \| `private` \| `closed`. | yes |
| `service` | HTTPS base URL of a server implementing the HTTP control plane defined below. | yes |
| `streaming` | Present for non-audio streams (e.g. `wss+livekit://…`, HLS). **MAY be omitted** for pure audio rooms — in that case audio flows via the MoQ endpoint returned by the `service` URL. | optional for audio rooms |
| `p` | `["p", <pubkey>, <relay-hint>?, <role>?, <proof>?]` — role is `host` or `speaker`. | one `host` required |

A client MUST reject a room whose `service` URL is not HTTPS.

### Kind 10312 (Room Presence) — extended

NIP-53 defines this event with an optional `["hand", "1"|"0"]` tag. This NIP
adds a second optional tag:

| Tag | Meaning |
|---|---|
| `["muted", "1"]` or `["muted", "0"]` | The participant's microphone is muted (`1`) or hot (`0`) at the time of publish. Absent means unspecified. |

Servers and peers MUST NOT rely on `muted` for authorization — it's a UI
signal, not an access control. The server still enforces who is allowed to
publish audio via the MoQ session.

### Kind 1311 (Live Activity Chat)

Used per NIP-53 with no changes. Each room's chat is scoped by the `a` tag
pointing at the `30312` event.

## HTTP control plane

### Base URL

The `service` tag from the kind `30312` event is the base URL. All control-
plane requests are constructed as:

```
GET  <service>/<room-d-tag>            — room-info / join
```

where `<room-d-tag>` is the `d` tag value of the kind `30312` event,
URL-path-encoded.

The server MAY expose additional paths under the same base (e.g.
`<service>/` for server metadata, `<service>/.well-known/nostr-audio-rooms`
for discovery). This NIP only specifies `<service>/<room-d-tag>`.

### Authentication

Every request MUST carry a NIP-98 `Authorization` header:

```
Authorization: Nostr <base64(kind-27235-event)>
```

The kind `27235` event MUST have:

- `["u", "<fully-qualified-URL-being-requested>"]`
- `["method", "GET"]` (or `POST`, `DELETE`, etc. matching the request verb)
- `created_at` within 60 s of the server's clock
- A valid signature

The server SHOULD reject requests older than 60 s with `401 Unauthorized`.
Servers MAY also reject requests whose signer isn't allowed in the room
(e.g. the room is `private` and the signer isn't on the allow-list).

### Join / room-info response

`GET <service>/<room-d-tag>` with a valid NIP-98 header returns a JSON body
with `Content-Type: application/json`:

```json
{
  "endpoint": "https://relay.example.com:4443/moq",
  "token": "eyJhbGciOi…",
  "transport": "webtransport",
  "codec": "opus",
  "sample_rate": 48000,
  "frame_duration_ms": 20,
  "moq_version": "draft-17"
}
```

Fields:

| Field | Type | Required | Meaning |
|---|---|---|---|
| `endpoint` | string (https URL) | yes | WebTransport URL the client connects to. |
| `token` | string | yes | Opaque bearer token the client passes to the WebTransport layer (see below). |
| `transport` | string | no, defaults to `"webtransport"` | Reserved for future transports. Clients MUST fail closed on unknown values. |
| `codec` | string | no, defaults to `"opus"` | Audio codec name. Reserved for future codecs. Clients MUST fail closed on unknown values. |
| `sample_rate` | integer | no, defaults to `48000` | Samples per second. |
| `frame_duration_ms` | integer | no, defaults to `20` | Audio frame duration. |
| `moq_version` | string | no, defaults to server's preferred | Identifier of the MoQ-transport draft the server speaks (e.g. `"draft-17"`). Clients MUST include this version (and nothing else) in CLIENT_SETUP. |

Unknown fields MUST be ignored by the client to allow forward-compatible
server extensions.

Error responses are standard HTTP status codes with an optional JSON body
`{"error": "<short-code>", "reason": "<human-readable>"}`:

| Status | When |
|---|---|
| `401` | NIP-98 missing, expired, or signature invalid. |
| `403` | Signer isn't allowed in this room (e.g. room `closed`, signer blocked). |
| `404` | Room `d` tag unknown to this server. |
| `410` | Room has ended. |
| `503` | Server is healthy but audio backend is unavailable. |

## Audio transport

### WebTransport

Clients open a WebTransport session against the `endpoint` URL using the
Extended CONNECT handshake ([RFC 9220](https://www.rfc-editor.org/rfc/rfc9220)
+ the [WebTransport-HTTP/3 draft](https://datatracker.ietf.org/doc/draft-ietf-webtrans-http3/)).
The HTTP CONNECT request MUST carry:

```
:method: CONNECT
:protocol: webtransport
:scheme: https
:authority: <host[:port] from endpoint>
:path: <path from endpoint>
Authorization: Bearer <token from room-info response>
```

Servers MUST:

- advertise `SETTINGS_ENABLE_CONNECT_PROTOCOL = 1` ([RFC 8441](https://www.rfc-editor.org/rfc/rfc8441)),
- advertise `SETTINGS_ENABLE_WEBTRANSPORT = 1`,
- advertise `SETTINGS_H3_DATAGRAM = 1` ([RFC 9297](https://www.rfc-editor.org/rfc/rfc9297)).

### MoQ session

After the WebTransport session is open, the client opens its first
bidirectional stream as the MoQ control stream and sends `CLIENT_SETUP`
advertising the `moq_version` from the room-info response. The server replies
with `SERVER_SETUP` selecting that version, or closes the session.

### Track namespace + name

A speaker's audio track is published by the server and subscribed-to by
clients under:

```
track_namespace = [ <room-d-tag> ]
track_name      = <speaker-pubkey-hex>     (64 lowercase hex chars)
```

The namespace is a **one-element tuple** containing the room's `d` tag. It is
intentionally vendor-neutral: there is no `"nests"` or server-brand prefix.

Rationale: the namespace is uniquely keyed by the NIP-53 room id and is
sufficient for a client to subscribe without knowing anything about the
server's brand. Multiple servers hosting rooms with the same `d` tag is
already not a concern — a `d` tag is unique under a host pubkey, and the
room-info response identifies exactly which server's MoQ endpoint the
client must connect to.

### Audio objects

Each OBJECT on a speaker's track carries one Opus packet as its payload:

- Opus encapsulated in **raw packet form** (no Ogg / no TOC-prefix), as
  produced by `libopus` / Android `MediaCodec("audio/opus")` encoder output.
- `sample_rate` from the room-info response (default 48 000 Hz).
- `frame_duration_ms` from the room-info response (default 20 ms).
- Mono (single channel), signed 16-bit PCM domain, VBR encoding.

Object delivery MAY use either:

- **OBJECT_DATAGRAM** (lowest latency, lossy) — recommended for live audio.
- **STREAM_HEADER_SUBGROUP** uni-streams (reliable) — MAY be used by servers
  that need delivery guarantees; clients MUST support receiving both.

`group_id` on each object is the speaker's monotonic group counter (one
group per speaker session). `object_id` is the zero-based Opus packet index
within the group. `publisher_priority` is `0x80` unless the server has
reason to vary it.

### Server-to-client vs. client-to-server audio

A **listener** SUBSCRIBEs to each speaker's track namespace + track name.
The server MUST accept SUBSCRIBEs from any authenticated client (subject to
its access control).

A **speaker** ANNOUNCEs `[ <room-d-tag> ]` and publishes objects on track
name `<speaker-pubkey-hex>`. The server MUST verify that the announcing
pubkey is listed in the room's `p` tag set with role `host` or `speaker`
**at the current moment** (the room event is replaceable — the set can
change). On role revocation, the server MUST close the publisher's track.

### Leaving

To leave, the client SHOULD:

1. UNSUBSCRIBE every track it had open.
2. If it was a speaker, send UNANNOUNCE + `SubscribeDone` for its own track.
3. Publish a final kind `10312` presence event (optional, improves UX for
   other peers) with `["muted","1"]` and without `["hand","1"]`.
4. Close the WebTransport session with capsule type `0x2843`
   (`WT_CLOSE_SESSION`, code `0`).

The server SHOULD treat 30 s without a kind `10312` refresh (per NIP-53) as
"left" for UI purposes.

## Chat

Per NIP-53 kind `1311`. No additions.

## Server requirements summary

A server claiming this NIP MUST:

1. Accept NIP-98-signed requests at `<service>/<d-tag>` and return the
   JSON shape above on success.
2. Enforce the `status` on the kind `30312` event (reject join when
   `closed`).
3. Enforce `p` tag role for publishers (speakers only).
4. Run a WebTransport / MoQ endpoint that speaks the `moq_version`
   advertised in its room-info response.
5. Publish each speaker's Opus audio on track
   `[<d-tag>] / <speaker-pubkey-hex>` and accept SUBSCRIBEs from any
   authenticated listener.
6. Not require any fields beyond those listed in this NIP for basic
   interop (but MAY include additional fields in its JSON response for
   server-specific features; clients ignore unknown fields).

## Client requirements summary

A client claiming this NIP MUST:

1. Resolve the `service` URL via NIP-98 GET before opening a transport.
2. Establish WebTransport against the returned `endpoint` with the
   returned `token` as a Bearer header.
3. Run the MoQ handshake at the returned `moq_version`.
4. SUBSCRIBE to one track per `host`+`speaker` pubkey listed in the
   room event.
5. Decode Opus per the returned `sample_rate` / `frame_duration_ms`.
6. Publish kind `10312` presence no less frequently than every 30 s while
   joined, with `["muted", …]` reflecting the local user's microphone
   state and `["hand", "1"]` when requesting to speak.
7. Fail closed on unknown `transport`, unknown `codec`, or missing
   `endpoint` fields.

## Reference implementation

A compliant Kotlin/Android reference client is in development at
[`amethyst/nestsClient`](../..) — see `docs/plans/2026-04-22-pure-kotlin-quic-webtransport-plan.md`
for the transport work-in-progress.

## Known divergences from current nostrnests/nests servers

At the time of writing, existing `nostrnests/nests` deployments use:

- MoQ track namespace `[ "nests", <d-tag> ]` (two elements, `"nests"` prefix).
  This NIP specifies `[ <d-tag> ]` (one element). Existing nests servers
  SHOULD accept both for a transition period; new deployments SHOULD use the
  one-element form.
- A `/api/v1/nests/<d-tag>` path convention. This NIP leaves the path
  entirely to the `service` tag — servers are free to pick any path.

A "compliance" phase is proposed where a server can advertise
`"nip_xx": true` in its room-info JSON response to signal it implements
this NIP verbatim. Clients MAY use that flag to choose the vendor-neutral
namespace when present, and fall back to the legacy nests convention when
absent.

## Security considerations

- The `token` returned by the server is a bearer token. Clients MUST NOT log
  it. Servers MUST scope it to a single room + pubkey and SHOULD expire it
  within the room's lifetime.
- The server MUST validate the NIP-98 event's `u` tag matches the exact
  request URL, preventing token reuse across rooms.
- Audio published by a pubkey `P` on this server can be replayed by a
  malicious peer claiming to be `P` on a different server — the listener
  has no signature on the Opus packets themselves. If end-to-end authenticity
  matters, a future NIP can extend this by signing each object with the
  speaker's Nostr key; this NIP leaves that out of scope.
- A malicious server can serve silent audio while presence indicators show
  a speaker talking. Clients SHOULD expose signal-level metering (VU meter)
  so users can spot this.

## Discussion

Send feedback to the author via Nostr or GitHub issues on the reference
client repository.
