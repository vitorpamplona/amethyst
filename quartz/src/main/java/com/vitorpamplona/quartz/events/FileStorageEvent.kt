/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.Base64

@Immutable
class FileStorageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    fun type() = tags.firstOrNull { it.size > 1 && it[0] == TYPE }?.get(1)

    fun decryptKey() = tags.firstOrNull { it.size > 2 && it[0] == DECRYPT }?.let { AESGCM(it[1], it[2]) }

    fun decode(): ByteArray? {
        return try {
            Base64.getDecoder().decode(content)
        } catch (e: Exception) {
            Log.e("FileStorageEvent", "Unable to decode base 64 ${e.message} $content")
            null
        }
    }

    companion object {
        const val KIND = 1064
        const val ALT = "Binary data"

        private const val TYPE = "type"
        private const val DECRYPT = "decrypt"

        fun encode(bytes: ByteArray): String {
            return Base64.getEncoder().encodeToString(bytes)
        }

        fun create(
            mimeType: String,
            data: ByteArray,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FileStorageEvent) -> Unit,
        ) {
            val tags =
                listOfNotNull(
                    arrayOf(TYPE, mimeType),
                    arrayOf("alt", ALT),
                )

            val content = encode(data)
            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }
}
