# Cordn interop: extract the MLS core, then add a second binding

Status: Stages 1, 2, the core of 3 and the wire half of Stage 0 landed. The RFC 9420 engine is `quartz/…/mls/` and imports
nothing from `marmot/` — a binding supplies its rules through `MlsGroupPolicy`. `quartz/…/contextvm/`
implements the core spec plus all 12 CEPs on the client side, with the Tier C fixture server;
`quartz/…/cordn/` implements the MLS profile, the eleven coordinator tools, the seal, envelopes,
group refs and the sync rules, verified against ts-mls in both directions **and against cordn's
own wire contracts** (`quartz/tools/cordn-vector-gen` → `resources/cordn/`). Stage 4 (app
integration) is open. **Tier B has now been run** — on an explicit decision to proceed despite
the licensing problem in §7 — and found two bugs no fixture could (§7.1). §4.1 turned out not to gate the binding — see Stage 3 — and is now settled on our
side by implementing both encodings rather than waiting for an agreement (§4.1).

Correction to an earlier gate in this plan: §4.1 does **not** block Stage 2. ContextVM is
credential-agnostic and has no MLS dependency at all, so the transport was safe to build first;
only Stage 3's KeyPackage work depends on that decision.

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

Verification status: `:quartz:jvmTest` passes in a container, covering the MLS engine, ContextVM
and cordn, so the interop claims in §3 are execution-verified rather than read-verified.
`:quartz:testAndroidHostTest` passes as well — the four failures this plan previously recorded as
pre-existing (`NostrServerTest`, `LiveNegentropyIndexStoreTest`) were fixed on 2026-09-18: the
event store classified insert failures by parsing the SQLite driver's exception message, and
Android's carries none. Note the source-set shape when reading test counts: `jvmTest` dependsOn
`jvmAndroidTest`, but `androidHostTest` does **not**, so the cordn and ContextVM suites under
`jvmAndroidTest/` run on the JVM target only.

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
speaks it.** Scope is **the core spec plus all 12 CEPs** (§6.2), so `:contextvm` is a complete
MCP-over-Nostr implementation rather than a cordn adapter — cordn only exercises 7 of the 13
documents, but the rest are cheap next to the two large transfer profiles and several ride on NIPs
we already have. Only **CEP-4, CEP-6 and CEP-16** are Final; the core spec and the other nine CEPs
are Draft, including CEP-22 and CEP-41 (§6.7).

Because the CEPs are symmetric, compliance is not demonstrable against cordn alone. §6.4 defines
five test tiers. **Tier C — a Kotlin fixture server that misbehaves on demand — is built**
(`contextvm/…/fixture/`) and is what makes the negative half testable: no real server sends a
non-monotonic `progress`, a stale `pong` nonce or a mismatched digest, yet those are MUST-fail
requirements. **RFC 8785 JCS** is built too, in `quartz/…/utils/jcs/`, shared by CEP-8 and CEP-15.
**Tier B (live coordinator) has been run** — see §7.1 for the two bugs it found and §7 for the
terms it was run on. Tiers D (cross-implementation vectors) and E (a real wallet) remain open.

Sequencing, as revised in practice: **Stage 2 (the transport) was built first**, because
ContextVM has no MLS dependency and so no dependency on the §4.1 decision. What that decision
gates is **Stage 3**, the cordn binding: if it goes the wrong way every KeyPackage is permanently
ecosystem-bound and "interop" degrades to Amethyst speaking two unrelated protocols. Remaining
order: Stage 0 (cordn-side vectors) → Stage 1 (extract the MLS engine) → decide §4.1 → Stage 3-4.

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

**Decision (2026-09-18): support both encodings; do not wait for an agreement.** Neither
ecosystem is expected to move, and neither needs to. The credential encoding is a *binding*
choice, and since Stage 1 the engine no longer has an opinion about it — `MlsGroupPolicy` picks
the profile, `MarmotCapabilities`/`CordnCredential` supply the encoding. Marmot KeyPackages carry
the raw 32 bytes; cordn KeyPackages carry the 64 ASCII hex bytes; both are produced and read by
the same engine, and neither can be mistaken for the other (32 raw bytes is not valid 64-char
ASCII hex, so `CordnCredential.identityOrNull` and `KeyPackageUtils`'s 32-byte check are
mutually exclusive by construction).

What this costs is §4.4: a member advertising only one profile's capabilities cannot be added to
the other's groups, so "one MLS group, both clients" stays out of reach. That was already a
deliberate profile decision rather than a consequence of this one. What it buys is that Amethyst
speaks both today rather than blocking on a conversation neither side has an incentive to finish.

Still worth raising with gzuuus as a fact rather than a request — an implementation that reads
both is useful evidence for whichever encoding a future joint profile picks.

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

**Superseded by Stage 1, which landed.** The measurements below are what the engine looked like
before the extraction; they are kept because they are what the stage was scoped against. The
engine is now `quartz/…/mls/` with zero `marmot/` imports.

`quartz/…/marmot/mls/` was **11,964 LOC**. Measured coupling to the Marmot layer:

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

## 6. ContextVM: full surface review and compliance matrix

We have to write this from scratch — nothing in Quartz speaks it, and the only SDKs are TypeScript
and Rust. **Scope decision: implement the whole CEP list**, not just the subset cordn exercises.
That makes `:contextvm` a complete MCP-over-Nostr implementation rather than a cordn adapter, and
it means compliance has to be demonstrable per CEP rather than "cordn works".

**Clean-room sourcing.** Everything below is derived from the specification documents in
`ContextVM/contextvm-docs` (the `docs/contextvm-docs` submodule of the SDK, at
`src/content/docs/reference/`), **not** from the LGPL SDK source (§7). Implementers should work
from those documents. The docs repo carries **no LICENSE file** — the protocol is free to
implement, but do not paste spec prose into our repo; paraphrase.

### 6.1 The core spec is small

`spec/ctxvm-draft-spec.md` (352 lines, Draft) is nearly all of the base protocol:

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

### 6.2 Compliance matrix

Thirteen documents: the core spec plus 12 CEPs. `Rules` is the rule-id prefix this plan assigns
for citing individual requirements, following the `STORE-Fxx` convention the `event-store-semantics`
skill established — so a future divergence can be named precisely instead of described. `Gate` is
what has to pass before we claim compliance; the test catalog is §6.5.

| Spec | Status | Surface | Rules | Gate |
| ---- | ------ | ------- | ----- | ---- |
| **Core** draft spec | Draft | Kind 25910, `content` = stringified JSON-RPC, `p`/`e` tags, optional MCP lifecycle | `CVM-CORE-*` | A + B |
| **CEP-4** Encryption | **Final** | NIP-44 encrypt the *signed* inner 25910 event into a NIP-59 wrap (kind 1059), **no rumor layer**; `support_encryption` | `CVM-4-*` | A + B + **D** |
| **CEP-19** Ephemeral Gift Wraps | Draft | Kind **21059**, identical semantics to 1059 but ephemeral; `support_encryption_ephemeral`; MUST fall back to 1059 | `CVM-19-*` | A + B |
| **CEP-6** Public Announcements | **Final** | Addressable **11316** server, **11317** tools, **11318** resources, **11319** resource templates, **11320** prompts; discovery tags `name`/`about`/`picture`/`website`/`support_*` | `CVM-6-*` | A + B |
| **CEP-17** Relay List Metadata | Draft | NIP-65 **kind 10002**, unmarked `r` tags in the ContextVM profile; bootstrap vs advertised relays are distinct | `CVM-17-*` | A + B |
| **CEP-35** Stateless Discovery | Draft, Info | Discovery tags on the **first direct message each side sends**; unknown tags MUST be preserved; `p`/`e` excluded from the learned surface | `CVM-35-*` | A + C |
| **CEP-22** Oversized Transfer | Draft | Bounded reassembly over `notifications/progress`; `progressToken` = transfer id; `start`/`accept`/`chunk`/`end`/`abort`; `completionMode: "render"`; SHA-256 digest + `totalBytes` + `totalChunks` | `CVM-22-*` | A + B + C |
| **CEP-41** Open Streams | Draft | Long-lived streams, same envelope; `start`/`accept`/`chunk`/`ping`/`pong`/`close`/`abort`; per-sender `progress`; contiguous `chunkIndex` | `CVM-41-*` | A + B + C |
| **CEP-16** Client Pubkey Injection | **Final**, Info | Server injects `_meta.clientPubkey` into inbound requests; opt-in, default off | `CVM-16-*` | A + C (server role) |
| **CEP-8** Pricing and Payment | Draft | `cap`/`pmi`/`payment_interaction`/`direct_payment`/`change` tags; transparent notification lifecycle vs `explicit_gating` JSON-RPC errors (`-32042`, `-32043`, `-32602`); canonical invocation identity | `CVM-8-*` | A + C + **E** |
| **CEP-15** Common Tool Schemas | Draft | RFC 8785 JCS hash of `{name, normalized inputSchema, normalized outputSchema?}`; `io.contextvm/common-schema` `_meta`; NIP-73 `i`/`k` tags | `CVM-15-*` | A + B |
| **CEP-21** PMI Recommendations | Draft, Info | PMI naming conventions, `-direct` suffix for bearer settlement | `CVM-21-*` | A |
| **CEP-23** Server Profile Metadata | Draft | Server-published **kind 0** and optional **kind 1** | `CVM-23-*` | A + B |
| **CEP-24** Server Reviews | Draft | NIP-22 **kind 1111** anchored to the `11316:<pubkey>:` `a` coordinate | `CVM-24-*` | A + B |

Gate legend (method in §6.4): **A** rule-derived unit tests · **B** live integration against a
real counterparty · **C** adversarial tests against our own fixture server · **D** cross-
implementation vector exchange · **E** wallet integration.

Status reality check: only **CEP-4, CEP-6 and CEP-16** are Final. The core spec and the nine other
CEPs are Draft, including both large transfer profiles. Per the CEP guidelines a CEP reaches Final
only once its reference implementation lands, so "Draft" here means the spec text may still move,
not that it is unimplemented. Budget for churn (§6.7).

### 6.3 The high-risk rules

These are where an implementation passes its own unit tests and then diverges against a real peer.
Each is a spec MUST and each maps to a named test in §6.5.

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
11. **Four separate correlation identifiers coexist**: the nostr `e` tag (event level), the
    JSON-RPC `id` (MCP level), `progressToken` (CEP-22/41 transfer level), and CEP-8's canonical
    invocation identity (payment level). Plus `_meta.clientPubkey` (CEP-16) as the authenticated
    caller identity — the mechanism §4.2's KeyPackage binding ultimately rests on.
12. **Ephemeral delivery has no replay.** Per §6.1, subscribe before you publish. Combined with
    per-identity subscriptions (§8.2), each identity needs its own live `#p` subscription on 25910
    plus both gift wrap kinds.
13. **A zero-chunk stream is valid** — `close` immediately after `start`, with `lastChunkIndex`
    omitted. An empty `msg_sub_many` backlog will exercise this on day one.
14. **`close.lastChunkIndex` is optional and meaningful.** Present, it is a completeness bound and
    every index `0..lastChunkIndex` must have arrived; omitted, the stream was open-ended and no
    bound is asserted. Senders omit it for live feeds.
15. **CEP-8 excludes `params._meta` from the canonical invocation identity, but forwards it at
    execution.** The exclusion exists because MCP clients regenerate `progressToken` per call, so
    without it two semantically identical invocations never match one paid authorization. Getting
    this backwards either breaks retry matching or strips transport metadata from the handler.
16. **CEP-8 forbids silent fallback.** A server that will not accept `explicit_gating` MUST NOT
    quietly use the transparent lifecycle; and a client that required `explicit_gating` SHOULD NOT
    auto-satisfy transparent `payment_required` notifications. A naive payment handler that pays
    whatever it is asked to pay violates the client half of this.
17. **CEP-15's hash is a verification target, not a label.** The whole point is that two servers
    documenting a tool differently produce the same hash. A client that trusts the advertised
    `schemaHash` without recomputing it from the tool definition gains nothing from the CEP.

### 6.4 Conformance method

Five tiers, because the CEPs are symmetric and no single counterparty exercises all of them.

**Tier A — rule-derived unit tests.** Every MUST/MUST NOT in a CEP becomes a named test carrying
its rule id, asserted against pure codecs and frame state machines with no network. This is where
the negative cases live and it is the bulk of the value: the CEP-22/41 validation sections are
written almost entirely as failure conditions. Offline, fast, runs in `commonTest`.

**Tier B — live integration against a real counterparty.** `ghcr.io/cordn-msg/cordn:latest` with
`CORDN_STORAGE_BACKEND=memory` and `CORDN_ANNOUNCED=false` boots in one command and enables
CEP-22 and CEP-41. It covers the core spec, CEP-4/19, CEP-6/17 and the happy paths of 22/41.
`cordn/packages/test-utils/src/mockRelay.ts` exists if we want a relay stub instead of a public
relay. Tagged as an integration suite, not run on every build.

**Tier C — adversarial tests against our own fixture server.** This is the tier that does not
exist yet and has to be built: **a Kotlin `:contextvm` test fixture that can play the server role
and misbehave on demand.** No real server will send a non-monotonic `progress`, a second `start`
on a live token, a `pong` with a stale nonce, a digest that does not match, or a
`payment_required` in a session where `explicit_gating` was accepted — but our client must handle
all of them correctly, and several are outright MUST-fail requirements. The fixture is also the
only practical way to test CEP-16 (a server-side obligation) and the server half of CEP-8.
Building it is a first-class Stage 2 deliverable, not test scaffolding.

**Tier D — cross-implementation vector exchange.** For the crypto surface, agreeing with ourselves
is not evidence. Generate CEP-4 wrap/unwrap vectors and CEP-22/41 frame sequences, check them in
under `quartz/src/commonTest/resources/contextvm/`, and verify both directions against the
reference implementation the way `TsMlsWelcomeInteropTest` does for MLS. Ask upstream to adopt them
(§10) — a shared vector set helps every non-TypeScript implementation and is a cheap contribution.

**Tier E — wallet integration.** CEP-8's client role is a payment *handler*, so compliance is only
demonstrable end to end against a real rail. `bitcoin-lightning-bolt11` is the one recommended PMI
(CEP-21) and we already have NIP-47 NWC and NIP-57 zaps in Quartz, so this is integration, not new
payment code. Regtest or a small-amount live wallet; gated behind a manual test tag.

**Shared prerequisite: RFC 8785 (JCS).** Both CEP-8 (canonical invocation identity) and CEP-15
(schema hash) require it, and **Quartz has no JCS implementation** — I checked; the
`canonicalize` hits in the tree are IPv6, media types and relay URLs, all unrelated. So JCS is its
own build item with its own vector suite (the RFC's test vectors, plus the number-formatting edge
cases that make JCS genuinely tricky: `1E30`, `-0`, very small and very large doubles). Put it in
`quartz/…/utils/` rather than in `:contextvm` — it is a generic primitive and NIP work may want it.

### 6.5 Per-CEP test catalog

Tier A cases, grouped by rule prefix. Negative cases are marked ✗ — they are the majority by
design, and a suite without them proves nothing.

**`CVM-CORE`** — round-trip every message class (request, response, error, notification);
`content` is a *string*, not an embedded object (a plausible early bug); reject a non-25910 kind;
`e`-tag correlation maps a response to its request; ✗ a response published before we subscribed is
unrecoverable (asserts the §6.1 lifecycle rule rather than pretending it works); the
`initialize` → `notifications/initialized` sequence; and the stateless path succeeding with no
initialize at all.

**`CVM-4`** — inner event is signed and verifies; wrap `p` tag names the recipient; two wraps of
the same payload have **different** outer pubkeys (fresh key per wrap); decrypt recovers the inner
event byte-for-byte; correlation uses the inner `id`; ✗ inner signature invalid → reject; ✗
unsupported wrap kind → reject; conversation-key symmetry both directions. Tier D vectors here.

**`CVM-19`** — prefer 21059 when both peers advertise `support_encryption_ephemeral`; MUST fall
back to 1059 when the peer does not; 21059 and 1059 decode identically; subscription filters
include both kinds.

**`CVM-6`** — parse each of 11316–11320 (`content` is a stringified initialize/list result);
replaceable semantics keep the newest `created_at` per `(kind, pubkey)`; all discovery tags parsed;
optional tags absent → no failure; discovery tags seen on a first direct message are treated as
equivalent to announcement tags (the CEP-6/CEP-35 overlap).

**`CVM-17`** — unmarked `r` tag means read **and** write; `read`/`write` markers honored when
present; latest-wins replacement; bootstrap relays are publication targets and MUST NOT be assumed
operational; absent 10002 → fall back to configured relays.

**`CVM-35`** — client sends capability/negotiation tags on its first direct message and omits them
after; server tags are learned from the first direct server→client message even when it is not an
initialize result; **unknown tags preserved** and reachable via a raw accessor; `p` and `e` excluded
from the learned surface; a feature tag on a later message is message-local and does not mutate the
session baseline.

**`CVM-22`** — happy path reassembles and validates; out-of-order chunks inside the buffer window
reassemble correctly by `progress`; nothing is surfaced upward before validation succeeds; ✗ digest
mismatch; ✗ `totalBytes` mismatch; ✗ `totalChunks` mismatch; ✗ `chunk` before `accept` in a
stateless flow; ✗ non-monotonic `progress`; ✗ `end` with unresolved gaps; ✗ unknown
`completionMode`; ✗ declared totals over local policy rejected at `start`; ✗ transfer started for a
request with no `progressToken`; `abort` is terminal.

**`CVM-41`** — happy path streams incrementally; zero-chunk stream (`close` straight after
`start`) succeeds; `close` with `lastChunkIndex` and every index present succeeds; `close` without
`lastChunkIndex` on an open-ended feed succeeds; the final JSON-RPC response is still required and
delivered after `close`; idle → `ping` → `pong` keeps the stream alive; ✗ no `pong` before probe
timeout fails the stream; ✗ `pong` with unknown, duplicate or expired nonce is not liveness
evidence; ✗ nonce over 64 bytes rejected; ✗ second `start` on a live `progressToken`; ✗
non-contiguous `chunkIndex` at `close`; ✗ `close` with `lastChunkIndex` and a missing index; ✗
frames after `close` or `abort` ignored; `pong.progress` unrelated to `ping.progress` (asserts
§6.3.2 explicitly).

**`CVM-16`** — the fixture server injects `_meta.clientPubkey` derived from the event pubkey;
injection is off by default; our client never sends `clientPubkey` itself (a client-supplied value
would be a spoof, and the coordinator's §4.2 binding depends on it being server-derived).

**`CVM-8`** — `cap` tag parses fixed (`"100"`) and range (`"100-1000"`) prices with the
`tool:`/`prompt:`/`resource:` prefixes; PMI intersection selection picks a mutually supported
method; absent `payment_interaction` means `transparent`; a requested `explicit_gating` accepted by
the server is disclosed on the first direct response; ✗ requested `explicit_gating` not accepted
MUST NOT silently become transparent, and our handler MUST NOT auto-pay transparent
`payment_required` in that session (§6.3.16); `-32602` shape on an unsupported mode; `-32042`
`Payment Required` carries one or more `payment_options`; `-32043` `Payment Pending` with
`retry_after`; mid-session mode upsert re-discloses on transition to `explicit_gating`; canonical
identity is stable across a changed JSON-RPC `id`, a changed outer event id, and a regenerated
`progressToken` (the `_meta` exclusion); transparent idempotency — the same outer event id is not
charged twice; `ttl` expiry; at most one `direct_payment` tag, first supported PMI wins; `change`
tag parsed on `payment_accepted`.

**`CVM-15`** — normalization strips `title`/`description`/`examples`/`default`/`deprecated`/
`readOnly`/`writeOnly` and `x-*` keys **at every nesting level**; the same tool documented
differently yields the same hash (the CEP's whole purpose); adding an `outputSchema` changes the
hash; `$ref` bundled into a self-contained representation, with ✗ no network resolution attempted;
`i`/`k` NIP-73 tags emitted and parsed; and the key client-side rule — **recompute the hash from
the tool definition and reject a mismatched advertised `schemaHash`** rather than trusting it.

**`CVM-21`** — PMI format matches `[a-z0-9-]+`; `-direct` suffix detected as bearer-settlement
capable; unknown PMI degrades gracefully rather than failing the session.

**`CVM-23`** — parse a server `kind 0` as NIP-01 metadata (reuses existing Quartz code); `kind 1`
notes from a server pubkey carry no special semantics.

**`CVM-24`** — top-level review builds both uppercase `A`/`K`/`P` and lowercase `a`/`k`/`p` with
`k = 11316`; a reply keeps uppercase `A`/`K`/`P` at the root announcement while using lowercase
`e` for the parent comment and `k = 1111`; the discovery filter returns reviews for a given server.

### 6.6 Build list for `:contextvm`

Ordered so each item is testable before the next depends on it:

| # | Component | Gate | Notes |
| - | --------- | ---- | ----- |
| 1 | Kinds, tags, frame types, JSON-RPC 2.0 codec | A | Pure data |
| 2 | Minimal MCP client | A + B | `initialize`, `notifications/initialized`, `tools/call`, `tools/list`, typed errors, `_meta`/`progressToken` plumbing |
| 3 | CEP-4/19 gift wrap | A + B + D | On existing `nip44Encryption` + `nip59Giftwrap`. Pin `REQUIRED` (§8.6) |
| 4 | Correlation + subscription lifecycle | A + B | `#p` subscriptions on 25910 + both wrap kinds, `e`-tag routing, subscribe-before-publish, per-identity scoping |
| 5 | CEP-35 discovery-tag learning | A | First-message exchange, unknown-tag preservation, raw accessor |
| 6 | CEP-6/17/23 server discovery | A + B | 11316–11320 + 10002 + kind 0. Reuses `INostrClient` `accessories/` one-shots |
| 7 | **Fixture server (Tier C)** | — | Plays the server role, misbehaves on demand. Unblocks every adversarial test below |
| 8 | CEP-22 receiver | A + B + C | Frame machine, bounded reassembly, admission control, digest verify |
| 9 | CEP-41 receiver + writer | A + B + C | Two-counter machine, keepalive, `Flow` delivery, dual completion |
| 10 | CEP-22 sender | A + C | Proactive fragmentation with relay-size margin |
| 11 | RFC 8785 JCS | A | In `quartz/…/utils/`, not here — shared by 8 and 15 (§6.4) |
| 12 | CEP-15 common tool schemas | A + B | Normalization, hash, `i`/`k` tags, recompute-and-verify |
| 13 | CEP-8 + CEP-21 payments | A + C + E | Both lifecycles, canonical identity, PMI registry; handler on NIP-47 NWC |
| 14 | CEP-16 injection (server role) | A + C | Only meaningful in the fixture server and any server we later expose |
| 15 | CEP-24 reviews | A + B | Thin layer on existing NIP-22 |
| 16 | Dual-signer plumbing | A + B | Account `NostrSigner` for stable, local keypair for ephemeral |

Sizing reference: the SDK's client-side surface (`src/core` + `src/transport/nostr-client` +
oversized-transfer + open-stream, tests excluded) is ~5.4k lines of TypeScript, and that excludes
payments, the server role and the announcement manager. Items 8, 9 and 13 are the bulk of the work
and the bulk of the risk.

Reusable beyond cordn: this is a general MCP-over-Nostr client *and* the beginnings of a server.
Any future Amethyst work that wants to call a remote MCP server — or expose one — lands here
rather than in a feature module.

### 6.7 Spec stability risk

Only CEP-4, CEP-6 and CEP-16 are **Final**. Everything else, including the core spec and both
transfer profiles, is **Draft** — and CEP-8 (722 lines), CEP-41 (534) and CEP-15 (502) are the
three largest documents. Per the CEP guidelines, Final requires a completed reference
implementation, so Draft here means the text can still move.

Mitigations: keep each CEP's rules behind a narrow interface so a revision is a localized change;
record the implemented revision (commit hash of `contextvm-docs`) in the module README and in each
rule-id group; and make the Tier A suite the tripwire — when a CEP revises, the diff against our
named rules says exactly what to change.

## 7. Licensing

🔴 **`ContextVM/sdk` is LGPL-3.0** (`COPYING.LESSER` over the GPL-3 base text; `package.json`
declares `LGPL-3.0-1`). We would not link a TypeScript library into Quartz regardless, but per the
dependency rule in `.claude/CLAUDE.md` this means:

- A Kotlin ContextVM implementation **must be clean-room from the spec and CEP documents**, not a
  translation of that source. A derivative translation would carry LGPL terms into MIT Quartz.
- `cordn-rs` links the Rust `contextvm-sdk` as a dependency; that is their artifact, not ours.
- If a Kotlin/JVM ContextVM library ever appears under LGPL, linking it is a WARN-and-call-out
  (call it out in the PR description), not an automatic stop.

🔴 **Correction (2026-09-18, verified): "everything under `Cordn-msg` is MIT" was wrong.** Only
two of the five packages are licensed at all. Checked against the actual files at `b465df0` and
against the published npm tarballs:

| Package | `LICENSE` file | `license` field | Published to npm |
| ------- | -------------- | --------------- | ---------------- |
| `packages/core` | yes | MIT | `@cordn/core` |
| `packages/cli` | yes | MIT | `@cordn/cli` |
| `packages/coordinator` | **no** | **none** | no |
| `packages/server` | **no** | **none** | no |
| `packages/test-utils` | **no** | **none** | no |

The repository root has no LICENSE either. So the **reference coordinator is unlicensed** —
default copyright, all rights reserved — and so is the `ghcr.io/cordn-msg/cordn` image built from
it. Almost certainly an oversight rather than intent, given the two licensed siblings, but the
rule in `.claude/CLAUDE.md` does not have an "obviously meant to be MIT" branch.

Consequences, and they are the reason Stage 0 landed the way it did:

- **Tier B rests on unlicensed code, and was run anyway on an explicit decision.** The plan's
  Tier B (run their coordinator locally with `CORDN_STORAGE_BACKEND=memory`) rests entirely on
  unlicensed code. Nothing about that has changed and nothing here is a shipping dependency: the
  image is pulled by hand, on a developer machine, and no build file, test task or CI job
  references it. What changed is that a maintainer decided the verification was worth doing on
  those terms — see §7.1 for what it found, which is the argument for the decision. The line the
  dependency rule draws still holds: **do not commit it as a dependency, and do not wire it into
  a build or CI.** If Tier B becomes a routine part of the test process, it needs the LICENSE
  below first.
- **`@cordn/core` is fine and is enough for the valuable half.** It is MIT, it ships a LICENSE,
  and it holds the authoritative zod contracts for all eleven tools plus the group-ref bech32
  codec — i.e. the wire surface. That is what `quartz/tools/cordn-vector-gen` uses.
- **Ask upstream for a LICENSE on the remaining packages.** Cheap for them, and it is what
  unblocks Tier B. Worth raising alongside §4.2.

The specs themselves (`spec/`, `design/`) are also unlicensed, so the existing rule stands: we
implement from them, we do not paste their prose into KDoc.

## 7.1 Tier B, run: what a live counterparty found that five tiers of our own tests could not

Run on 2026-09-22 against `ghcr.io/cordn-msg/cordn:latest` (`v0.5.7`, digest
`sha256:3e20391…`, `CORDN_STORAGE_BACKEND=memory`, `CORDN_ANNOUNCED=false`) over **geode** as the
relay, driven by the new `amy cordn …` verbs. Two accounts, one process per command.

**It works, end to end.** `kp_publish` → `group create` → `invite` (take KeyPackage, verify the
publication payload, commit, store Welcome) → `welcome_take` → accept → messages in both
directions → `group info` agreeing on both sides at epoch 1 with the same two members. The
coordinator reports `name: cordn-server`, `version: 0.1.0`, `protocolVersion: 2025-11-25`,
`capabilities: [tools]`.

But it did not work at first, and neither reason was findable without a real peer.

### Finding 1 — our CEP-4 gift wraps were invisible to any real ContextVM server

`CvmGiftWrap.wrap` set `created_at` to NIP-59's randomized timestamp: the real time minus a
random offset of up to two days. The reference server subscribes with **`since = now`** when it
connects, which is the obvious filter for a live request stream, and a relay honours `since`. So
every request we have ever sent to a real coordinator was dropped **by the relay**, before the
coordinator saw it. The symptom is a 20-second timeout with nothing in any log, because from the
server's side nothing happened.

Why no test caught it: our fixture server (Tier C) reads whatever is addressed to it with no
`since` filter, and so does every test double. A backdated wrap is indistinguishable from a
fresh one unless something on the other end filters on time — and only a real server does.
There was even a test, `CVM-4-14`, asserting the *broken* behaviour ("the wrap timestamp is
shifted and must not be used for ordering"); it passed for the whole life of the transport. A
test that pins what the code does is not evidence about what the protocol needs.

The fix is to send the real time, and the reasoning is worth keeping because the NIP-59 rule is
right in its own context: NIP-59 shifts the timestamp because a gift-wrapped DM is **stored** and
fetched later, so its timestamp would otherwise reveal when a conversation happened. A ContextVM
wrap carries an RPC request to a peer listening right now. The shift also protects nothing here —
CEP-4 puts the recipient in a visible `p` tag, delivery is real time, and kind 21059 is ephemeral
so no relay retains it — so it hid from an observer a fact that same observer reads off the
socket, at the cost of the request never arriving. `CVM-4-14` now asserts the send time.

### Finding 2 — self-echo bookkeeping does not survive a process boundary

With delivery working, a client's own traffic came back to it as `undecryptable`: alice's
`fetch` after her own invite and message reported `cursor 1: Authentication failed` (her Commit,
sealed under the pre-commit epoch key she has since left) and `cursor 2: Generation 0 already
consumed` (her own message, whose sender ratchet has moved on).

The cause is that `GroupInbox` holds `pendingOperations` and `ownMessageCursors` in memory while
the cursor beside them is persisted. Post advances neither cursor nor disk — correctly, since a
lower cursor may still hold somebody else's unprocessed message — so a client that exits between
posting and ingesting starts the next run with a cursor that will re-read its own traffic and no
record that it is its own.

`amy` exposes this on every run because each verb is a process. It is **not** an `amy` bug: the
Android app hits it whenever the OS kills it between sending and syncing, which is ordinary. The
visible damage is a gap in the sender's own conversation. The latent damage is worse and narrower:
`Ingestion.SelfEchoUnapplied` is how a client that died mid-post applies its own Commit, and that
recovery path depends on exactly the record that dying destroys.

Fixed by persisting the bookkeeping next to the cursor: `EchoState` beside `GroupCursor`, saved on
the same beat, deleted when there is nothing pending. Own-message cursors at or below the fetch
cursor are pruned — the stream has delivered them and they can never come round again — while
pending Commits are kept until their echo matches, because that echo is the only copy that will
ever be offered.

There was a test for this too, and it also passed: `a group survives a restart` asserted
`delivered.none { it is Delivery.Message }`. An `Undecryptable` is not a `Message`, so a gap in
the sender's own conversation satisfied it. Both restart tests now assert what the delivery **is**
— `Echo` — and both fail if either half of the persistence is removed.

With both fixed, `cli/tests/cordn/tier-b.sh` passes end to end: handshake, `kp_publish`, group
create, invite, Welcome opened without joining, join, messages both ways with the sender's own
traffic reported as echoes rather than gaps, and both sides agreeing on epoch 1 and the same two
members.

### 7.2 The other half: our MLS against theirs

Tier B above runs amy against amy through their coordinator. Both MLS
endpoints are ours, so the ratchet tree, the Welcome and the Commit only ever
agree with themselves — it proves the transport and the coordinator client,
and nothing about RFC 9420 interop.

`cli/tests/cordn/interop-client.sh` closes that. It puts **`@cordn/cli`
(ts-mls)** on one end and **amy (quartz)** on the other, in one group, over the
live wire. That half carries no licensing problem — `@cordn/cli` is MIT and
comes from npm — though the coordinator underneath it still does.

Three directions, and they are not redundant:

1. **Their group, our joiner.** Our engine opens a ts-mls Welcome and reads
   their GroupContext extensions, their metadata and their credentials out of
   it, then decrypts their application messages.
2. **Our group, their joiner.** Their engine opens **our** Welcome. This is
   the direction no fixture can test: a fixture we wrote accepts what we emit
   by construction, so only a foreign implementation can say our Welcome is
   well formed.
3. **Our later Commit.** The sharpest, and the one worth having built the
   harness for. Until direction 3 their epoch came from a Welcome, which
   carries the group state ready-made; this is the first time they must apply
   one of our handshake messages. Ours are **public-framed**
   (`MlsMessage(PublicMessage)`, wireformat 2) where theirs are private-framed,
   and `CordnGroupManager.invite` has always asserted in its KDoc that their
   `processMessageBase64` admits both — a claim read off their source and never
   executed. It holds: they apply our Commit, advance to epoch 2, and seal a
   message we then open.

All of it passes. Mutation-checked rather than trusted: sealing
`result.commitBytes` (the bare RFC 9420 struct) instead of
`result.framedCommitBytes` fails directions 3 and the third-member join and
**leaves direction 2 green**, because a peer that joined by Welcome never
parses that Commit and only stalls once it has to. That is the whole reason
direction 3 exists as its own case, and it is now demonstrated rather than
argued.

One asymmetry this surfaced and did not resolve: **the reference client sends
kind 25910 in the clear**, unwrapped, where we pin `EncryptionMode.REQUIRED`
and always gift-wrap (§8.6). Both work against the coordinator, so nothing is
broken — but the two clients exercise different halves of CEP-4 against the
same server, and our encrypted path is the one with no second implementation
behind it. Worth a Tier D vector exchange.

### What Tier B is, and is not

These are both **transport and bookkeeping** bugs. Not one byte of the crypto surface moved: the
MLS engine, the seal, the envelopes and the group refs were already verified against ts-mls and
against cordn's own wire contracts, and Tier B found nothing wrong with any of them. That is the
shape to expect from a live tier — it tests the things a fixture cannot model, which are the
things a fixture was written by the same person who wrote the client. §7.2 then covers the
crypto surface against a foreign implementation, and finds it sound.

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

### Stage 0 — Ground truth and the upstream conversation — PARTLY LANDED

The wire half landed on 2026-09-18; the live-coordinator half ran on 2026-09-22 (§7.1).

**Landed: contract vectors from cordn's own code.** `quartz/tools/cordn-vector-gen` generates
`resources/cordn/coordinator-contracts.json` from **`@cordn/core`** (MIT) — the package their
reference coordinator and client both import. It hands each payload *we* send to *their* zod
schema, so a field we named wrong fails at generation time, and carries a `rejects` set per
method so a passing positive means something. `CoordinatorContractVectorTest` (15 tests) drives
`CoordinatorClient` through the real ContextVM transport and asserts the arguments the
coordinator sees equal those vectors, then replays their result shapes back through our parser;
`CordnGroupRefVectorTest` (5 tests) checks `cordn1…` refs in both directions against their bech32
codec.

Two findings from doing it:

1. **Mutation-checked, because 20 tests passing on the first run means nothing on its own.**
   Making a first-ever fetch send `after = 0` instead of omitting it, and flipping the group-ref
   TLV emission to ascending order, each killed exactly one test. The TLV mutation was caught
   *only* by the encode-direction test — decoders accept any order by spec, so a decode-only
   suite would have shipped that divergence.
2. **The `kp_publish` legacy field is read-only.** Their current schema rejects
   `{kp_ref, keyPackageBase64}`, so our `?? keyPackageBase64` fallback is correct for parsing old
   publication events and must never be used when sending.

**Landed: Tier B (live coordinator).** Run on 2026-09-22 against the reference coordinator in
Docker, over geode as the relay, driven by `amy cordn …`; the harness is
`cli/tests/cordn/tier-b.sh`. It found two bugs — backdated CEP-4 gift wraps that no real
ContextVM server could see, and self-echo bookkeeping that did not survive a process boundary —
both fixed. §7.1 has the detail; §7 has the licensing terms it was run on, which have not
changed: not a dependency, not in CI.

**Not done: the upstream conversation**, which is the maintainer's to have. §4.1 no longer waits
on it (we implement both encodings); §4.2 and the missing LICENSE files are the asks worth making.

The original plan for this stage, for reference:

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

### Stage 1 — Extract a binding-agnostic RFC 9420 engine — LANDED

Done in four commits. The engine is `quartz/…/mls/` and imports nothing from `quartz/…/marmot/`.

| What | Where it went |
| ---- | ------------- |
| The engine (28 files, 11,964 LOC) | `quartz/…/marmot/mls/` → `quartz/…/mls/` |
| `MlsGroupManager`, `MlsGroupStateStore`, `MarmotMessageStore` | → `marmot/groups/` (all keyed on `nostrGroupId`) |
| MIP-03 authorization, depletion guard, join role check, self-remove gate | → `marmot/groups/MarmotGroupPolicy` |
| Leaf + `required_capabilities` profiles | → `marmot/groups/MarmotCapabilities` |
| `currentMarmotData/GroupState/NostrGroupId`, `agentTextStreamSecret` | → `marmot/groups/MarmotGroupViews` (extension functions) |
| `last_resort_key_package` (0x0004) | → `mls/components/ComponentsList` — it is the extensions draft's, not Marmot's |

**The seam is `MlsGroupPolicy`**: three hooks the engine calls where RFC 9420 defers to the
application (`authorizeCommit`, `authorizeSelfRemove`, `validateJoin`) and four values it reads
(leaf capabilities, `required_capabilities`, extra known extension types, the commit exporter
label). One argument selects a whole profile — `MlsGroup.create(id, policy = MarmotGroupPolicy)`
brings the rules, the capabilities and `MLS-Exporter("marmot", "group-event", 32)` together — so
adopting it cost one added argument per call site rather than five.

Policies receive a read-only `GroupView`, not the `MlsGroup`: a policy holding the group could
commit or rotate keys from inside the check meant to gate those things.

**The default is permissive**, which is a trade worth naming. Closed would make the engine
unusable without a policy and would push callers into writing an allow-everything one anyway.
The cost is that a group restored without its policy silently drops the binding's rules — a
policy is behaviour, not state, so it is deliberately not in `MlsGroupState`. All ten production
construction sites are in `marmot/` and all ten name it.

Two findings from doing it:

1. **`:quic` was already a second consumer, reaching through the wrong package.**
   `quic/tls/TlsClient.kt` imported `quartz.marmot.mls.crypto.X25519` for its TLS 1.3 handshake —
   neither Marmot nor MLS. That is the argument for this stage independent of cordn.
2. **The test suite found every site that had been relying on Marmot defaults.** The first run
   after `MlsGroup.create` stopped defaulting to Marmot's profile failed 13 tests, each one a
   Marmot test that had been getting a Marmot-shaped group for free. The other ~180 construction
   sites kept passing on the permissive default — they are engine tests, and they now prove the
   engine runs without Marmot at all.

8 tests were added for the seam itself (`MlsGroupPolicySeamTest`, `MarmotPolicySeamTest`),
including the half no existing test covered: the same group at the same state accepts the same
commit once the policy is gone. Verified by mutation — ignoring the policy in `commit()` and
re-hardcoding the exporter label each kill exactly their guarding tests.

Suite: `:quartz:jvmTest` 5074 → 5082, `:commons:jvmTest` 2202, both green.

What Stage 3 still owes: `MlsGroupManager` is keyed on `nostrGroupId` 147 times, so cordn cannot
reuse it and needs its own manager over the same `MlsGroup`. Class names were left alone in the
move — `MlsGroupManager` under `marmot/groups/` reads correctly and renaming four classes would
have churned 22 files across five modules for clarity the package path already gives.

### Stage 2 — ContextVM (clean-room) — LANDED

Shipped as `quartz/…/contextvm/`, implemented from the specification documents rather than the
LGPL SDK. 172 tests,
green on jvm. What is in:

| Build item | Where |
| ---------- | ----- |
| 1 constants, tags, JSON-RPC codec | `core/CvmKinds`, `core/CvmTags`, `jsonrpc/` |
| 2 minimal MCP client | `mcp/CvmMcpClient`, `mcp/McpMethods` |
| 3 CEP-4/19 gift wrap | `cep04Encryption/CvmGiftWrap` (pins `REQUIRED`) |
| 4 correlation + subscription lifecycle | `transport/CvmTransport` |
| 5 CEP-35 discovery learning | `cep35Discovery/SessionDiscovery` |
| 6 CEP-6/17/23 discovery | `cep06Announcements/`, `cep17RelayList/ServerRelay` |
| 7 **fixture server (Tier C)** | `fixture/CvmFixtureServer`, `fixture/InMemoryRelayPool` |
| 8 CEP-22 receiver | `cep22OversizedTransfer/OversizedTransferReceiver` |
| 9 CEP-41 receiver | `cep41OpenStreams/OpenStreamReceiver` |
| 10 CEP-22 sender | `cep22OversizedTransfer/OversizedTransferSender` |
| 11 RFC 8785 JCS | `quartz/…/utils/jcs/JsonCanonicalization` |
| 12 CEP-15 schemas | `cep15CommonSchemas/CommonToolSchema` |
| 13 CEP-8 + CEP-21 | `cep08Payments/` |
| 14 CEP-16 injection | in the fixture's server role |
| 15 CEP-24 reviews | `cep24Reviews/ServerReview` |
| 16 dual-signer | `transport/DualSigner` |

Source is organized one package per CEP (`cep04Encryption/`, `cep08Payments/`, …), matching the
`nipXX` convention in `quartz` and `mipXX` in `marmot`; only what the core draft spec defines
(`core/`, `jsonrpc/`, `transport/`, `mcp/`) and the CEP-22/41 shared framing (`transfer/`) keep
names, having no CEP number to carry. `contextvm/README.md` records the four placements that are
judgment calls.

Four findings worth carrying forward, all caught by tests rather than review:

1. **CEP-22/41 ordering.** The first receiver rejected frames whose `progress` did not increase on
   arrival, conflating "the sender emits monotonic progress" with "frames arrive in order". Both
   CEPs say the opposite: `progress` is the assembly index and explicitly not an arrival-order
   guarantee, and receivers may buffer out-of-order chunks. Validation is positional now.
2. **JCS and `Double.MIN_VALUE`.** JVM prints `4.9E-324` where ECMAScript requires `5e-324`, so
   "trust the platform to already be shortest" would have hashed differently from every other
   implementation. Digits are shortened explicitly until the shortest round-tripping form is
   found, which removes the platform assumption entirely.
3. **Event subclassing.** `CvmMessageEvent` began as an `Event` subclass whose `create()` claimed
   to return that subclass, but quartz mints subclasses through its own kind-to-class factory,
   which knows nothing about 25910. It is a wrapper over `Event` now.
4. **Subscribe-before-publish is an API-shape problem, not a discipline problem.** `CvmTransport`
   exposes `request()` with no public publish/subscribe pair, and `InMemoryRelayPool` drops an
   event nobody is subscribed to so the property is actually tested rather than assumed.

Remaining gaps in this stage: Tier B (live integration against
`ghcr.io/cordn-msg/cordn:latest`), Tier D (cross-implementation vectors) and Tier E (a real
wallet for CEP-8) are all unstarted — see §6.4. `testAndroidHostTest` has not been run in a
container yet; Maven Central rate-limits the Android secp256k1 artifact.

Original scope notes follow.


New Gradle module, peer of `:quic`. Depends on `:quartz` only; no Android framework deps. **Scope,
CEP inventory, the fourteen subtle rules and the ordered build list are §6** — this stage is that
section turned into code, so read it before starting rather than working from this summary.

Sourcing discipline, restated because it constrains the whole stage: implement from the
specification documents in `ContextVM/contextvm-docs`, **not** from the LGPL SDK (§7). Whoever
takes this should avoid reading the SDK source at all; §6 was written so they do not have to.

Substages, matching §6.6's build list. Each ships when its rule-id group in §6.5 is green at the
tier §6.2 assigns it:

- **2a — wire layer.** Items 1–4: constants and codecs, the minimal MCP client, CEP-4/19 gift wrap
  pinned to `REQUIRED` (§8.6), and the correlation + subscription lifecycle. Ships when a
  `tools/list` round-trips against the reference coordinator in Docker and `CVM-CORE`/`CVM-4`/
  `CVM-19` pass, with CEP-4 vectors exchanged both ways (Tier D).
- **2b — discovery.** Items 5–6: CEP-35 first-message tag learning, CEP-6/17/23 announcement and
  relay resolution. Ships when a coordinator pubkey alone is enough to connect.
- **2c — fixture server.** Item 7, and the gate for everything after it. A Kotlin `:contextvm`
  test double that plays the server role and can be told to violate any rule in §6.5. Without it
  the adversarial half of CEP-22/41 and all of CEP-16 are untestable.
- **2d — transfer profiles.** Items 8–10: CEP-22 receiver, CEP-41 receiver/writer, CEP-22 sender.
  The bulk of the work and the risk; §6.3 items 1–7, 13 and 14 all live here. Ships when
  `msg_sub_many` streams a live backlog, a >64 KiB `msg_fetch_many` reassembles with a verified
  digest, and every ✗ case in `CVM-22`/`CVM-41` fails the way the spec requires.
- **2e — JCS and schemas.** Items 11–12: RFC 8785 in `quartz/…/utils/` against the RFC's own
  vectors plus number-formatting edge cases, then CEP-15 on top of it.
- **2f — payments and the rest.** Items 13–16: CEP-8 both lifecycles with the NIP-47 NWC handler
  (Tier E), CEP-16 injection in the fixture, CEP-24 reviews on existing NIP-22, dual-signer
  plumbing. Lowest priority — cordn needs none of it — but it is what makes the module complete.

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

### Stage 3 — the cordn binding — LANDED (core), app work outstanding

Shipped as `quartz/…/cordn/`, as this plan originally said. Packages follow the spec documents
(`spec00Coordinator`, `spec01GroupMetadata`, `spec02Envelopes`, `spec03Payloads`, `appGroupRef`)
plus `groups/` and `sync/`.

Both ContextVM and cordn spent a while as separate Gradle modules and were folded back in. The
stated reason for `:cordn` — "the coordinator client needs `:contextvm`, and quartz cannot depend
on it without a cycle" — was circular: it only held because `:contextvm` had been put outside
quartz first, on the grounds that it was "a peer of `:quic`". That analogy does not survive
contact: `:quic` is a transport library with no Nostr in it at all, while ContextVM is nothing
but Nostr — kind 25910 events, NIP-59 gift wraps, relay subscriptions. Meanwhile `marmot/`
(18,876 LOC) is a complete non-NIP protocol family living inside quartz, `concord/` is another,
and neither new module had a single platform-specific file. Both now compile for every quartz
target, including iOS and linuxX64, which they never did as jvm+android modules.

| Item | Where |
| ---- | ----- |
| 11-tool coordinator client, identity split in the API | `spec00Coordinator/CoordinatorClient`, `CoordinatorMethod` |
| `cordn1…` group ref | `appGroupRef/CordnGroupRef` |
| `CordnGroupMetadata` (`0xC04D`) | `spec01GroupMetadata/` |
| last-resort `app_data_dictionary` variant | `groups/CordnGroupPolicy.lastResortExtension` |
| cordn-profile KeyPackage | `groups/CordnCredential` + `CordnGroupPolicy` |
| the seal | `spec03Payloads/SealedPayload` |
| the application envelope | `spec02Envelopes/CordnEnvelope` |
| cursors, self-echo, fetch-then-subscribe | `sync/GroupCursor`, `sync/CordnGroupSync` |
| KeyPackage publication + §9 verification | `spec00Coordinator/KeyPackagePublication` |

`CordnGroupPolicy` is what Stage 1 was for: one argument gives an `MlsGroup` cordn's
capabilities, extension registry and payload exporter. It deliberately leaves `authorizeCommit`
at the default — **cordn has no MIP-03**. `spec/01.md` §5.3 makes `admin_pubkeys` presentation
metadata and nothing restricts who may commit, so any member can commit anything MLS permits.
Empty means egalitarian *permanently*, where Marmot reads the same empty set as a bootstrap
window. Same bytes, opposite meaning; no authorization code is shared.

Five findings, all caught by tests or by reading the reference rather than the prose:

1. **§4.1 does not block the binding.** The 32-byte credential check is in Marmot's own
   `KeyPackageUtils`, not the engine, so our binding just implements cordn's encoding
   (`groups/CordnCredential`). The incompatibility between the two ecosystems is unchanged and
   still worth raising.
2. **`spec/01.md` §3 contradicts itself** — "MLS variable-length vector encoding conventions"
   and then `opaque Name<0..2^16-1>`. The reference emits a plain uint16, so that is what
   interoperates. Our test derives the bytes by hand from the spec, because a round trip agrees
   with itself whichever encoding we had picked.
3. **Group refs match the reference byte for byte** on the first run. The three golden strings
   in `packages/core/src/groupRef.test.ts` are cross-checked there against an independent
   TLV+bech32 assembly, so pinning them is a real Tier D vector.
4. **quartz's NIP-19 `Tlv.parse` is too lenient for a group ref.** It stops silently at a
   malformed tuple — right for an `nprofile`, wrong here, where dropping the tail turns a ref
   naming a coordinator into one that reaches for a default. `appGroupRef` parses strictly and
   still ignores unknown types.
5. **ContextVM's fixture could not answer a second call.** It echoed a constant JSON-RPC id,
   which passed every contextvm test because each made exactly one call, and hung the first
   cordn test that made two. The handler now takes the request id, and two contextvm tests pin
   the behaviour: a second call correlates, and a stale id is ignored rather than resolving the
   wrong call.

Verified by mutation: ignoring a pending self-echo, and advancing the cursor only for processed
messages, each kill their guarding tests at both the unit and end-to-end level. The second of
those needed a new end-to-end test — a single catch-up pass looks correct either way, and only a
second pass reveals the stall.

**Cross-implementation verification — added after reading
[Staircase](https://code.relay.tools/opensauce/staircase) (`d9dd1a0`, MIT), an
independent Kotlin cordn client that vendors this project's own MLS engine.**

`quartz/…/cordn/interop/CordnLifecycleInteropTest` walks the whole lifecycle against
fixtures **ts-mls** generated (Staircase's `conformance/fixtures/gen`, vendored
under `quartz/src/commonTest/resources/tsmls/`): read their KeyPackage and agree on
its `kp_ref`, unseal their commit under the published epoch exporter, join from
their Welcome, derive the same epoch exporter byte for byte, apply their
`0xC04D` metadata commit, and read their application message end to end. 15
tests, all green.

Reading Staircase found three things no amount of self-consistent testing would
have:

1. **`authenticated_data` is a wire requirement the cordn spec never mentions.**
   The reference client puts the sender's account pubkey in MLS
   `authenticated_data` and **rejects** any application message that arrives
   with it empty (`packages/cli/src/groupSync.ts:247`). Our engine AEAD-bound
   the field correctly on receive but hardcoded `ByteArray(0)` on send and never
   exposed it — so we would have shipped a client every cordn peer silently
   discarded. `MlsGroup.encrypt` now takes it and `DecryptedMessage` carries it;
   `spec02Envelopes/CordnApplicationMessage` owns the binding. Worth raising
   upstream: it belongs in `spec/02.md` §5.
2. **Our GroupContextExtensions check implemented a rule RFC 9420 does not
   have, and omitted the one it does.** §12.1.7 says nothing about recognising
   extension types; its only validity rule is that the resulting group must not
   require capabilities some member lacks. We rejected any type outside a
   hardcoded list — which would have refused cordn's `0xC04D` metadata commit
   outright — while never checking the real rule, so a commit could install a
   `required_capabilities` a sitting member could not meet and split the group.
   Both fixed; `MlsGroupPolicy.knownExtensionTypes` is gone, because it encoded
   the invented rule.
3. **`Ed25519` could not rebuild a key pair from a known seed.** `keyPairFromSeed`
   is now in the expect/actual set — any interop fixture needs it, and ts-mls
   stores only the seed (inside a PKCS#8 blob).

Staircase also independently confirms Stage 1's design. Their `VENDORED.md`
lists the same decoupling we did — remove `currentMarmotData`/`currentGroupState`/
`currentNostrGroupId`/`agentTextStreamSecret`, drop the `AdminPolicyV1` branch,
inject the admin resolver, do not vendor `MlsGroupManager` or
`MarmotMessageStore` — arrived at independently, as patches against a fork.
**Now that the seam is upstream they could stop forking**, and their remaining
patches are a ready-made list of what a cordn binding still wants from the
engine: caller-chosen `group_id`, explicit leaf lifetimes (cordn uses ~100
years), retained per-epoch receiver data, and skipped-generation keys.

One trap worth recording: `MlsGroup.memberIdentityHex` hex-encodes the
credential bytes, which is right only for a binding that stores a raw key.
cordn's identity is already hex, so it returns 128 characters of hex-of-hex;
`CordnCredential.memberIdentities` is the cordn-side accessor.

**Both directions now verified.** Reading their output was half of it; a client
can parse everything correctly and still emit something nobody accepts, and that
failure keeps our own tests green while every peer silently drops us. So
`KotlinArtifactProducerTest` builds a group under `CordnGroupPolicy`, adds a real
ts-mls KeyPackage, sends an application message and commits a metadata change,
and `quartz/interop/verify-with-ts-mls.sh` hands the result to Staircase's
`verify.ts`. ts-mls joins from our Welcome, reads our metadata, derives the same
epoch-1 and epoch-2 exporters, decrypts our message, checks the AAD sender and
envelope id, and applies our commit — **ten checks, all passing on the first
run**. Confirmed non-vacuous: flipping one nibble of `k-exporter-e1.hex` fails
exactly that check and exits 1.

That gate lives in a script rather than the test suite because it needs a cordn
checkout with `pnpm install` and a staircase checkout. The producer half runs
unconditionally in `:quartz:jvmTest`.

**Run it under Node, not bun.** Staircase's own `run.sh` uses bun, and bun's
WebCrypto has no X25519 DHKEM, so ts-mls there cannot open a Welcome at all —
not even one it generated itself. It surfaces as `DecapError: The algorithm is
not supported` inside HPKE and reads exactly like a wire-format mismatch. Worth
telling them; a one-line change to their runner.

Still open in Stage 3:

- **A ts-mls `ClientState` export.** `verify.ts` carries an optional gate that
  decodes a Kotlin-exported ts-mls state and sends from it. We write no such
  file, so it is skipped. Producing one means re-encoding `MlsGroupState` into
  ts-mls's layout — real work, and only needed for multi-device, an explicit
  non-goal (§4.6).
- **Tier B**, live against `ghcr.io/cordn-msg/cordn:latest`.

### Stage 4 — App integration — LANDED (disclosure UI + headless layer); group UI open

Landed in `commons/…/cordn/` (2026-09-19):

| What | Where |
| ---- | ----- |
| Coordinator identity, relays, provenance | `CoordinatorConfig` (+ `from(CordnGroupRef)`) |
| Per-coordinator health, recorded not polled | `CoordinatorHealth` |
| §8 as a model a UI renders | `CordnExposure` (`GroupExposure`, `ExposureNote`) |
| Group lifecycle keyed on `gid` | `CordnGroupManager` |
| Encrypted-at-rest state + cursors | `CordnGroupStore` (+ an in-memory one for tests) |
| The 11 tools as a contract | `ICoordinator` in `quartz`, implemented by `CoordinatorClient` |
| `amy cordn ref encode/decode`, `amy cordn exposure` | `cli/…/CordnCommands` |
| §8 rendered: the disclosure card, health row, group badge | `commonsUI/…/cordn/ui/` |
| Parse a pasted `cordn1…` and compute its disclosure | `commons/…/cordn/CordnLinkInspection` |
| The screen that shows it, Settings → Cordn group link | `amethyst/…/settings/cordn/CordnLinkScreen` |

`CordnGroupManager` is the cordn counterpart of `MarmotManager` and shares no code with it, as
Stage 1 predicted: Marmot's is keyed on the Nostr group id 147 times and its delivery model —
kinds 443/444/445, `h`-tag subscriptions, publish obligations — has no cordn analogue. What the
two share is the engine underneath. Its store is separate from `MlsGroupStateStore` for the same
reason: the keys look alike (`nostrGroupId` vs `gid`) and mean different things, so one interface
serving both would invite handing the wrong id to the wrong store.

Four findings, each of which was a bug until the test that found it:

1. **`CommitResult.commitBytes` is the bare RFC 9420 `Commit` struct**, not an `MLSMessage`.
   Posting it produces a payload no receiver can parse. `framedCommitBytes` is the wire form.
2. **The pre-commit epoch rule (`spec/03.md` §5) is invisible to a two-party test.** Sealing a
   Commit under the epoch it *creates* still passes create→invite→join→read, because the
   joiner's Welcome cursor skips the only Commit in the stream. It takes a third member — one
   already in the group when a later Commit lands — to catch it. `CommitResult` already hands
   back `preCommitExporterSecret` precisely because the key is unobtainable afterwards.
3. **A Welcome does not carry the `gid`.** `spec/03.md` §2 decouples the delivery id from the MLS
   `group_id`, so in principle a joiner cannot know where to fetch from. The reference client
   closes the gap by convention — `group_id = utf8(gid)` — which staircase's fixtures confirm
   (their `group_id` decodes to exactly their published `gid`). We follow the convention and
   treat it as an observation, not a guarantee: a non-UTF-8 group id yields a named skip rather
   than a mojibake key that fetches nothing forever. `MlsGroup.create` gained an optional
   `groupId` so our groups carry it too.
4. **Public-framed handshake messages interoperate.** Our engine frames Commits as
   `MLSMessage(PublicMessage)`; cordn's client emits private framing. Checked rather than
   assumed: `packages/cli/src/utils/mlsMessages.ts:68` accepts wireformat 1 *and* 2 and hands
   both to ts-mls's `processMessage`. Nothing is weakened — the coordinator sees only the outer
   seal either way — so the manager emits public framing and reads both.

**The §8 disclosure now has a screen** (2026-09-19). The requirement was that exposure be
"surfaced in the UI if we ship this, not buried" — so it sits where it can still change a
decision: on a pasted `cordn1…` link, before joining. The screen reads the link, names the
coordinator and its relays, and renders `GroupExposure`; it deliberately does not join, because
joining needs a live coordinator and Tier B is blocked (§7).

The card states facts and does not rank the two bindings — cordn is weaker against the operator
and stronger against the network (§8.5), and which trade is right depends on who runs the
coordinator. Its notes come from `GroupExposure.notes()` rather than from prose, so a group
linked to four others says so and a lone group does not.

Rendering it found two things compiling could not:

1. **The severity colours were not monotonic.** `tertiary` for the middle level rendered pink
   against a dark `onSurface` for the worst one, so "under a throwaway key" looked more alarming
   than "tied to your real account". Emphasis for the worst case is now weight, not another hue;
   nothing uses `error`, because everything on the card is how cordn works, not a fault.
2. **Note order is part of the disclosure.** Cross-group linkage (§8.2) — the item nobody
   predicts — was below message padding, the least consequential one. `notes()` now returns
   most-surprising-first.

`CordnExposureRenderTest` keeps both honest by rasterising the surface headlessly
(`ImageComposeScene`, software Skia, no display) and looking at the pixels. It catches the two
things a compile cannot: a missing string resource, which throws at render because the generated
`Res.string.*` accessors compile regardless, and a composable that draws nothing. Mutation-checked
— hardcoding the card's background and disabling the §8.2 conditional each kill exactly one test.
The first attempt at the theme test sampled only the page background and let the hardcoded card
through, which is why it now samples inside the card too.

Still open:

- **Group UI.** Chatroom/feed models beside `model/marmotGroups/`, a ViewModel, and the badge
  wired into a real group list — all of which need groups to exist on a device first.
- **Coordinator-driving `amy` verbs** (`publish`, `invite`, `send`, `sync`). Deliberately not
  shipped: with Tier B blocked (§7) there is nothing to exercise them against, and unexercised
  coordinator verbs are a guess with a command-line interface. The logic they would call is in
  `CordnGroupManager` and is covered against an in-memory coordinator.
- Key-package rotation and a published-KeyPackage lifecycle, which cordn has no event kind for
  (§4.2) and which therefore lives entirely in coordinator calls.

The original scope, for reference:

- `commons/` state holders and ViewModels for cordn groups, alongside the Marmot ones.
- Coordinator configuration and health surface (their client tracks per-coordinator health).
- **Surface the metadata model (§8) in the UI.** A cordn group and a Marmot group are not
  equivalent privacy-wise and must not look identical.
- `amy` verbs for scripted interop testing, per the thin-assembly-layer rule.

### Non-goals

- Multi-device (§4.6) — nothing interoperable to build.
- Reusing any `Marmot*`/`Mip*` type for cordn.
- A production Kotlin ContextVM *server*. The Tier C fixture (§6.4) plays the server role for
  tests only. For a real coordinator, `cordn-rs` exists, is faster, and shares the SQLite schema —
  run theirs. The fixture is deliberately not hardened for deployment.
- A full MCP SDK. `:contextvm` implements the client surface the CEPs define plus the fixture's
  server role, not MCP's sampling/roots/elicitation breadth.
- Bridging a Marmot group and a cordn group into one MLS group. Possible in principle once §4.1
  and §4.4 are resolved, but it is a separate design with its own trust questions.

## 10. Open questions

1. ~~**§4.1 credential encoding** — who moves?~~ **Answered 2026-09-18: nobody has to.** We
   implement both. The engine has had no opinion on credential encoding since Stage 1, and the
   two encodings are 32 raw bytes vs 64 ASCII bytes — mutually exclusive by length, so no leaf
   can be misread as the other profile's. `BothCredentialProfilesTest` pins that, including the
   `memberIdentityHex` hex-of-hex trap. The cost is §4.4 (no single group holding both clients),
   which was already a deliberate profile decision.
2. **§4.2 publication payload** — will upstream give KeyPackage publication its own signed payload
   or kind? Changes whether cordn KeyPackages can exist outside a coordinator. Still open, and now
   the highest-value ask, since §4.1 no longer needs one.
2b. **Will upstream license the coordinator?** `packages/coordinator`, `packages/server` and
   `packages/test-utils` carry no LICENSE (§7). Until they do, Tier B cannot be built on them.
   Cheap for upstream to fix and it unblocks the only remaining verification tier that needs
   their code.
3. **Is the goal interop or a second transport?** "Amethyst can talk to cordn users" and "Amethyst
   supports coordinator-backed groups" are different products. The second is strictly less work
   (no §4.1 dependency for group creation among Amethyst users) and strictly less valuable.
4. **Padding.** Nothing in `spec/03.md` pads the sealed payload, so message sizes leak. Worth
   proposing; cheap to add on both sides while the deployed base is small.
5. **Whose coordinator?** The privacy analysis reads very differently for a self-hosted
   per-community coordinator versus the shipped public default.
6. **Are there ContextVM conformance vectors?** None found in `contextvm-docs` or the SDK. Ask
   upstream, and offer ours — a shared CEP-4 wrap and CEP-22/41 framing vector set helps every
   non-TypeScript implementation and is a cheap contribution (Tier D, §6.4).
7. **How stable are CEP-22 and CEP-41?** Both are Draft, both are the largest CEPs, and both are
   mandatory for cordn. A breaking revision mid-implementation is the main schedule risk in
   Stage 2c. Worth asking whether either is close to Final.
8. **Should ContextVM be published separately?** It is a general MCP-over-Nostr client with no
   Amethyst or cordn dependency. If the Nostr ecosystem wants a JVM/KMP ContextVM implementation,
   this is it — but that is a maintenance commitment, and the answer changes how carefully the
   public API needs designing in Stage 2a.
9. **The ContextVM spec repo has no LICENSE.** Implementing a protocol from a published spec is
   normal and fine, but if we want to quote rule text into KDoc or the module README, ask upstream
   to add one (CC-BY or similar) rather than assuming.
10. **Should the Tier C fixture server become a shared conformance harness?** It is the piece the
    ecosystem is missing — a counterparty that can violate any rule on demand. Offering it
    upstream would make it the de facto ContextVM test suite, which is influence worth having but
    also a maintenance commitment beyond our own needs.
11. **Is CEP-8 worth implementing at all, or just not precluding?** It is 722 lines, needs Tier E
    wallet integration, and no coordinator we know of prices anything. The full-list decision says
    build it; if that is really "build it when someone charges", say so now and 2f drops to a
    stub that surfaces `-32042` to the user instead of paying.
