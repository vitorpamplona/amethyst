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
package com.vitorpamplona.quartz.experimental.bitchat.geohash

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.bitchat.geohash.tags.NicknameTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A presence heartbeat for a geohash chat channel (Bitchat "location channel").
 *
 * Wire format (interoperates with Bitchat iOS/Android):
 * - kind [KIND] = 20001 (ephemeral).
 * - content is empty.
 * - `["g", geohash]` (required) is the cell the sender is present in.
 *
 * Bitchat emits presence with only the `g` tag; the optional `["n", nickname]`
 * here is a benign extra tag that lets peers show who is present without waiting
 * for a chat message. Clients count distinct recent pubkeys (over 20000 + 20001)
 * to estimate the number of people in the channel.
 */
@Immutable
class GeohashPresenceEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun geohash() = tags.firstNotNullOfOrNull(GeoHashTag::parse)

    fun nickname() = tags.firstNotNullOfOrNull(NicknameTag::parse)

    companion object {
        const val KIND = 20001

        fun build(
            geohash: String,
            nickname: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GeohashPresenceEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            geohashCell(geohash)
            nickname?.let { nickname(it) }
            initializer()
        }
    }
}
