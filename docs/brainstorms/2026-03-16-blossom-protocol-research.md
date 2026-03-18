# Blossom Protocol Research

**Date**: 2026-03-16
**Sources**: hzrd149/blossom GitHub (BUD specs), NIP-B7, Nostrify docs, Amethyst upstream codebase, Primal blog posts

---

## Overview

Blossom (**Bl**obs **O**n **S**imple **S**erver**om**... or something) is a specification for HTTP endpoints that let users store binary blobs on publicly accessible servers. Blobs are content-addressed by their **SHA-256 hash**. Uses Nostr keypairs for identity and authorization.

**Two Nostr event kinds:**
- **Kind 24242** -- Authorization token (BUD-11)
- **Kind 10063** -- User's Blossom server list (BUD-03, NIP-B7)

**BUD index (BUD-00 through BUD-11):**

| BUD | Name | Status | Required |
|-----|------|--------|----------|
| 00 | BUD framework | - | - |
| 01 | Server requirements + blob retrieval | draft | mandatory |
| 02 | Upload + management | draft | optional |
| 03 | User server list (kind 10063) | draft | optional |
| 04 | Mirroring | draft | optional |
| 05 | Media optimization | draft | optional |
| 06 | Upload requirements (HEAD preflight) | draft | optional |
| 07 | Payment required (402) | draft | optional |
| 08 | NIP-94 file metadata tags | draft | optional |
| 09 | Blob report | draft | optional |
| 10 | Blossom URI scheme | draft | optional |
| 11 | Nostr authorization | draft | optional |

---

## BUD-01: Server Requirements + Blob Retrieval

**Status:** `draft` `mandatory`

### CORS

All responses MUST set `Access-Control-Allow-Origin: *`.

Preflight (`OPTIONS`) responses MUST also set:
```
Access-Control-Allow-Headers: Authorization, *
Access-Control-Allow-Methods: GET, HEAD, PUT, DELETE
```

MAY set `Access-Control-Max-Age: 86400` (cache 24h).

### Error Responses

Any 4xx/5xx response MAY include `X-Reason` header with human-readable error message.

### Endpoints

All endpoints served from domain root. No path prefix.

#### GET /<sha256> -- Get Blob

```http
GET /b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553.pdf HTTP/1.1
Host: cdn.example.com
```

Response:
```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Length: 184292

<binary blob data>
```

- MUST accept optional file extension in URL (`.pdf`, `.png`, etc.)
- MUST return correct `Content-Type` regardless of extension
- MUST default to `application/octet-stream` if MIME unknown
- MAY require authorization (BUD-11)

**Proxying/Redirection:**
- 3xx redirects MUST redirect to URL containing same SHA-256 hash
- Destination MUST set `Access-Control-Allow-Origin: *`, `Content-Type`, `Content-Length`

**Range Requests:**
- Servers SHOULD support `Range` header (RFC 7233) on GET
- Signal via `Accept-Ranges: bytes` and `Content-Length` on HEAD

#### HEAD /<sha256> -- Has Blob

Identical to GET but MUST NOT return body. MUST return same `Content-Type` and `Content-Length` headers.

---

## BUD-02: Upload + Management

**Status:** `draft` `optional`

### Blob Descriptor

The standard JSON response for all upload/mirror operations:

```json
{
  "url": "https://cdn.example.com/b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553.pdf",
  "sha256": "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553",
  "size": 184292,
  "type": "application/pdf",
  "uploaded": 1725105921
}
```

Fields:
- `url` -- Public URL to `GET /<sha256>` endpoint **with file extension**
- `sha256` -- Hex-encoded SHA-256 of the blob
- `size` -- Size in bytes
- `type` -- MIME type (fallback `application/octet-stream`)
- `uploaded` -- Unix timestamp

MAY include: `magnet`, `infohash`, `ipfs`

### PUT /upload -- Upload Blob

```http
PUT /upload HTTP/1.1
Host: cdn.example.com
Authorization: Nostr <base64url-encoded kind 24242 event>
Content-Type: image/png
Content-Length: 184292
X-SHA-256: b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553

<raw binary data>
```

Response (success):
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "url": "https://cdn.example.com/b167...553.png",
  "sha256": "b167...553",
  "size": 184292,
  "type": "image/png",
  "uploaded": 1725105921
}
```

Key rules:
- Server MUST NOT modify the blob
- Server MUST compute SHA-256 over exact bytes received
- Client SHOULD include `Content-Type` and `Content-Length`
- Client MAY provide `X-SHA-256` header (hex lowercase)
- Server MAY use `X-SHA-256` for pre-upload rejection policies
- Success: 2xx with Blob Descriptor
- Failure: 4xx with error message

### GET /list/<pubkey> -- List Blobs (Unrecommended)

Optional. Returns JSON array of Blob Descriptors for a pubkey.

Query params:
- `cursor` -- SHA-256 of last blob (cursor-based pagination)
- `limit` -- Max results
- `since`/`until` -- Filter by upload date (deprecated for pagination)

Sorted by `uploaded` descending.

### DELETE /<sha256> -- Delete Blob

```http
DELETE /b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553.pdf HTTP/1.1
Host: cdn.example.com
Authorization: Nostr <base64url-encoded kind 24242 event>
```

- Multiple `x` tags in auth token MUST NOT be interpreted as batch delete

---

## BUD-03: User Server List

**Kind 10063** (replaceable event).

```json
{
  "kind": 10063,
  "tags": [
    ["server", "https://cdn.self.hosted"],
    ["server", "https://cdn.satellite.earth"],
    ["alt", "File servers used by the author"]
  ],
  "content": ""
}
```

- Tag order = priority. Most trusted/reliable first.
- Clients MUST upload to at least the first server in user's list.
- Clients MAY mirror to other listed servers via BUD-04.

**Discovery flow when URL breaks:**
1. Extract 64-char hex hash from broken URL
2. Fetch author's kind:10063 event
3. Try each listed server in order
4. Fall back to well-known servers

---

## BUD-04: Mirroring

**Status:** `draft` `optional`

### PUT /mirror -- Mirror Blob

```http
PUT /mirror HTTP/1.1
Host: backup-server.example.com
Authorization: Nostr <base64url-encoded kind 24242 event>
Content-Type: application/json

{
  "url": "https://cdn.satellite.earth/b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553.pdf"
}
```

Response: Blob Descriptor (same as upload).

Key rules:
- Server downloads blob from provided URL
- Server SHOULD use `Content-Type` from origin server
- Server verifies downloaded blob hash matches `x` tag in auth token
- Returns 2xx + Blob Descriptor on success, 4xx on failure

**Typical flow:**
1. Client uploads to Server A, gets Blob Descriptor with URL
2. Client sends URL to Server B's `/mirror` with same upload auth token
3. Server B downloads from Server A
4. Server B verifies hash matches `x` tag
5. Server B returns Blob Descriptor

---

## BUD-05: Media Optimization

**Status:** `draft` `optional`

### PUT /media -- Optimized Upload

```http
PUT /media HTTP/1.1
Host: trusted-server.example.com
Authorization: Nostr <base64url-encoded kind 24242 event with t=media>
Content-Type: image/png
Content-Length: 4194304

<raw binary data>
```

Response: Blob Descriptor -- but hash will differ from input because server transforms the file.

Key differences from `/upload`:
- Server MAY modify/optimize the blob (strip EXIF, compress, transcode)
- The returned SHA-256 will be of the **optimized** blob, not the original
- Client has NO control over optimization process
- `t` tag in auth event must be `media` (not `upload`)

### HEAD /media

Same as HEAD /upload (BUD-06) but for the media endpoint.

### Client Implementation Pattern

1. User selects a "trusted processing" server
2. Client uploads original media to `/media` on trusted server
3. Gets back optimized blob descriptor (new hash)
4. Client signs new upload auth for the optimized hash
5. Calls `/mirror` on other servers to distribute the optimized blob

This is what Primal does -- all Primal 2.2+ apps use `/media` by default, strips metadata, then mirrors.

---

## BUD-06: Upload Requirements (HEAD Preflight)

**Status:** `draft` `optional`

### HEAD /upload -- Pre-flight Check

Client sends blob metadata, server says yes/no before actual upload.

Request:
```http
HEAD /upload HTTP/1.1
Host: cdn.example.com
X-Content-Type: application/pdf
X-Content-Length: 184292
X-SHA-256: 88a74d0b866c8ba79251a11fe5ac807839226870e77355f02eaf68b156522576
Authorization: Nostr <base64url-encoded event>
```

Success:
```http
HTTP/1.1 200 OK
```

Failure examples:
```http
HTTP/1.1 400 Bad Request
X-Reason: Invalid X-SHA-256 header format. Expected a string.

HTTP/1.1 401 Unauthorized
X-Reason: Authorization required for uploading video files.

HTTP/1.1 403 Forbidden
X-Reason: SHA-256 hash banned.

HTTP/1.1 411 Length Required
X-Reason: Missing X-Content-Length header.

HTTP/1.1 413 Content Too Large
X-Reason: File too large. Max allowed size is 100MB.

HTTP/1.1 415 Unsupported Media Type
X-Reason: Unsupported file type.
```

**Note:** Uses `X-Content-Type`, `X-Content-Length`, `X-SHA-256` headers (not standard `Content-*`).

---

## BUD-07: Payment Required

Servers MAY return `402 Payment Required` with payment method headers:

```http
HTTP/1.1 402 Payment Required
X-Cashu: "<NUT-24 cashu token>"
X-Lightning: "<BOLT-11 invoice>"
```

After payment, client retries with proof:
- Cashu: serialized `cashuB` token per NUT-24
- Lightning: preimage of the BOLT-11 payment

HEAD requests inform about cost but should not be retried with payment; proceed to PUT/GET after paying.

---

## BUD-08: NIP-94 File Metadata Tags

Servers MAY include a `nip94` field in Blob Descriptor responses:

```json
{
  "url": "https://cdn.example.com/b167...553.pdf",
  "sha256": "b167...553",
  "size": 184292,
  "type": "application/pdf",
  "uploaded": 1725105921,
  "nip94": [
    ["url", "https://cdn.example.com/b167...553.pdf"],
    ["m", "application/pdf"],
    ["x", "b167...553"],
    ["size", "184292"],
    ["magnet", "magnet:?xt=urn:btih:..."],
    ["i", "infohash-here"]
  ]
}
```

Follows NIP-94 tag format as KV pairs. Allows clients to get standardized metadata without separate requests.

---

## BUD-09: Blob Report

### PUT /report

Body: signed NIP-56 report event (kind 1984):

```json
{
  "kind": 1984,
  "content": "This blob contains illegal content",
  "tags": [
    ["x", "<sha256>", "illegal"],
    ["p", "<uploader-pubkey>"]
  ]
}
```

Server maintains records for operator review. Optionally authorizes trusted moderators for autonomous removal.

---

## BUD-10: Blossom URI Scheme

Format:
```
blossom:<sha256>.<ext>[?param1=value1&param2=value2...]
```

Example:
```
blossom:b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553.pdf?xs=cdn.example.com&xs=backup.example.com&as=<author-hex-pubkey>&sz=184292
```

Query parameters:
- `xs` -- Server domain hints (tried first). Repeatable.
- `as` -- Author hex pubkey for BUD-03 server list lookup. Repeatable.
- `sz` -- Size in bytes.

**Resolution priority:**
1. Direct server hints (`xs`) via `GET /<sha256>`
2. Author server lists (fetch kind:10063 for each `as` pubkey)
3. Fallback to well-known servers or local cache

---

## BUD-11: Authorization

### Kind 24242 Event Structure

```json
{
  "id": "<event-id>",
  "pubkey": "<hex-pubkey>",
  "created_at": 1725105921,
  "kind": 24242,
  "tags": [
    ["t", "upload"],
    ["x", "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"],
    ["expiration", "1725109521"],
    ["server", "cdn.example.com"]
  ],
  "content": "Upload cat photo",
  "sig": "<schnorr-signature>"
}
```

### Required Tags

| Tag | Description |
|-----|-------------|
| `t` | Action verb: `get`, `upload`, `list`, `delete`, `media` |
| `expiration` | Unix timestamp when token expires (NIP-40) |

### Optional Tags

| Tag | Description |
|-----|-------------|
| `x` | SHA-256 hash of specific blob. Multiple allowed. |
| `server` | Domain restriction (lowercase). Multiple allowed. |
| `size` | File size in bytes (Amethyst adds this) |

### Authorization Header

```
Authorization: Nostr <base64url-encoded JSON of the kind 24242 event>
```

Note: Amethyst uses standard Base64 (not Base64url), which works in practice.

### Endpoint Authorization Requirements

| Endpoint | `t` tag | Hash source | `x` tag |
|----------|---------|-------------|---------|
| GET/HEAD /<sha256> | `get` | URL path | optional |
| PUT /upload | `upload` | X-SHA-256 header | required |
| HEAD /upload | `upload` | X-SHA-256 header | required |
| DELETE /<sha256> | `delete` | URL path | required |
| GET /list/<pubkey> | `list` | -- | N/A |
| PUT /mirror | `upload` | mirrored blob hash | required |
| PUT /media | `media` | X-SHA-256 header | required |
| HEAD /media | `media` | X-SHA-256 header | required |

### Validation Checklist (Server)

1. Event kind == 24242
2. `created_at` < now
3. `expiration` > now
4. `t` tag matches endpoint action
5. `server` tags (if present) include this server's domain
6. `x` tags (if required) match the blob hash

### Security Note

Unscoped tokens (no `server` tag) can be replayed to other servers. Always scope `delete` tokens.

---

## Error Handling -- HTTP Status Codes

| Code | Meaning | When |
|------|---------|------|
| 200 | Success | GET, HEAD, successful upload/mirror/delete |
| 3xx | Redirect | GET with CDN redirect (must preserve hash in URL) |
| 400 | Bad Request | Invalid headers, malformed auth |
| 401 | Unauthorized | Missing or invalid authorization |
| 402 | Payment Required | BUD-07 paid servers |
| 403 | Forbidden | Hash banned, user blocked |
| 404 | Not Found | Blob doesn't exist |
| 411 | Length Required | Missing Content-Length / X-Content-Length |
| 413 | Content Too Large | File exceeds server limit |
| 415 | Unsupported Media Type | Server doesn't accept this MIME type |

All error responses MAY include `X-Reason` header.

---

## Popular Blossom Servers

| Server | Limits | Notes |
|--------|--------|-------|
| `blossom.nostr.build` | 100 MiB hard, 20 MiB free | Run by nostr.build team. Supports BUD-01,02,04,05,06,08 |
| `blossom.band` | 100 MiB hard, 20 MiB free | Community server |
| `blossom.primal.net` | Integrated with Primal stack | Uses /media by default, strips metadata |
| `cdn.satellite.earth` | Unknown | Satellite CDN |
| `blossom.azzamo.net` | Free tier + premium | Azzamo's server |
| `blosstr.com` | Enterprise-grade | Commercial offering |

Rate limiting is not standardized in the protocol. Each server implements its own policies. Free tiers generally have stricter limits. BUD-06 HEAD preflight is the mechanism for discovering server limitations before uploading.

---

## Client Implementations

### Amethyst (Kotlin -- upstream)

**Quartz library** (`quartz/src/commonMain/kotlin/.../nipB7Blossom/`):
- `BlossomAuthorizationEvent` -- Kind 24242 event creation (get, upload, delete, list)
- `BlossomServersEvent` -- Kind 10063 server list management
- `BlossomUploadResult` -- Blob Descriptor deserialization (kotlinx.serialization)
- `BlossomUri` -- BUD-10 URI parsing/serialization

**Android app** (`amethyst/src/main/java/.../service/uploads/blossom/`):
- `BlossomUploader` -- PUT /upload + DELETE implementation using OkHttp
- `BlossomServerResolver` -- BUD-10 URI resolution with LruCache
- `ServerHeadCache` -- HEAD request caching for blob existence checks
- `UploadOrchestrator` -- Orchestrates NIP-95, NIP-96, and Blossom uploads

**Upload flow:**
1. Read file, compute SHA-256 hash + size
2. Compute blurhash metadata locally
3. Create kind 24242 auth event (t=upload, x=hash, expiration=+1hr)
4. Base64-encode auth event JSON
5. PUT /upload with `Authorization: Nostr <base64>`, `Content-Type`, `Content-Length`
6. Parse Blob Descriptor response
7. Download + verify the uploaded file (re-hash check)

**Key:** Amethyst does NOT use `/media` endpoint. Uses `/upload` only. No mirroring implemented.

### Primal

- All Primal 2.2+ apps use `/media` (BUD-05) by default
- Strips all metadata before saving
- Optionally mirrors to other Blossom servers per user settings
- Deeply integrated into Primal stack, enabled by default

### Nostrify (TypeScript/Web)

```typescript
const uploader = new BlossomUploader({
  servers: ['https://blossom.primal.net/'],
  signer: window.nostr,
  expiresIn: 60, // seconds
});

const tags = await uploader.upload(file);
// Returns NIP-94 tags: url, x, ox, size, m
```

- `ox` tag = original hash (before server processing)
- `x` tag = final hash (after optimization if /media used)

### NDK Blossom (@nostr-dev-kit/ndk-blossom)

npm package wrapping BUD-01 through BUD-06. TypeScript.

### Dart NDK (dart-nostr.com)

Has Blossom use case documentation. Flutter/Dart integration.

---

## Key Protocol Design Decisions

1. **Content addressing via SHA-256** -- Same file = same hash everywhere. Deduplication is free.
2. **Servers are interchangeable** -- Any server with the blob can serve it. URLs break? Find the hash elsewhere.
3. **No server-side processing on /upload** -- Bit-perfect storage. Hash computed over exact bytes received.
4. **/media is the exception** -- Trusted server processes/optimizes. New hash for result.
5. **Authorization is opt-in per endpoint** -- Servers choose what to protect.
6. **User controls server list** -- Kind 10063 event = user's preferred servers.
7. **Mirror for redundancy** -- Upload once, mirror to N servers.

---

## Unanswered Questions

- Does BUD-11 require base64url (no padding) or standard base64? Spec says base64url, Amethyst uses standard base64 -- servers seem to accept both.
- What's the recommended expiration window for auth tokens? Amethyst uses 1 hour.
- How do clients handle the `/media` flow when the optimized hash differs from original? Need to re-sign auth for mirror requests with the new hash.
- Is there a standard way to discover server capabilities (which BUDs supported)? Not currently -- no capability endpoint defined.
- How to handle upload failures mid-stream for large files? No chunked upload in spec.
- Server-side dedup behavior when same hash uploaded by different users? Implementation-specific.
