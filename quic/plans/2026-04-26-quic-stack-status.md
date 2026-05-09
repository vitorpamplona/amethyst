# QUIC + WebTransport stack — current state

Living status doc for `:quic`. First written 2026-04-26 as the post-implementation
snapshot of the [pure-kotlin QUIC + WebTransport
plan](../../docs/plans/2026-04-22-pure-kotlin-quic-webtransport-plan.md);
last full rewrite **2026-05-09** after a four-RFC compliance audit (RFC 9000,
9001, 9002, 9221 + 9114 + 9204) found the doc had drifted ~2 weeks behind
shipping work. The original plan is now historical.

## TL;DR

`:quic` is a self-contained Kotlin-Multiplatform module that speaks QUIC v1
+ HTTP/3 + WebTransport against real-world servers (aioquic + picoquic +
quic-go interop verified, including 0-RTT). Pure Kotlin: Quartz crypto
primitives + JCA AEAD only — no BouncyCastle, no JNI. ~17k lines of
production code, 81 test files, ten-plus rounds of audit + fix passes
since 2026-04-26.

The 2026-05-09 audit confirmed the core handshake / packet protection /
loss recovery / frame routing surface is RFC-grade. Real gaps are listed
in [§ RFC compliance gaps](#rfc-compliance-gaps-2026-05-09).

## What shipped vs the original plan

| Phase | Original estimate | Actual | Notes |
|---|---|---|---|
| A. Foundations | 1 wk | done | KMP module, UdpSocket on `jvmAndroid`, Varint migrated from nestsClient |
| B. TLS 1.3 | 3 wk | done | RFC 8446 client state machine, X25519 ECDHE, RFC 8448 §3 vectors pass bit-for-bit |
| C. Initial + Handshake packets | 2 wk | done | RFC 9001 §A.2/§A.3 vectors pass; ChaCha20 per §A.5 |
| D. 1-RTT + STREAM | 1 wk | done | Stream offset reassembly, FIN, fuzzed |
| E. ACK + flow control | 1 wk | done | MAX_DATA / MAX_STREAM_DATA / MAX_STREAMS routing + writer enforcement + per-stream receive enforcement |
| F. Loss recovery + congestion control | 1 wk | done (no CC) | RFC 9002 §5/§6 RTT estimator + packet/time-threshold loss detection + PTO + per-frame retransmit shipped 2026-05-05; congestion control still TBD |
| G. Datagram extension | ½ wk | done | RFC 9221 frames + bounded incoming queue |
| H. Connection lifecycle | 1 wk | done | CONNECTION_CLOSE, draining/closing, idempotent driver close (idle-timeout enforcement still missing — see gaps) |
| I. HTTP/3 | 2 wk | done | Control stream + SETTINGS + GOAWAY (with id-regression check) + duplicate-id rejection |
| J. QPACK | 2 wk | done | Static-table + Huffman + integer codec (RFC 7541 §B + RFC 9204 §B.1 vectors) |
| K. Extended CONNECT + WT | 1 wk | done | Stream-type prefixes, HTTP Datagram + WT_CLOSE_SESSION capsule |
| L. Interop + hardening | 2 wk | done + much more | Live interop against aioquic + picoquic + quic-go; **10+ rounds of audit + fix**, multiplexing, 0-RTT, key update, ECN, path migration |

The original plan estimated 17–19 weeks. We shipped the full sequence plus
a long tail of audit-driven hardening. Every audit found real bugs; the
test suite is what catches them on regression.

## What's actually in the module

```
quic/
├── plans/                                ← module-local design docs
└── src/
    ├── commonMain/kotlin/com/vitorpamplona/quic/
    │   ├── Buffer.kt                     ← QuicReader / QuicWriter
    │   ├── Varint.kt                     ← RFC 9000 §16
    │   ├── connection/                   ← QuicConnection orchestrator + Driver
    │   │   ├── QuicConnection.kt              (≈ 2800 lines, the hub)
    │   │   ├── QuicConnectionDriver.kt        (read/send loops + close)
    │   │   ├── QuicConnectionParser.kt        (feedDatagram + dispatchFrames)
    │   │   ├── QuicConnectionWriter.kt        (drainOutbound + flow-control updates)
    │   │   ├── PathValidator.kt               ← RFC 9000 §5.1.2 + §8.2 + §9
    │   │   ├── PacketProtection.kt + builder
    │   │   ├── PacketNumberSpace.kt
    │   │   ├── ConnectionId.kt + TransportParameters.kt
    │   │   ├── EncryptionLevel.kt + LevelState.kt
    │   │   └── recovery/                       ← RecoveryToken, SentPacket,
    │   │                                         AckedPackets, QuicLossDetection
    │   ├── crypto/                       ← Aead, header protection, HKDF helpers,
    │   │                                   AesEcbHeaderProtection, ChaCha20HeaderProtection,
    │   │                                   ChaCha20Poly1305Aead, InitialSecrets,
    │   │                                   PlatformAesOneBlock, PlatformChaCha20Block (expect),
    │   │                                   bestAes128GcmAead (expect)
    │   ├── frame/                        ← Frame.kt sealed hierarchy + FrameFuzzerTest target
    │   │                                   includes RESET_STREAM / STOP_SENDING / NEW_TOKEN /
    │   │                                   PATH_CHALLENGE / PATH_RESPONSE / NEW_CONNECTION_ID /
    │   │                                   RETIRE_CONNECTION_ID / ACK_ECN
    │   ├── http3/                        ← Http3FrameReader + Http3Settings + frame types
    │   ├── packet/                       ← LongHeaderPacket, ShortHeaderPacket (with
    │   │                                   reserved-bit + key-phase peek), RetryPacket, peekHeader
    │   ├── qpack/                        ← QpackDecoder, QpackEncoder, QpackHuffman, QpackInteger,
    │   │                                   QpackStaticTable
    │   ├── recovery/                     ← AckTracker (with ack-eliciting gating)
    │   ├── stream/                       ← QuicStream, ReceiveBuffer (with FIN-fully-read +
    │   │                                   per-stream receive-limit), SendBuffer, StreamId
    │   ├── tls/                          ← TlsClient state machine + ClientHello (incl. PSK +
    │   │                                   early_data resumption variant) / ServerHello / EE /
    │   │                                   Cert / CV / Finished / NewSessionTicket codecs,
    │   │                                   TlsKeySchedule (incl. 0-RTT + resumption_master_secret),
    │   │                                   TlsTranscriptHash (incremental), TlsConstants,
    │   │                                   PermissiveCertificateValidator, TlsRunningSha256 (expect)
    │   ├── transport/                    ← UdpSocket (expect)
    │   └── webtransport/                 ← QuicWebTransportFactory + QuicWebTransportSessionState +
    │                                       WtPeerStreamDemux + WtCapsule + WtDatagram + ExtendedConnect
    └── jvmAndroid/kotlin/com/vitorpamplona/quic/
        ├── crypto/JcaAesGcmAead.kt       ← cached JCA Cipher per direction with IV-reuse fallback
        ├── crypto/JcaChaCha20Poly1305Aead.kt ← JCA ChaCha20-Poly1305 (with Quartz fallback)
        ├── crypto/PlatformCrypto.kt      ← actuals
        ├── tls/JdkCertificateValidator.kt ← system-trust-store chain validation + PSL filter +
        │                                    RSA-PSS / ECDSA / Ed25519
        ├── tls/TlsRunningSha256.kt       ← MessageDigest.clone()-based incremental hash
        └── transport/UdpSocket.kt        ← DatagramChannel + suspend wrapper
```

## Crypto surface

Quartz primitives + JCA only:

| Primitive | Source |
|---|---|
| AES-128-GCM | `JcaAesGcmAead` (jvmAndroid) — cached `Cipher` per direction; `Aes128Gcm` singleton (commonMain) for non-hot paths |
| ChaCha20-Poly1305 | `JcaChaCha20Poly1305Aead` (jvmAndroid, JCA fast path) with Quartz pure-Kotlin fallback in `ChaCha20Poly1305Aead` |
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
`JdkCertificateValidator` (with PSL-subset wildcard rules).

## What we deliberately don't do

- **QUIC server role.** Client-only.
- **Path MTU discovery.** Fixed 1200-byte ceiling per RFC 9000 §14.
- **Anti-amplification limits (server side).** We're a client; the server
  applies its own.
- **HTTP/3 server push.**
- **QPACK dynamic-table inserts on the encoder.** We send literal +
  static-indexed only; decoder accepts dynamic-table indexed lines.
- **Congestion control (NewReno / CUBIC / BBR).** Loss detection is
  in place (RFC 9002 §5/§6) but no rate-limiting feedback loop reacts
  to losses; we send as fast as the application provides bytes. See
  [`2026-05-05-congestion-control.md`](2026-05-05-congestion-control.md)
  (parked indefinitely; acceptable for moq-lite audio).

## What's now shipped that the original "deliberately don't do" list disclaimed

The plan's pre-rewrite version listed the following as out-of-scope. They
shipped between 2026-04-26 and 2026-05-09 and now WORK:

- **0-RTT / session resumption.** TLS 1.3 PSK + `early_data` extension on
  both ends; client caches `resumption_master_secret`, parses
  `NewSessionTicket`, and replays 0-RTT data with prior-connection ALPN
  + transport params. Verified against picoquic and quic-go.
- **TLS Key-Update / NewSessionTicket.** RFC 9001 §6 1-RTT key update —
  client-initiated AND peer-initiated. `KEY_PHASE`-bit peek before AEAD,
  in-flight gate, PN-regression check on the rotation packet, proper
  HKDF roll of `application_traffic_secret_N+1`.
- **NEW_CONNECTION_ID / RETIRE_CONNECTION_ID handling, including
  `retire_prior_to`** per RFC 9000 §5.1.2 + §19.15 (`PathValidator.recordPeerNewConnectionId`
  with full state machine: stale-offer retire, watermark monotonicity,
  pool-full bound, duplicate-with-mismatch rejection).
- **Path validation (client- and server-initiated).** RFC 9000 §8.2 +
  §9 — full PATH_CHALLENGE / PATH_RESPONSE round-trip, 3*PTO timeout
  per §8.2.4, abrupt migration to a new DCID on success, forced
  rotation when `retire_prior_to` exceeds the active CID.
- **ECN (1-RTT only).** ECT(0) on outbound, `ACK_ECN` (0x03) frames
  with truthful CE / ECT(0) / ECT(1) counters. Initial / Handshake
  ACKs intentionally skip ECN counts (interop conservatism).

The remaining pre-rewrite "don't do" items (server role, PMTU, HTTP/3
push, QPACK encoder dynamic table, CC) are still out-of-scope.

## Verified interop

- **aioquic** (Python): `quic-interop-runner`-style Docker setup; full
  handshake + multiplexing + Extended CONNECT + h3 datagram round-trip.
- **picoquic** (C): Docker image, h3, 0-RTT, key-update, longRTT, ECN
  test cases pass.
- **quic-go** (Go): 0-RTT pass.
- **In-memory pipe** (`InMemoryQuicPipe`, modeled on Cloudflare quiche's
  `Pipe`) drives both sides of the handshake in one JVM for fast tests
  without sockets.

What's NOT verified: a live nests/MoQ audio-room exchange end to end. That
gates on the audio-rooms completion plan
([nestsClient/plans/2026-04-26-audio-rooms-completion.md](../../nestsClient/plans/2026-04-26-audio-rooms-completion.md)).

## Audit summary

| Round | Date | Focus | Findings | Status |
|---|---|---|---|---|
| 1 | 2026-04-22 | Initial review (pre-interop) | 6 critical correctness/security bugs | all fixed |
| 2 | 2026-04-23 | TLS hardening + lifecycle | hangs + TLS edge cases | all fixed |
| 3 | 2026-04-24 | Performance + concurrency | cipher caching, polling, transcript O(n²) | all fixed |
| 4 | 2026-04-25 | Core + TLS + perf + coverage gaps (4 parallel agents) | ~30 items including 4 CRITICAL interop blockers | all fixed; comprehensive regression tests added |
| 5 | 2026-04-26 | Regression check + concurrency-specific (2 parallel agents) | 1 CRITICAL ackEliciting regression I'd just introduced + WT scope leak + others | all fixed |
| 6 | 2026-05-04 | Per-frame retransmit + SendBuffer rewrite | retain-until-ACK + control-frame retx + STREAM/CRYPTO recovery | all fixed; see [`2026-05-04-control-frame-retransmit.md`](2026-05-04-control-frame-retransmit.md) |
| 7 | 2026-05-05 | Loss-detection timer + PSK rejection signal | RFC 9002 §6.1.2 timer; PSK rejection recover-in-place | all fixed |
| 8 | 2026-05-06 | Path validation pass 1 + 2 | DCID rotation, retire-prior-to monotonicity, abrupt migration | all fixed |
| 9 | 2026-05-07 | DoS hardening + reserved-bit checks + TLS bounds + SendBuffer shrink | bound peer-controlled buffers and channels | all fixed |
| 10 | 2026-05-08 | Lock-split design + close-under-load + key-update PN gate | streamsLock + lifecycleLock split | all fixed; see [`2026-05-08-lock-split-design.md`](2026-05-08-lock-split-design.md) |
| 11 | 2026-05-09 | Four-RFC compliance audit (4 parallel agents: 9000, 9001, 9002, 9221+9114+9204) | ~15 items, mostly receive-side validation gaps | catalogued in [§ RFC compliance gaps](#rfc-compliance-gaps-2026-05-09) below; **all 🔴 High and all 🟡 Medium items resolved same-day across 7 commits** |

Every fix carries an inline `audit-N #M` reference comment so the regression
test → fix → comment chain is auditable. The whole audit corpus is in the
git log (commits whose subject starts with `fix(quic):` or `perf(quic):`).

## Test inventory

81 `*Test.kt` files. Roughly grouped:

- **RFC vectors:** RFC 8448 §3 (TLS handshake), RFC 9001 §A.1–A.5 (Initial
  encrypt/decrypt, Retry, ChaCha20, server-side HP), RFC 9204 §B.1 (QPACK),
  RFC 7541 (Huffman).
- **End-to-end pipe tests:** `InMemoryQuicPipeTest`, `CoalescedPacketSkipTest`,
  `ReceiveLimitEnforcementTest`, `PeerStreamLimitTest`, `FrameRoutingTest`,
  `AckElicitingFramesTest`, `MultiplexingRoundTripTest`,
  `MultiplexingThroughputTest`, `MultiplexingCoalescingTest`,
  `MultiStreamFinDeliveryTest`, `MultiplexingAioquicTpsTest`.
- **Adversarial:** `FrameFuzzerTest`, `HostilePacketInputTest`,
  `TlsSecurityPropertiesTest`, `HelloRetryRequestTest`,
  `CloseDatagramRfcComplianceTest`, `CloseUnderLoadTest`.
- **Crypto:** `JcaAesGcmAeadTest`, `ChaCha20Poly1305AeadTest`,
  `TlsTranscriptHashTest`.
- **WT / HTTP/3:** `CapsuleReaderTest`, `WtPeerStreamDemuxTest`,
  `WtFramingTest`.
- **Recovery:** `AckTrackerCoalescedTest`, `AckTrackerGatingTest`,
  `AckTrackerPurgeOnAckOfAckTest`, `RecoveryTokenTest`, `SentPacketTest`,
  `ReceiverFlowControlTest` (9 cases mirrored from neqo `fc.rs`),
  `QuicLossDetectionTest`, `PtoTest`, `PtoCryptoRetransmitTest`,
  `QuicConnectionRetransmitTest`, `RetransmitIntegrationTest`,
  `SendBufferRetainUntilAckTest` (14 cases), `StreamRetransmitTest`,
  `CryptoRetransmitTest`, `ResetStopSendingEmitTest` (7 cases),
  `OnTokensLostTest`, `MoqLiteLossHarnessTest`.
- **Path validation / migration:** `PathValidatorTest`,
  `PathValidationTest`, `ClientPathMigrationTest`.
- **Key lifecycle:** `KeyDiscardTest`, `KeyUpdatePeerInitiatedTest`.
- **Concurrency / soak:** `BatchedOpenLockContractTest`,
  `StreamRetirementSoakTest`, `QuicHeapSoakTest`,
  `QuicConnectionDriverLifecycleTest`.
- **Misc:** `VersionNegotiationTest`, `RetryHandlingTest`,
  `PacketNumberSpaceTest`, `ConnectionIdTest`,
  `FlowControlSnapshotTest`, `PendingFlowControlEmitTest`,
  `PeerStreamCreditExtensionTest`, `PeerUniStreamDrainTest`.
- **Interop (jvmTest, opt-in):** `InteropRunner` drives a real socket
  against a Dockerised aioquic / picoquic / quic-go. Not in CI.

## Known limitations / deferred work

These are items future audit rounds keep flagging that we've consciously
not tackled:

1. ~~**No STREAM retransmit on loss** (audit-4 #10).~~ **Resolved 2026-05-05** —
   `SendBuffer` rewritten for retain-until-ACK with three-state range
   tracking; ACK / loss dispatchers re-queue lost ranges to the
   retransmit FIFO. Also covers CRYPTO retransmit per encryption level
   and RESET_STREAM / STOP_SENDING / NEW_CONNECTION_ID retransmit.
   See [`2026-05-04-control-frame-retransmit.md`](2026-05-04-control-frame-retransmit.md).
2. ~~**`SendBuffer` doesn't retain bytes until ACK.**~~ Resolved with #1.
3. ~~**No Initial / Handshake key discard.**~~ **Resolved 2026-05-05.**
4. ~~**No path validation for `NEW_CONNECTION_ID`.**~~ **Resolved 2026-05-06–08** —
   full client-initiated DCID rotation + server-initiated path
   validation per RFC 9000 §8.2 + §9. See `PathValidator.kt` and the
   three test files (`PathValidatorTest`, `PathValidationTest`,
   `ClientPathMigrationTest`).
5. ~~**Stateless reset detection.**~~ **Resolved 2026-05-09** —
   `QuicConnection.isStatelessReset` matches every short-header-form
   datagram's trailing 16 bytes against the peer's transport-param
   token AND every NEW_CONNECTION_ID token retained in
   `PathValidator.allKnownStatelessResetTokens` (lifetime store —
   covers the WiFi-handoff case where the migrated CID's token would
   otherwise be lost the moment migration started). Constant-time per
   §10.3.1; silent CLOSED transition on match.
6. ~~**`AckTracker.purgeBelow` threshold semantics.**~~ **Resolved
   2026-05-05.**
7. **Driver direct unit tests** require turning `UdpSocket` from `expect
   class` into an interface so the test side can stub. The driver is
   covered indirectly by every pipe-based test plus the live interop
   runner.
8. **No congestion control.** Loss detection is wired but nothing
   throttles send rate in response. Independent ~1–2 wk project.
   See [`2026-05-05-congestion-control.md`](2026-05-05-congestion-control.md).

## RFC compliance gaps (2026-05-09)

Catalogued from a four-agent compliance audit (RFC 9000 / 9001 / 9002 /
9221+9114+9204). Each item lists the spec section, the gap, and
file:line evidence. Severity:

- 🔴 **High** — exploitable by a hostile peer (resource exhaustion,
  silent desync, RTT poisoning).
- 🟡 **Medium** — interop or correctness risk; cooperative peers
  unaffected.
- 🟦 **Low / cosmetic** — diagnostic loss, doesn't break the wire.

### Receive-side validation gaps

| RFC § | Sev | Gap | Evidence |
|---|---|---|---|
| RFC 9000 §17.2 + §17.3 | ~~🔴~~ | ~~**Inbound fixed-bit (0x40) not validated.**~~ **Resolved 2026-05-09** — both `LongHeaderPacket.parseAndDecrypt`, `ShortHeaderPacket.parseAndDecrypt`, and `ShortHeaderPacket.peekKeyPhase` now reject fixed-bit=0 packets per RFC §17.2 / §17.3 (silent discard). Tests in `FixedBitValidationTest`. |
| RFC 9000 §10.1 | ~~🔴~~ | ~~**`max_idle_timeout` not enforced.**~~ **Resolved 2026-05-09** — `QuicConnection.lastActivityMs` updated on inbound packet receipt and outbound ack-eliciting send; `effectiveIdleTimeoutMs()` computes min(local, peer) with 3 × PTO floor per §10.1; driver send-loop folds the deadline into its `withTimeoutOrNull` and silently closes via `markClosedExternally` per §10.2.1 on expiry. Tests in `IdleTimeoutTest` (8 cases). |
| RFC 9000 §4.1 | ~~🟡~~ | ~~**Connection-level inbound flow control missing.**~~ **Resolved 2026-05-09** — `QuicStream.receiveHighestOffset` + `QuicConnection.connectionInboundOffsetSum` track the §4.1 spec quantity (sum of largest-received-offset across streams). Parser closes with FLOW_CONTROL_ERROR when the sum exceeds `advertisedMaxData`. RESET_STREAM finalSize counted toward the limit per §4.5. Tests in `ConnectionLevelFlowControlTest`. |
| RFC 9000 §3 | ~~🟡~~ | ~~**Stream state machine implicit, not validated.**~~ **Resolved 2026-05-09** — `QuicStream.peerResetReceived` latches when a RESET_STREAM arrives; the parser closes with STREAM_STATE_ERROR on any subsequent STREAM frame for the same id. (Other state transitions — FIN-size mismatch, illegal peer-initiated opens — were already caught.) Tests in `StreamAfterResetTest`. |
| RFC 9000 §10.2 | ~~🟡~~ | ~~**No DRAINING state.**~~ **Resolved 2026-05-09** — `Status.DRAINING` added; parser routes peer's CONNECTION_CLOSE through `enterDraining` (sets status + 3 × PTO deadline) instead of immediate CLOSED. Driver folds the deadline into its send-loop sleep and flips to CLOSED on expiry. Late inbound during DRAINING is silently dropped at the top of `feedDatagramInner`. Tests in `DrainingStateTest`. |
| RFC 9000 §10.3 | ~~🟡~~ | ~~**Stateless reset detection missing.**~~ **Resolved 2026-05-09** — `QuicConnection.isStatelessReset` checks the trailing 16 bytes of every short-header-form datagram against the peer's `statelessResetToken` AND every NEW_CONNECTION_ID token in `PathValidator.allKnownStatelessResetTokens` (lifetime store — extends past CID rotation / retirement so WiFi↔cellular handoffs still detect reset on the new path), in constant time per §10.3.1. On match, silently transitions to CLOSED. Tests in `StatelessResetDetectionTest` (7 cases including handoff + force-rotation). |
| RFC 9001 §6.6 | ~~🟡~~ | ~~**AEAD invocation limit not tracked.**~~ **Resolved 2026-05-09** — `Aead.confidentialityLimit` / `integrityLimit` properties surface the §B.1 values; `QuicConnection.aeadEncryptCount` / `aeadDecryptFailureCount` track per-key usage and reset on rotation. Writer triggers a key update at half the confidentiality limit and closes with AEAD_LIMIT_REACHED if rotation can't complete; parser closes if decrypt failures hit the integrity limit. Tests in `AeadInvocationLimitTest`. |

### Transport-parameter bounds

| RFC § | Sev | Gap | Evidence |
|---|---|---|---|
| RFC 9000 §18.2 | ~~🟡~~ | ~~**`max_udp_payload_size < 1200` not rejected.**~~ **Resolved 2026-05-09** — `applyPeerTransportParameters` closes with TRANSPORT_PARAMETER_ERROR. Tests in `TransportParameterBoundsTest`. |
| RFC 9000 §18.2 | ~~🟡~~ | ~~**`ack_delay_exponent > 20` not rejected.**~~ **Resolved 2026-05-09** — same path. |
| RFC 9000 §18.2 | ~~🟡~~ | ~~**`active_connection_id_limit < 2` not rejected.**~~ **Resolved 2026-05-09** — same path. |
| RFC 9000 §13.2.5 | ~~🟦~~ | ~~**`ack_delay_exponent` decode uses our config, not peer's.**~~ **Resolved 2026-05-09** — parser now uses `peerTransportParameters?.ackDelayExponent` with the §18.2 default of 3 as fallback; defensive 0..20 coercion preserved. |

### HTTP/3 + DATAGRAM gaps

| RFC § | Sev | Gap | Evidence |
|---|---|---|---|
| RFC 9114 §7.2.4.1 | ~~🟡~~ | ~~**HTTP/2 reserved SETTINGS ids 0x02/0x03/0x04/0x05 not rejected.**~~ **Resolved 2026-05-09** — `Http3Settings.decodeBody` raises H3_SETTINGS_ERROR for ids 0x02..0x05. Tests in `Http3ReservedSettingsTest`. |
| RFC 9221 §3 | ~~🟡~~ | ~~**Outbound DATAGRAM size not gated.**~~ **Resolved 2026-05-09** — writer drops outbound DATAGRAM frames when peer didn't advertise `max_datagram_frame_size` or when the encoded frame would exceed the advertised value. Diagnostic via `qlogObserver.onPacketDropped`. |
| RFC 9114 §6.2.1 + RFC 9204 §4.2 | ~~🟦~~ | ~~**Closing the control stream surfaces a flag rather than auto-closing.**~~ **Resolved 2026-05-09** — `WtPeerStreamDemux` now drives `connection.close(errorCode, reason)` itself when ANY critical unidirectional stream closes (control + QPACK encoder + QPACK decoder), whether by clean FIN (H3_CLOSED_CRITICAL_STREAM) or HTTP/3 protocol violation (mapped to the specific §8.1 code where possible). New `Http3ErrorCode` constants. Tests in `CriticalStreamClosureTest` (5 cases) cover FIN-on-control, FIN-on-QPACK, MISSING_SETTINGS, FRAME_UNEXPECTED, and idempotency. Audio rooms now get an immediate disconnect event when the relay drops the control stream mid-session. |

### TLS / error reporting cosmetics

| RFC § | Sev | Gap | Evidence |
|---|---|---|---|
| RFC 9001 §4.8 | 🟦 | **TLS alerts not mapped to CRYPTO_ERROR + alert_code.** Connection still closes (a generic `QuicCodecException` propagates) but the alert code is lost — debuggability hit, not a wire-spec violation. | `TlsClient.kt:311-317` (catches as `Throwable`) |
| RFC 9000 §22 | 🟦 | **Several error codes never emitted.** INVALID_TOKEN (no Retry token validation), CRYPTO_BUFFER_EXCEEDED (no buffer limit), KEY_UPDATE_ERROR (uses generic exception path on KeyUpdate failures, despite key update being implemented). | various |

### Gaps the 2026-05-09 audit got WRONG

For audit traceability — three audit findings fingered code that's
actually compliant. Listed here so future audit rounds don't repeat
them:

| Audit claim | Reality |
|---|---|
| "Inbound flow-control enforcement missing." | Per-stream IS enforced. `QuicConnectionParser.kt:655` raises a connection close when STREAM offset+len exceeds `stream.receiveLimit`. The actual gap is *connection-level* (above). |
| "ack_delay_exponent not applied on decode." | IS applied at `QuicConnectionParser.kt:540-553`, with explicit overflow protection per audit-4. The actual gap is using *our* exponent instead of the peer's. |
| "Short-header reserved bits not validated." | ARE validated at `ShortHeaderPacket.kt:182-187` — `QuicProtocolViolationException` raised on any of the 0x18 bits set. The `0x30` mask the audit cited was a mis-read of RFC 9000 §17.3 (reserved bits are 0x18). |

## Pointers

- Original (frozen) plan: `docs/plans/2026-04-22-pure-kotlin-quic-webtransport-plan.md`
- Audio-rooms NIP draft: `docs/plans/2026-04-22-nip-audio-rooms-draft.md`
- Completion plan: `nestsClient/plans/2026-04-26-audio-rooms-completion.md`
- Retransmit plan + implementation log: [`2026-05-04-control-frame-retransmit.md`](2026-05-04-control-frame-retransmit.md)
- Congestion control deferral rationale: [`2026-05-05-congestion-control.md`](2026-05-05-congestion-control.md)
- Lock-split design: [`2026-05-08-lock-split-design.md`](2026-05-08-lock-split-design.md)
- Live interop runner: `quic/src/jvmTest/.../interop/InteropRunner.kt`
- Audit history: `git log --grep='audit\|fix(quic)\|feat(quic)' -- quic/`
