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
package com.vitorpamplona.quartz.nip28PublicChat.admin

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelData
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelDataNorm
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

@Immutable
class ChannelCreateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    var cache: ChannelDataNorm? = null

    override fun eventHints() = channelInfo().relays?.map { EventIdHint(id, it) } ?: emptyList()

    override fun linkedEventIds() = listOf(id)

    fun isEncrypted() = tags.any { it.has(1) && it[0] == "encrypted" && it[1] == "true" }

    fun channelInfo(): ChannelDataNorm {
        cache?.let { return it }

        val newInfo =
            try {
                if (content.isEmpty() || !content.startsWith("{") || isEncrypted()) {
                    ChannelDataNorm()
                } else {
                    ChannelData.parse(content).normalize()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("ChannelCreateEvent", "Failure to parse ${this.toJson()}")
                ChannelDataNorm()
            }

        cache = newInfo
        return newInfo
    }

    companion object {
        const val KIND = 40

        fun build(
            name: String?,
            about: String?,
            picture: String?,
            relays: List<NormalizedRelayUrl>?,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelCreateEvent>.() -> Unit = {},
        ) = build(ChannelDataNorm(name, about, picture, relays), createdAt, initializer)

        fun build(
            data: ChannelDataNorm,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelCreateEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, data.toContent(), createdAt) {
            alt("Public chat creation event ${data.name?.let { "about $it" }}")
            initializer()
        }
    }
}
