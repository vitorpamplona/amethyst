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

import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.QuicWriter
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * RFC 8446 §4.1.4 — HelloRetryRequest is a ServerHello whose `random` equals
 * the SHA-256 of the ASCII string "HelloRetryRequest". We don't implement
 * HRR (we offer X25519 only, the group every modern QUIC server accepts).
 * Before the round-3 fix, an HRR was treated as a regular ServerHello, then
 * X25519 was performed against the cookie/extension bytes (garbage), then
 * AEAD failed downstream with a confusing error. The current code rejects
 * cleanly with a [QuicCodecException].
 */
class HelloRetryRequestTest {
    @Test
    fun hello_retry_request_is_rejected_cleanly() {
        val tls =
            TlsClient(
                serverName = "example.test",
                transportParameters = ByteArray(0),
                secretsListener = NoopSecretsListener,
                certificateValidator = null,
            )
        tls.start()
        // Drain (and discard) ClientHello.
        tls.pollOutbound(TlsClient.Level.INITIAL)

        val hrr = buildHelloRetryRequest()
        assertFailsWith<QuicCodecException> {
            tls.pushHandshakeBytes(TlsClient.Level.INITIAL, hrr)
        }
    }

    /**
     * Build a minimal HRR: handshake type 2 (ServerHello), with the magic
     * SHA-256("HelloRetryRequest") random.
     */
    private fun buildHelloRetryRequest(): ByteArray {
        val helloRetryRequestRandom =
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
        val w = QuicWriter()
        w.writeByte(TlsConstants.HS_SERVER_HELLO)
        w.withUint24Length {
            writeUint16(TlsConstants.LEGACY_VERSION_TLS_1_2)
            writeBytes(helloRetryRequestRandom)
            writeByte(0) // legacy_session_id_echo: empty
            writeUint16(TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256)
            writeByte(0) // null compression
            withUint16Length {
                // supported_versions = TLS 1.3
                writeUint16(TlsConstants.EXT_SUPPORTED_VERSIONS)
                withUint16Length { writeUint16(TlsConstants.VERSION_TLS_1_3) }
                // key_share with empty group (we don't care; HRR check fires first)
                writeUint16(TlsConstants.EXT_KEY_SHARE)
                withUint16Length { writeUint16(TlsConstants.GROUP_X25519) }
            }
        }
        return w.toByteArray()
    }

    private object NoopSecretsListener : TlsSecretsListener {
        override fun onHandshakeKeysReady(
            cipherSuite: Int,
            clientSecret: ByteArray,
            serverSecret: ByteArray,
        ) = Unit

        override fun onApplicationKeysReady(
            cipherSuite: Int,
            clientSecret: ByteArray,
            serverSecret: ByteArray,
        ) = Unit

        override fun onHandshakeComplete() = Unit
    }
}
