# EGG-02: Auth & WebTransport handshake

`status: draft`
`requires: EGG-01, NIP-98`
`category: required`

## Summary

To open the audio plane (EGG-03), a peer first proves identity to a per-room
auth sidecar (moq-auth) using a NIP-98 HTTP signature, receives a short-lived
JWT, then opens a WebTransport session against the moq-relay carrying the JWT
in the URL.

Two separate URLs are involved: the `service` tag from EGG-01 is the auth
sidecar; the `endpoint` tag is the relay.

## Wire format

### Step 1 — token request

```
POST <service>/auth
Authorization: Nostr <base64(NIP-98 kind:27235 event)>
Content-Type:  application/json

{ "namespace": "nests/<kind>:<host_pubkey_hex>:<room_d>",
  "publish":   <boolean> }
```

`<kind>` is `30312` for nests audio rooms (EGG-01).

The NIP-98 event MUST bind to this exact URL, method `POST`, and the SHA-256
hash of the request body.

### Step 2 — token response

```
HTTP/1.1 200 OK
Content-Type: application/json

{ "token": "<jwt>" }
```

The JWT carries (at minimum):

| claim   | meaning                                                       |
|---------|---------------------------------------------------------------|
| `root`  | The exact `namespace` echoed back. Authorisation is scoped here. |
| `get`   | Read-allowed sub-paths; for nests this is `[""]` (any).       |
| `put`   | Publish-allowed sub-paths. For listeners: `[]`. For speakers: `[<own pubkey hex>]`. |
| `iat`   | Unix seconds; issuance.                                       |
| `exp`   | Unix seconds; expiry. MUST be `iat + 600` for nests today.    |

### Step 3 — WebTransport CONNECT

```
:method  = CONNECT
:protocol = webtransport
:scheme  = https
:authority = <host:port from `endpoint`>
:path    = /<namespace>?jwt=<token>
```

The relay reads the JWT from the `?jwt=` query string. No `Authorization`
header is used at this step.

## Behavior

1. Listening peers MUST request `publish: false`. Speaking peers MUST request
   `publish: true` AND the JWT's `put` claim MUST list the speaker's own
   pubkey hex.
2. The auth sidecar's TLS certificate MUST be a publicly-trusted chain. The
   relay's TLS chain MAY be self-signed in development; production deployments
   MUST use a public chain.
3. The auth sidecar SHOULD reject any request whose NIP-98 `created_at` is
   more than 60 s in the past or future.
4. The JWT lifetime is fixed at 600 s. There is no refresh endpoint. A client
   that needs a longer session MUST mint a fresh token and open a new
   WebTransport session before the old token expires; see the deployment-side
   commentary in `nestsClient/plans/`.
5. The relay MUST close the WebTransport session within 30 s of `exp`. A peer
   receiving an unexpected close MUST be prepared to mint a fresh token and
   reconnect.
6. The relay MUST NOT trust the WebTransport authority for authorisation —
   only the JWT's `root` claim. A token issued for namespace A MUST NOT be
   accepted on a session opened against namespace B.
7. A peer MUST NOT log the JWT or include it in error reports. The token is a
   bearer credential.

## Example

```
> POST https://moq.nostrnests.com/auth
> Authorization: Nostr eyJ...kind27235...
> Content-Type: application/json
>
> {"namespace":"nests/30312:abchost:office-hours-2026-04","publish":false}

< HTTP/1.1 200 OK
< Content-Type: application/json
<
< {"token":"eyJhbGc..."}

> CONNECT :path=/nests/30312:abchost:office-hours-2026-04?jwt=eyJhbGc...
< 200 OK   (WebTransport session established)
```

## Compatibility

EGG-03 (audio plane) operates inside the WebTransport session this EGG opens.
EGG-12 (catalog) shares the same session.
