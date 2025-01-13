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
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChannelCreateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun channelInfo(): ChannelData =
        try {
            EventMapper.mapper.readValue(content)
        } catch (e: Exception) {
            Log.e("ChannelMetadataEvent", "Can't parse channel info $content", e)
            ChannelData(null, null, null)
        }

    companion object {
        const val KIND = 40

        fun create(
            name: String?,
            about: String?,
            picture: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelCreateEvent) -> Unit,
        ) = create(
            ChannelData(
                name,
                about,
                picture,
            ),
            signer,
            createdAt,
            onReady,
        )

        fun create(
            channelInfo: ChannelData?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelCreateEvent) -> Unit,
        ) {
            val content =
                try {
                    if (channelInfo != null) {
                        EventMapper.mapper.writeValueAsString(channelInfo)
                    } else {
                        ""
                    }
                } catch (t: Throwable) {
                    Log.e("ChannelCreateEvent", "Couldn't parse channel information", t)
                    ""
                }

            val tags =
                arrayOf(
                    arrayOf("alt", "Public chat creation event ${channelInfo?.name?.let { "about $it" }}"),
                )

            signer.sign(createdAt, KIND, tags, content, onReady)
        }
    }

    @Immutable data class ChannelData(
        val name: String?,
        val about: String?,
        val picture: String?,
    )
}
