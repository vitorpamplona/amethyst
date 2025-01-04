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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class EmojiPackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : GeneralListEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    companion object {
        const val KIND = 30030
        const val ALT = "Emoji pack"

        fun create(
            name: String = "",
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (EmojiPackEvent) -> Unit,
        ) {
            val content = ""

            val tags = mutableListOf<Array<String>>()
            tags.add(arrayOf("d", name))
            tags.add(arrayOf("alt", ALT))

            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }
}

@Immutable
data class EmojiUrl(
    val code: String,
    val url: String,
) {
    fun encode(): String = ":$code:$url"

    fun toTagArray() = arrayOf("emoji", code, url)

    companion object {
        fun decode(encodedEmojiSetup: String): EmojiUrl? {
            val emojiParts = encodedEmojiSetup.split(":", limit = 3)
            return if (emojiParts.size > 2) {
                EmojiUrl(emojiParts[1], emojiParts[2])
            } else {
                null
            }
        }

        fun parse(tag: Array<String>): EmojiUrl? =
            if (tag.size > 2 && tag[0] == "emoji") {
                EmojiUrl(tag[1], tag[2])
            } else {
                null
            }
    }
}
