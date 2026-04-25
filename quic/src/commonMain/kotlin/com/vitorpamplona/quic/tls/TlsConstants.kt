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

/**
 * TLS 1.3 protocol constants from RFC 8446 + RFC 9001 (TLS-over-QUIC binding).
 */
object TlsConstants {
    // ── Record / handshake message types ──────────────────────────────────────
    /** TLS 1.3 over QUIC uses `legacy_version = 0x0303` ("TLS 1.2") on the wire. */
    const val LEGACY_VERSION_TLS_1_2: Int = 0x0303
    const val VERSION_TLS_1_3: Int = 0x0304

    // RFC 8446 §B.3 HandshakeType
    const val HS_CLIENT_HELLO: Int = 1
    const val HS_SERVER_HELLO: Int = 2
    const val HS_NEW_SESSION_TICKET: Int = 4
    const val HS_END_OF_EARLY_DATA: Int = 5
    const val HS_ENCRYPTED_EXTENSIONS: Int = 8
    const val HS_CERTIFICATE: Int = 11
    const val HS_CERTIFICATE_REQUEST: Int = 13
    const val HS_CERTIFICATE_VERIFY: Int = 15
    const val HS_FINISHED: Int = 20
    const val HS_KEY_UPDATE: Int = 24
    const val HS_MESSAGE_HASH: Int = 254

    // ── Cipher suites ─────────────────────────────────────────────────────────
    const val CIPHER_TLS_AES_128_GCM_SHA256: Int = 0x1301
    const val CIPHER_TLS_AES_256_GCM_SHA384: Int = 0x1302
    const val CIPHER_TLS_CHACHA20_POLY1305_SHA256: Int = 0x1303

    // ── Extensions (RFC 8446 §4.2) ────────────────────────────────────────────
    const val EXT_SERVER_NAME: Int = 0
    const val EXT_SUPPORTED_GROUPS: Int = 10
    const val EXT_SIGNATURE_ALGORITHMS: Int = 13
    const val EXT_ALPN: Int = 16
    const val EXT_SUPPORTED_VERSIONS: Int = 43
    const val EXT_PSK_KEY_EXCHANGE_MODES: Int = 45
    const val EXT_KEY_SHARE: Int = 51
    /** RFC 9001 §8.2 — the QUIC TLS extension carrying transport parameters. */
    const val EXT_QUIC_TRANSPORT_PARAMETERS: Int = 0x39

    // ── Named groups (RFC 8446 §4.2.7) ────────────────────────────────────────
    const val GROUP_X25519: Int = 0x001D
    const val GROUP_SECP256R1: Int = 0x0017

    // ── Signature schemes (RFC 8446 §4.2.3) ───────────────────────────────────
    const val SIG_ECDSA_SECP256R1_SHA256: Int = 0x0403
    const val SIG_ECDSA_SECP384R1_SHA384: Int = 0x0503
    const val SIG_RSA_PSS_RSAE_SHA256: Int = 0x0804
    const val SIG_RSA_PSS_RSAE_SHA384: Int = 0x0805
    const val SIG_RSA_PSS_RSAE_SHA512: Int = 0x0806
    const val SIG_ED25519: Int = 0x0807
    const val SIG_RSA_PKCS1_SHA256: Int = 0x0401

    // ── PSK key exchange modes ────────────────────────────────────────────────
    const val PSK_MODE_KE: Int = 0
    const val PSK_MODE_DHE_KE: Int = 1

    // ── Server-name (SNI) types ───────────────────────────────────────────────
    const val SERVER_NAME_TYPE_HOST_NAME: Int = 0

    // ── Alert constants — only the ones we actually look at ───────────────────
    const val ALERT_CLOSE_NOTIFY: Int = 0
    const val ALERT_DECODE_ERROR: Int = 50
    const val ALERT_HANDSHAKE_FAILURE: Int = 40

    // ── ALPN ──────────────────────────────────────────────────────────────────
    val ALPN_H3: ByteArray = "h3".encodeToByteArray()
}
