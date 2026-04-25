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

import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter

/**
 * Single TLS 1.3 extension (RFC 8446 §4.2): `extension_type` (2 bytes) plus
 * an opaque `extension_data<0..2^16-1>`. We carry the data raw — encoders for
 * specific extension shapes live in TlsClientHello.
 */
class TlsExtension(
    val type: Int,
    val data: ByteArray,
) {
    fun encode(out: QuicWriter) {
        out.writeUint16(type)
        out.writeTlsOpaque2(data)
    }

    companion object {
        fun decode(r: QuicReader): TlsExtension {
            val type = r.readUint16()
            val data = r.readTlsOpaque2()
            return TlsExtension(type, data)
        }

        /**
         * Decode an Extension list (`extensions<0..2^16-1>`) from [r] until
         * the inner length is consumed.
         */
        fun decodeList(r: QuicReader): List<TlsExtension> {
            val totalLen = r.readUint16()
            val end = r.position + totalLen
            val out = mutableListOf<TlsExtension>()
            while (r.position < end) {
                out += decode(r)
            }
            return out
        }
    }
}

/** Build the `server_name` extension (RFC 6066) with a single host_name entry. */
fun encodeServerNameExtension(hostName: String): ByteArray {
    val name = hostName.encodeToByteArray()
    val w = QuicWriter()
    w.withUint16Length {
        writeByte(TlsConstants.SERVER_NAME_TYPE_HOST_NAME)
        writeTlsOpaque2(name)
    }
    return w.toByteArray()
}

/** Build the `supported_versions` extension carrying just TLS 1.3. */
fun encodeSupportedVersionsExtensionClient(): ByteArray {
    val w = QuicWriter()
    w.withUint8Length {
        writeUint16(TlsConstants.VERSION_TLS_1_3)
    }
    return w.toByteArray()
}

/** Build the `supported_groups` extension with just X25519 listed. */
fun encodeSupportedGroupsX25519(): ByteArray {
    val w = QuicWriter()
    w.withUint16Length {
        writeUint16(TlsConstants.GROUP_X25519)
    }
    return w.toByteArray()
}

/** Build the `signature_algorithms` extension covering ECDSA-P256, RSA-PSS, Ed25519. */
fun encodeSignatureAlgorithms(): ByteArray {
    val w = QuicWriter()
    w.withUint16Length {
        writeUint16(TlsConstants.SIG_ECDSA_SECP256R1_SHA256)
        writeUint16(TlsConstants.SIG_RSA_PSS_RSAE_SHA256)
        writeUint16(TlsConstants.SIG_RSA_PSS_RSAE_SHA384)
        writeUint16(TlsConstants.SIG_RSA_PSS_RSAE_SHA512)
        writeUint16(TlsConstants.SIG_ED25519)
        writeUint16(TlsConstants.SIG_RSA_PKCS1_SHA256)
        writeUint16(TlsConstants.SIG_ECDSA_SECP384R1_SHA384)
    }
    return w.toByteArray()
}

/** Build a single-key-share `key_share` extension carrying the X25519 client public. */
fun encodeKeyShareClientX25519(publicKey: ByteArray): ByteArray {
    val w = QuicWriter()
    w.withUint16Length {
        writeUint16(TlsConstants.GROUP_X25519)
        writeTlsOpaque2(publicKey)
    }
    return w.toByteArray()
}

/** Build the `psk_key_exchange_modes` extension advertising only DHE-KE. */
fun encodePskKeyExchangeModesDhe(): ByteArray {
    val w = QuicWriter()
    w.withUint8Length {
        writeByte(TlsConstants.PSK_MODE_DHE_KE)
    }
    return w.toByteArray()
}

/** Build the `application_layer_protocol_negotiation` extension with a single ALPN entry. */
fun encodeAlpn(protocols: List<ByteArray>): ByteArray {
    val w = QuicWriter()
    w.withUint16Length {
        for (p in protocols) writeTlsOpaque1(p)
    }
    return w.toByteArray()
}
