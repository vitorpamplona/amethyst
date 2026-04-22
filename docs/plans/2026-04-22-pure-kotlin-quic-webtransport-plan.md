# Pure-Kotlin QUIC + WebTransport plan

## Context

The Audio Rooms feature (NIP-53 kind 30312) is wire-incompatible with anything but
WebTransport over QUIC, because that's what the nostrnests/nests MoQ relay speaks.
Phases 3a / 3c / 3d of the rollout shipped the entire Kotlin stack from NIP-98 auth
through MoQ framing through Opus playback — every layer except the WebTransport
factory itself, which is a stub that throws `WebTransportException(NotImplemented)`.

We tried the easy path (depend on a Java QUIC library) and exhausted it:

| Library | Outcome |
|---|---|
| `tech.kwik:kwik-core`, `tech.kwik:kwik`, `tech.kwik:flupke` | Maven Central returns `Could not find` for every coord/version we tried. |
| `io.netty.incubator:netty-incubator-codec-http3` | Resolves, **but** it depends on `netty-incubator-codec-native-quic` (JNI to Cloudflare quiche) which has no Android-compatible native classifier published. Compile-time green, runtime `UnsatisfiedLinkError`. |
| Cronet | QUIC support is internal; WebTransport not exposed in any stable public API. |
| WebView JavaScript bridge | Rejected by product. |

So the path forward is a **pure-Kotlin QUIC client** with just enough WebTransport
on top to interop with nests. This plan scopes that work honestly.

## Scope

### What we build in pure Kotlin

- QUIC v1 client per RFC 9000 (no server, no version negotiation beyond v1).
- QUIC-TLS binding per RFC 9001 (CRYPTO-frame splicing, per-level keys, header
  protection).
- QUIC loss recovery + congestion control per RFC 9002 (NewReno minimum).
- QUIC datagram extension per RFC 9221.
- HTTP/3 client subset per RFC 9114 (control stream, SETTINGS, request streams).
- QPACK encoder per RFC 9204 (literal-only emit; full decoder).
- Extended CONNECT (RFC 8441 / WT-H3 draft-13) for `:protocol = webtransport`.
- WebTransport over HTTP/3 framing: stream-type prefix bytes, capsule protocol
  for `WT_SESSION_CLOSE`.
- HTTP Datagrams per RFC 9297 carrying WT datagrams.

### What we delegate

- **TLS 1.3 AEAD ciphers** → Quartz's existing `AESGCM` (common, with AAD
  support) + `ChaCha20Poly1305` (common, pure-Kotlin).
- **HKDF primitives** → Quartz's existing `Hkdf` class + its `MacInstance`
  (HMAC-SHA256). We add one thin helper — HKDF-Expand-Label from RFC 8446 §7.1
  — on top, plus a general `hkdfExpand(prk, info, length)` since Quartz only
  ships NIP-44's specialised `fastExpand`.
- **Key agreement** → Quartz's existing `X25519` (common expect/actual).
- **SHA-256 hashing** → Quartz's existing `Sha256` (common expect/actual).
- **Cert chain signature verification** → Quartz's `Ed25519` where the leaf
  uses Ed25519; JDK `Signature.getInstance("SHA256withRSA")` /
  `"SHA256withECDSA"` for the common cases (nests typically uses Let's Encrypt
  ECDSA certs).
- **X.509 parsing + chain validation** → JDK `CertificateFactory` +
  `TrustManagerFactory.getInstance(...).init(null as KeyStore?)` (Android
  system trust store).
- **AES-ECB for QUIC header protection** (one block, 5-byte sample) → JDK
  `Cipher.getInstance("AES/ECB/NoPadding")` — trivial, no dep.
- **SecureRandom** → Quartz's `RandomInstance` / `SecureRandom`.

**No BouncyCastle dependency.** The QUIC-TLS cryptographic surface is already
fully covered by Quartz's primitives — nip44Encryption/crypto (AEAD + HKDF +
ChaCha20 + Poly1305), marmot/mls/crypto (X25519 + Ed25519 + Curve25519Field),
utils/ciphers/AESGCM, utils/mac, utils/sha256, utils/SecureRandom. We write
the TLS 1.3 client state machine ourselves using those primitives.

### Explicitly out of scope

- QUIC server role.
- 0-RTT / session tickets (defer until needed).
- Connection migration / preferred address.
- Multiple concurrent paths.
- Path MTU discovery (assume 1200-byte safe ceiling).
- HTTP/3 server push.
- QPACK dynamic table on the encoder side (use literal-only headers; still decode
  dynamic table from the peer).
- ECN-based congestion signalling.
- Anti-amplification limits (we're a client; rarely matters).

## Architecture

```
nestsClient/src/jvmAndroid/kotlin/com/vitorpamplona/nestsclient/
└── transport/
    ├── quic/                   ← new: pure-Kotlin QUIC client
    │   ├── crypto/             ← BC adapter (TLS over CRYPTO frames)
    │   ├── packet/             ← long/short-header encode/decode + protection
    │   ├── frame/              ← STREAM, ACK, CRYPTO, MAX_*, CONNECTION_CLOSE …
    │   ├── stream/             ← per-stream send/recv buffers, flow control
    │   ├── recovery/           ← loss detection, PTO, NewReno
    │   ├── connection/         ← QuicConnection state machine
    │   └── transport/          ← UDP socket + datagram pump
    ├── http3/                  ← new: HTTP/3 client
    │   ├── frame/              ← DATA, HEADERS, SETTINGS, GOAWAY, MAX_PUSH_ID
    │   ├── qpack/              ← static + dynamic tables, Huffman
    │   └── Http3Connection.kt  ← request/response state machine
    └── webtransport/           ← new: WT layer
        ├── ExtendedConnect.kt  ← :protocol = webtransport
        ├── WtStreamType.kt     ← 0x41 (bidi) / 0x54 (uni) prefix bytes
        ├── WtCapsule.kt        ← WT_SESSION_CLOSE = 0x2843, etc.
        └── KotlinWebTransportFactory.kt  ← implements existing WebTransportFactory
```

Naming note: the existing `KwikWebTransportFactory.kt` stub becomes
`KotlinWebTransportFactory.kt` once real. The current ViewModel wiring
(`AudioRoomConnectionViewModel`) imports the class directly, so the rename is
one change with no protocol impact.

## Phase breakdown (one developer, full-time estimates)

Each phase ends with green commonTest tests that exercise its layer in isolation,
plus an integration test against the next layer up.

### Phase A — Foundations (1 week)
- UDP socket abstraction (`DatagramChannel`, suspend wrapper, single-receive loop).
- Connection ID generation (cryptographically random, 8-byte source IDs).
- Packet number space tracking (Initial, Handshake, Application).
- Largest-acked / next-pn tracking per space.
- **Tests:** varint already done; add CID + packet-number-space unit tests.

### Phase B — TLS 1.3 client state machine (3 weeks)

Built entirely on Quartz primitives; no BouncyCastle.

- `HkdfExpandLabel` helper per RFC 8446 §7.1 on top of Quartz's `Hkdf.extract`
  + a new `hkdfExpand(prk, info, length)` written against `MacInstance`.
- `TlsKeySchedule` — derive Early / Handshake / Master secrets + per-direction
  traffic secrets per RFC 8446 §7.1.
- `TlsTranscriptHash` — running SHA-256 over the handshake messages using
  Quartz's `Sha256`.
- `TlsCipherSuite` — the three QUIC-mandatory suites: TLS_AES_128_GCM_SHA256
  (uses Quartz `AESGCM`), TLS_CHACHA20_POLY1305_SHA256 (uses Quartz
  `ChaCha20Poly1305`), and optionally TLS_AES_256_GCM_SHA384 (SHA-384 is the
  only primitive not yet in Quartz; skip this suite for v1).
- `TlsKeyExchange` — X25519 via Quartz `X25519.generateKeyPair()` +
  `X25519.dh()`.
- `TlsClientHello` / `TlsServerHello` / `TlsEncryptedExtensions` /
  `TlsCertificate` / `TlsCertificateVerify` / `TlsFinished` encode/decode.
- QUIC transport parameters TLS extension (codec for the opaque blob carrying
  `initial_max_data`, `max_idle_timeout`, `max_datagram_frame_size`, etc.).
- Certificate chain validation delegated to JDK `TrustManagerFactory`.
- `TlsClient` state machine: drive handshake by consuming CRYPTO-frame payload
  bytes and producing outbound CRYPTO-frame payload bytes; expose per-
  encryption-level secrets as they're derived so the QUIC layer can install
  keys.
- **Tests:** RFC 8448 §3 (Simple 1-RTT Handshake) test vectors — bit-for-bit
  reproduction of the Client's generated bytes given fixed randomness. In-
  process handshake against a minimal test TLS server built with the same
  primitives.

### Phase C — Initial + Handshake packets (2 weeks)
- Long-header packet codec (Type, Version, DCID, SCID, Token, Length, PN).
- Header protection (AES-ECB sample mask for AES suites, ChaCha20 for CC20).
- Payload protection via JCA AEAD; nonce = packet-number XOR static IV.
- Initial-secret derivation with the v1 salt (RFC 9001 §5.2).
- CRYPTO frame encode/decode with offset reassembly.
- **Tests:** decode the `Initial` packet from RFC 9001 Appendix A.2 (Cloudflare's
  test vectors). Bit-for-bit match required.

### Phase D — 1-RTT + STREAM frames (1 week)
- Short-header packet codec.
- STREAM frame encode/decode with OFF/LEN/FIN bits.
- Per-stream receive buffer with out-of-order chunk reassembly.
- Per-stream send buffer with retransmit hold (until ACKed).
- **Tests:** stream offset reassembly against a property-based fuzz that injects
  random reordering / duplication.

### Phase E — ACK + flow control (1 week)
- ACK frame encoding (ranges from packet-number tracking).
- Connection-level + stream-level send credits.
- MAX_DATA, MAX_STREAM_DATA, MAX_STREAMS handling.
- DATA_BLOCKED / STREAM_DATA_BLOCKED emission when blocked.
- **Tests:** unit tests for credit accounting + ACK range encoding edge cases.

### Phase F — Loss recovery + congestion control (1 week)
- PTO timer per RFC 9002 §6.
- RACK-based loss detection (packet-threshold + time-threshold).
- NewReno congestion controller (slow start, congestion avoidance, recovery).
- Pacing not strictly required for v1; optional time-spaced send.
- **Tests:** simulate packet loss at controlled rates; assert retransmissions
  arrive within PTO + cwnd doesn't oscillate pathologically.

### Phase G — Datagram extension (2 days)
- DATAGRAM frame (frame types 0x30 / 0x31).
- Negotiate `max_datagram_frame_size` transport parameter.
- **Tests:** datagram round-trip against in-process test server.

### Phase H — Connection lifecycle (1 week)
- Transport parameters codec (~30 parameters; only ~10 we send).
- CONNECTION_CLOSE handling (transport vs. application-level).
- Idle timeout (default 30 s, both sides MIN).
- Draining + closing states per RFC 9000 §10.2.
- **Tests:** assert clean close round-trip; assert idle timeout fires.

**End of Phase H = working QUIC client.** ~9 weeks elapsed.

### Phase I — HTTP/3 (2 weeks)
- Unidirectional control stream (peer-direction opens with stream-type 0x00).
- SETTINGS frame (we MUST emit `SETTINGS_ENABLE_CONNECT_PROTOCOL = 1`,
  `SETTINGS_ENABLE_WEBTRANSPORT = 1`, `SETTINGS_H3_DATAGRAM = 1`).
- HEADERS frame (carrying QPACK output).
- DATA frame.
- GOAWAY handling.
- **Tests:** SETTINGS round-trip; HEADERS+DATA request/response.

### Phase J — QPACK (2 weeks)
- Static table (RFC 9204 Appendix A).
- Encoder: literal-with-name-reference + literal-without-name-reference only.
  No dynamic table inserts on our side. Simplifies massively; nests will accept
  literals.
- Decoder: full dynamic table support so we can read what nests sends.
- Huffman codec (RFC 9204 Appendix B).
- Encoder + decoder streams (the two QPACK uni streams).
- **Tests:** decode known-good QPACK headers from the IETF interop corpus.

### Phase K — Extended CONNECT + WebTransport (1 week)
- HEADERS request with `:method=CONNECT`, `:protocol=webtransport`,
  `:scheme=https`, `:authority=...`, `:path=...`.
- Response handling (2xx → session open, 4xx/5xx → ConnectRejected).
- Stream-type prefix bytes (0x41 client-bidi, 0x54 client-uni).
- Capsule protocol for graceful close (capsule type 0x2843 = WT_CLOSE_SESSION).
- HTTP Datagram (RFC 9297) wrapping WT datagrams with quarter-stream-id prefix.
- Wire into existing `WebTransportSession` interface.
- **Tests:** unit tests for capsule + datagram framing; integration test using
  the in-process H3 server from Phase J.

### Phase L — Interop + hardening (2 weeks)
- Run against `nests-rs` locally. Fix every protocol mismatch.
- Run against `nostrnests.com` once local works.
- Fuzz testing: malformed packet acceptance, off-by-one frame lengths, etc.
- Performance pass: confirm < 10 ms median round-trip on local testing.

**End of Phase L = audible audio against real nests.** ~16 weeks elapsed.

## Validation strategy

1. **Unit tests per phase.** Already established pattern in nestsClient.
2. **In-process integration**, Phase B onward: spin up a BC-based QUIC server
   in the same JVM and let client + server drive each other. No real network.
3. **`quic-interop-runner` corpus** (Phase L): the IETF QUIC WG publishes
   reference test vectors for client behavior across every implementation. We
   can replay their packet captures and assert correct decoding.
4. **Local nests-rs** (Phase L): run nests' Rust reference server in Docker;
   point our client at it. Verifies real Extended CONNECT + MoQ on top.
5. **Production `nostrnests.com`** (Phase L): final confidence step.

## Risks + stop conditions

| Risk | Mitigation | Stop condition |
|---|---|---|
| Quartz's `Hkdf` only ships the NIP-44-specialised `fastExpand`, not a general-purpose expand | Write generic `hkdfExpand(prk, info, length)` in Phase B on top of `MacInstance`; upstream the helper back to Quartz `utils/` | Trivial — add one file |
| Quartz's `X25519` doesn't accept arbitrary-length secrets or has subtle incompatibilities with TLS 1.3's derivation | Verify in Phase B day-1 with a micro-test: `X25519.dh(fixed_priv, fixed_pub)` against RFC 7748 test vector | If mismatched, pin a specific Quartz version or fork the primitive |
| Header protection edge cases mis-encode 4-byte packet numbers | RFC 9001 has explicit test vectors | None — must work |
| QPACK dynamic table from nests is more aggressive than literal-only | Decoder supports full dynamic table; only encoder is literal-only | If nests rejects literal-only encoded headers, add minimal encoder dynamic table (~+1 week) |
| Loss recovery oscillates badly under real RTT variance | NewReno is conservative enough | If empirically bad, swap to BBRv1 implementation reference (~+2 weeks) |
| Server certs use RSA + SHA-256 / ECDSA P-256 / Ed25519 — we need all three sig verifiers | JDK `Signature.getInstance("SHA256withRSA")` + `Signature.getInstance("SHA256withECDSA")` cover RSA + ECDSA; Quartz `Ed25519` covers Ed25519 | None — all three are cheap |
| WebTransport draft mismatch with nests | Pin to whatever draft nests serves; advertise both legacy + current setting IDs | Hard fail back to documenting the version skew |

**Hard abandonment trigger:** if at end of Phase D (~6 weeks in) we cannot pass
the RFC 9001 Appendix A test vectors bit-for-bit, the implementation has a deep
bug we can't shake without dedicated cryptographic review. At that point the
honest call is to wait for an Android-compatible Java QUIC library to ship
elsewhere.

## Timeline summary

| Phase | Weeks | Cumulative |
|---|---|---|
| A. Foundations | 1 | 1 |
| B. TLS 1.3 client on Quartz primitives | 3 | 4 |
| C. Initial + Handshake packets | 2 | 6 |
| D. 1-RTT + STREAM | 1 | 7 |
| E. ACK + flow control | 1 | 8 |
| F. Loss + congestion | 1 | 9 |
| G. Datagrams | ½ | 9.5 |
| H. Connection lifecycle | 1 | 10.5 |
| I. HTTP/3 | 2 | 12.5 |
| J. QPACK | 2 | 14.5 |
| K. Extended CONNECT + WT | 1 | 15.5 |
| L. Interop + hardening | 2 | 17.5 |

**17-19 weeks of full-time work for one developer**, or 5-6 months at a
normal review-and-iteration cadence. TLS 1.3 is +1 week vs. the original
BC-adapter estimate because we write the state machine ourselves, but the
total is within the same band and we've eliminated the external-library
integration risk entirely. Add a security review of the QUIC + TLS + new
HKDF-Expand helper before shipping to users — minimum 2 weeks calendar
time, possibly external.

## Dependencies to add

**None.** Every cryptographic primitive lives in Quartz already:

| Primitive | Quartz location |
|---|---|
| AES-128-GCM with AAD | `utils/ciphers/AESGCM.kt` (commonMain expect, jvmAndroid actual via JCA) |
| ChaCha20-Poly1305 | `nip44Encryption/crypto/ChaCha20Poly1305.kt` (commonMain, pure Kotlin) |
| HKDF-Extract + MAC | `nip44Encryption/crypto/Hkdf.kt` + `utils/mac/MacInstance.kt` |
| SHA-256 | `utils/sha256/Sha256.kt` |
| X25519 ECDH | `marmot/mls/crypto/X25519.kt` (commonMain expect) |
| Ed25519 signatures | `marmot/mls/crypto/Ed25519.kt` |
| SecureRandom | `utils/SecureRandom.kt` + `utils/RandomInstance.kt` |

Nothing goes into `gradle/libs.versions.toml`. AES-ECB for header protection
is one-block and uses JDK `javax.crypto.Cipher` directly (no provider swap).

One small helper lives in the new `quic/crypto/` package:
`HkdfExpandLabel` (RFC 8446 §7.1) built on `MacInstance`. If it's clean, we
upstream it to Quartz's `Hkdf` as a general-purpose `expand(prk, info,
length)`.

## What ships first

Phases A+B+C in a single PR demonstrating TLS over QUIC handshake completion
against an in-process test server, bit-matching RFC 9001 vectors. That's the
proof-of-concept gate. If it works, the rest is execution. If it doesn't, we
abandon and revisit.
