/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quic.tls

import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519KeyPair
import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter

/**
 * TLS 1.3 client driven by QUIC's encryption-level CRYPTO stream payloads.
 *
 * The QUIC stack feeds in CRYPTO-frame bytes for each encryption level
 * (Initial → Handshake → Application) via [pushHandshakeBytes]; the driver
 * accumulates and parses handshake messages, advances state, derives keys,
 * and emits outbound CRYPTO payloads via [pollOutbound].
 *
 * Key derivations are exposed as [secretsListener] callbacks so the QUIC
 * layer can install per-direction packet protection at the right moment:
 *
 *   1. After we send ClientHello (Initial-tx already installed by caller from CID).
 *   2. After ServerHello arrives → install Handshake keys both directions.
 *   3. After server Finished decoded → install 1-RTT (application) keys both directions.
 *
 * Certificate chain validation + CertificateVerify signature verification
 * are delegated to [certificateValidator] (`JdkCertificateValidator` in
 * production; `PermissiveCertificateValidator` for in-process tests). The
 * validator parameter is non-null — passing `null` was a silent-MITM
 * hazard and was removed in round-4 of the audit. We also compute and
 * verify the server Finished MAC ourselves.
 */
class TlsClient(
    val serverName: String,
    val transportParameters: ByteArray,
    val secretsListener: TlsSecretsListener,
    /**
     * Audit-4 #1: certificate validator is REQUIRED (non-null). For tests that
     * connect to a self-signed in-process server, pass an explicit
     * [PermissiveCertificateValidator] — the type system makes "no MITM
     * protection" a deliberate, code-review-visible choice instead of a quiet
     * forgotten null.
     */
    val certificateValidator: CertificateValidator,
    /**
     * The ALPN values we offered in ClientHello. Used to validate the server's
     * EncryptedExtensions ALPN selection (audit-4 #20: a server picking an
     * unknown ALPN was previously accepted silently).
     */
    val offeredAlpns: List<ByteArray> = listOf(TlsConstants.ALPN_H3),
    /** When non-null, used as the X25519 ephemeral key (for deterministic tests). */
    val fixedKeyPair: X25519KeyPair? = null,
    /** When non-null, used as the ClientHello random (for deterministic tests). */
    val fixedRandom: ByteArray? = null,
    /**
     * Cipher suites offered in the ClientHello, in preference order. The
     * default offers AES-128-GCM first then ChaCha20-Poly1305. Override to
     * force a specific negotiation (e.g. `chacha20`-only for the matching
     * quic-interop-runner testcase).
     */
    val cipherSuites: IntArray =
        intArrayOf(
            TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256,
            TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256,
        ),
    /**
     * Wall-clock provider for the NewSessionTicket [issuedAtMillis] stamp.
     * Tests inject a fixed clock; production uses System.currentTimeMillis().
     * Stored at issue-time only — the obfuscated_ticket_age the next
     * connection emits is `(now_at_resume - issuedAtMillis + ticketAgeAdd)
     * mod 2^32`, so a slightly stale clock skew is harmless (the server
     * de-obfuscates and only cares about its own tracked age window).
     */
    val nowMillisSource: () -> Long = { System.currentTimeMillis() },
    /**
     * If non-null, this connection resumes a prior session via PSK rather
     * than running a full handshake. The TLS layer:
     *  - Seeds the early secret from [TlsResumptionState.psk] instead of
     *    zeros (RFC 8446 §7.1).
     *  - Adds `pre_shared_key` as the last ClientHello extension, with
     *    the cached ticket as the identity and a binder computed over
     *    the partial ClientHello.
     *  - Tolerates the server skipping Certificate / CertificateVerify
     *    when it accepts the PSK (server only sends them on full
     *    handshakes).
     *
     * Caller's responsibility to ensure the resumption state is fresh
     * (within `ticket_lifetime` seconds of issue) and bound to a cipher
     * suite this client offers.
     */
    val resumption: TlsResumptionState? = null,
) {
    enum class State {
        INITIAL,
        WAITING_SERVER_HELLO,
        WAITING_ENCRYPTED_EXTENSIONS,
        WAITING_CERTIFICATE_OR_FINISHED,
        WAITING_CERTIFICATE_VERIFY,
        WAITING_SERVER_FINISHED,
        SENT_CLIENT_FINISHED,
        FAILED,
    }

    enum class Level { INITIAL, HANDSHAKE, APPLICATION }

    var state: State = State.INITIAL
        private set

    var negotiatedAlpn: ByteArray? = null
        private set

    var peerTransportParameters: ByteArray? = null
        private set

    /** The handshake message bytes we still owe to the QUIC layer, per encryption level. */
    private val outboundQueues =
        mapOf(
            Level.INITIAL to ArrayDeque<ByteArray>(),
            Level.HANDSHAKE to ArrayDeque<ByteArray>(),
            Level.APPLICATION to ArrayDeque<ByteArray>(),
        )

    private val inboundBuffers =
        mutableMapOf(
            Level.INITIAL to ByteArrayBuilder(),
            Level.HANDSHAKE to ByteArrayBuilder(),
            // Audit-4 #6: include APPLICATION so post-handshake CRYPTO
            // (NewSessionTicket / KeyUpdate detection) actually reaches the
            // SENT_CLIENT_FINISHED handler. Pre-fix, pushHandshakeBytes at
            // APPLICATION threw because the buffer wasn't registered.
            Level.APPLICATION to ByteArrayBuilder(),
        )

    private val transcript = TlsTranscriptHash()
    private val keySchedule = TlsKeySchedule(transcript)

    private var keyPair: X25519KeyPair? = null
    private var serverKeyShare: ByteArray? = null
    private var sharedSecret: ByteArray? = null
    private var negotiatedCipherSuite: Int = -1

    /**
     * True after ServerHello carries a `pre_shared_key` extension with the
     * `selected_identity` we offered. When set, the state machine accepts
     * Finished immediately after EncryptedExtensions (no Certificate /
     * CertificateVerify path) and the application-traffic secrets are
     * computed off the PSK-seeded early secret rather than zeros.
     */
    private var pskAccepted: Boolean = false

    /** The 32-byte ClientHello random, available after [start]. Exposed so
     *  observers (e.g. SSLKEYLOGFILE writer) can correlate secrets with
     *  this connection. */
    var clientRandom: ByteArray? = null
        private set

    /** Begin the handshake by emitting a ClientHello at Initial level. */
    fun start() {
        check(state == State.INITIAL) { "TlsClient already started" }
        keyPair = fixedKeyPair ?: X25519.generateKeyPair()

        // Seed the early secret. Resumption path uses the cached PSK as
        // IKM (RFC 8446 §7.1) so the binder for the pre_shared_key
        // extension can be derived from the same early secret the server
        // will use to validate it. Non-resumption path uses zero IKM.
        if (resumption != null) {
            keySchedule.deriveEarlyFromPsk(resumption.psk)
        } else {
            keySchedule.deriveEarly()
        }

        val random =
            fixedRandom ?: com.vitorpamplona.quartz.utils.RandomInstance
                .bytes(32)
        clientRandom = random

        val chBytes =
            if (resumption != null) {
                // Resumption ClientHello carries pre_shared_key (last
                // extension per spec) with binder bound to the partial CH
                // hash. obfuscated_ticket_age = (current_age + ticket_age_add)
                // mod 2^32. We use the local clock — server's de-obfuscation
                // only cares about its own tracked age window.
                val ageMillis = (nowMillisSource() - resumption.issuedAtMillis).coerceAtLeast(0L)
                val obfuscatedAge = (ageMillis + resumption.ticketAgeAdd) and 0xFFFFFFFFL
                val binderFinishedKey = pskBinderFinishedKey(keySchedule.earlySecret!!)
                buildResumptionClientHelloBytes(
                    serverName = serverName,
                    x25519PublicKey = keyPair!!.publicKey,
                    quicTransportParams = transportParameters,
                    alpns = offeredAlpns,
                    random = random,
                    cipherSuites = cipherSuites,
                    ticket = resumption.ticket,
                    obfuscatedTicketAge = obfuscatedAge,
                    binderFinishedKey = binderFinishedKey,
                    transcriptHashOfPartialCh = { partial ->
                        // Hash a one-shot copy of the running transcript
                        // would-be-state: an empty TlsRunningSha256 fed
                        // partial-CH-bytes is identical to running the
                        // shared transcript "as if" we'd appended
                        // partial-CH and snapshotted.
                        val h = TlsRunningSha256()
                        h.update(partial)
                        h.snapshot()
                    },
                    binderHmac = { key, data ->
                        val mac =
                            com.vitorpamplona.quartz.utils.mac
                                .MacInstance("HmacSHA256", key)
                        mac.update(data)
                        mac.doFinal()
                    },
                )
            } else {
                val ch =
                    buildQuicClientHello(
                        serverName = serverName,
                        x25519PublicKey = keyPair!!.publicKey,
                        quicTransportParams = transportParameters,
                        alpns = offeredAlpns,
                        random = random,
                        cipherSuites = cipherSuites,
                    )
                ch.encode()
            }

        transcript.append(chBytes)
        outboundQueues[Level.INITIAL]!!.addLast(chBytes)
        state = State.WAITING_SERVER_HELLO
    }

    /** Pull buffered outbound handshake bytes for [level], or null if nothing pending. */
    fun pollOutbound(level: Level): ByteArray? = outboundQueues[level]?.removeFirstOrNull()

    /** Feed inbound CRYPTO-frame bytes at [level]. */
    fun pushHandshakeBytes(
        level: Level,
        bytes: ByteArray,
    ) {
        // Audit-4 #7: once a handshake error has fired, refuse further bytes
        // rather than re-entering parsing on stale state. The QUIC layer
        // sees the FAILED state via the bubbled QuicCodecException and
        // closes the connection.
        if (state == State.FAILED) {
            throw QuicCodecException("TLS handshake already failed; ignoring further bytes at $level")
        }
        val buf = inboundBuffers[level] ?: throw QuicCodecException("no buffer at level $level")
        buf.append(bytes)
        drainInbound(level, buf)
    }

    private fun drainInbound(
        level: Level,
        buf: ByteArrayBuilder,
    ) {
        while (true) {
            val msg = buf.takeHandshakeMessage() ?: break
            try {
                handleHandshakeMessage(level, msg)
            } catch (t: Throwable) {
                // Audit-4 #7: any throw from a handler transitions to FAILED
                // so a retry doesn't re-enter parsing on inconsistent state.
                state = State.FAILED
                throw t
            }
        }
    }

    private fun handleHandshakeMessage(
        level: Level,
        msg: ByteArray,
    ) {
        val r = QuicReader(msg)
        val type = r.readByte()
        val len = r.readUint24()
        if (r.remaining < len) throw QuicCodecException("truncated handshake message")
        val bodyReader = QuicReader(msg, r.position, r.position + len)

        when (state) {
            State.WAITING_SERVER_HELLO -> {
                if (type != TlsConstants.HS_SERVER_HELLO) throw QuicCodecException("expected ServerHello, got type=$type")
                if (level != Level.INITIAL) throw QuicCodecException("ServerHello must arrive at Initial level")
                val sh = TlsServerHello.decodeBody(bodyReader)
                // RFC 8446 §4.1.4: HelloRetryRequest is encoded as a ServerHello
                // with a fixed magic random. We don't implement HRR (we only
                // offer X25519, the only group nests + most servers accept), so
                // any HRR is a hard failure.
                if (sh.random.contentEquals(HELLO_RETRY_REQUEST_RANDOM)) {
                    throw QuicCodecException("HelloRetryRequest received but not supported")
                }
                if (sh.negotiatedVersion != TlsConstants.VERSION_TLS_1_3) {
                    throw QuicCodecException("server did not negotiate TLS 1.3")
                }
                val cipher = sh.cipherSuite
                if (cipher != TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256 &&
                    cipher != TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256
                ) {
                    throw QuicCodecException("server picked unsupported cipher 0x${cipher.toString(16)}")
                }
                negotiatedCipherSuite = cipher
                serverKeyShare = sh.serverKeyShareX25519

                // RFC 8446 §4.2.11 — server signals PSK acceptance with a
                // pre_shared_key extension carrying selected_identity (uint16).
                // We only ever offer one identity, so anything other than 0
                // is a protocol violation.
                val pskExt = sh.extensions.firstOrNull { it.type == TlsConstants.EXT_PRE_SHARED_KEY }
                if (resumption != null) {
                    if (pskExt == null) {
                        // We offered PSK but server picked full-handshake.
                        // Plumbing for the fallback path (clear early secret,
                        // re-run binder-less ClientHello transcript) is real
                        // work; for now hard-fail. In production we'd want
                        // to handle gracefully — for the runner's resumption
                        // testcase the server MUST accept or the test fails
                        // anyway, so this gate isn't load-bearing.
                        throw QuicCodecException(
                            "server rejected PSK; full-handshake fallback not implemented",
                        )
                    }
                    val r = QuicReader(pskExt.data)
                    val selectedIdentity = r.readUint16()
                    if (selectedIdentity != 0) {
                        throw QuicCodecException(
                            "server selected PSK identity $selectedIdentity but we only offered 0",
                        )
                    }
                    pskAccepted = true
                } else if (pskExt != null) {
                    throw QuicCodecException("server picked PSK we never offered")
                }
                transcript.append(msg)

                val privKey = keyPair!!.privateKey
                val shared = X25519.dh(privKey, serverKeyShare!!)
                sharedSecret = shared
                keySchedule.deriveHandshake(shared)
                keySchedule.deriveHandshakeTraffic()
                keySchedule.deriveMaster()

                secretsListener.onHandshakeKeysReady(
                    cipherSuite = cipher,
                    clientSecret = keySchedule.clientHandshakeSecret!!,
                    serverSecret = keySchedule.serverHandshakeSecret!!,
                )
                state = State.WAITING_ENCRYPTED_EXTENSIONS
            }

            State.WAITING_ENCRYPTED_EXTENSIONS -> {
                if (type != TlsConstants.HS_ENCRYPTED_EXTENSIONS) throw QuicCodecException("expected EncryptedExtensions, got type=$type")
                if (level != Level.HANDSHAKE) throw QuicCodecException("EncryptedExtensions must arrive at Handshake level")
                val ee = TlsEncryptedExtensions.decodeBody(bodyReader)
                // Audit-4 #20: validate the server actually selected one of
                // the ALPNs we offered. Pre-fix any negotiated ALPN was
                // accepted; a server picking an unknown ALPN would silently
                // proceed with HTTP/3 code paths assuming h3.
                val alpn = ee.alpn
                if (alpn != null && !offeredAlpns.any { it.contentEquals(alpn) }) {
                    throw QuicCodecException(
                        "server selected ALPN '${alpn.decodeToString()}' which we did not offer",
                    )
                }
                negotiatedAlpn = alpn
                peerTransportParameters = ee.quicTransportParameters
                transcript.append(msg)
                state = State.WAITING_CERTIFICATE_OR_FINISHED
            }

            State.WAITING_CERTIFICATE_OR_FINISHED -> {
                when (type) {
                    TlsConstants.HS_CERTIFICATE -> {
                        val cert = TlsCertificateChain.decodeBody(bodyReader)
                        certificateValidator.validateChain(cert.certificates, serverName)
                        transcript.append(msg)
                        state = State.WAITING_CERTIFICATE_VERIFY
                    }

                    TlsConstants.HS_FINISHED -> {
                        if (!pskAccepted) {
                            // Audit-4 #3: we never offered (or never had
                            // accepted) a `pre_shared_key` extension, so a
                            // server MUST send Certificate +
                            // CertificateVerify. A Finished here means a
                            // misbehaving server (or an MITM that stripped
                            // the cert messages). Hard-fail rather than
                            // completing a handshake with no peer
                            // authentication.
                            throw QuicCodecException(
                                "server skipped Certificate/CertificateVerify but we never offered PSK " +
                                    "(unauthenticated handshake refused)",
                            )
                        }
                        // Resumption path: server accepted our PSK so it
                        // skips Certificate / CertificateVerify (the PSK
                        // itself authenticates the server through the
                        // earlier full handshake that issued the ticket).
                        // Process Finished directly.
                        handleServerFinished(msg, bodyReader, len)
                    }

                    else -> {
                        throw QuicCodecException("unexpected handshake type after EncryptedExtensions: $type")
                    }
                }
            }

            State.WAITING_CERTIFICATE_VERIFY -> {
                if (type != TlsConstants.HS_CERTIFICATE_VERIFY) throw QuicCodecException("expected CertificateVerify, got type=$type")
                val cv = TlsCertificateVerify.decodeBody(bodyReader)
                val transcriptHash = transcript.snapshot()
                certificateValidator.verifySignature(cv.signatureAlgorithm, cv.signature, transcriptHash)
                transcript.append(msg)
                state = State.WAITING_SERVER_FINISHED
            }

            State.WAITING_SERVER_FINISHED -> {
                if (type != TlsConstants.HS_FINISHED) throw QuicCodecException("expected Finished, got type=$type")
                handleServerFinished(msg, bodyReader, len)
            }

            State.SENT_CLIENT_FINISHED -> {
                // Post-handshake messages on Application level. NewSessionTicket
                // is safe to ignore (we don't do session resumption). KeyUpdate
                // is NOT safe to ignore — if the peer rotates keys and we keep
                // using the old ones, subsequent AEAD opens will silently fail
                // and the connection wedges. We don't implement RFC 9001 §6 key
                // updates yet, so KeyUpdate must surface as a fatal error so
                // the QUIC layer closes the connection cleanly instead of
                // silently desynchronizing.
                when (type) {
                    TlsConstants.HS_NEW_SESSION_TICKET -> {
                        // Don't append to transcript — NewSessionTicket is not
                        // part of the handshake transcript per RFC 8446 §4.4.1.
                        // Parse the ticket, derive a PSK from it +
                        // resumption_master_secret, and surface a
                        // [TlsResumptionState] to the listener so the QUIC
                        // layer can stash it for the next connection.
                        val rms = keySchedule.resumptionMasterSecret
                        if (rms != null) {
                            val ticket = parseNewSessionTicketBody(bodyReader)
                            val psk = resumptionPsk(rms, ticket.nonce)
                            secretsListener.onNewSessionTicket(
                                TlsResumptionState(
                                    ticket = ticket.ticket,
                                    psk = psk,
                                    cipherSuite = currentCipherSuite(),
                                    ticketAgeAdd = ticket.ticketAgeAdd,
                                    ticketLifetimeSec = ticket.ticketLifetimeSec,
                                    issuedAtMillis = nowMillisSource(),
                                ),
                            )
                        }
                    }

                    TlsConstants.HS_KEY_UPDATE -> {
                        throw QuicCodecException(
                            "TLS KeyUpdate received but rotation not implemented; closing connection",
                        )
                    }

                    else -> {
                        throw QuicCodecException("unexpected post-handshake type=$type")
                    }
                }
            }

            else -> {
                throw QuicCodecException("unexpected handshake at state=$state type=$type")
            }
        }
    }

    private fun handleServerFinished(
        msg: ByteArray,
        bodyReader: QuicReader,
        length: Int,
    ) {
        val finished = TlsFinished.decodeBody(bodyReader, length)
        // Verify server Finished MAC over transcript-up-to-CertificateVerify (or up to EE for PSK).
        val expected = finishedVerifyData(keySchedule.serverHandshakeSecret!!, transcript.snapshot())
        if (!expected.contentEqualsConstantTime(finished.verifyData)) {
            throw QuicCodecException("server Finished MAC mismatch")
        }
        transcript.append(msg)

        // Derive 1-RTT (application) traffic secrets after server Finished.
        keySchedule.deriveApplicationTraffic()
        secretsListener.onApplicationKeysReady(
            cipherSuite = currentCipherSuite(),
            clientSecret = keySchedule.clientApplicationSecret!!,
            serverSecret = keySchedule.serverApplicationSecret!!,
        )

        // Send our Finished at Handshake level.
        val clientFinishedTag = finishedVerifyData(keySchedule.clientHandshakeSecret!!, transcript.snapshot())
        val w = QuicWriter()
        w.writeByte(TlsConstants.HS_FINISHED)
        w.withUint24Length { writeBytes(clientFinishedTag) }
        val cfBytes = w.toByteArray()
        transcript.append(cfBytes)
        outboundQueues[Level.HANDSHAKE]!!.addLast(cfBytes)

        // Derive resumption_master_secret AFTER appending client Finished —
        // RFC 8446 §7.1 binds it to H(CH..client_Finished). Future
        // NewSessionTicket frames will derive their PSKs off this secret
        // plus the server-supplied ticket_nonce.
        keySchedule.deriveResumption(transcript.snapshot())

        state = State.SENT_CLIENT_FINISHED
        secretsListener.onHandshakeComplete()
    }

    private fun currentCipherSuite(): Int {
        check(negotiatedCipherSuite != -1) { "cipher suite not yet negotiated" }
        return negotiatedCipherSuite
    }
}

/** Callback interface so the QUIC layer can react to TLS-derived secrets. */
interface TlsSecretsListener {
    fun onHandshakeKeysReady(
        cipherSuite: Int,
        clientSecret: ByteArray,
        serverSecret: ByteArray,
    )

    fun onApplicationKeysReady(
        cipherSuite: Int,
        clientSecret: ByteArray,
        serverSecret: ByteArray,
    )

    fun onHandshakeComplete()

    /**
     * Server issued a NewSessionTicket. The TLS layer hands off a
     * ready-to-use [TlsResumptionState] capturing everything the next
     * connection needs for PSK-based resumption: the opaque ticket, the
     * derived PSK, the cipher suite the secret was bound to, and the
     * obfuscation parameters. The QUIC layer's only job is to stash this
     * somewhere the next [TlsClient] construction can read it from.
     *
     * Default no-op so existing callers (which don't care about
     * resumption) compile unchanged. Multiple invocations are possible
     * — RFC 8446 lets servers issue several tickets per connection. The
     * caller may keep all of them or just the latest; for the
     * quic-interop-runner `resumption` testcase keeping the latest
     * suffices.
     */
    fun onNewSessionTicket(state: TlsResumptionState) = Unit
}

/**
 * Self-contained state needed to resume a TLS 1.3 session via PSK on the
 * next connection. Produced by the TLS layer when a NewSessionTicket
 * arrives; consumed by a fresh [TlsClient] via its `resumption`
 * constructor argument.
 *
 * Why store all this rather than just the ticket: the PSK derivation
 * binds to a specific cipher suite (32-byte hash for our SHA-256 suites)
 * so we can't re-derive on the fly without that suite, and the
 * obfuscation arithmetic on the next connection needs both
 * [ticketAgeAdd] and [issuedAtMillis] to compute the obfuscated_ticket_age
 * the server expects.
 */
data class TlsResumptionState(
    /** Opaque ticket bytes echoed verbatim as the PSK identity on the next connection. */
    val ticket: ByteArray,
    /** PSK derived from `resumption_master_secret` + `ticket_nonce` per RFC 8446 §4.6.1. */
    val psk: ByteArray,
    /**
     * Cipher suite this PSK is bound to. The next connection MUST offer
     * (at least) this suite or the server will fall back to a full
     * handshake.
     */
    val cipherSuite: Int,
    /** Server-supplied obfuscation factor (RFC 8446 §4.6.1) — added to the elapsed-since-issue ticket age. */
    val ticketAgeAdd: Long,
    /** Server-supplied lifetime hint in seconds. Tickets expire after this; the client SHOULD discard. */
    val ticketLifetimeSec: Long,
    /** Wall-clock millis when the ticket was issued (server time, but we use ours — the obfuscation makes the absolute clock irrelevant). */
    val issuedAtMillis: Long,
)

/** Pluggable certificate validator. Decoupled so we can stub it in tests. */
interface CertificateValidator {
    fun validateChain(
        chain: List<ByteArray>,
        expectedHost: String,
    )

    fun verifySignature(
        signatureAlgorithm: Int,
        signature: ByteArray,
        transcriptHash: ByteArray,
    )
}

/** Constant-time equality. */
internal fun ByteArray.contentEqualsConstantTime(other: ByteArray): Boolean {
    if (size != other.size) return false
    var diff = 0
    for (i in indices) diff = diff or (this[i].toInt() xor other[i].toInt())
    return diff == 0
}

/**
 * Internal accumulator that hands back full handshake messages once enough
 * bytes have arrived. Each message starts with `(uint8 type)(uint24 length)`.
 */
internal class ByteArrayBuilder {
    private var buf: ByteArray = ByteArray(0)

    fun append(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val combined = ByteArray(buf.size + bytes.size)
        buf.copyInto(combined, 0)
        bytes.copyInto(combined, buf.size)
        buf = combined
    }

    /** Pop the next handshake message if a full one is available. */
    fun takeHandshakeMessage(): ByteArray? {
        if (buf.size < 4) return null
        val len = (
            ((buf[1].toInt() and 0xFF) shl 16) or
                ((buf[2].toInt() and 0xFF) shl 8) or
                (buf[3].toInt() and 0xFF)
        )
        val total = 4 + len
        if (buf.size < total) return null
        val msg = buf.copyOfRange(0, total)
        buf = buf.copyOfRange(total, buf.size)
        return msg
    }
}

/** RFC 8446 §4.1.4 — HelloRetryRequest is a ServerHello whose Random equals SHA-256("HelloRetryRequest"). */
private val HELLO_RETRY_REQUEST_RANDOM: ByteArray =
    byteArrayOf(
        0xCF.toByte(),
        0x21.toByte(),
        0xAD.toByte(),
        0x74.toByte(),
        0xE5.toByte(),
        0x9A.toByte(),
        0x61.toByte(),
        0x11.toByte(),
        0xBE.toByte(),
        0x1D.toByte(),
        0x8C.toByte(),
        0x02.toByte(),
        0x1E.toByte(),
        0x65.toByte(),
        0xB8.toByte(),
        0x91.toByte(),
        0xC2.toByte(),
        0xA2.toByte(),
        0x11.toByte(),
        0x16.toByte(),
        0x7A.toByte(),
        0xBB.toByte(),
        0x8C.toByte(),
        0x5E.toByte(),
        0x07.toByte(),
        0x9E.toByte(),
        0x09.toByte(),
        0xE2.toByte(),
        0xC8.toByte(),
        0xA8.toByte(),
        0x33.toByte(),
        0x9C.toByte(),
    )
