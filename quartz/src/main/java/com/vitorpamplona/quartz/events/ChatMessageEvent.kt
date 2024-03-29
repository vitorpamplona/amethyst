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
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip92MediaAttachments
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

@Immutable
class ChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : WrappedEvent(id, pubKey, createdAt, KIND, tags, content, sig), ChatroomKeyable {
    /** Recipients intended to receive this conversation */
    fun recipientsPubKey() = tags.mapNotNull { if (it.size > 1 && it[0] == "p") it[1] else null }

    fun replyTo() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun talkingWith(oneSideHex: String): Set<HexKey> {
        val listedPubKeys = recipientsPubKey()

        val result =
            if (pubKey == oneSideHex) {
                listedPubKeys.toSet().minus(oneSideHex)
            } else {
                listedPubKeys.plus(pubKey).toSet().minus(oneSideHex)
            }

        if (result.isEmpty()) {
            // talking to myself
            return setOf(pubKey)
        }

        return result
    }

    override fun chatroomKey(toRemove: String): ChatroomKey {
        return ChatroomKey(talkingWith(toRemove).toImmutableSet())
    }

    companion object {
        const val KIND = 14
        const val ALT = "Direct message"

        fun create(
            msg: String,
            to: List<String>? = null,
            subject: String? = null,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            geohash: String? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            nip94attachments: List<FileHeaderEvent>? = null,
            isDraft: Boolean,
            onReady: (ChatMessageEvent) -> Unit,
        ) {
            val tags = mutableListOf<Array<String>>()
            to?.forEach { tags.add(arrayOf("p", it)) }
            replyTos?.forEach { tags.add(arrayOf("e", it, "", "reply")) }
            mentions?.forEach { tags.add(arrayOf("p", it, "", "mention")) }
            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let { tags.add(arrayOf("zapraiser", "$it")) }
            geohash?.let { tags.addAll(geohashMipMap(it)) }
            subject?.let { tags.add(arrayOf("subject", it)) }
            nip94attachments?.let {
                it.forEach {
                    Nip92MediaAttachments().convertFromFileHeader(it)?.let {
                        tags.add(it)
                    }
                }
            }
            // tags.add(arrayOf("alt", alt))

            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            } else {
                signer.sign(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            }
        }
    }
}

interface ChatroomKeyable {
    fun chatroomKey(toRemove: HexKey): ChatroomKey
}

@Stable
data class ChatroomKey(
    val users: ImmutableSet<HexKey>,
)
