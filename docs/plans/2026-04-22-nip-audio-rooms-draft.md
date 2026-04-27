# NIP-XX — Interactive Audio Rooms Join Protocol

`draft` `optional`

> **Revision 2026-04-26.** Replaces the IETF MoQ-transport / `GET <service>/<room-d-tag>`
> sketch from the original draft. The wire described here matches the
> `nostrnests/nests` + `kixelated/moq` reference deployment running in
> production today, and is what `amethyst/nestsClient` implements.
> See "Reconciliation with previous spec drafts" near the bottom.

## Abstract

This NIP specifies the client/server control plane and real-time audio
transport for joining a NIP-53 Meeting Space (kind `30312`) as a listener
or speaker. It closes the gap between NIP-53's room discovery (which
defines only *what* a room is) and what an audio-capable client must do
to actually hear and speak in it.

The transport is **moq-lite Lite-03** over WebTransport with a thin
NIP-98-authenticated HTTP `/auth` endpoint that mints an ES256 JWT. A
client that follows this NIP will work against any server speaking the
same wire shape.

## Dependencies

- [NIP-53: Live Activities](https://github.com/nostr-protocol/nips/blob/master/53.md)
  defines kind `30312` (Meeting Space), kind `10312` (Room Presence),
  and kind `1311` (Live Activity Chat).
- [NIP-98: HTTP Auth](https://github.com/nostr-protocol/nips/blob/master/98.md)
  defines kind `27235` and the `Authorization: Nostr <base64-event>` header.
- [IETF `webtransport-http3`](https://datatracker.ietf.org/doc/draft-ietf-webtrans-http3/)
  carries the audio transport.
- **moq-lite Lite-03** (kixelated/moq) — the application-level framing
  on top of WebTransport. Selected by ALPN `"moq-lite-03"`. Wire spec:
  `kixelated/moq-rs/rs/moq-lite/src/`. NOT to be confused with IETF
  `draft-ietf-moq-transport`, which is wire-incompatible.

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

### Kind 4312 (Admin Command) — moderation

Ephemeral event used by hosts and admins to issue moderation actions:

```
{ "kind": 4312, "content": "",
  "tags": [
    ["a", "30312:<host-pubkey>:<room-d-tag>"],
    ["p", "<target-pubkey>"],
    ["action", "kick" | ...] ] }
```

Recipients matching the `p` tag MUST verify the signer is a `host` or
`admin` per the room's current `30312` event before acting. The
target self-disconnects on `action=kick`. The host SHOULD also
re-publish the `30312` with the target's `p`-tag removed so any
future joiner sees the updated roster.

### Kind 10112 (Audio-room server list)

Replaceable event listing a user's preferred MoQ host servers. Wire
shape mirrors NIP-B7's BlossomServersEvent (kind 10063):

```
{ "kind": 10112,
  "tags": [
    ["alt", "Audio-room (nests) MoQ servers used by the author"],
    ["server", "https://moq.nostrnests.com"],
    ["server", "https://moq.example.org"],
    ... ],
  "content": "" }
```

Each `["server", <baseUrl>]` URL is a moq-auth + moq-relay base URL.
Clients SHOULD consume this event when "starting a new space" to
default the `service` / `endpoint` tag fields on the kind-30312
event they're about to publish.

## HTTP control plane

### Base URL

The `service` tag from the kind `30312` event is the base URL of the
auth sidecar (a.k.a. `moq-auth`). It exposes exactly two routes:

```
POST <service>/auth                      — mint a JWT for one room+role
GET  <service>/.well-known/jwks.json     — public keys for JWT verification
```

The server SHOULD also expose `GET <service>/health` returning
`{"status":"ok"}` for liveness probes. Anything else SHOULD return 404.

### Authentication

Every `POST /auth` request MUST carry a NIP-98 `Authorization` header:

```
Authorization: Nostr <base64(kind-27235-event)>
```

The kind `27235` event MUST have:

- `["u", "<fully-qualified-URL-being-requested>"]`
- `["method", "POST"]`
- `["payload", "<sha256-hex of the request body>"]` (NIP-98 §2.2)
- `created_at` within 60 s of the server's clock
- A valid signature

The server MUST reject requests older than 60 s with `401 Unauthorized`.

### Join / room-info response

`POST <service>/auth` body:

```json
{ "namespace": "nests/30312:<host-pubkey>:<room-d-tag>", "publish": false }
```

The `namespace` MUST match the regex
`^nests/\d+:[0-9a-f]{64}:[a-zA-Z0-9._-]+$`
(`<event-kind>:<host-pubkey>:<d-tag>`, where `<event-kind>` is `30312`
for now). `publish` is `true` for a host/speaker minting a publish
token, `false` (or omitted) for a listener.

Response on success:

```json
{ "token": "eyJhbGciOi…" }
```

The `token` is an ES256 JWT signed by the `service` server's keypair;
its public key is available at `<service>/.well-known/jwks.json`. The
relay (`moq-relay`) refreshes the JWKS every 30 s.

JWT claims:

| Claim | Meaning |
|---|---|
| `root` | Echoed `namespace` value. The relay matches this against the WT URL path. |
| `get` | `[""]` — listener may subscribe to anything under `root`. |
| `put` | `[<requester's pubkey>]` — publisher may only `ANNOUNCE` under `root/<pubkey>`. Present only when `publish: true`. |
| `iat` / `exp` | Standard. Token lifetime is **600 s**; clients re-mint on expiry. |

Error responses:

| Status | When |
|---|---|
| `400` | Body missing / malformed JSON / `namespace` fails the regex. |
| `401` | Authorization missing, signature invalid, `u`/`method`/`payload` mismatch, or `created_at` outside ±60 s. |
| `429` | Rate-limited (≥ 20 mint requests per IP per 60 s). |

There is **no per-room HTTP info endpoint, no `/permissions`, no
`/recording*`** — the only mutable per-room state lives in Nostr
events (kind 30312 for the room, 10312 for presence, 1311 for chat,
etc.).

## Audio transport

### WebTransport

Clients open a WebTransport session against the `endpoint` URL using
the Extended CONNECT handshake
([RFC 9220](https://www.rfc-editor.org/rfc/rfc9220) +
[`webtransport-http3`](https://datatracker.ietf.org/doc/draft-ietf-webtrans-http3/)):

```
:method: CONNECT
:protocol: webtransport
:scheme: https
:authority: <host[:port] from endpoint>
:path: /<namespace>?jwt=<token>
```

The path component is the **`namespace`** the JWT was minted for —
i.e. exactly the value sent in the `POST /auth` body. The relay
matches it against `claims.root` and rejects any mismatch with HTTP
401 (`IncorrectRoot`). The token is delivered as the **`?jwt=`** query
parameter; the relay does **not** inspect the `Authorization` header.

Servers MUST advertise `SETTINGS_ENABLE_CONNECT_PROTOCOL = 1`,
`SETTINGS_ENABLE_WEBTRANSPORT = 1`, and `SETTINGS_H3_DATAGRAM = 1`.

### moq-lite session

The application-level framing is **moq-lite Lite-03** (kixelated/moq).
The variant is selected by the WebTransport ALPN — clients SHOULD
advertise `"moq-lite-03"` (and MAY include the legacy `"moql"`).

There is **no in-band SETUP message** in Lite-03 — the WebTransport
handshake itself is the handshake. After the WT session opens, both
sides go straight to opening per-purpose streams.

### Per-bidi `ControlType` discriminator

Every client-initiated bidi opens with a single varint
`ControlType` byte that selects the message family:

| Code | Name |
|---|---|
| 1 | Announce (subscriber-of-announces ↔ publisher) |
| 2 | Subscribe (subscriber → publisher) |
| 3 | Fetch (decl. only; not used for live audio) |
| 4 | Probe (bitrate hint) |

Body framing on every bidi/uni stream is `varint(size) + payload bytes`.
Strings are `varint(length) + UTF-8`. Integers are RFC 9000 §16
varints.

### Speaker — publisher path

The relay opens both `Announce` and `Subscribe` bidis **to** the
publisher (publisher accepts inbound bidis). For each:

- `Announce` body: `AnnouncePlease { prefix: string }`. The publisher
  replies `Announce { status: u8 (0=Ended,1=Active), suffix: string,
  hops: u62 }` with `status=1`, `suffix=<own pubkey>`. Publishers
  MUST emit `status=0` on graceful shutdown.
- `Subscribe` body: `Subscribe { id: u62, broadcast: string,
  track: string, priority: u8, ordered: u8, maxLatencyMillis: varint,
  startGroup: varint, endGroup: varint }`. The publisher replies
  `SubscribeOk { priority, ordered, maxLatencyMillis, startGroup,
  endGroup }`.

Per-group audio bytes flow on **client-initiated uni streams** the
publisher opens. Each uni stream has the layout:

```
DataType varint = 0 (Group)
GroupHeader (size-prefixed): { subscribe: u62, sequence: u62 }
frames until QUIC FIN: { size: varint, payload: <Opus packet> }
```

### Listener — subscriber path

A listener opens its own client-initiated bidis to the relay:

- `Announce` (ControlType=1) with
  `AnnouncePlease { prefix: <empty or namespace prefix> }` to receive
  a flow of `Announce` updates from the relay (one per active
  publisher).
- `Subscribe` (ControlType=2) per `(broadcast, track)` pair the listener
  wants. The relay replies `SubscribeOk` and forwards group uni
  streams as the publisher emits them.

For a nests audio room the listener's wire usage per speaker is:

| Field | Value |
|---|---|
| `broadcast` | `<speaker-pubkey-hex>` (single string, no `nests/` prefix) |
| `track` | `"audio/data"` |
| Optional metadata track | `"catalog.json"` (JSON description of the broadcast — clients MAY skip and just subscribe to `audio/data`). |

Path normalisation (strip leading/trailing/duplicate `/`) is
**mandatory** on every wire boundary; `"/foo//bar/"` and `"foo/bar"`
MUST round-trip identically.

### Audio frame format

Each frame payload on the `audio/data` track is one **Opus packet** in
raw form (no Ogg, no TOC prefix), as produced by `libopus` /
Android `MediaCodec("audio/opus")` output:

- 48 000 Hz, mono, signed-16-bit PCM domain, 20 ms frame duration, VBR.

There is **no per-frame envelope** beyond the size varint — no
timestamp, no codec config, no flags. Receivers reconstruct timing
from frame arrival + the group sequence.

### Unsubscribe / close

There is **no UNSUBSCRIBE message**: a subscriber FINs the send side
of the subscribe bidi and the relay tears down. A publisher closes by
emitting `Announce { status: 0=Ended }` on every active announce bidi
and FINing the current group's uni stream.

Errors on any stream are conveyed by `RESET_STREAM` with a u32 error
code; there is no SUBSCRIBE_ERROR / ANNOUNCE_ERROR message.

### Leaving the room (Nostr-side)

In addition to the wire-level cleanup above, a leaving client SHOULD:

1. Publish a final kind `10312` presence event with
   `["muted","1"]`, `["onstage","0"]`, no `["hand","1"]` to flush
   stale UI on other peers.
2. Close the WebTransport session with capsule type `0x2843`
   (`WT_CLOSE_SESSION`, code `0`).

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

A compliant Kotlin/Android reference client ships in
[`amethyst/nestsClient`](../..). The reference server is the
nostrnests stack:
[`nostrnests/nests`](https://github.com/nostrnests/nests) (auth
sidecar) +
[`kixelated/moq`](https://github.com/kixelated/moq) (relay).

This NIP describes the wire as the reference server speaks it — there
is no "vendor-neutral" alternative being trialled. Any server claiming
compliance MUST speak the moq-lite Lite-03 framing detailed above and
the `POST /auth` HTTP shape verbatim.

## Reconciliation with previous spec drafts

Earlier drafts of this NIP described the audio transport in terms of
IETF `draft-ietf-moq-transport` (CLIENT_SETUP / SERVER_SETUP, namespace
tuples, OBJECT_DATAGRAM) and a `GET <service>/<room-d-tag>` HTTP
control plane returning `{endpoint, token, codec, sample_rate, …}`.

Both have been **superseded**. nostrnests's reference deployment uses
`POST /auth` with a `{namespace, publish}` body returning `{token}`,
and runs moq-lite Lite-03 (single-string broadcast/track names, group-
per-uni-stream framing, no in-band SETUP). IETF MoQ-transport remains
a possible future ALPN, but no audio-rooms server implements it today
and this NIP no longer specifies it.

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
