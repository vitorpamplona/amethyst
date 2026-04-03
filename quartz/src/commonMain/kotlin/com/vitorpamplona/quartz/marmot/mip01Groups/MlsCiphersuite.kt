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
 * MLS ciphersuites defined in RFC 9420 Section 17.1.
 * Marmot default: MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 (0x0001).
 */
enum class MlsCiphersuite(
    val code: String,
    val hashAlgorithm: String,
    val hashOutputBytes: Int,
) {
    MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519("0x0001", "SHA-256", 32),
    MLS_128_DHKEMP256_AES128GCM_SHA256_P256("0x0002", "SHA-256", 32),
    MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519("0x0003", "SHA-256", 32),
    MLS_256_DHKEMX448_AES256GCM_SHA512_ED448("0x0004", "SHA-512", 64),
    MLS_256_DHKEMP521_AES256GCM_SHA512_P521("0x0005", "SHA-512", 64),
    MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448("0x0006", "SHA-512", 64),
    MLS_256_DHKEMP384_AES256GCM_SHA384_P384("0x0007", "SHA-384", 48),
    ;

    companion object {
        val DEFAULT = MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519

        fun fromCode(code: String): MlsCiphersuite? = entries.find { it.code == code }
    }
}
