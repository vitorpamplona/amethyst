# :contextvm

A Kotlin Multiplatform client for **ContextVM** — the Model Context Protocol
(MCP) carried over Nostr.

This is a general MCP-over-Nostr implementation, not a client for any one
server. It exists because Amethyst needs to talk to
[cordn](https://cordn.net) coordinators (see
`quartz/plans/2026-09-17-cordn-interop.md`), but nothing in it is
cordn-specific.

## Implemented specification revision

Built from the specification documents in
[`ContextVM/contextvm-docs`](https://github.com/ContextVM/contextvm-docs) at
commit **`e63bce6`**, read on 2026-09-17.

Most of these are **Draft**, including both transfer profiles, so the text can
still move. When bumping the revision, diff against the `CVM-*` rule ids in the
plan's §6.5 catalog — the test names carry them, so a spec change shows up as
named failures rather than as silent divergence.

| Spec | Status | Implemented in |
| ---- | ------ | -------------- |
| Core draft spec | Draft | `core/`, `jsonrpc/`, `transport/` |
| CEP-4 Encryption | Final | `crypto/CvmGiftWrap` |
| CEP-6 Public Announcements | Final | `discovery/ServerAnnouncement` |
| CEP-8 Pricing and Payment | Draft | `payment/` |
| CEP-15 Common Tool Schemas | Draft | `schema/CommonToolSchema` |
| CEP-16 Client Pubkey Injection | Final | `fixture/` (server role) |
| CEP-17 Relay List Metadata | Draft | `discovery/ServerRelay` |
| CEP-19 Ephemeral Gift Wraps | Draft | `crypto/CvmGiftWrap` |
| CEP-21 PMI Recommendations | Draft | `payment/Pmi` |
| CEP-22 Oversized Transfer | Draft | `transfer/oversized/` |
| CEP-23 Server Profile Metadata | Draft | `discovery/` (kind 0 via quartz) |
| CEP-24 Server Reviews | Draft | `discovery/ServerReview` |
| CEP-35 Stateless Discovery | Draft | `discovery/SessionDiscovery` |
| CEP-41 Open Streams | Draft | `transfer/stream/` |

RFC 8785 (JCS), required by CEP-8 and CEP-15, lives in
`quartz/…/utils/jcs/JsonCanonicalization.kt` — it is a generic primitive, not a
ContextVM concern.

## Licensing

Implemented **clean-room from the specification**. The reference
[`ContextVM/sdk`](https://github.com/ContextVM/sdk) is **LGPL-3.0**; Amethyst
ships under MIT, so translating that source would carry copyleft terms into
Quartz. Do not read it while working here — the plan's §6 was written so you do
not have to.

The spec repository carries no LICENSE file. Implementing a published protocol
is fine; do not paste spec prose into this repo.

## Three things that are easy to get wrong

1. **Kind 25910 is ephemeral, so relays do not store it.** A subscription
   opened after the peer published has missed the response permanently. This is
   why `CvmTransport` exposes `request()` and no public publish/subscribe pair:
   the ordering is the transport's job, not the caller's. `InMemoryRelayPool`
   drops an event nobody is listening for, so the property is tested rather
   than assumed.

2. **CEP-41 has two independent ordering fields.** `progress` orders every
   frame, control frames included, and is explicitly *not* a chunk counter;
   `chunkIndex` (contiguous from 0) is what validates completeness. Progress
   sequences are also per-sender, so a `pong`'s progress bears no relation to
   the `ping` it answers — they match by nonce alone.

3. **`close` does not complete the request.** After a CEP-41 stream closes, the
   originating JSON-RPC request still needs its own response, and a client must
   never synthesize success from `close`. `ToolCallResult` carries `streamed`
   fragments and the `result` separately for exactly this reason.

## Testing

```bash
./gradlew :contextvm:jvmTest              # 172 tests
./gradlew :contextvm:testAndroidHostTest  # 143 tests
```

The Android run is smaller because the tests needing real secp256k1 and NIP-44
— the gift wrap round trip, the transport and the MCP client — live in
`src/jvmTest` where the JVM JNI artifact is on the classpath. Everything
protocol-level is in `commonTest` and runs on both.

The suite is **Tier A and Tier C** from the plan's §6.4: rule-derived unit
tests, plus adversarial tests against `fixture/CvmFixtureServer`, which plays
the server role and can be told to violate any rule on demand (`FixtureFaults`).
Most tests are negative, because the CEPs are written as failure conditions.

Still open:

- **Tier B** — live integration against `ghcr.io/cordn-msg/cordn:latest`.
- **Tier D** — cross-implementation vector exchange for CEP-4 wraps and
  CEP-22/41 framing. Worth offering upstream; no official vectors exist.
- **Tier E** — a real Lightning wallet behind CEP-8 (NIP-47 NWC is already in
  Quartz).

## Not in scope

- A production server. `fixture/` plays the server role for tests only and is
  deliberately not hardened; for a real coordinator, `cordn-rs` exists.
- Full MCP. Lifecycle, tool listing and tool calling are implemented because
  that is what the CEPs define; sampling, roots and elicitation are not.
