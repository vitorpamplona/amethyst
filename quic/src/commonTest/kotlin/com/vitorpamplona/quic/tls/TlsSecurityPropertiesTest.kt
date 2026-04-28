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
import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * TLS 1.3 client must REJECT specific server misbehaviors. These are the
 * negative-path security properties that have to be assertions, not
 * comments — without an explicit `assertFailsWith` the compiler doesn't
 * catch a regression when someone removes the validation.
 *
 * Patterns informed by the kwik server-side hostile-peer matrix
 * (`whenParsingClientHelloLeadsToTlsErrorConnectionIsClosed` etc.) and
 * RFC 8446 §4.1.3 / §4.4 mandates.
 */
class TlsSecurityPropertiesTest {
    @Test
    fun server_hello_with_non_empty_session_id_echo_is_rejected() {
        // We send legacy_session_id = empty. RFC 8446 §4.1.3: server MUST
        // echo it. Non-empty echo means the server is in a TLS-1.2-resumption
        // mindset (downgrade signal) — abort.
        val sh = buildServerHello(sessionIdEcho = byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        assertFailsWith<QuicCodecException>("non-empty session_id_echo must be rejected") {
            TlsServerHello.decodeBody(QuicReader(sh))
        }
    }

    @Test
    fun server_hello_with_pre_tls13_legacy_version_is_rejected() {
        // We must reject anything that's not 0x0303 in legacy_version.
        val sh = buildServerHello(legacyVersion = 0x0301)
        assertFailsWith<QuicCodecException>("non-TLS-1.2 legacy_version must be rejected") {
            TlsServerHello.decodeBody(QuicReader(sh))
        }
    }

    @Test
    fun server_hello_with_unsupported_group_in_key_share_is_rejected() {
        // We advertise X25519 only. If the server picks secp256r1 we have no
        // ECDH primitive for it and must abort.
        val sh =
            buildServerHello(
                extensions =
                    listOf(
                        TlsExtension(
                            TlsConstants.EXT_SUPPORTED_VERSIONS,
                            byteArrayOf(0x03, 0x04), // selected_version = TLS 1.3
                        ),
                        TlsExtension(
                            TlsConstants.EXT_KEY_SHARE,
                            buildKeyShare(group = TlsConstants.GROUP_SECP256R1, pubLen = 65),
                        ),
                    ),
            )
        val parsed = TlsServerHello.decodeBody(QuicReader(sh))
        assertFailsWith<QuicCodecException>("unsupported group must be rejected") {
            parsed.serverKeyShareX25519
        }
    }

    @Test
    fun server_hello_missing_supported_versions_extension_is_rejected() {
        val sh =
            buildServerHello(
                extensions =
                    listOf(
                        TlsExtension(
                            TlsConstants.EXT_KEY_SHARE,
                            buildKeyShare(group = TlsConstants.GROUP_X25519, pubLen = 32),
                        ),
                    ),
            )
        val parsed = TlsServerHello.decodeBody(QuicReader(sh))
        assertFailsWith<QuicCodecException>("missing supported_versions must be rejected") {
            parsed.negotiatedVersion
        }
    }

    @Test
    fun server_hello_missing_key_share_is_rejected() {
        val sh =
            buildServerHello(
                extensions =
                    listOf(
                        TlsExtension(
                            TlsConstants.EXT_SUPPORTED_VERSIONS,
                            byteArrayOf(0x03, 0x04),
                        ),
                    ),
            )
        val parsed = TlsServerHello.decodeBody(QuicReader(sh))
        assertFailsWith<QuicCodecException>("missing key_share must be rejected") {
            parsed.serverKeyShareX25519
        }
    }

    private fun buildServerHello(
        legacyVersion: Int = TlsConstants.LEGACY_VERSION_TLS_1_2,
        sessionIdEcho: ByteArray = ByteArray(0),
        cipherSuite: Int = TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256,
        extensions: List<TlsExtension> =
            listOf(
                TlsExtension(TlsConstants.EXT_SUPPORTED_VERSIONS, byteArrayOf(0x03, 0x04)),
                TlsExtension(TlsConstants.EXT_KEY_SHARE, buildKeyShare(TlsConstants.GROUP_X25519, 32)),
            ),
    ): ByteArray {
        val w = QuicWriter()
        w.writeUint16(legacyVersion)
        w.writeBytes(ByteArray(32))
        w.writeTlsOpaque1(sessionIdEcho)
        w.writeUint16(cipherSuite)
        w.writeByte(0) // null compression
        w.withUint16Length {
            for (e in extensions) e.encode(this)
        }
        return w.toByteArray()
    }

    private fun buildKeyShare(
        group: Int,
        pubLen: Int,
    ): ByteArray {
        val w = QuicWriter()
        w.writeUint16(group)
        w.writeTlsOpaque2(ByteArray(pubLen))
        return w.toByteArray()
    }
}
