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
package com.vitorpamplona.quartz.nip28PublicChat

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChannelMetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    IsInPublicChatChannel {
    override fun channel() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun channelInfo() =
        try {
            EventMapper.mapper.readValue(content, ChannelCreateEvent.ChannelData::class.java)
        } catch (e: Exception) {
            Log.e("ChannelMetadataEvent", "Can't parse channel info $content", e)
            ChannelCreateEvent.ChannelData(null, null, null)
        }

    companion object {
        const val KIND = 41
        const val ALT = "This is a public chat definition update"

        fun create(
            name: String?,
            about: String?,
            picture: String?,
            originalChannelIdHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelMetadataEvent) -> Unit,
        ) {
            create(
                ChannelCreateEvent.ChannelData(
                    name,
                    about,
                    picture,
                ),
                originalChannelIdHex,
                signer,
                createdAt,
                onReady,
            )
        }

        fun create(
            newChannelInfo: ChannelCreateEvent.ChannelData?,
            originalChannelIdHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelMetadataEvent) -> Unit,
        ) {
            val content =
                if (newChannelInfo != null) {
                    EventMapper.mapper.writeValueAsString(newChannelInfo)
                } else {
                    ""
                }

            val tags =
                listOf(
                    arrayOf("e", originalChannelIdHex, "", "root"),
                    arrayOf("alt", "Public chat update to ${newChannelInfo?.name}"),
                )
            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }
}
