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

/**
 * Parsed TLS 1.3 ServerHello. Only the fields we actually use to drive the
 * key schedule are surfaced.
 */
data class TlsServerHello(
    val random: ByteArray,
    val sessionId: ByteArray,
    val cipherSuite: Int,
    val extensions: List<TlsExtension>,
) {
    /** The negotiated protocol version. Must be 0x0304 (TLS 1.3) per RFC 8446. */
    val negotiatedVersion: Int
        get() {
            val ext =
                extensions.firstOrNull { it.type == TlsConstants.EXT_SUPPORTED_VERSIONS }
                    ?: throw QuicCodecException("server hello missing supported_versions extension")
            // server hello carries selected_version (uint16)
            val r = QuicReader(ext.data)
            return r.readUint16()
        }

    /** The peer's X25519 public key, extracted from key_share. */
    val serverKeyShareX25519: ByteArray
        get() {
            val ext =
                extensions.firstOrNull { it.type == TlsConstants.EXT_KEY_SHARE }
                    ?: throw QuicCodecException("server hello missing key_share extension")
            val r = QuicReader(ext.data)
            val group = r.readUint16()
            if (group != TlsConstants.GROUP_X25519) {
                throw QuicCodecException("server selected unsupported group 0x${group.toString(16)}")
            }
            return r.readTlsOpaque2()
        }

    companion object {
        /** Parse the body of a ServerHello after the 4-byte handshake header has been stripped. */
        fun decodeBody(r: QuicReader): TlsServerHello {
            val legacyVersion = r.readUint16()
            if (legacyVersion != TlsConstants.LEGACY_VERSION_TLS_1_2) {
                throw QuicCodecException("ServerHello legacy_version != 0x0303 (got 0x${legacyVersion.toString(16)})")
            }
            val random = r.readBytes(32)
            val sessionId = r.readTlsOpaque1()
            val cipherSuite = r.readUint16()
            r.readByte() // legacy_compression_method = 0
            val extensions = TlsExtension.decodeList(r)
            return TlsServerHello(random, sessionId, cipherSuite, extensions)
        }
    }
}

/** Parsed EncryptedExtensions message (RFC 8446 §4.3.1). */
data class TlsEncryptedExtensions(
    val extensions: List<TlsExtension>,
) {
    val quicTransportParameters: ByteArray?
        get() = extensions.firstOrNull { it.type == TlsConstants.EXT_QUIC_TRANSPORT_PARAMETERS }?.data

    val alpn: ByteArray?
        get() =
            extensions.firstOrNull { it.type == TlsConstants.EXT_ALPN }?.data?.let {
                // ALPN response carries a single protocol_name<1..2^8-1> inside protocols<3..2^16-1>
                val r = QuicReader(it)
                r.skip(2) // outer length
                r.readTlsOpaque1()
            }

    companion object {
        fun decodeBody(r: QuicReader): TlsEncryptedExtensions = TlsEncryptedExtensions(TlsExtension.decodeList(r))
    }
}

/** Parsed Certificate message (RFC 8446 §4.4.2). For nests interop we only need the leaf. */
data class TlsCertificateChain(
    val certificateRequestContext: ByteArray,
    val certificates: List<ByteArray>,
) {
    val leaf: ByteArray
        get() = certificates.firstOrNull() ?: throw QuicCodecException("server sent empty certificate chain")

    companion object {
        fun decodeBody(r: QuicReader): TlsCertificateChain {
            val ctx = r.readTlsOpaque1()
            val listLen = r.readUint24()
            val end = r.position + listLen
            val certs = mutableListOf<ByteArray>()
            while (r.position < end) {
                val cert = r.readTlsOpaque3()
                // skip per-certificate extensions (length-prefixed)
                r.readTlsOpaque2()
                certs += cert
            }
            return TlsCertificateChain(ctx, certs)
        }
    }
}

/** Parsed CertificateVerify message (RFC 8446 §4.4.3). */
data class TlsCertificateVerify(
    val signatureAlgorithm: Int,
    val signature: ByteArray,
) {
    companion object {
        fun decodeBody(r: QuicReader): TlsCertificateVerify {
            val sig = r.readUint16()
            val data = r.readTlsOpaque2()
            return TlsCertificateVerify(sig, data)
        }
    }
}

/** Parsed Finished message — the 32-byte HMAC tag for SHA-256-based suites. */
data class TlsFinished(
    val verifyData: ByteArray,
) {
    companion object {
        fun decodeBody(
            r: QuicReader,
            length: Int,
        ): TlsFinished = TlsFinished(r.readBytes(length))
    }
}
