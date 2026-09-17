# Cordn interop: extract the MLS core, then add a second binding

Status: Queued. Research complete; no code written. Blocked on one upstream protocol decision
(§4.1) before any of Stage 2+ is worth starting.

Sources checked on 2026-09-17:

- `Cordn-msg/cordn` @ `b465df0` (2026-08-27), package version `0.5.1` — specs + reference
  coordinator + `@cordn/cli`
- `Cordn-msg/cordn-web` @ `c38e307` (2026-09-16), version `0.4.0` — the client at
  <https://cordn.net>
- `Cordn-msg/cordn-rs` @ `9aff928` (2026-08-29) — Rust coordinator, wire- and SQLite-compatible
  with the TS one; **not** an MLS implementation
- `ContextVM/contextvm-docs` @ `e63bce6` — the ContextVM specification and all 12 CEPs; the
  clean-room source for §6. **No LICENSE file in the repo**
- `ContextVM/sdk` @ `b5d1e4e` (2026-09-17), version `0.13.17` — **LGPL-3.0**, see §7. Read only to
  confirm deployed defaults, never as an implementation source

Not verified by execution: this container could not run `:quartz:jvmTest` (no Gradle dependency
cache and Maven Central returns HTTP 429 through the agent proxy), so every claim below about our
own code comes from reading it, not from a green test run. Stage 0 exists to fix that.

## 1. Executive summary

Cordn is **an alternative to Marmot, not an alternative to MLS**. Both are bindings of RFC 9420
onto Nostr; they agree almost exactly on the crypto layer and disagree on everything above it.

- **Same:** ciphersuite `0x0001`, the ChaCha20-Poly1305 outer seal (byte-identical framing), the
  MLS-exporter-derived seal key, the pre-commit epoch rule for Commits, and the unsigned
  NIP-01-shaped application envelope with `kind: 9` chat.
- **Different:** the delivery service. Marmot publishes kinds 443/444/445 to relays. Cordn calls
  an **MCP server over ContextVM** — 11 tools, per-group monotonic cursors, history in the
  coordinator's SQLite, and *no key-package event kind at all*.

So the only code both bindings can share is the RFC 9420 engine. Everything named `Marmot*`,
`Mip*` or `mip0*` is the wrong layer and must not be reused.

The engine is reusable but **not yet binding-agnostic**: it lives inside
`quartz/…/marmot/mls/`, and three files plus three hardcoded policies leak Marmot into it (§5.1).
Extracting it is the prerequisite for this work and is worth doing on its own merits.

The transport has to be written from scratch: **ContextVM is MCP-over-Nostr and nothing in Quartz
speaks it.** Reviewing the full spec set (§6) puts the scope at **7 of 12 CEPs** — the core spec,
CEP-4/19 (encryption), CEP-6/17 (discovery), CEP-35 (stateless discovery), and the two big ones
CEP-22 (bounded oversized transfer) and CEP-41 (open streams), which together are most of the work
and nearly all of the risk. Payments (CEP-8) and common tool schemas (CEP-15) are confirmed
unused by cordn. Only 3 of the 7 are **Final**; the rest, including both big ones, are **Draft**
(§6.6). The result is a general MCP-over-Nostr client, reusable well beyond cordn.

Recommended sequencing: **Stage 0 (vectors) → Stage 1 (extract engine) → decide → Stage 2+.**
Do not start Stage 2 before the §4.1 decision, because if it goes the wrong way every KeyPackage
is permanently ecosystem-bound and "interop" degrades to Amethyst speaking two unrelated
protocols.

## 2. The coordinator protocol surface

Eleven MCP tools (`cordn/packages/core/src/contracts.ts`):

```
kp_publish  kp_list  kp_take  kp_remove
welcome_store  welcome_take
join_request_store  join_request_take_many
msg_post  msg_fetch_many  msg_sub_many
```

Transport is ContextVM: kind **25910** ephemeral events whose `content` is a stringified MCP
JSON-RPC message, `p` = peer, `e` = request correlation, optionally gift-wrapped in 1059/21059
(`contextvm-sdk/src/core/constants.ts`). `msg_sub_many` needs **CEP-41** open streams and the
coordinator also enables **CEP-22** oversized transfer, both framed over
`notifications/progress`.

Delivery model (`cordn/spec/00.md`, `spec/03.md`): the coordinator stores opaque bytes keyed by an
outer delivery id `gid`, assigns a monotonic per-group cursor, and must not parse payloads. It
cannot distinguish a Commit from a chat message — epoch, wireformat and content-type were
deliberately removed from its view (`cordn/design/private-coordinator-refactor.md`, which is an
explicit study of Marmot's kind-445 and the reason the two seals match).

`gid` is spec'd as decoupled from the MLS `group_id`, but the reference client sets both to
`utf8(crypto.randomUUID())` (`cordn-web/src/lib/services/chatGroupLifecycle.svelte.ts:76`,
and `getProtocolGroupId` at `:83` reads the `gid` straight back out of the group context).

Group sharing is a bech32 `cordn1…` string with NIP-19-shaped TLV (type 0 = `gid` as UTF-8,
1 = coordinator pubkey as raw 32 bytes, 2 = relay URL), bech32 not bech32m
(`cordn/spec/applications/group-ref.md`). Our NIP-19 codec covers it with a prefix change.

## 3. Verified compatible (the crypto layer)

| Layer | Marmot (ours) | cordn | Verdict |
| ----- | ------------- | ----- | ------- |
| Ciphersuite | `MlsCiphersuite.DEFAULT` = `0x0001` (`mip01Groups/MlsCiphersuite.kt`) | `MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519` (`chatMlsUtils.ts:38`) | identical |
| Outer seal | ChaCha20-Poly1305, 12-byte random nonce, **empty AAD**, `base64(nonce‖ct‖tag)` (`mip03GroupMessages/GroupEventEncryption.kt`) | same, same, same, same (`spec/03.md` §4) | byte-identical framing |
| Seal key | `MLS-Exporter("marmot","group-event",32)` | `MLS-Exporter("cordn","group-payload",32)` | label/context only |
| Commit epoch | pre-commit epoch | pre-commit epoch (`spec/03.md` §5) | identical |
| App payload | unsigned NIP-01 event, no `sig`, `id` = NIP-01 hash, `kind: 9` default (`foundation/appEvents/MarmotAppEvent.kt:78`) | same, and rejects any payload carrying `sig` (`spec/02.md`) | same wire shape |
| Threading / reactions | NIP-22 `1111`, NIP-25 `7` | same (`spec/02.md` §6) | identical |
| Sender privacy intent | fresh ephemeral key per kind-445 | ephemeral ContextVM identity on the message path | same idea, different grain (§8.2) |

cordn-web's MLS engine is **ts-mls 2.0.0-rc.13**, the same library our existing
`TsMlsWelcomeInteropTest` has vectors for
(`quartz/src/commonTest/resources/mls/tsmls-welcome.json` — key packages, Welcome, exporter and
app messages at ciphersuite 1). The RFC 9420 core is therefore a known quantity, subject to the
caveat in the header that this container could not re-run it.

Note what this table is *not*: none of these are reuse candidates. `GroupEventEncryption`,
`MarmotAppEvent` and MIP-04 media are Marmot-layer code. The table says a cordn-side
implementation will look structurally familiar and can be written with confidence — it will be
parallel code under `quartz/…/cordn/`, not a shared path.

## 4. Verified divergences

### 4.1 Credential identity encoding — hard incompatibility, decide first

- Ours: raw **32 bytes**, x-only pubkey. `KeyPackageUtils.kt:253` —
  `if (credential.identity.size != 32) return false`.
- Theirs: **64 ASCII bytes** of lowercase hex. `chatMlsUtils.ts:251` —
  `identity: new TextEncoder().encode(stablePubkey)`; the coordinator reads it back with
  `TextDecoder` and string-compares to `event.pubkey`
  (`coordinatorMethods.ts:118-151`).

One KeyPackage cannot satisfy both. This is exactly what `spec/00.md` §13 requires
implementations to agree on, and the two ecosystems picked differently. It is a one-line change
on either side today and unfixable once either has deployed users at scale.

**Action:** raise with gzuuus before Stage 2. Either side moving is fine; what matters is that
one does.

### 4.2 No key-package event kind

There is no cordn equivalent of Marmot's kind 443. `kp_publish` takes `{ kp_ref, kp_64 }` — plain
base64 in the JSON-RPC arguments. The "signed publication payload" of `spec/00.md` §7 is the
**ContextVM request event itself**: the coordinator reaches back into the transport for it
(`coordinatorServer.ts:92` → `transport.getNostrRequestEvent(requestEventId)`), stores it
verbatim, and returns it on `kp_take`. A consumer then does
(`chatMlsUtils.ts:579-613`):

```ts
if (!verifyEvent(publicationEvent)) throw …
const kp64 = JSON.parse(publicationEvent.content).params?.arguments?.kp_64
            ?? JSON.parse(publicationEvent.content).params?.arguments?.keyPackageBase64;
```

i.e. the KeyPackage is recovered by parsing JSON-RPC out of a kind-25910 event's `content`, and
identity binding is `decodeKeyPackageIdentity(kp) === publicationEvent.pubkey`.

Consequences:

- A Marmot kind-443 event cannot serve as a cordn publication payload or vice versa. There is no
  "publish to relays, interop on KeyPackages only" shortcut — KeyPackage publication is
  inseparable from having a working ContextVM client.
- The invariant rides JSON-RPC envelope shape rather than a stable event schema, and the client
  already carries a fallback from an earlier field rename (the `?? keyPackageBase64` above).

**Worth proposing upstream** alongside §4.1: give KeyPackage publication its own signed payload
(or its own kind). That is mutual-benefit — it would let a cordn KeyPackage be published to relays
and consumed without a coordinator at all.

### 4.3 Last-resort marker

Ours: MLS extension `0x000A`. Theirs: mls-extensions `app_data_dictionary` (`0x0006`) with
component id `0x0004` and empty component data
(`cordn/packages/core/src/lastResortKeyPackage.ts`). We already have `AppDataDictionary.kt`,
`ComponentsList.kt` and `AppDataDictionaryInteropTest` — this is a codec selection, not new work.

### 4.4 Capability gates keep the two group types disjoint

Marmot groups carry `required_capabilities = { extensions: [0xF2EE], proposals: [0x000A
self_remove] }` (`MlsGroup.kt:3255`, built by `buildMarmotRequiredCapabilitiesExtension()` at
`:3259`). Cordn groups use GroupContext extension `0xC04D cordn_group_metadata` and require
members to advertise it (`spec/01.md` §7). Neither client can be *added* to the other's groups.
Cheap to fix by advertising both in `Capabilities`, but "one group, both clients" is a deliberate
profile decision, not a free consequence of sharing an engine.

### 4.5 Encrypted media diverges

Exporter *context* matches (`"encrypted-media"`), but cordn uses the exporter output directly as
the file key with `aad = mime‖0x00‖filename‖0x00‖sha256(plaintext)`, where our MIP-04 v2 does
`HKDF-Expand(exporter, context)` with `aad = "mip04-v2"‖0x00‖hash‖0x00‖mime‖0x00‖filename`.
Separate codec; shared primitives (ChaCha20-Poly1305, NIP-92 `imeta`, Blossom) all already exist.

### 4.6 Multi-device is not interoperable — explicit non-goal

`spec/applications/multi-device.md` ships `base64(serialized ts-mls ClientState)` inside sealed
Blossom documents advertised by an opaque tip. That is a ts-mls-internal serialization, not an MLS
wire format. There is nothing to implement against. Do not attempt it.

## 5. What we already have

### 5.1 The engine, and how Marmot-clean it is

`quartz/…/marmot/mls/` is **11,964 LOC**. Measured coupling to the Marmot layer:

- **3 files**, **10 imports** total:
  - `group/MlsGroup.kt` (7): `MarmotGroupData`, `MarmotGroupState`, `AdminPolicyV1`,
    `AppComponentIds`, `AgentTextStreamCrypto`, `AgentTextStreamQuicPolicyV1`,
    `AgentTextStreamRoles`
  - `group/MlsGroupManager.kt` (2): `AdminPolicyV1`, `GroupLifecycleV1`
  - `messages/MlsKeyPackage.kt` (1): `AppComponentIds`
- **Zero** Marmot imports in `codec/`, `crypto/`, `tree/`, `schedule/`, `framing/`,
  `components/`, `messages/{Commit,Proposal,Welcome}`.

Plus three Marmot policies hardcoded *inside* the engine that a cordn group must not inherit:

| Location | Hardcoded |
| -------- | --------- |
| `MlsGroup.kt:3259` | `required_capabilities = {ext:[0xF2EE], props:[0x000A]}` |
| `MlsGroup.kt:650`, `:4276` | `exporterSecret("marmot", "group-event", 32)` — label baked in |
| `KeyPackageRotationManager.kt:645-670` | leaf `capabilities.extensions = [0x000A, 0xF2EE]` |

`MlsGroup.kt:2072` already exposes `exporterSecret(label, context, length)`, so the last of those
is a call-site fix, not a redesign.

### 5.2 Reusable as-is

NIP-44 (and NIP-59) for the ContextVM gift wrap, NIP-19 bech32/TLV for `cordn1…`,
`ChaCha20Poly1305`, the TLS presentation-language codec (`mls/codec/`) for the `0xC04D` extension,
Blossom (`nipB7Blossom`), `INostrClient` and the `accessories/` one-shot helpers for relay I/O,
and `NostrSigner` for both identities.

## 6. ContextVM: full surface review

We have to write this from scratch — nothing in Quartz speaks it, and the only SDKs are TypeScript
and Rust. This section is the complete protocol surface so Stage 2 can be scoped honestly.

**Clean-room sourcing.** Everything below is derived from the specification documents in
`ContextVM/contextvm-docs` (the `docs/contextvm-docs` submodule of the SDK, cloned at
`src/content/docs/reference/`), **not** from the LGPL SDK source (§7). Implementers should work
from those documents. Note that the docs repo carries **no LICENSE file** — the protocol is free
to implement, but do not paste spec prose into our repo; paraphrase.

### 6.1 The core spec is small

`spec/ctxvm-draft-spec.md` (352 lines, Draft) is nearly all it is:

- **One event kind, 25910**, ephemeral (NIP-01 range 20000–30000). `content` is the stringified
  MCP JSON-RPC message, preserved exactly. Nostr metadata lives only in tags: `p` addresses the
  peer, `e` references the request event for correlation.
- **Standard MCP lifecycle** — `initialize` → result → `notifications/initialized` — and the spec
  explicitly says it is **not required**, because servers may operate statelessly.
- Everything else (`tools/list`, `tools/call`, notifications) is unmodified MCP in `content`.

The consequence worth flagging up front: **25910 is ephemeral, so relays do not store it.** A
client must already be subscribed when the response is published or the response is simply gone —
there is no REQ-after-the-fact recovery. That shapes our subscription lifecycle more than anything
else in the spec.

### 6.2 CEP inventory and scope

Twelve CEPs exist. Seven are load-bearing for cordn; five are not.

| CEP | Status | What it adds | Needed |
| --- | ------ | ------------ | ------ |
| **4** Encryption Support | Final | NIP-44 encrypt the signed inner 25910 event, place it in a NIP-59 gift wrap (kind 1059) with **no rumor layer**. `support_encryption` tag. | **Yes** |
| **19** Ephemeral Gift Wraps | Draft | Kind **21059**, identical structure/semantics to 1059 but in the ephemeral range so relays do not persist the envelope. `support_encryption_ephemeral`. | **Yes** — cordn-web pins `EPHEMERAL` |
| **6** Public Server Announcements | Final | Addressable **11316** (server), **11317** tools, **11318** resources, **11319** resource templates, **11320** prompts. `content` is the stringified initialize/list result. Discovery tags: `name`, `about`, `picture`, `website`, `support_*`. | **Yes** (read side) |
| **35** Stateless Session Discovery | Draft, Informational | Discovery tags ride the **first direct message each side sends** in a session — role-oriented, not initialize-oriented. Unknown discovery tags MUST be preserved. | **Yes** — cordn-web sets `isStateless: true` |
| **22** Oversized Payload Transfer | Draft | Bounded reassembly over `notifications/progress`. `progressToken` is the transfer id. Frames `start`/`accept`/`chunk`/`end`/`abort`, `completionMode: "render"`, SHA-256 digest + `totalBytes` + `totalChunks`. | **Yes** — coordinator enables it; a large `msg_fetch_many` reply needs it |
| **41** Open-Ended Streams | Draft | Long-lived streams over the same envelope. Frames `start`/`accept`/`chunk`/`ping`/`pong`/`close`/`abort`. | **Yes** — `msg_sub_many` is built on it |
| **16** Client Public Key Injection | Final, Informational | Server transport injects `_meta.clientPubkey` into inbound requests. | **Server-side**, but load-bearing (§6.3.11) |
| **17** Server Relay List Metadata | Draft | NIP-65 **kind 10002**, `r` tags, unmarked by default in the ContextVM profile. | Recommended — lets a `cordn1…` ref with no relay hints still resolve |
| **23** Server Profile Metadata | Draft | Servers MAY publish **kind 0** and **kind 1**. | Optional — free win for a coordinator picker; we already render kind 0 |
| **24** Server Reviews | Draft | NIP-22 **kind 1111** anchored to the `11316:<pubkey>:` `a` coordinate. | Optional — we already have NIP-22 |
| **8** Capability Pricing and Payment | Draft | `cap` pricing tags, `pmi` payment-method ids, `payment_interaction` negotiation (`transparent` vs `explicit_gating`), payment notifications/errors. | **No** — verified zero payment references anywhere in cordn |
| **15** Common Tool Schemas | Draft | RFC 8785 (JCS) hash of normalized tool schemas, `io.contextvm/common-schema` `_meta` namespace, NIP-73 discovery. | **No** — irrelevant to a client with 11 fixed tools |
| **21** PMI Recommendations | Draft, Informational | Naming guidance for CEP-8 PMIs. | **No** (depends on CEP-8) |

Design the module so CEP-8 is not *precluded* — a priced coordinator is plausible later — but do
not build it.

### 6.3 The subtle parts

These are where a naive implementation passes unit tests and then fails against the live
coordinator. Each is a spec MUST and should become a test.

1. **CEP-41 has two ordering fields, and they are not interchangeable.** `progress` orders *all*
   frames (control frames included) and is explicitly **not** a chunk counter. `chunkIndex` starts
   at `0`, increases contiguously by `1`, and is what receivers MUST use to validate contiguity
   and completeness. Conflating them is the obvious first bug.
2. **Progress sequences are per-sender**, each starting at `1`. A frame's `progress` may only be
   compared against frames from the same peer. A `pong`'s `progress` comes from the responder's own
   sequence and has **no** required ordering relationship to the triggering `ping`; pongs match by
   `nonce` only. Do not build one shared counter per stream.
3. **`close` does not complete the JSON-RPC request.** After `close` the sender MUST still send the
   final JSON-RPC success response, and a client MUST NOT synthesize success from `close` alone.
   So `msg_sub_many` has two independent completion signals — cordn-web's API shape
   (`{ stream, result, abort }`) is a direct consequence, not a style choice.
4. **The CEP-22 digest is over the exact serialized string**, UTF-8-encoded — not over a
   re-serialized JSON object. Any reparse-and-reserialize (key reordering, whitespace) breaks it.
   The reassembly path must carry bytes end to end and only parse after the digest verifies.
5. **`accept` is conditional bootstrap, not a handshake.** Skip it when peer support is already
   known; it is **mandatory before the first `chunk` in stateless flows**. cordn is stateless, so
   our client→server oversized path must wait for `accept`.
6. **CEP-41 keepalive is mandatory.** Any valid frame resets the idle timer; on expiry the peer
   MUST send `ping`; no matching `pong` before the probe timeout MUST fail the stream. cordn-web
   runs 30s/30s explicitly because the SDK's 20s default turned a single lost relay round-trip into
   a stream abort. Do not ship a 20s window.
7. **Relay size limits apply to the whole serialized event**, ~64 KiB in practice, not just
   `content`. Chunk sizing must budget for base64 expansion plus JSON plus event overhead, with
   conservative margin, and MUST NOT assume a uniform threshold across relays.
8. **Encryption leaks the recipient.** CEP-4 says so outright: the gift wrap carries a `p` tag for
   the recipient. Sender, inner kind and real timestamp are hidden; the recipient pubkey is not.
9. **Gift wrap timestamps are randomized** per NIP-59 — never use them for ordering.
10. **The inner event is fully signed, not a rumor.** CEP-4's flow signs the 25910 event *first*,
    then NIP-44-encrypts the whole thing into the wrap. Receivers verify the inner signature, and
    response correlation uses the **inner** `id`, not the gift wrap's.
11. **Three separate correlation identifiers coexist**: the nostr `e` tag (event level), the
    JSON-RPC `id` (MCP level), and `progressToken` (CEP-22/41 transfer level). Plus
    `_meta.clientPubkey` (CEP-16) as the coordinator's authenticated caller identity — which is the
    mechanism §4.2's KeyPackage binding ultimately rests on.
12. **Ephemeral delivery has no replay.** Per §6.1, subscribe before you publish. Combined with
    per-identity subscriptions (§8.2), each identity needs its own live `#p` subscription on 25910
    plus both gift wrap kinds.
13. **A zero-chunk stream is valid** — `close` immediately after `start`, with `lastChunkIndex`
    omitted. An empty `msg_sub_many` backlog will exercise this on day one.
14. **`close.lastChunkIndex` is optional and meaningful.** Present, it is a completeness bound and
    every index `0..lastChunkIndex` must have arrived; omitted, the stream was open-ended and no
    bound is asserted. Senders omit it for live feeds.

### 6.4 Build list for `:contextvm`

Ordered so each item is testable before the next depends on it:

| # | Component | Notes |
| - | --------- | ----- |
| 1 | Kinds, tags, frame types, JSON-RPC 2.0 codec | Pure data; port the constant set from the spec |
| 2 | Minimal MCP client | `initialize`, `notifications/initialized`, `tools/call`, typed errors, `_meta`/`progressToken` plumbing. Not a full MCP SDK — cordn uses tools only |
| 3 | CEP-4/19 gift wrap | NIP-44 + 1059/21059 on existing `nip44Encryption` and `nip59Giftwrap`. Pin `REQUIRED` (§8.6) |
| 4 | Correlation + subscription lifecycle | `#p` subscriptions on 25910 + both wrap kinds, `e`-tag routing, the subscribe-before-publish rule of §6.1, per-identity scoping |
| 5 | CEP-35 discovery-tag learning | First-message exchange each way; preserve unknown tags |
| 6 | CEP-6/17 server discovery | 11316–11320 readers + NIP-65 10002 relay resolution. Reuses `INostrClient` `accessories/` one-shots |
| 7 | CEP-22 receiver | Frame state machine, bounded reassembly, admission control on `totalBytes`/`totalChunks`, digest verify |
| 8 | CEP-41 receiver + writer | The two-counter state machine, keepalive, incremental delivery as a `Flow`, the dual completion of §6.3.3 |
| 9 | CEP-22 sender | Only needed if we ever post a >64 KiB `msg_post`; defer until a real case appears |
| 10 | Dual-signer plumbing | Account `NostrSigner` for stable, local keypair for ephemeral |

Sizing reference: the SDK's client-side surface (`src/core` + `src/transport/nostr-client` +
oversized-transfer + open-stream, tests excluded) is ~5.4k lines of TypeScript. Items 7 and 8 are
the bulk of it and the bulk of the risk.

Reusable beyond cordn: this is a general MCP-over-Nostr client. Any future Amethyst work that
wants to call a remote MCP server — or expose one — lands here rather than in a feature module.

### 6.5 Conformance strategy

No official ContextVM test vectors exist as far as I can find (worth asking upstream — see §10).
So conformance has to be built:

- **Rule-derived unit tests.** Every MUST in §6.3 and in CEP-22/41's validation sections becomes a
  test against the frame state machines, including the negative cases: non-monotonic `progress`, a
  second `start` on a live token, `close` with unresolved gaps, a `pong` with an unknown nonce,
  digest mismatch, `totalBytes` mismatch.
- **Live integration against the reference coordinator.** `ghcr.io/cordn-msg/cordn:latest` with
  `CORDN_STORAGE_BACKEND=memory` and `CORDN_ANNOUNCED=false` boots in one command and enables both
  CEP-22 and CEP-41. `cordn/packages/test-utils/src/mockRelay.ts` exists if we want a relay stub
  rather than a public one.
- **Ask upstream for vectors**, and offer ours. A shared vector set for CEP-22/41 framing would
  benefit every non-TypeScript implementation and is a cheap contribution.

### 6.6 Spec stability risk

Only CEP-4, CEP-6 and CEP-16 are **Final**. Everything cordn actually depends on beyond the core —
CEP-19, CEP-22, CEP-41, CEP-35 — is **Draft**, and the core spec itself is Draft. CEP-22 and CEP-41
are also the two largest and most intricate documents. Budget for churn, keep the frame state
machines isolated behind a narrow interface, and pin which CEP revision we implemented in the
module's README so a future reader can diff.

## 7. Licensing

🔴 **`ContextVM/sdk` is LGPL-3.0** (`COPYING.LESSER` over the GPL-3 base text; `package.json`
declares `LGPL-3.0-1`). We would not link a TypeScript library into Quartz regardless, but per the
dependency rule in `.claude/CLAUDE.md` this means:

- A Kotlin ContextVM implementation **must be clean-room from the spec and CEP documents**, not a
  translation of that source. A derivative translation would carry LGPL terms into MIT Quartz.
- `cordn-rs` links the Rust `contextvm-sdk` as a dependency; that is their artifact, not ours.
- If a Kotlin/JVM ContextVM library ever appears under LGPL, linking it is a WARN-and-call-out
  (call it out in the PR description), not an automatic stop.

Everything under `Cordn-msg` is MIT, so the specs and the reference coordinator are safe to read
and to implement against.

## 8. What the coordinator can see

Content: **nothing.** Double-sealed (MLS ciphertext, then ChaCha20-Poly1305 under the epoch
exporter), and the coordinator is forbidden from parsing. It cannot even tell handshake from
application traffic. This is genuinely stronger than a conventional MLS delivery service.

Metadata is where the cost sits. Every call carries an authenticated caller pubkey
(`extra._meta.clientPubkey`, derived from the signed inner 25910 event), and cordn-web splits
identities deliberately:

| identity | calls |
| -------- | ----- |
| **stable** (real npub) | `kp_publish`, `kp_remove`, `welcome_take`, `join_request_store` |
| **ephemeral** | `kp_take`, `kp_list`, `welcome_store`, `join_request_take_many`, `msg_post`, `msg_fetch_many`, `msg_sub_many` |

### 8.1 Admission is in the clear on both ends

`join_request_store` carries the **stable** identity and the `gid` → "npub X wants into group G".
`welcome_store` names the **target's stable pubkey**; `welcome_take` is called by that target's
**stable** identity. For any group joined via a share link the coordinator observes real-identity
membership directly. Structural, not a bug.

### 8.2 The ephemeral identity is per-session, not per-message

`coordinatorClient.ts:169` → `new PrivateKeySigner()` with no argument, constructed once per
`cordnClient`, and `chatRuntime.ts:62` caches one client per coordinator pubkey. So **one
pseudonym posts, fetches and subscribes across every group you have on that coordinator, for the
whole session.** That `gid` set is a stable fingerprint: it links all your groups together and
leaks how many you are in. Marmot rotates per kind-445 event and has no equivalent linkage
(relays still see `h` tags and fetch patterns — different exposure, not obviously better).

### 8.3 Complete ordered history in one place

Per-group cursors, `at` timestamps, sealed payload sizes (no padding specified anywhere I could
find), per-`gid` rates, live subscription membership. Cross-`gid` timing correlation inside one
session is trivial. Unlike relays there is no redundancy or partitioning: one operator sees the
whole graph of every group it serves, retained in SQLite.

### 8.4 A signed, verifiable record that you use cordn

`kp_publish` rides the stable identity and the coordinator retains the signed event — it is
*designed* to be re-servable as proof, rotation cadence included. `kp_take`/`kp_list` also reveal
who is being looked up, i.e. "someone is about to add X to a group".

### 8.5 IPs go to relays, not the coordinator

A real structural win over an HTTP delivery service. The relay then sees the 21059 traffic pattern
and the `p` tag naming the coordinator: who talks to which coordinator, how often, how big.

### 8.6 Encryption can silently downgrade — pin it

cordn-web pins `giftWrapMode: GiftWrapMode.EPHEMERAL` (`coordinatorClient.ts:186`) but leaves
`encryptionMode` at the SDK default **`OPTIONAL`**
(`contextvm-sdk/src/transport/base-nostr-transport.ts:102`), which resolves as
`isEncrypted ?? true` from negotiated session state (`:544`). The reference coordinator does
announce `support_encryption`, so live deployments are encrypted — but the policy permits a
coordinator that does not announce it to receive **plaintext JSON-RPC on public relays**,
exposing `gid`, `target_pk`, `kp_64` and cursors to any relay.

**Our client must pin `REQUIRED` and fail closed.** Non-negotiable.

### 8.7 Net

Content privacy equals Marmot's. Metadata privacy is **weaker than Marmot's against the delivery
operator** and **stronger against the network**. Whether that trade is acceptable depends on
whether coordinators are self-hosted per community or a handful of public ones; the spec permits
either, and the shipped default is one coordinator pubkey over three public relays
(`cordn/README.md`).

This section should be surfaced in the UI if we ship this, not buried. A Marmot group and a cordn
group have materially different metadata exposure and users cannot infer that from either one
looking like a group chat.

## 9. Plan

### Stage 0 — Ground truth and the upstream conversation

No production code. Two deliverables.

1. **Restore executable verification.** Get `:quartz:jvmTest` green in a clean container
   (the 429 in the header) and confirm `TsMlsWelcomeInteropTest`, `MdkWelcomeInteropTest` and
   `AppDataDictionaryInteropTest` still pass. Everything downstream assumes the engine is sound.
2. **cordn-side vectors.** `@cordn/cli` is published and has a filesystem-queue mode built for
   scripting; the coordinator ships as `ghcr.io/cordn-msg/cordn:latest` with
   `CORDN_STORAGE_BACKEND=memory`. Drive both to emit a vector file (KeyPackage with hex-ASCII
   credential, Welcome, a sealed application payload, a sealed Commit, a `cordn1…` group ref, a
   `0xC04D` extension blob) into `quartz/src/commonTest/resources/cordn/`, with a generator under
   `quartz/tools/cordn-vector-gen/` mirroring `tools/tsmls-vector-gen/`. Assert them in
   `quartz/…/cordn/interop/`.

   These tests will fail on the credential encoding, by design — that is the point. They convert
   §4.1 from an argument into a reproducible artifact we can hand upstream.
3. **Raise §4.1 and §4.2 with gzuuus.** Credential encoding is the blocking one. The
   key-package-payload shape is the higher-value ask.

**Gate:** do not start Stage 2 until §4.1 has an answer. Stage 1 is safe to do regardless.

### Stage 1 — Extract a binding-agnostic RFC 9420 engine

Worth doing whether or not cordn ever ships. Today the engine is one protocol's private detail;
this makes it a library.

- Move `quartz/…/marmot/mls/` → `quartz/…/mls/`. Packages change; `marmot/` keeps everything
  else.
- Parameterize what §5.1 lists as hardcoded: group id, credential identity bytes,
  `required_capabilities`, leaf `capabilities`, and the exporter label/context all become
  constructor or call-site inputs. `exporterSecret(label, context, length)` already exists —
  stop calling it with a literal `"marmot"` from inside the engine.
- Push the Marmot-specific reads out to `marmot/`: `currentMarmotData()`, `currentGroupState()`,
  `currentNostrGroupId()`, the `AdminPolicyV1`/`GroupLifecycleV1` hooks and the agent-text-stream
  helpers. Where the engine needs a policy decision, it takes an interface; `marmot/` supplies the
  Marmot implementation.
- Move `group/MarmotMessageStore.kt` out (it is named for Marmot and keyed on
  `nostrGroupId` — it is a binding concern).
- Behaviour must not change. The existing MLS + Marmot test suites are the contract; they pass
  unmodified except for import lines.

Risk: `MlsGroup.kt` is 4,505 lines and carries the convergence/lifecycle logic. Keep this stage
strictly mechanical — extraction and parameterization, no logic edits — so the diff stays
reviewable and the test suite is a real check.

### Stage 2 — `:contextvm` module (clean-room)

New Gradle module, peer of `:quic`. Depends on `:quartz` only; no Android framework deps. **Scope,
CEP inventory, the fourteen subtle rules and the ordered build list are §6** — this stage is that
section turned into code, so read it before starting rather than working from this summary.

Sourcing discipline, restated because it constrains the whole stage: implement from the
specification documents in `ContextVM/contextvm-docs`, **not** from the LGPL SDK (§7). Whoever
takes this should avoid reading the SDK source at all; §6 was written so they do not have to.

Substages, matching §6.4's build list:

- **2a — wire layer.** Items 1–4: constants and codecs, the minimal MCP client, CEP-4/19 gift wrap
  pinned to `REQUIRED` (§8.6), and the correlation + subscription lifecycle. Ships when a
  `tools/list` round-trips against the reference coordinator in Docker.
- **2b — discovery.** Items 5–6: CEP-35 first-message tag learning and CEP-6/17 announcement and
  relay resolution. Ships when a coordinator pubkey alone is enough to connect.
- **2c — transfer profiles.** Items 7–8: the CEP-22 receiver and the CEP-41 receiver/writer. This
  is the bulk of the work and the bulk of the risk; §6.3 items 1–7 and 13–14 all live here. Ships
  when `msg_sub_many` streams a live backlog and a >64 KiB `msg_fetch_many` reassembles with a
  verified digest.
- **2d — deferred.** Item 9 (CEP-22 *sender*) until a real >64 KiB `msg_post` exists. Not CEP-8,
  not CEP-15 — but do not architect them out (§6.2).

Two constraints that shape the API and are easy to discover too late:

- **Ephemeral events have no replay** (§6.1). The subscription must be live before the request is
  published. This is a lifecycle requirement on the public API, not an implementation detail —
  a `suspend fun call()` that subscribes after publishing will lose responses nondeterministically
  and look like flaky relays.
- **The stable transport needs `sign()` *and* NIP-44 encrypt/decrypt per MCP message.** On a
  NIP-55/NIP-46 external signer that is a signer round-trip per request. cordn-web's
  stable/ephemeral split (§8) already keeps stable traffic rare; preserve that property rather
  than fighting it, and make the identity split explicit in the API so it cannot be bypassed.

Conformance approach — rule-derived unit tests plus live integration against
`ghcr.io/cordn-msg/cordn:latest` — is §6.5. Write the negative tests; the MUST-fail cases in
CEP-22/41 are where an implementation that "works" quietly diverges.

### Stage 3 — `quartz/…/cordn/` binding

- The 11-tool coordinator client over `:contextvm`, with the stable/ephemeral split of §8 encoded
  in the API so a caller cannot accidentally leak the stable identity onto the message path.
- `cordn1…` group ref codec on the existing NIP-19 bech32/TLV primitives.
- `CordnGroupMetadata` (`0xC04D`) TLS codec on `mls/codec/`.
- cordn's `app_data_dictionary` last-resort variant (§4.3).
- A cordn-profile KeyPackage builder: hex-ASCII credential identity, cordn capabilities, no
  `0xF2EE`, no Marmot required-capabilities.
- The seal: ChaCha20-Poly1305 over `MLS-Exporter("cordn","group-payload",32)`, pre-commit epoch
  for Commits. Same algorithm as `GroupEventEncryption`, separate call site — do not import the
  Marmot one.
- The application envelope: same shape as `MarmotAppEvent`, separate type in `cordn/`.
- Per-group cursor tracking, fetch-then-subscribe (bounded `msg_fetch_many` catch-up, then
  `msg_sub_many` from the freshest cursor), self-echo reconciliation by envelope `id`, and
  pending-epoch-operation finalization only on observing the matching inbound Commit. The
  reference client's `packages/cli/README.md` documents these rules and they are not optional —
  getting them wrong desynchronizes MLS state.

### Stage 4 — App integration

- `commons/` state holders and ViewModels for cordn groups, alongside the Marmot ones.
- Coordinator configuration and health surface (their client tracks per-coordinator health).
- **Surface the metadata model (§8) in the UI.** A cordn group and a Marmot group are not
  equivalent privacy-wise and must not look identical.
- `amy` verbs for scripted interop testing, per the thin-assembly-layer rule.

### Non-goals

- Multi-device (§4.6) — nothing interoperable to build.
- Reusing any `Marmot*`/`Mip*` type for cordn.
- A Kotlin ContextVM *server* — `cordn-rs` exists, is faster, and shares the SQLite schema. If we
  ever want a coordinator, run theirs.
- Bridging a Marmot group and a cordn group into one MLS group. Possible in principle once §4.1
  and §4.4 are resolved, but it is a separate design with its own trust questions.

## 10. Open questions

1. **§4.1 credential encoding** — who moves? Blocks Stage 2.
2. **§4.2 publication payload** — will upstream give KeyPackage publication its own signed payload
   or kind? Changes whether cordn KeyPackages can exist outside a coordinator.
3. **Is the goal interop or a second transport?** "Amethyst can talk to cordn users" and "Amethyst
   supports coordinator-backed groups" are different products. The second is strictly less work
   (no §4.1 dependency for group creation among Amethyst users) and strictly less valuable.
4. **Padding.** Nothing in `spec/03.md` pads the sealed payload, so message sizes leak. Worth
   proposing; cheap to add on both sides while the deployed base is small.
5. **Whose coordinator?** The privacy analysis reads very differently for a self-hosted
   per-community coordinator versus the shipped public default.
6. **Are there ContextVM conformance vectors?** None found in `contextvm-docs` or the SDK. Ask
   upstream, and offer ours — a shared CEP-22/41 framing vector set helps every non-TypeScript
   implementation and is a cheap contribution (§6.5).
7. **How stable are CEP-22 and CEP-41?** Both are Draft, both are the largest CEPs, and both are
   mandatory for cordn. A breaking revision mid-implementation is the main schedule risk in
   Stage 2c. Worth asking whether either is close to Final.
8. **Should `:contextvm` be published separately?** It is a general MCP-over-Nostr client with no
   Amethyst or cordn dependency. If the Nostr ecosystem wants a JVM/KMP ContextVM implementation,
   this is it — but that is a maintenance commitment, and the answer changes how carefully the
   public API needs designing in Stage 2a.
9. **The ContextVM spec repo has no LICENSE.** Implementing a protocol from a published spec is
   normal and fine, but if we want to quote rule text into KDoc or the module README, ask upstream
   to add one (CC-BY or similar) rather than assuming.
