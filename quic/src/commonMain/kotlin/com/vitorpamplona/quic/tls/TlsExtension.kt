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
         * the inner length is consumed. The inner reads are bounded against
         * `end` so a malicious server can't claim a small extensions block
         * but encode an extension whose `data` length escapes past the end
         * and into trailing bytes (e.g. compression_method on a ServerHello).
         */
        fun decodeList(r: QuicReader): List<TlsExtension> {
            val totalLen = r.readUint16()
            val end = r.position + totalLen
            if (end > r.limit) {
                throw com.vitorpamplona.quic.QuicCodecException(
                    "TLS extensions length $totalLen exceeds record bounds (have ${r.limit - r.position})",
                )
            }
            val out = mutableListOf<TlsExtension>()
            while (r.position < end) {
                val ext = decode(r)
                if (r.position > end) {
                    throw com.vitorpamplona.quic.QuicCodecException(
                        "TLS extension type=${ext.type} overran extension-list end",
                    )
                }
                out += ext
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

/** Build the `signature_algorithms` extension covering ECDSA-P256/P384, RSA-PSS, Ed25519. */
fun encodeSignatureAlgorithms(): ByteArray {
    val w = QuicWriter()
    w.withUint16Length {
        // RFC 8446 §4.2.3 forbids rsa_pkcs1_* in CertificateVerify (only
        // permitted as a server-side cert chain hint). The JdkCertificateValidator
        // already rejects it, so advertising rsa_pkcs1_sha256 here lied to the
        // peer about what we accept and risked a 0x0401 selection that we'd
        // then reject with an alert. Stick to RSA-PSS / ECDSA / Ed25519.
        writeUint16(TlsConstants.SIG_ECDSA_SECP256R1_SHA256)
        writeUint16(TlsConstants.SIG_RSA_PSS_RSAE_SHA256)
        writeUint16(TlsConstants.SIG_RSA_PSS_RSAE_SHA384)
        writeUint16(TlsConstants.SIG_RSA_PSS_RSAE_SHA512)
        writeUint16(TlsConstants.SIG_ED25519)
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

/**
 * Encode the `pre_shared_key` extension body with a single PSK identity
 * and a placeholder binder (zeros). RFC 8446 §4.2.11. The caller MUST
 * substitute the real binder bytes into the trailing 32 bytes of the
 * encoded ClientHello AFTER hashing the partial CH up to the binder
 * field — see [pskBinderHashRangeEnd] for the offset.
 *
 * One identity, one binder, SHA-256 (binder is 32 bytes). Wire layout:
 *
 *   identities<7..2^16-1>:
 *     opaque identity<1..2^16-1>;        // ticket bytes
 *     uint32 obfuscated_ticket_age;
 *   binders<33..2^16-1>:
 *     opaque PskBinderEntry<32..255>;    // 32 zero bytes for now
 */
fun encodePreSharedKeyPlaceholder(
    ticket: ByteArray,
    obfuscatedTicketAge: Long,
): ByteArray {
    val w = QuicWriter()
    w.withUint16Length {
        // identities list
        writeTlsOpaque2(ticket)
        writeUint32(obfuscatedTicketAge.toInt())
    }
    w.withUint16Length {
        // binders list — one PskBinderEntry of 32 zero bytes
        writeTlsOpaque1(ByteArray(BINDER_BYTES))
    }
    return w.toByteArray()
}

/**
 * Encode the `early_data` extension body. In a ClientHello the body is
 * empty (the extension's mere presence signals "I'm sending 0-RTT
 * data"). In a NewSessionTicket the body is `uint32 max_early_data_size`.
 * In an EncryptedExtensions message the body is empty (server's
 * acceptance signal). We only emit the empty form (ClientHello side).
 *
 * RFC 8446 §4.2.10. Trailing position requirement: it goes WITH the
 * pre_shared_key extension in the ClientHello extensions list — we put
 * it just before pre_shared_key for symmetry with what aioquic / picoquic
 * emit.
 */
fun encodeEarlyDataEmpty(): ByteArray = ByteArray(0)

/** RFC 8446 §4.2.11.2 — SHA-256 binder size for our cipher suites. */
const val BINDER_BYTES: Int = 32

/**
 * Trailing-byte count of the encoded binders field in a one-PSK-identity
 * ClientHello: `[uint16 outer_length][uint8 inner_length][32 binder bytes]`
 * = 35. The transcript hash for binder computation is the ClientHello
 * bytes excluding this trailing region.
 */
const val BINDERS_TRAILING_BYTES: Int = 2 + 1 + BINDER_BYTES
