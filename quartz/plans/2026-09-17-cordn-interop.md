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
- `ContextVM/sdk` @ `b5d1e4e` (2026-09-17), version `0.13.17` — **LGPL-3.0**, see §6

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
| Sender privacy intent | fresh ephemeral key per kind-445 | ephemeral ContextVM identity on the message path | same idea, different grain (§7.2) |

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

## 6. Licensing

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

## 7. What the coordinator can see

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

### 7.1 Admission is in the clear on both ends

`join_request_store` carries the **stable** identity and the `gid` → "npub X wants into group G".
`welcome_store` names the **target's stable pubkey**; `welcome_take` is called by that target's
**stable** identity. For any group joined via a share link the coordinator observes real-identity
membership directly. Structural, not a bug.

### 7.2 The ephemeral identity is per-session, not per-message

`coordinatorClient.ts:169` → `new PrivateKeySigner()` with no argument, constructed once per
`cordnClient`, and `chatRuntime.ts:62` caches one client per coordinator pubkey. So **one
pseudonym posts, fetches and subscribes across every group you have on that coordinator, for the
whole session.** That `gid` set is a stable fingerprint: it links all your groups together and
leaks how many you are in. Marmot rotates per kind-445 event and has no equivalent linkage
(relays still see `h` tags and fetch patterns — different exposure, not obviously better).

### 7.3 Complete ordered history in one place

Per-group cursors, `at` timestamps, sealed payload sizes (no padding specified anywhere I could
find), per-`gid` rates, live subscription membership. Cross-`gid` timing correlation inside one
session is trivial. Unlike relays there is no redundancy or partitioning: one operator sees the
whole graph of every group it serves, retained in SQLite.

### 7.4 A signed, verifiable record that you use cordn

`kp_publish` rides the stable identity and the coordinator retains the signed event — it is
*designed* to be re-servable as proof, rotation cadence included. `kp_take`/`kp_list` also reveal
who is being looked up, i.e. "someone is about to add X to a group".

### 7.5 IPs go to relays, not the coordinator

A real structural win over an HTTP delivery service. The relay then sees the 21059 traffic pattern
and the `p` tag naming the coordinator: who talks to which coordinator, how often, how big.

### 7.6 Encryption can silently downgrade — pin it

cordn-web pins `giftWrapMode: GiftWrapMode.EPHEMERAL` (`coordinatorClient.ts:186`) but leaves
`encryptionMode` at the SDK default **`OPTIONAL`**
(`contextvm-sdk/src/transport/base-nostr-transport.ts:102`), which resolves as
`isEncrypted ?? true` from negotiated session state (`:544`). The reference coordinator does
announce `support_encryption`, so live deployments are encrypted — but the policy permits a
coordinator that does not announce it to receive **plaintext JSON-RPC on public relays**,
exposing `gid`, `target_pk`, `kp_64` and cursors to any relay.

**Our client must pin `REQUIRED` and fail closed.** Non-negotiable.

### 7.7 Net

Content privacy equals Marmot's. Metadata privacy is **weaker than Marmot's against the delivery
operator** and **stronger against the network**. Whether that trade is acceptable depends on
whether coordinators are self-hosted per community or a handful of public ones; the spec permits
either, and the shipped default is one coordinator pubkey over three public relays
(`cordn/README.md`).

This section should be surfaced in the UI if we ship this, not buried. A Marmot group and a cordn
group have materially different metadata exposure and users cannot infer that from either one
looking like a group chat.

## 8. Plan

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

New Gradle module, peer of `:quic`. Depends on `:quartz` only; no Android framework deps. Written
from `docs.contextvm.org` + the CEPs, **not** from the LGPL SDK (§6).

- kind 25910 request/response with `p` addressing and `e` correlation
- NIP-44 gift wrap to 1059/21059 — note their scheme is a *simplified* NIP-59 (direct NIP-44 to a
  wrapper event, no inner seal/rumor), and the server verifies the decrypted inner event's
  signature
- `encryptionMode = REQUIRED`, fail closed (§7.6)
- server discovery: addressable 11316–11320 + NIP-65 10002, with the `support_encryption`,
  `support_encryption_ephemeral`, `support_oversized_transfer`, `support_open_stream` tags
- CEP-22 oversized payload and CEP-41 open streams over `notifications/progress`
- a minimal MCP client: `initialize` → response → `notifications/initialized`, then `tools/call`
- dual-signer support: an account `NostrSigner` for the stable identity and a locally generated
  key for the ephemeral one

Sizing reference: the SDK's client-side surface (`src/core` + `src/transport/nostr-client` +
oversized-transfer + open-stream, tests excluded) is ~5.4k lines of TypeScript. Reusable beyond
cordn — any MCP-over-Nostr work lands here.

Signer note: the stable transport needs `sign()` *and* NIP-44 encrypt/decrypt per MCP message. On
a NIP-55/NIP-46 external signer that is a round-trip per request. cordn-web's stable/ephemeral
split already keeps stable traffic rare; preserve that property rather than fighting it.

### Stage 3 — `quartz/…/cordn/` binding

- The 11-tool coordinator client over `:contextvm`, with the stable/ephemeral split of §7 encoded
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
- **Surface the metadata model (§7) in the UI.** A cordn group and a Marmot group are not
  equivalent privacy-wise and must not look identical.
- `amy` verbs for scripted interop testing, per the thin-assembly-layer rule.

### Non-goals

- Multi-device (§4.6) — nothing interoperable to build.
- Reusing any `Marmot*`/`Mip*` type for cordn.
- A Kotlin ContextVM *server* — `cordn-rs` exists, is faster, and shares the SQLite schema. If we
  ever want a coordinator, run theirs.
- Bridging a Marmot group and a cordn group into one MLS group. Possible in principle once §4.1
  and §4.4 are resolved, but it is a separate design with its own trust questions.

## 9. Open questions

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
