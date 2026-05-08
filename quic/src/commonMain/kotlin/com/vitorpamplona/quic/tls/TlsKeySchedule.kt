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

import com.vitorpamplona.quartz.utils.mac.MacInstance
import com.vitorpamplona.quic.crypto.EMPTY_SHA256
import com.vitorpamplona.quic.crypto.HKDF
import com.vitorpamplona.quic.crypto.deriveSecret
import com.vitorpamplona.quic.crypto.expandLabel

/**
 * The TLS 1.3 SHA-256 key schedule per RFC 8446 §7.1, plus the QUIC-flavour
 * key/iv/hp expand labels per RFC 9001 §5.
 *
 *   Early Secret           = HKDF-Extract(0, PSK)
 *   Derived "derived"      = Derive-Secret(Early, "derived", "")
 *   Handshake Secret       = HKDF-Extract(Derived, ECDHE)
 *   client_handshake_secret = Derive-Secret(Handshake, "c hs traffic", H(CH..SH))
 *   server_handshake_secret = Derive-Secret(Handshake, "s hs traffic", H(CH..SH))
 *   Derived "derived"      = Derive-Secret(Handshake, "derived", "")
 *   Master Secret          = HKDF-Extract(Derived, 0)
 *   client_app_secret      = Derive-Secret(Master, "c ap traffic", H(CH..server.Finished))
 *   server_app_secret      = Derive-Secret(Master, "s ap traffic", H(CH..server.Finished))
 */
class TlsKeySchedule(
    val transcript: TlsTranscriptHash,
) {
    var earlySecret: ByteArray? = null
        private set
    var handshakeSecret: ByteArray? = null
        private set
    var masterSecret: ByteArray? = null
        private set

    var clientHandshakeSecret: ByteArray? = null
        private set
    var serverHandshakeSecret: ByteArray? = null
        private set
    var clientApplicationSecret: ByteArray? = null
        private set
    var serverApplicationSecret: ByteArray? = null
        private set

    /**
     * RFC 8446 §7.1 — `client_early_traffic_secret`, derived from the
     * early secret + transcript-up-to-and-including-ClientHello. Used to
     * encrypt 0-RTT packets the client sends before ServerHello arrives.
     * Only non-null on resumption-with-early-data connections.
     */
    var clientEarlyTrafficSecret: ByteArray? = null
        private set

    /**
     * RFC 8446 §7.1 resumption master secret. Derived AFTER client Finished
     * is sent (transcript = CH..client.Finished). Used as the input keying
     * material for the [resumptionPsk] computation when the server later
     * issues a NewSessionTicket — that PSK is the value the client offers
     * back via the pre_shared_key extension on the next connection.
     *
     * Derived lazily by [deriveResumption] which the QUIC layer calls right
     * after handing the client Finished bytes off to the writer. The TLS
     * layer caches the secret here so multiple NewSessionTickets (servers
     * routinely send a few) all derive PSKs from the same base.
     */
    var resumptionMasterSecret: ByteArray? = null
        private set

    /** Step 1: derive the Early Secret. PSK is all-zeros for non-resumption. */
    fun deriveEarly() {
        val zeros = ByteArray(32)
        earlySecret = HKDF.extract(zeros, zeros)
    }

    /**
     * Step 1' (resumption path): derive the Early Secret from a PSK rather
     * than zeros. The QUIC layer calls this on resumed connections when
     * the caller passes in a [TlsResumptionState] from a prior connection.
     * For non-resumption [deriveEarly] is the equivalent zero-keyed call.
     *
     * RFC 8446 §7.1: `Early Secret = HKDF-Extract(0, PSK)` — salt is
     * zeros, IKM is the PSK. [HKDF.extract]'s signature is
     * `extract(IKM, salt)` (despite the misleading first-parameter name
     * in the Quartz Hkdf class — the IMPLEMENTATION uses the second
     * arg as the MAC key per RFC 5869), so the call is
     * `extract(psk, zeros)`. The non-PSK [deriveEarly] passes zeros for
     * both so the order didn't matter there.
     */
    fun deriveEarlyFromPsk(psk: ByteArray) {
        val zeros = ByteArray(32)
        earlySecret = HKDF.extract(psk, zeros)
    }

    /**
     * Derive the client early-data traffic secret. RFC 8446 §7.1:
     *
     *   client_early_traffic_secret = Derive-Secret(Early Secret,
     *       "c e traffic", H(ClientHello))
     *
     * Caller passes the post-ClientHello transcript hash explicitly so
     * the schedule doesn't have to track which transcript snapshot is
     * needed (this is the binder-substituted ClientHello, exactly the
     * bytes the server will hash on its side).
     */
    fun deriveEarlyTraffic(transcriptAfterClientHello: ByteArray) {
        val es = earlySecret ?: error("call deriveEarlyFromPsk first")
        clientEarlyTrafficSecret = deriveSecret(es, "c e traffic", transcriptAfterClientHello)
    }

    /** Step 2: derive Handshake Secret using ECDHE shared secret. */
    fun deriveHandshake(ecdheSharedSecret: ByteArray) {
        val early = earlySecret ?: error("call deriveEarly first")
        val derived = deriveSecret(early, "derived", EMPTY_SHA256)
        handshakeSecret = HKDF.extract(ecdheSharedSecret, derived)
    }

    /** Step 3: derive client + server handshake traffic secrets given a transcript ending after ServerHello. */
    fun deriveHandshakeTraffic() {
        val hs = handshakeSecret ?: error("call deriveHandshake first")
        val transcriptHash = transcript.snapshot()
        clientHandshakeSecret = deriveSecret(hs, "c hs traffic", transcriptHash)
        serverHandshakeSecret = deriveSecret(hs, "s hs traffic", transcriptHash)
    }

    /** Step 4: derive the Master Secret. */
    fun deriveMaster() {
        val hs = handshakeSecret ?: error("call deriveHandshake first")
        val derived = deriveSecret(hs, "derived", EMPTY_SHA256)
        masterSecret = HKDF.extract(ByteArray(32), derived)
    }

    /** Step 5: derive client + server application traffic secrets after the server Finished. */
    fun deriveApplicationTraffic() {
        val ms = masterSecret ?: error("call deriveMaster first")
        val transcriptHash = transcript.snapshot()
        clientApplicationSecret = deriveSecret(ms, "c ap traffic", transcriptHash)
        serverApplicationSecret = deriveSecret(ms, "s ap traffic", transcriptHash)
    }

    /**
     * Step 6 (resumption path): derive [resumptionMasterSecret] from the
     * Master Secret + transcript-up-to-client-Finished. RFC 8446 §7.1:
     *
     *     resumption_master_secret = Derive-Secret(Master, "res master",
     *                                              H(CH..client_Finished))
     *
     * Caller passes the post-Finished transcript hash explicitly so the
     * key schedule doesn't have to track which transcript snapshot it
     * needs (the schedule's [transcript] holds the latest, but only if
     * the caller appended client Finished before calling — which the
     * TlsClient does in its [TlsClient.handleServerFinished] just-after-
     * Finished branch).
     */
    fun deriveResumption(transcriptAfterClientFinished: ByteArray) {
        val ms = masterSecret ?: error("call deriveMaster first")
        resumptionMasterSecret = deriveSecret(ms, "res master", transcriptAfterClientFinished)
    }
}

/**
 * RFC 8446 §4.6.1 — derive the per-ticket PSK from the
 * [TlsKeySchedule.resumptionMasterSecret] plus the server-supplied
 * `ticket_nonce`. The same `resumption_master_secret` can issue many
 * PSKs (one per NewSessionTicket); each one is keyed off its nonce.
 *
 *   PSK = HKDF-Expand-Label(resumption_master_secret, "resumption",
 *                           ticket_nonce, Hash.length)
 *
 * Hash.length is 32 for SHA-256 — the only hash the QUIC v1 cipher
 * suites use.
 */
fun resumptionPsk(
    resumptionMasterSecret: ByteArray,
    ticketNonce: ByteArray,
): ByteArray = HKDF.expandLabel(resumptionMasterSecret, "resumption", ticketNonce, 32)

/**
 * RFC 8446 §4.2.11.2 — the binder finished_key, derived from the early
 * secret. The PSK extension's binder is HMAC(finished_key,
 * transcript_hash_up_to_partial_CH).
 *
 *     binder_key   = Derive-Secret(early_secret, "res binder", H(""))
 *     finished_key = HKDF-Expand-Label(binder_key, "finished", "", 32)
 */
fun pskBinderFinishedKey(earlySecret: ByteArray): ByteArray {
    val binderKey = deriveSecret(earlySecret, "res binder", EMPTY_SHA256)
    return expandLabel(binderKey, "finished", 32)
}

/**
 * QUIC packet-protection key/iv/hp triple, derived from a TLS traffic secret
 * via the QUIC-specific labels in RFC 9001 §5.1.
 *
 * For TLS_AES_128_GCM_SHA256 keyLen=16, ivLen=12, hpLen=16.
 * For TLS_CHACHA20_POLY1305_SHA256 keyLen=32, ivLen=12, hpLen=32.
 */
class QuicProtectionKeys(
    val key: ByteArray,
    val iv: ByteArray,
    val hp: ByteArray,
)

fun deriveQuicKeys(
    secret: ByteArray,
    keyLen: Int,
    ivLen: Int,
    hpLen: Int,
): QuicProtectionKeys =
    QuicProtectionKeys(
        key = expandLabel(secret, "quic key", keyLen),
        iv = expandLabel(secret, "quic iv", ivLen),
        hp = expandLabel(secret, "quic hp", hpLen),
    )

/**
 * Compute the Finished MAC per RFC 8446 §4.4.4:
 *
 *   finished_key = HKDF-Expand-Label(base_key, "finished", "", Hash.length)
 *   verify_data  = HMAC(finished_key, transcript_hash)
 */
fun finishedVerifyData(
    baseKey: ByteArray,
    transcriptHash: ByteArray,
): ByteArray {
    val finishedKey = expandLabel(baseKey, "finished", 32)
    val mac = MacInstance("HmacSHA256", finishedKey)
    mac.update(transcriptHash)
    return mac.doFinal()
}
