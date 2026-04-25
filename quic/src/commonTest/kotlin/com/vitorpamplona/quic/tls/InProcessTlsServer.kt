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
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter

/**
 * Minimal TLS 1.3 **server** that uses the same primitives as our client to
 * drive an end-to-end handshake without touching the network.
 *
 * Used purely for in-process round-trip tests of [TlsClient] — it does not
 * implement certificate-based authentication (it skips Certificate +
 * CertificateVerify) and assumes a one-shot handshake. The transcript
 * therefore goes:
 *
 *   ClientHello → ServerHello → EncryptedExtensions → Finished (server) →
 *   Finished (client)
 *
 * This is **not** a valid TLS 1.3 mode for real interop (a non-PSK handshake
 * MUST send Certificate + CertificateVerify), but for the purposes of
 * exercising [TlsClient]'s key derivation + Finished verification it covers
 * the path we care about until cert chain validation lands in Phase L.
 */
class InProcessTlsServer(
    private val keyPair: X25519KeyPair = X25519.generateKeyPair(),
    private val random: ByteArray = RandomInstance.bytes(32),
    private val transportParameters: ByteArray = ByteArray(0),
    private val alpn: ByteArray = TlsConstants.ALPN_H3,
) {
    private val transcript = TlsTranscriptHash()
    private val keySchedule = TlsKeySchedule(transcript)

    /** Handshake bytes the server has produced and not yet handed back. */
    private val outboundInitial = ArrayDeque<ByteArray>()
    private val outboundHandshake = ArrayDeque<ByteArray>()

    var clientHandshakeSecret: ByteArray? = null
        private set
    var serverHandshakeSecret: ByteArray? = null
        private set
    var clientApplicationSecret: ByteArray? = null
        private set
    var serverApplicationSecret: ByteArray? = null
        private set
    var negotiatedCipherSuite: Int = -1
        private set

    fun pollOutboundInitial(): ByteArray? = outboundInitial.removeFirstOrNull()

    fun pollOutboundHandshake(): ByteArray? = outboundHandshake.removeFirstOrNull()

    /** Process a ClientHello (Initial level). Produces ServerHello + EE + Finished. */
    fun receiveClientHello(clientHello: ByteArray) {
        // 1. Append CH to transcript
        transcript.append(clientHello)

        // 2. Parse CH to get the client's X25519 key share
        val r = QuicReader(clientHello)
        require(r.readByte() == TlsConstants.HS_CLIENT_HELLO)
        r.readUint24() // body length
        require(r.readUint16() == TlsConstants.LEGACY_VERSION_TLS_1_2)
        r.readBytes(32) // random
        r.readTlsOpaque1() // legacy_session_id
        val cipherSuiteCount = r.readUint16() / 2
        val pickedSuite =
            (0 until cipherSuiteCount).map { r.readUint16() }.firstOrNull {
                it == TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256 ||
                    it == TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256
            } ?: error("no acceptable cipher suite in ClientHello")
        negotiatedCipherSuite = pickedSuite
        r.readByte() // legacy_compression_methods_len
        r.readByte() // null compression
        val exts = TlsExtension.decodeList(r)
        val keyShareExt = exts.first { it.type == TlsConstants.EXT_KEY_SHARE }
        val ksReader = QuicReader(keyShareExt.data)
        val ksOuterLen = ksReader.readUint16()
        val ksEnd = ksReader.position + ksOuterLen
        var clientPub: ByteArray? = null
        while (ksReader.position < ksEnd) {
            val group = ksReader.readUint16()
            val pub = ksReader.readTlsOpaque2()
            if (group == TlsConstants.GROUP_X25519) clientPub = pub
        }
        clientPub ?: error("no X25519 key share in ClientHello")

        // 3. Derive the keys
        keySchedule.deriveEarly()
        val shared = X25519.dh(keyPair.privateKey, clientPub)
        keySchedule.deriveHandshake(shared)

        // 4. Build ServerHello
        val sh = buildServerHello(pickedSuite)
        transcript.append(sh)
        outboundInitial.addLast(sh)

        // 5. Now we have CH..SH transcript → derive handshake traffic
        keySchedule.deriveHandshakeTraffic()
        clientHandshakeSecret = keySchedule.clientHandshakeSecret
        serverHandshakeSecret = keySchedule.serverHandshakeSecret
        keySchedule.deriveMaster()

        // 6. Build EncryptedExtensions
        val ee = buildEncryptedExtensions()
        transcript.append(ee)
        outboundHandshake.addLast(ee)

        // 7. Build server Finished
        val sf = buildFinished(serverHandshakeSecret!!)
        transcript.append(sf)
        outboundHandshake.addLast(sf)

        // 8. Derive application traffic now (after server Finished)
        keySchedule.deriveApplicationTraffic()
        clientApplicationSecret = keySchedule.clientApplicationSecret
        serverApplicationSecret = keySchedule.serverApplicationSecret
    }

    /** Process the client Finished — verifies its MAC. */
    fun receiveClientFinished(clientFinished: ByteArray) {
        val r = QuicReader(clientFinished)
        require(r.readByte() == TlsConstants.HS_FINISHED)
        val len = r.readUint24()
        val tag = r.readBytes(len)
        val expected = finishedVerifyData(clientHandshakeSecret!!, transcript.snapshot())
        check(expected.contentEquals(tag)) { "client Finished MAC mismatch" }
        transcript.append(clientFinished)
    }

    private fun buildServerHello(pickedSuite: Int): ByteArray {
        val w = QuicWriter()
        w.writeByte(TlsConstants.HS_SERVER_HELLO)
        w.withUint24Length {
            writeUint16(TlsConstants.LEGACY_VERSION_TLS_1_2)
            writeBytes(random)
            writeByte(0) // legacy_session_id_len
            writeUint16(pickedSuite)
            writeByte(0) // null compression
            // Extensions: supported_versions (selected), key_share
            withUint16Length {
                // supported_versions = TLS 1.3
                writeUint16(TlsConstants.EXT_SUPPORTED_VERSIONS)
                withUint16Length { writeUint16(TlsConstants.VERSION_TLS_1_3) }
                // key_share: group + key
                writeUint16(TlsConstants.EXT_KEY_SHARE)
                withUint16Length {
                    writeUint16(TlsConstants.GROUP_X25519)
                    writeTlsOpaque2(keyPair.publicKey)
                }
            }
        }
        return w.toByteArray()
    }

    private fun buildEncryptedExtensions(): ByteArray {
        val w = QuicWriter()
        w.writeByte(TlsConstants.HS_ENCRYPTED_EXTENSIONS)
        w.withUint24Length {
            withUint16Length {
                // ALPN with the single negotiated protocol
                writeUint16(TlsConstants.EXT_ALPN)
                withUint16Length {
                    withUint16Length { writeTlsOpaque1(alpn) }
                }
                // QUIC transport parameters
                writeUint16(TlsConstants.EXT_QUIC_TRANSPORT_PARAMETERS)
                writeTlsOpaque2(transportParameters)
            }
        }
        return w.toByteArray()
    }

    private fun buildFinished(secret: ByteArray): ByteArray {
        val tag = finishedVerifyData(secret, transcript.snapshot())
        val w = QuicWriter()
        w.writeByte(TlsConstants.HS_FINISHED)
        w.withUint24Length { writeBytes(tag) }
        return w.toByteArray()
    }
}
