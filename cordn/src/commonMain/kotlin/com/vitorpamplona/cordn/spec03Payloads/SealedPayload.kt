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
package com.vitorpamplona.cordn.spec03Payloads

import com.vitorpamplona.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The outer seal every cordn message carries, from `spec/03.md` §4.
 *
 * ```
 * base64( nonce[12] || ChaCha20-Poly1305(key, nonce, mlsMessageBytes, aad = "") )
 * key = MLS-Exporter("cordn", "group-payload", 32)
 * ```
 *
 * This is why a coordinator sees nothing. MLS has already encrypted the
 * message; this second layer means the coordinator cannot even tell a Commit
 * from a chat line, so it cannot derive group state from traffic it is
 * forbidden to parse.
 *
 * ## Byte-identical to Marmot's kind-445 seal, and deliberately separate code
 *
 * Same AEAD, same 12-byte random nonce, same empty AAD, same
 * `base64(nonce‖ct‖tag)` layout as
 * `marmot/mip03GroupMessages/GroupEventEncryption`. Only the exporter label
 * differs, which is the entire reason the two protocols never read each
 * other's traffic. Importing the Marmot one here would save a few lines and
 * put one protocol's seal on another's wire, where a later Marmot-side change
 * would silently break cordn interop.
 */
object SealedPayload {
    /** 12-byte nonce plus a 16-byte tag, before any ciphertext (`spec/03.md` §4). */
    const val MIN_SIZE = 28

    private const val NONCE_SIZE = 12
    private val EMPTY_AAD = ByteArray(0)

    /**
     * Seals [mlsMessageBytes] under [key].
     *
     * The nonce is fresh per payload and MUST NOT repeat under one key.
     * `spec/03.md` §4 makes that a requirement rather than advice: ChaCha20
     * is a stream cipher, so a repeat under the same epoch key leaks the XOR
     * of two plaintexts to anyone holding both — including the coordinator,
     * which holds every payload by construction.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun seal(
        mlsMessageBytes: ByteArray,
        key: ByteArray,
    ): String {
        val nonce = RandomInstance.bytes(NONCE_SIZE)
        return Base64.encode(nonce + ChaCha20Poly1305.encrypt(mlsMessageBytes, EMPTY_AAD, nonce, key))
    }

    /** Opens a sealed payload, or throws if it is malformed or fails AEAD verification. */
    @OptIn(ExperimentalEncodingApi::class)
    fun open(
        sealedBase64: String,
        key: ByteArray,
    ): ByteArray {
        val payload =
            try {
                Base64.decode(sealedBase64)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("cordn sealed payload is not valid base64", e)
            }
        require(payload.size >= MIN_SIZE) {
            "cordn sealed payload must be at least $MIN_SIZE bytes, was ${payload.size}"
        }
        return ChaCha20Poly1305.decrypt(
            payload.copyOfRange(NONCE_SIZE, payload.size),
            EMPTY_AAD,
            payload.copyOfRange(0, NONCE_SIZE),
            key,
        )
    }

    /**
     * The epoch key for an APPLICATION message: the sender's current epoch
     * (`spec/03.md` §5).
     */
    fun applicationKey(group: MlsGroup): ByteArray = CordnGroupPolicy.PAYLOAD_EXPORTER.let { group.exporterSecret(it.label, it.context, it.length) }
}
