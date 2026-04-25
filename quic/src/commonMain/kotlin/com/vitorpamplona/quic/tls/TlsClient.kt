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
 * For Phase B we **do not yet validate the certificate chain or the
 * CertificateVerify signature**. That's wired in during Phase C/L when we
 * have a real server to talk to. We DO compute and verify the server
 * Finished MAC.
 */
class TlsClient(
    val serverName: String,
    val transportParameters: ByteArray,
    val secretsListener: TlsSecretsListener,
    /**
     * Certificate validator MUST be supplied for any production / network-facing
     * use. The only acceptable null is in-process tests where there's no real
     * server identity to authenticate (e.g. [TlsRoundTripTest]'s loopback). A
     * null validator here means "no MITM protection" — a misconfigured caller
     * must fail loudly, not silently accept any certificate.
     */
    val certificateValidator: CertificateValidator?,
    /** When non-null, used as the X25519 ephemeral key (for deterministic tests). */
    val fixedKeyPair: X25519KeyPair? = null,
    /** When non-null, used as the ClientHello random (for deterministic tests). */
    val fixedRandom: ByteArray? = null,
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
        )

    private val transcript = TlsTranscriptHash()
    private val keySchedule = TlsKeySchedule(transcript)

    private var keyPair: X25519KeyPair? = null
    private var serverKeyShare: ByteArray? = null
    private var sharedSecret: ByteArray? = null
    private var negotiatedCipherSuite: Int = -1

    /** Begin the handshake by emitting a ClientHello at Initial level. */
    fun start() {
        check(state == State.INITIAL) { "TlsClient already started" }
        keyPair = fixedKeyPair ?: X25519.generateKeyPair()

        keySchedule.deriveEarly()

        val ch =
            buildQuicClientHello(
                serverName = serverName,
                x25519PublicKey = keyPair!!.publicKey,
                quicTransportParams = transportParameters,
                random =
                    fixedRandom ?: com.vitorpamplona.quartz.utils.RandomInstance
                        .bytes(32),
            )

        val chBytes = ch.encode()
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
            handleHandshakeMessage(level, msg)
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
                negotiatedAlpn = ee.alpn
                peerTransportParameters = ee.quicTransportParameters
                transcript.append(msg)
                state = State.WAITING_CERTIFICATE_OR_FINISHED
            }

            State.WAITING_CERTIFICATE_OR_FINISHED -> {
                when (type) {
                    TlsConstants.HS_CERTIFICATE -> {
                        val cert = TlsCertificateChain.decodeBody(bodyReader)
                        certificateValidator?.validateChain(cert.certificates, serverName)
                        transcript.append(msg)
                        state = State.WAITING_CERTIFICATE_VERIFY
                    }

                    TlsConstants.HS_FINISHED -> {
                        // PSK-only handshake skips Certificate/CertificateVerify. We never use PSK,
                        // but the state machine handles the transition for completeness.
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
                certificateValidator?.verifySignature(cv.signatureAlgorithm, cv.signature, transcriptHash)
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
}

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
