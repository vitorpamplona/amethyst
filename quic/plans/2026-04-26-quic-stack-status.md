# QUIC + WebTransport stack — current state (2026-04-26)

This document is the post-implementation snapshot of `:quic`. It supersedes
the original [pure-kotlin QUIC + WebTransport plan](../../docs/plans/2026-04-22-pure-kotlin-quic-webtransport-plan.md)
which was written before any code shipped and is now historical.

## TL;DR

`:quic` is a self-contained Kotlin-Multiplatform module that speaks QUIC v1
+ HTTP/3 + WebTransport against real-world servers (aioquic + picoquic
verified; nestsClient/MoQ on top). It uses Quartz crypto primitives only —
no BouncyCastle, no JNI. ~8.5k lines of production code, ~5k lines of
tests, 39 test files, five rounds of parallel audit + fix passes.

## What shipped vs the original plan

| Phase | Original estimate | Actual | Notes |
|---|---|---|---|
| A. Foundations | 1 wk | done | KMP module, UdpSocket on `jvmAndroid`, Varint migrated from nestsClient |
| B. TLS 1.3 | 3 wk | done | RFC 8446 client state machine, X25519 ECDHE, RFC 8448 §3 vectors pass bit-for-bit |
| C. Initial + Handshake packets | 2 wk | done | RFC 9001 §A.2/§A.3 vectors pass; ChaCha20 per §A.5 |
| D. 1-RTT + STREAM | 1 wk | done | Stream offset reassembly, FIN, fuzzed |
| E. ACK + flow control | 1 wk | done | MAX_DATA / MAX_STREAM_DATA / MAX_STREAMS routing + writer enforcement |
| F. Loss recovery + congestion control | 1 wk | done (no CC) | RFC 9002 §5/§6 RTT estimator + packet/time-threshold loss detection + PTO + per-frame retransmit shipped 2026-05-05; congestion control still TBD |
| G. Datagram extension | ½ wk | done | RFC 9221 frames + bounded incoming queue |
| H. Connection lifecycle | 1 wk | done | CONNECTION_CLOSE, idle timeout, draining/closing, idempotent driver close |
| I. HTTP/3 | 2 wk | done | Control stream + SETTINGS + GOAWAY (with id-regression check) + duplicate-id rejection |
| J. QPACK | 2 wk | done | Static-table + Huffman + integer codec (RFC 7541 §B + RFC 9204 §B.1 vectors) |
| K. Extended CONNECT + WT | 1 wk | done | Stream-type prefixes, HTTP Datagram + WT_CLOSE_SESSION capsule |
| L. Interop + hardening | 2 wk | done + much more | Live interop against aioquic Docker; **5 rounds of audit + fix** beyond the plan |

The original plan estimated 17–19 weeks. We ran the full sequence plus five
unscheduled audit rounds. Every audit found real bugs; the suite is what
caught them on regression.

## What's actually in the module

```
quic/
├── plans/                                ← module-local design docs
└── src/
    ├── commonMain/kotlin/com/vitorpamplona/quic/
    │   ├── Buffer.kt                     ← QuicReader / QuicWriter
    │   ├── Varint.kt                     ← RFC 9000 §16
    │   ├── connection/                   ← QuicConnection orchestrator + Driver
    │   │   ├── QuicConnection.kt              (≈ 600 lines, the hub)
    │   │   ├── QuicConnectionDriver.kt        (read/send loops + close)
    │   │   ├── QuicConnectionParser.kt        (feedDatagram + dispatchFrames)
    │   │   ├── QuicConnectionWriter.kt        (drainOutbound + flow-control updates)
    │   │   ├── PacketProtection.kt + builder
    │   │   ├── PacketNumberSpace.kt
    │   │   ├── ConnectionId.kt + TransportParameters.kt
    │   │   └── EncryptionLevel.kt + LevelState.kt
    │   ├── crypto/                       ← Aead, header protection, HKDF helpers, AesEcbHeaderProtection,
    │   │                                   ChaCha20HeaderProtection, ChaCha20Poly1305Aead, InitialSecrets,
    │   │                                   PlatformAesOneBlock, PlatformChaCha20Block (expect),
    │   │                                   bestAes128GcmAead (expect)
    │   ├── frame/                        ← Frame.kt sealed hierarchy + FrameFuzzerTest target
    │   │                                   includes RESET_STREAM / STOP_SENDING / NEW_TOKEN
    │   ├── http3/                        ← Http3FrameReader + Http3Settings + frame types
    │   ├── packet/                       ← LongHeaderPacket, ShortHeaderPacket, RetryPacket, peekHeader
    │   ├── qpack/                        ← QpackDecoder, QpackEncoder, QpackHuffman, QpackInteger,
    │   │                                   QpackStaticTable
    │   ├── connection/recovery/         ← RecoveryToken + SentPacket + QuicLossDetection
    │   │                                   (RFC 9002 §5/§6 RTT estimator + loss detection +
    │   │                                   PTO; per-frame retransmit dispatch)
    │   ├── recovery/                     ← AckTracker (with ack-eliciting gating)
    │   ├── stream/                       ← QuicStream, ReceiveBuffer (with FIN-fully-read), SendBuffer, StreamId
    │   ├── tls/                          ← TlsClient state machine + ClientHello/ServerHello/EE/Cert/CV/Finished
    │   │                                   codecs, TlsKeySchedule, TlsTranscriptHash (incremental), TlsConstants,
    │   │                                   PermissiveCertificateValidator, TlsRunningSha256 (expect)
    │   ├── transport/                    ← UdpSocket (expect)
    │   └── webtransport/                 ← QuicWebTransportFactory + QuicWebTransportSessionState +
    │                                       WtPeerStreamDemux + WtCapsule + WtDatagram + ExtendedConnect
    └── jvmAndroid/kotlin/com/vitorpamplona/quic/
        ├── crypto/JcaAesGcmAead.kt       ← cached JCA Cipher per direction with IV-reuse fallback
        ├── crypto/PlatformCrypto.kt      ← actuals
        ├── tls/JdkCertificateValidator.kt ← system-trust-store chain validation + RSA-PSS / ECDSA / Ed25519
        ├── tls/TlsRunningSha256.kt       ← MessageDigest.clone()-based incremental hash
        └── transport/UdpSocket.kt        ← DatagramChannel + suspend wrapper
```

## Crypto surface

Quartz primitives only:

| Primitive | Source |
|---|---|
| AES-128-GCM | `JcaAesGcmAead` (jvmAndroid) — cached `Cipher` per direction; `Aes128Gcm` singleton (commonMain) for non-hot paths |
| ChaCha20-Poly1305 | Quartz `ChaCha20Poly1305` (commonMain pure-Kotlin) wrapped in `ChaCha20Poly1305Aead` |
| HKDF-Extract / Expand-Label | Quartz `Hkdf` + `MacInstance`; thin RFC 8446 §7.1 helper in `crypto/HkdfHelpers.kt` |
| SHA-256 (one-shot) | Quartz `sha256(...)` |
| SHA-256 (incremental, for transcript) | `TlsRunningSha256` (expect/actual; jvmAndroid wraps `MessageDigest.clone()`) |
| X25519 ECDHE | Quartz `X25519` |
| Ed25519 | Quartz `Ed25519` (only inside the JVM cert validator path) |
| AES-ECB (one block, for header protection) | `Cipher.getInstance("AES/ECB/NoPadding")` (jvmAndroid only) |
| ChaCha20 keystream (header protection) | Quartz `ChaCha20Core.chaCha20Xor` |
| SecureRandom | Quartz `RandomInstance` |

X.509 chain validation, hostname verification, signature verification all
delegate to JDK `TrustManagerFactory` / `Signature.getInstance(...)`.
`CertificateValidator` is a non-null typed parameter — tests pass an
explicit `PermissiveCertificateValidator`; production passes
`JdkCertificateValidator`.

## What we deliberately don't do

- **QUIC server role.** Client-only.
- **0-RTT / session resumption.** No PSK extension offered; an arriving
  ServerFinished without prior Certificate is hard-failed.
- **Connection migration / preferred address / multiple paths.** `NEW_CONNECTION_ID`
  and `PATH_*` frames decode (so peers don't break us) but aren't acted on.
- **Path MTU discovery.** Fixed 1200-byte ceiling per RFC 9000 §14.
- **HTTP/3 server push.**
- **QPACK dynamic-table inserts on the encoder.** We send literal-only;
  decoder accepts dynamic-table indexed lines.
- **ECN / anti-amplification limits.** We're a client.
- **TLS Key-Update / NewSessionTicket.** Detected and refused (KeyUpdate
  fails the connection rather than silently desynchronising).
- **Congestion control (NewReno / CUBIC / BBR).** Loss detection is in
  place (RFC 9002 §5/§6) but no rate-limiting feedback loop reacts to
  losses; we send as fast as the application provides bytes. Independent
  follow-up.

## Verified interop

- **aioquic** (Python): `quic-interop-runner`-style Docker setup; full
  handshake + Extended CONNECT + h3 datagram round-trip.
- **picoquic** (C): Docker image, lightweight HTTP/3 GET.
- **In-memory pipe** (`InMemoryQuicPipe`, modeled on Cloudflare quiche's
  `Pipe`) drives both sides of the handshake in one JVM for fast tests
  without sockets.

What's NOT verified: a live nests/MoQ audio-room exchange end to end. That
gates on the audio-rooms completion plan
([nestsClient/plans/2026-04-26-audio-rooms-completion.md](../../nestsClient/plans/2026-04-26-audio-rooms-completion.md)).

## Audit summary

| Round | Focus | Findings | Status |
|---|---|---|---|
| 1 | Initial review (pre-interop) | 6 critical correctness/security bugs | all fixed |
| 2 | TLS hardening + lifecycle | hangs + TLS edge cases | all fixed |
| 3 | Performance + concurrency | cipher caching, polling, transcript O(n²) | all fixed |
| 4 | Core + TLS + perf + coverage gaps (4 parallel agents) | ~30 items including 4 CRITICAL interop blockers | all fixed; comprehensive regression tests added |
| 5 | Regression check + concurrency-specific (2 parallel agents) | 1 CRITICAL ackEliciting regression I'd just introduced + WT scope leak + others | all fixed |

Every fix carries an inline `audit-N #M` reference comment so the regression
test → fix → comment chain is auditable. The whole audit corpus is in the
git log (commits whose subject starts with `fix(quic):` or `perf(quic):`).

## Test inventory

Roughly grouped:

- **RFC vectors:** RFC 8448 §3 (TLS handshake), RFC 9001 §A.1–A.5 (Initial
  encrypt/decrypt, Retry, ChaCha20, server-side HP), RFC 9204 §B.1 (QPACK),
  RFC 7541 (Huffman).
- **End-to-end pipe tests:** `InMemoryQuicPipeTest`, `CoalescedPacketSkipTest`,
  `ReceiveLimitEnforcementTest`, `PeerStreamLimitTest`, `FrameRoutingTest`,
  `AckElicitingFramesTest`.
- **Adversarial:** `FrameFuzzerTest`, `HostilePacketInputTest`,
  `TlsSecurityPropertiesTest`, `HelloRetryRequestTest`.
- **Crypto:** `JcaAesGcmAeadTest`, `ChaCha20Poly1305AeadTest`,
  `TlsTranscriptHashTest`.
- **WT / HTTP/3:** `CapsuleReaderTest`, `WtPeerStreamDemuxTest`,
  `WtFramingTest`.
- **Recovery:** `AckTrackerCoalescedTest`, `AckTrackerGatingTest`,
  `RecoveryTokenTest`, `SentPacketTest`, `ReceiverFlowControlTest` (9
  cases mirrored from neqo `fc.rs`), `QuicLossDetectionTest`,
  `PtoTest`, `QuicConnectionRetransmitTest`,
  `SendBufferRetainUntilAckTest` (14 cases for the rewrite),
  `StreamRetransmitTest`, `CryptoRetransmitTest`,
  `ResetStopSendingEmitTest` (7 cases).
- **Interop:** `InteropRunner` (jvmTest, drives a real socket against a
  Dockerised aioquic; opt-in, not in CI).

## Known limitations / deferred work

These are the items future audit rounds keep flagging that we've
consciously not tackled — all confined to the steady-state path that audio
rooms don't exercise heavily:

1. ~~**No STREAM retransmit on loss** (audit-4 #10).~~ **Resolved 2026-05-05** —
   `SendBuffer` rewritten for retain-until-ACK with three-state range
   tracking; ACK / loss dispatchers re-queue lost ranges to the
   retransmit FIFO. Also covers CRYPTO retransmit per encryption level
   and RESET_STREAM / STOP_SENDING / NEW_CONNECTION_ID retransmit.
   See [`2026-05-04-control-frame-retransmit.md`](2026-05-04-control-frame-retransmit.md).
2. ~~**`SendBuffer` doesn't retain bytes until ACK.**~~ Resolved with #1.
3. **No Initial / Handshake key discard.** RFC 9000 §17.2.2 / RFC 9001 §4.9
   require dropping these after handshake completes; we hold them
   indefinitely. Memory leak per long session.
4. **No path validation for `NEW_CONNECTION_ID`.** We don't migrate.
5. **Stateless reset detection.** Stateless-reset packets look like
   corruption to us.
6. **`AckTracker.purgeBelow` threshold semantics.** Pre-existing bug:
   purges based on peer's largestAcknowledged of OUR outbound PNs, but
   purges OUR inbound PN tracker. Causes range-list bloat, not correctness
   failure.
7. **Driver direct unit tests** require turning `UdpSocket` from `expect
   class` into an interface so the test side can stub. The driver is
   covered indirectly by every pipe-based test plus the live interop
   runner.
8. **No congestion control.** Loss detection is wired but nothing
   throttles send rate in response. Independent ~1-2 wk project.

## Pointers

- Original (frozen) plan: `docs/plans/2026-04-22-pure-kotlin-quic-webtransport-plan.md`
- Audio-rooms NIP draft: `docs/plans/2026-04-22-nip-audio-rooms-draft.md`
- Completion plan: `nestsClient/plans/2026-04-26-audio-rooms-completion.md`
- Retransmit plan + implementation log: [`2026-05-04-control-frame-retransmit.md`](2026-05-04-control-frame-retransmit.md)
- Live interop runner: `quic/src/jvmTest/.../interop/InteropRunner.kt`
- Audit history: `git log --grep='audit' -- quic/`
