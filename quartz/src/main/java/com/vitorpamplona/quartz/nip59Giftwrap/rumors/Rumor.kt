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
package com.vitorpamplona.quartz.nip59Giftwrap.rumors

import com.fasterxml.jackson.annotation.JsonProperty
import com.vitorpamplona.quartz.EventFactory
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.jackson.JsonMapper
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent

class Rumor(
    val id: HexKey?,
    @field:JsonProperty("pubkey") val pubKey: HexKey?,
    @field:JsonProperty("created_at") val createdAt: Long?,
    val kind: Int?,
    val tags: Array<Array<String>>?,
    val content: String?,
) {
    fun mergeWith(event: SealedRumorEvent): Event {
        val newPubKey = event.pubKey // forces to be the pubkey of the seal to make sure impersonators don't impersonate
        val newCreatedAt = if (createdAt != null && createdAt > 1000) createdAt else event.createdAt
        val newKind = kind ?: -1
        val newTags = (tags ?: emptyArray()).plus(event.tags)
        val newContent = content ?: ""
        val newID = id?.ifBlank { null } ?: EventHasher.hashId(newPubKey, newCreatedAt, newKind, newTags, newContent)
        val sig = ""

        return EventFactory.create(newID, newPubKey, newCreatedAt, newKind, newTags, newContent, sig)
    }

    companion object {
        fun fromJson(json: String): Rumor = JsonMapper.mapper.readValue(json, Rumor::class.java)

        fun toJson(event: Rumor): String = JsonMapper.mapper.writeValueAsString(event)

        fun create(event: Event): Rumor = Rumor(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content)
    }
}
