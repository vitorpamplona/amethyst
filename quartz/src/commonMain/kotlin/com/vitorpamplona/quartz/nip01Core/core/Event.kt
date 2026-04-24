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
package com.vitorpamplona.quartz.nip01Core.core

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.EventKSerializer
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.Serializable

@Immutable
@Serializable(with = EventKSerializer::class)
open class Event(
    val id: HexKey,
    val pubKey: HexKey,
    val createdAt: Long,
    val kind: Kind,
    val tags: TagArray,
    val content: String,
    val sig: HexKey,
) : IEvent,
    OptimizedSerializable {
    /**
     * Set this to true if the .content is encrypted or encoded in a
     * way that it should not be indexed for local search.
     */
    open fun isContentEncoded() = false

    /**
     * Returns true when this event is intended to notify [userHex].
     *
     * The default uses the lowercase `p` tag as the notification channel,
     * which is the convention for most kinds that address a single recipient
     * or a set of mentions (NIP-01 mentions, NIP-04/17 DMs, NIP-25 reactions,
     * NIP-28 chat messages, NIP-34 git issues/patches, NIP-57 zap receipts,
     * NIP-68 pictures, NIP-71 videos, NIP-84 highlights, NIP-AC calls, chess,
     * WakeUps, wiki/long-form/poll mentions).
     *
     * Subclasses override when the NIP defines additional notification tags
     * — e.g. NIP-22 comments use uppercase `P` for the root author in
     * addition to lowercase `p` for the direct-reply author.
     */
    open fun notifies(userHex: HexKey): Boolean {
        for (tag in tags) {
            if (tag.size >= 2 && tag[0] == "p" && tag[1] == userHex) return true
        }
        return false
    }

    fun toJson(): String = OptimizedJsonMapper.toJson(this)

    companion object {
        fun fromJson(json: String): Event = OptimizedJsonMapper.fromJson(json)

        fun fromJsonOrNull(json: String) =
            try {
                fromJson(json)
            } catch (e: Exception) {
                Log.w("Event", "Unable to parse event JSON: $json", e)
                null
            }

        fun build(
            kind: Int,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<Event>.() -> Unit = {},
        ) = eventTemplate(kind, content, createdAt, initializer)
    }
}
