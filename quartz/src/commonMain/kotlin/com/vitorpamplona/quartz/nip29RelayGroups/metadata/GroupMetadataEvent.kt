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
package com.vitorpamplona.quartz.nip29RelayGroups.metadata

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.core.hasTagName
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip29RelayGroups.tags.ChildTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.ParentTag
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GroupMetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(name(), about()).joinToString("\n")

    fun groupId() = dTag()

    fun name() = tags.firstTagValue("name")

    fun about() = tags.firstTagValue("about")

    fun picture() = tags.firstTagValue("picture")

    /**
     * Topic hashtags (`t` tags) the relay advertises for this group, used by the
     * discovery feed's hashtag filter. NIP-29 doesn't define these; a group only
     * carries them if its host relay copies the requested `t` tags onto the 39000.
     */
    fun hashtags() = tags.hashtags()

    /**
     * Geohashes (`g` tags) the relay advertises for this group, used by the discovery
     * feed's geo filter. Same relay-cooperation caveat as [hashtags].
     */
    fun geohashes() = tags.geohashes()

    /** Only members can read. Presence of the `private` flag; absent = public read. */
    fun isPrivate() = tags.hasTagName("private")

    /** Only members can write. Presence of the `restricted` flag; absent = open write. */
    fun isRestricted() = tags.hasTagName("restricted")

    /** Metadata hidden from non-members. Presence of the `hidden` flag. */
    fun isHidden() = tags.hasTagName("hidden")

    /** Join requests are ignored (invite-only). Presence of the `closed` flag; absent = open join. */
    fun isClosed() = tags.hasTagName("closed")

    /** Group supports LiveKit-powered live audio/video. Presence of the `livekit` flag. */
    fun hasLivekit() = tags.hasTagName("livekit")

    /**
     * Subgroups: the id of this group's parent, or null when this is a root group.
     * At most one `parent` tag is expected (NIP-29 §Subgroups).
     */
    fun parent(): String? = tags.firstNotNullOfOrNull(ParentTag::parse)

    /**
     * Subgroups: the ordered ids of this group's direct children. The position of
     * each `child` tag in the array is the intended display order. Empty when the
     * group has no children (or the relay doesn't advertise them).
     */
    fun children(): List<String> = tags.mapNotNull(ChildTag::parse)

    /** A group with no `parent` tag is a root group in the subgroup tree. */
    fun isRoot(): Boolean = parent() == null

    /**
     * The kinds this group accepts, when constrained, e.g. `["supported_kinds", "9", "11"]`.
     * `null` (tag absent) means all kinds are accepted.
     */
    fun supportedKinds(): List<Int>? =
        tags
            .firstOrNull { it.isNotEmpty() && it[0] == "supported_kinds" }
            ?.drop(1)
            ?.mapNotNull { it.toIntOrNull() }

    fun statusTags(): Set<GroupStatus> {
        val statuses = mutableSetOf<GroupStatus>()
        GroupStatus.entries.forEach { status ->
            if (tags.hasTagName(status.code)) {
                statuses.add(status)
            }
        }
        return statuses
    }

    enum class GroupStatus(
        val code: String,
    ) {
        PRIVATE("private"),
        PUBLIC("public"),
        RESTRICTED("restricted"),
        OPEN("open"),
        CLOSED("closed"),
        HIDDEN("hidden"),
        LIVEKIT("livekit"),
    }

    companion object {
        const val KIND = 39000

        fun build(
            groupId: String,
            name: String? = null,
            about: String? = null,
            picture: String? = null,
            status: Set<GroupStatus> = emptySet(),
            supportedKinds: List<Int>? = null,
            hashtags: List<String> = emptyList(),
            geohashes: List<String> = emptyList(),
            parent: String? = null,
            children: List<String> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GroupMetadataEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(groupId)
            name?.let { add(arrayOf("name", it)) }
            about?.let { add(arrayOf("about", it)) }
            picture?.let { add(arrayOf("picture", it)) }
            status.forEach { add(arrayOf(it.code)) }
            supportedKinds?.let { kinds ->
                add((listOf("supported_kinds") + kinds.map { it.toString() }).toTypedArray())
            }
            addAll(HashtagTag.assemble(hashtags))
            // Mip-map each geohash into every prefix so a coarser followed geohash still matches.
            geohashes.forEach { addAll(GeoHashTag.assemble(it).toList()) }
            parent?.let { add(ParentTag.assemble(it)) }
            addAll(ChildTag.assemble(children))
            initializer()
        }
    }
}
