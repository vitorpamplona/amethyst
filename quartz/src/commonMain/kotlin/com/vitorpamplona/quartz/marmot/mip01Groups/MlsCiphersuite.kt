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
package com.vitorpamplona.quartz.marmot.mip01Groups

/**
 * TLS `SignatureScheme` code points used by the RFC 9420 ciphersuites, as the
 * `0x`-prefixed four-digit lowercase hex that Marmot's proof events carry.
 *
 * These live outside [MlsCiphersuite] rather than in its companion because an
 * enum's entries are initialized BEFORE its companion object, so entry
 * constructor arguments cannot read companion properties. `const val` in a
 * plain object is a compile-time constant and inlines cleanly.
 */
object MlsSignatureScheme {
    const val ED25519 = "0x0807"
    const val ED448 = "0x0808"
    const val ECDSA_SECP256R1_SHA256 = "0x0403"
    const val ECDSA_SECP384R1_SHA384 = "0x0503"
    const val ECDSA_SECP521R1_SHA512 = "0x0603"
}

/**
 * MLS ciphersuites defined in RFC 9420 Section 17.1.
 * Marmot default: MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 (0x0001).
 */
enum class MlsCiphersuite(
    val code: String,
    val hashAlgorithm: String,
    val hashOutputBytes: Int,
    /**
     * The TLS `SignatureScheme` this ciphersuite implies, per RFC 9420 §17.1.
     *
     * It is redundant with [code] by definition, but Marmot signs it explicitly
     * in the account-identity-proof event and validates it there, so the
     * mapping has to be first-class rather than inferred at each call site.
     */
    val signatureScheme: String,
) {
    MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519("0x0001", "SHA-256", 32, MlsSignatureScheme.ED25519),
    MLS_128_DHKEMP256_AES128GCM_SHA256_P256("0x0002", "SHA-256", 32, MlsSignatureScheme.ECDSA_SECP256R1_SHA256),
    MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519("0x0003", "SHA-256", 32, MlsSignatureScheme.ED25519),
    MLS_256_DHKEMX448_AES256GCM_SHA512_ED448("0x0004", "SHA-512", 64, MlsSignatureScheme.ED448),
    MLS_256_DHKEMP521_AES256GCM_SHA512_P521("0x0005", "SHA-512", 64, MlsSignatureScheme.ECDSA_SECP521R1_SHA512),
    MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448("0x0006", "SHA-512", 64, MlsSignatureScheme.ED448),
    MLS_256_DHKEMP384_AES256GCM_SHA384_P384("0x0007", "SHA-384", 48, MlsSignatureScheme.ECDSA_SECP384R1_SHA384),
    ;

    companion object {
        val DEFAULT = MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519

        fun fromCode(code: String): MlsCiphersuite? = entries.find { it.code == code }
    }
}
