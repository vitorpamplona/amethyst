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
import com.vitorpamplona.quartz.experimental.bitchat.geohash.tags.TeleportTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A public, ephemeral geohash chat message (Bitchat "location channel").
 *
 * Wire format (interoperates with Bitchat iOS/Android):
 * - kind [KIND] = 20000 (NIP-16 ephemeral range — relays broadcast but need not store).
 * - content is the plain UTF-8 message text (no envelope, no prefix).
 * - `["g", geohash]` (required) is the exact channel cell.
 * - `["n", nickname]` (optional) is the sender's display name.
 * - `["t", "teleport"]` (optional) flags a sender who is not physically in the cell.
 * - `["nonce", …]` (optional) is a NIP-13 proof-of-work commitment; Bitchat mines
 *   8 bits by default and uses it to relax per-sender relay rate limits.
 *
 * Each participant signs with a per-geohash ephemeral key that is unlinkable to
 * their main Nostr identity (see GeohashKeyDerivation), so authorship inside a
 * cell does not reveal who they are elsewhere.
 */
@Immutable
class GeohashChatEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun geohash() = tags.firstNotNullOfOrNull(GeoHashTag::parse)

    fun nickname() = tags.firstNotNullOfOrNull(NicknameTag::parse)

    fun isTeleported() = tags.any(TeleportTag::match)

    companion object {
        const val KIND = 20000

        fun build(
            message: String,
            geohash: String,
            nickname: String? = null,
            teleported: Boolean = false,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GeohashChatEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            geohashCell(geohash)
            nickname?.let { nickname(it) }
            if (teleported) teleport()
            initializer()
        }
    }
}
