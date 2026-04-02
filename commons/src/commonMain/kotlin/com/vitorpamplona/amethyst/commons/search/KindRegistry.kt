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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent

data class ContentPreset(
    val kinds: List<Int> = emptyList(),
    val pseudoKind: String? = null,
) {
    /** Check if this preset is active given current query state. */
    fun isSelected(
        queryKinds: List<Int>,
        queryPseudoKinds: List<String>,
    ): Boolean =
        if (pseudoKind != null) {
            pseudoKind in queryPseudoKinds
        } else {
            kinds.isNotEmpty() && queryKinds.containsAll(kinds)
        }
}

object KindRegistry {
    val aliases: Map<String, List<Int>> =
        mapOf(
            "note" to listOf(TextNoteEvent.KIND),
            "article" to listOf(LongTextNoteEvent.KIND),
            "repost" to listOf(RepostEvent.KIND),
            "profile" to listOf(MetadataEvent.KIND),
            "channel" to listOf(ChannelCreateEvent.KIND, ChannelMetadataEvent.KIND),
            "live" to listOf(LiveActivitiesEvent.KIND, MeetingSpaceEvent.KIND, MeetingRoomEvent.KIND),
            "community" to listOf(CommunityDefinitionEvent.KIND),
            "wiki" to listOf(WikiNoteEvent.KIND),
            "classified" to listOf(ClassifiedsEvent.KIND),
            "highlight" to listOf(HighlightEvent.KIND),
        )

    val pseudoKinds: Set<String> = setOf("reply", "media")

    val presets: Map<String, ContentPreset> =
        mapOf(
            "Notes" to ContentPreset(kinds = listOf(TextNoteEvent.KIND)),
            "Articles" to ContentPreset(kinds = listOf(LongTextNoteEvent.KIND)),
            "Media" to ContentPreset(pseudoKind = "media"),
            "Channels" to ContentPreset(kinds = listOf(ChannelCreateEvent.KIND, ChannelMetadataEvent.KIND)),
            "Communities" to ContentPreset(kinds = listOf(CommunityDefinitionEvent.KIND)),
            "Wiki" to ContentPreset(kinds = listOf(WikiNoteEvent.KIND)),
        )

    fun resolve(alias: String): List<Int>? = aliases[alias.lowercase()]

    fun isPseudoKind(alias: String): Boolean = alias.lowercase() in pseudoKinds

    fun nameFor(kind: Int): String? = aliases.entries.find { kind in it.value }?.key
}
