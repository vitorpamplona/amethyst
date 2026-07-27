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
package com.vitorpamplona.quartz.nip29RelayGroups.moderation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class CreateGroupEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun groupId() = tags.groupId()

    fun name() = tags.firstTagValue("name")

    companion object {
        const val KIND = 9007

        /**
         * NIP-29 create-group. The spec carries only the group id here and leaves the metadata to a
         * following kind-9002, which is what plain relay29 expects.
         *
         * Buzz is stricter: `ingest.rs` rejects a 9007 **before storage** with
         * `invalid: channel name is required` unless the create event itself carries a `name` tag,
         * and reads `about` / `visibility` / `channel_type` off the same event. A create without
         * them is dropped outright, so the 9002 that follows addresses a channel that was never
         * made — the group simply never appears.
         *
         * Sending the metadata on both events satisfies both: relay29 ignores the extra tags and
         * takes the 9002, Buzz takes the 9007. [visibility] (`open` / `private`) and [channelType]
         * (`stream` / `forum` / …) are Buzz's vocabulary and are omitted unless given.
         */
        fun build(
            groupId: String,
            name: String? = null,
            about: String? = null,
            visibility: String? = null,
            channelType: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CreateGroupEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            groupId(groupId)
            name?.takeIf { it.isNotBlank() }?.let { add(arrayOf("name", it)) }
            about?.takeIf { it.isNotBlank() }?.let { add(arrayOf("about", it)) }
            visibility?.let { add(arrayOf("visibility", it)) }
            channelType?.let { add(arrayOf("channel_type", it)) }
            initializer()
        }
    }
}
