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

import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quic.QuicWriter

/**
 * Build a TLS 1.3 ClientHello + handshake header carrying the QUIC-required
 * extensions. Output is the full handshake message (1-byte type, 3-byte length,
 * then the body) ready to feed into a CRYPTO frame.
 *
 * Per RFC 8446 §4.1.2 + RFC 9001 §8 the message layout is:
 *
 *   uint8         msg_type = 0x01 (client_hello)
 *   uint24        length
 *   uint16        legacy_version = 0x0303 ("TLS 1.2")
 *   opaque        random[32]
 *   uint8         legacy_session_id_len = 0 (TLS 1.3 over QUIC; no resumption)
 *   uint16        cipher_suites_len
 *   uint16        cipher_suites[]
 *   uint8         legacy_compression_methods_len = 1
 *   uint8         legacy_compression_methods[] = { 0 }  // null
 *   uint16        extensions_len
 *   Extension     extensions[]
 */
class TlsClientHello(
    val random: ByteArray = RandomInstance.bytes(32),
    val cipherSuites: IntArray = intArrayOf(TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256, TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256),
    val extensions: List<TlsExtension>,
) {
    init {
        require(random.size == 32) { "TLS random must be 32 bytes" }
    }

    /** Encode just the body (no msg_type/length wrapper). */
    fun encodeBody(out: QuicWriter) {
        out.writeUint16(TlsConstants.LEGACY_VERSION_TLS_1_2)
        out.writeBytes(random)
        out.writeByte(0) // legacy_session_id_len = 0
        out.withUint16Length {
            for (c in cipherSuites) writeUint16(c)
        }
        out.writeByte(1) // legacy_compression_methods_len
        out.writeByte(0) // null compression
        out.withUint16Length {
            for (e in extensions) e.encode(this)
        }
    }

    /** Encode the full handshake message: 1-byte type + 3-byte length + body. */
    fun encode(): ByteArray {
        val w = QuicWriter()
        w.writeByte(TlsConstants.HS_CLIENT_HELLO)
        w.withUint24Length { encodeBody(this) }
        return w.toByteArray()
    }
}

/**
 * Convenience builder that wires up the standard QUIC + WebTransport ClientHello:
 *   - SNI
 *   - supported_versions = [ TLS 1.3 ]
 *   - supported_groups   = [ X25519 ]
 *   - signature_algorithms covering ECDSA / RSA-PSS / Ed25519
 *   - key_share with the caller's X25519 public
 *   - psk_key_exchange_modes = [ psk_dhe_ke ]
 *   - ALPN  = [ h3 ]
 *   - quic_transport_parameters = (caller-supplied opaque bytes)
 */
fun buildQuicClientHello(
    serverName: String,
    x25519PublicKey: ByteArray,
    quicTransportParams: ByteArray,
    additionalAlpn: List<ByteArray> = emptyList(),
    random: ByteArray = RandomInstance.bytes(32),
): TlsClientHello {
    val alpn = mutableListOf<ByteArray>()
    alpn += TlsConstants.ALPN_H3
    alpn += additionalAlpn
    val exts =
        listOf(
            TlsExtension(TlsConstants.EXT_SERVER_NAME, encodeServerNameExtension(serverName)),
            TlsExtension(TlsConstants.EXT_SUPPORTED_VERSIONS, encodeSupportedVersionsExtensionClient()),
            TlsExtension(TlsConstants.EXT_SUPPORTED_GROUPS, encodeSupportedGroupsX25519()),
            TlsExtension(TlsConstants.EXT_SIGNATURE_ALGORITHMS, encodeSignatureAlgorithms()),
            TlsExtension(TlsConstants.EXT_KEY_SHARE, encodeKeyShareClientX25519(x25519PublicKey)),
            TlsExtension(TlsConstants.EXT_PSK_KEY_EXCHANGE_MODES, encodePskKeyExchangeModesDhe()),
            TlsExtension(TlsConstants.EXT_ALPN, encodeAlpn(alpn)),
            TlsExtension(TlsConstants.EXT_QUIC_TRANSPORT_PARAMETERS, quicTransportParams),
        )
    return TlsClientHello(random = random, extensions = exts)
}
