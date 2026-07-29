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
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class EditMetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    fun groupId() = tags.groupId()

    fun name() = tags.firstTagValue("name")

    fun about() = tags.firstTagValue("about")

    fun hashtags() = tags.hashtags()

    fun geohashes() = tags.geohashes()

    /** Subgroups: the requested parent group id, or null to (re-)root this group. */
    fun parent() = tags.parentGroupId()

    /**
     * Subgroups: the ordered child ids carried on this edit. Per NIP-29 a metadata
     * edit of a parent group MUST re-list all of its children, so the relay rejects
     * a `kind:9002` that drops any of them.
     */
    fun children() = tags.childGroupIds()

    fun previousEvents() = tags.previousEvents()

    override fun indexableContent() = (listOfNotNull(name(), about()) + hashtags()).joinToString("\n")

    companion object {
        const val KIND = 9002

        fun build(
            groupId: String,
            name: String? = null,
            about: String? = null,
            picture: String? = null,
            status: Set<GroupMetadataEvent.GroupStatus> = emptySet(),
            hashtags: List<String> = emptyList(),
            geohashes: List<String> = emptyList(),
            parent: String? = null,
            children: List<String> = emptyList(),
            previousEvents: List<String> = emptyList(),
            // Buzz-only channel-settings tags (a Buzz relay reads these on 9002; a plain NIP-29 relay
            // ignores them). [visibility] is "open"/"private" — Buzz's own vocabulary, which it reads
            // instead of the NIP-29 `private` status flag, so editing visibility on a Buzz channel needs
            // this tag to take. [archived] toggles the channel's archived state ("true"/"false").
            visibility: String? = null,
            archived: Boolean? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EditMetadataEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            groupId(groupId)
            name?.let { add(arrayOf("name", it)) }
            about?.let { add(arrayOf("about", it)) }
            picture?.let { add(arrayOf("picture", it)) }
            status.forEach { add(arrayOf(it.code)) }
            visibility?.let { add(arrayOf("visibility", it)) }
            archived?.let { add(arrayOf("archived", if (it) "true" else "false")) }
            addAll(HashtagTag.assemble(hashtags))
            // Mip-map each geohash into every prefix so a coarser followed geohash still matches.
            geohashes.forEach { addAll(GeoHashTag.assemble(it).toList()) }
            parent?.let { parentGroup(it) }
            childGroups(children)
            previous(previousEvents)
            initializer()
        }
    }
}
