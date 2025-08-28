/**
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
package com.vitorpamplona.quartz.nip55AndroidSigner.api

enum class CommandType(
    val code: String,
) {
    SIGN_EVENT("sign_event"),
    NIP04_ENCRYPT("nip04_encrypt"),
    NIP04_DECRYPT("nip04_decrypt"),
    NIP44_ENCRYPT("nip44_encrypt"),
    NIP44_DECRYPT("nip44_decrypt"),
    GET_PUBLIC_KEY("get_public_key"),
    DECRYPT_ZAP_EVENT("decrypt_zap_event"),
    DERIVE_KEY("derive_key"),
    ;

    companion object {
        fun parse(code: String): CommandType? =
            when (code) {
                SIGN_EVENT.code -> SIGN_EVENT
                NIP04_ENCRYPT.code -> NIP04_ENCRYPT
                NIP04_DECRYPT.code -> NIP04_DECRYPT
                NIP44_ENCRYPT.code -> NIP44_ENCRYPT
                NIP44_DECRYPT.code -> NIP44_DECRYPT
                GET_PUBLIC_KEY.code -> GET_PUBLIC_KEY
                DECRYPT_ZAP_EVENT.code -> DECRYPT_ZAP_EVENT
                DERIVE_KEY.code -> DERIVE_KEY
                else -> null
            }
    }
}
