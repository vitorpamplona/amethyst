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
 *   - ALPN  = caller-supplied list (default `[ h3 ]`)
 *   - quic_transport_parameters = (caller-supplied opaque bytes)
 *
 * Caller must pass the FULL list of ALPNs to offer. Earlier shape took an
 * `additionalAlpn` parameter and forced `h3` first — that silently dropped
 * any caller-supplied list (e.g. `[hq-interop, h3]` for the interop
 * runner's hq-interop testcases) because the production call site never
 * threaded the list through. quic-go enforces strictly with TLS alert
 * 120 (`no_application_protocol`, CRYPTO_ERROR 376) when the offered
 * ALPNs don't include one its server is configured for.
 */
fun buildQuicClientHello(
    serverName: String,
    x25519PublicKey: ByteArray,
    quicTransportParams: ByteArray,
    alpns: List<ByteArray> = listOf(TlsConstants.ALPN_H3),
    random: ByteArray = RandomInstance.bytes(32),
    cipherSuites: IntArray =
        intArrayOf(
            TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256,
            TlsConstants.CIPHER_TLS_CHACHA20_POLY1305_SHA256,
        ),
): TlsClientHello {
    val exts =
        buildList {
            // RFC 6066 §3: omit the SNI extension entirely for IP-literal
            // targets — a literal address is not a valid host_name and a
            // strict server rejects the extension, then fails cert selection.
            if (!isIpLiteralHostname(serverName)) {
                add(TlsExtension(TlsConstants.EXT_SERVER_NAME, encodeServerNameExtension(serverName)))
            }
            add(TlsExtension(TlsConstants.EXT_SUPPORTED_VERSIONS, encodeSupportedVersionsExtensionClient()))
            add(TlsExtension(TlsConstants.EXT_SUPPORTED_GROUPS, encodeSupportedGroupsX25519()))
            add(TlsExtension(TlsConstants.EXT_SIGNATURE_ALGORITHMS, encodeSignatureAlgorithms()))
            add(TlsExtension(TlsConstants.EXT_KEY_SHARE, encodeKeyShareClientX25519(x25519PublicKey)))
            add(TlsExtension(TlsConstants.EXT_PSK_KEY_EXCHANGE_MODES, encodePskKeyExchangeModesDhe()))
            add(TlsExtension(TlsConstants.EXT_ALPN, encodeAlpn(alpns)))
            add(TlsExtension(TlsConstants.EXT_QUIC_TRANSPORT_PARAMETERS, quicTransportParams))
        }
    return TlsClientHello(random = random, cipherSuites = cipherSuites, extensions = exts)
}

/**
 * Build a resumption ClientHello (PSK-bound) and return the encoded bytes
 * with the binder field already substituted in.
 *
 * Layout differs from [buildQuicClientHello] in two ways:
 *  - The pre_shared_key extension MUST be the LAST extension per
 *    RFC 8446 §4.2.11. Reorder the list accordingly.
 *  - The binder is HMAC-finished_key over the partial ClientHello
 *    (everything BEFORE the binders field). Two-pass: encode with
 *    binder=zeros, hash partial CH, compute binder, splice it in.
 *
 * The caller provides:
 *  - [resumption]: ticket + obfuscation factor + cipher suite from the
 *    prior connection's NewSessionTicket.
 *  - [binderFinishedKey]: derived from `early_secret` via
 *    [pskBinderFinishedKey] (the QUIC layer's responsibility).
 *  - [transcriptHashOfPartialCh]: a function (bytesUpToBinder) -> ByteArray
 *    that hashes the partial ClientHello using the negotiated suite's
 *    hash. We don't know the hash inside this function (no transcript
 *    state); the caller injects it.
 *
 * Returns the FULL encoded handshake message bytes (handshake header
 * included) ready to feed into a CRYPTO frame. Caller appends to its
 * own transcript.
 */
fun buildResumptionClientHelloBytes(
    serverName: String,
    x25519PublicKey: ByteArray,
    quicTransportParams: ByteArray,
    alpns: List<ByteArray>,
    random: ByteArray,
    cipherSuites: IntArray,
    ticket: ByteArray,
    obfuscatedTicketAge: Long,
    binderFinishedKey: ByteArray,
    transcriptHashOfPartialCh: (ByteArray) -> ByteArray,
    binderHmac: (key: ByteArray, data: ByteArray) -> ByteArray,
    /** When true, include the empty `early_data` extension to opt into 0-RTT. */
    includeEarlyData: Boolean = false,
): ByteArray {
    val exts =
        buildList {
            // RFC 6066 §3: no SNI for IP-literal targets — see buildQuicClientHello.
            if (!isIpLiteralHostname(serverName)) {
                add(TlsExtension(TlsConstants.EXT_SERVER_NAME, encodeServerNameExtension(serverName)))
            }
            add(TlsExtension(TlsConstants.EXT_SUPPORTED_VERSIONS, encodeSupportedVersionsExtensionClient()))
            add(TlsExtension(TlsConstants.EXT_SUPPORTED_GROUPS, encodeSupportedGroupsX25519()))
            add(TlsExtension(TlsConstants.EXT_SIGNATURE_ALGORITHMS, encodeSignatureAlgorithms()))
            add(TlsExtension(TlsConstants.EXT_KEY_SHARE, encodeKeyShareClientX25519(x25519PublicKey)))
            add(TlsExtension(TlsConstants.EXT_PSK_KEY_EXCHANGE_MODES, encodePskKeyExchangeModesDhe()))
            add(TlsExtension(TlsConstants.EXT_ALPN, encodeAlpn(alpns)))
            add(TlsExtension(TlsConstants.EXT_QUIC_TRANSPORT_PARAMETERS, quicTransportParams))
            if (includeEarlyData) {
                // RFC 8446 §4.2.10 — empty body in ClientHello signals
                // "I'm about to send 0-RTT data". Goes BEFORE
                // pre_shared_key (which must be last per §4.2.11).
                add(TlsExtension(TlsConstants.EXT_EARLY_DATA, encodeEarlyDataEmpty()))
            }
            // pre_shared_key MUST be last (RFC 8446 §4.2.11).
            add(
                TlsExtension(
                    TlsConstants.EXT_PRE_SHARED_KEY,
                    encodePreSharedKeyPlaceholder(ticket, obfuscatedTicketAge),
                ),
            )
        }
    val ch = TlsClientHello(random = random, cipherSuites = cipherSuites, extensions = exts)
    val withPlaceholder = ch.encode()
    // PartialClientHello = encoded bytes minus the trailing binders block
    // (uint16 outer length + uint8 inner length + 32 binder bytes for one
    // SHA-256 PSK).
    val partialCh = withPlaceholder.copyOfRange(0, withPlaceholder.size - BINDERS_TRAILING_BYTES)
    val transcriptHash = transcriptHashOfPartialCh(partialCh)
    val binder = binderHmac(binderFinishedKey, transcriptHash)
    require(binder.size == BINDER_BYTES) {
        "binder size ${binder.size} != expected $BINDER_BYTES"
    }
    // Splice the binder bytes into the placeholder zeros: the binder
    // sits at the very end of the encoded message, after the
    // 3-byte trailer (uint16 outer + uint8 inner length).
    binder.copyInto(withPlaceholder, withPlaceholder.size - BINDER_BYTES)
    return withPlaceholder
}
