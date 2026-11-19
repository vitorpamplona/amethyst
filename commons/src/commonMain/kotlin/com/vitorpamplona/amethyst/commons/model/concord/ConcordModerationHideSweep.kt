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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Applies a session's held moderation hides (CORD-03 §3) to the notes already in a store.
 *
 * A hide only ever reaches the Chat rumors of its own community's channel: the note must sit in that
 * channel's [ConcordChannel] (or be carried by a wrap the session holds on it) and be a Chat kind. A
 * kind 5 naming a DM, a public note or another community's message changes nothing, however the
 * hider ranks. Whether the hide is honored is the session's call ([ConcordCommunitySession.isHiddenByModeration]),
 * against the current fold, so a hide whose Grant folds later applies on the next sweep.
 */
object ConcordModerationHideSweep {
    /**
     * Removes from [cache] every note [session]'s held hides cover now, on [onlyChannel] or on
     * every channel. Returns the ids removed.
     */
    fun sweep(
        cache: EventCache,
        session: ConcordCommunitySession,
        onlyChannel: HexKey? = null,
    ): List<HexKey> {
        val removed = ArrayList<HexKey>()
        val communityId = session.entry.id
        for ((channelIdHex, targets) in session.moderationHideTargets()) {
            if (onlyChannel != null && channelIdHex != onlyChannel) continue
            val channelId = ConcordChannelId(communityId, channelIdHex)
            for (target in targets) {
                val note = cache.getNoteIfExists(target) ?: continue
                if (!isRumorOf(note, channelId, session)) continue
                val author = note.event?.pubKey ?: continue
                if (!session.isHiddenByModeration(channelIdHex, target, author)) continue
                note.detachFromChildren()
                cache.pruner.unlinkAndRemove(note)
                removed.add(target)
            }
        }
        return removed
    }

    /** True when [note] is a Chat rumor of [channelId]: attached to its channel, or carried by a wrap the session holds there. */
    fun isRumorOf(
        note: Note,
        channelId: ConcordChannelId,
        session: ConcordCommunitySession,
    ): Boolean {
        val event = note.event ?: return false
        if (!ChannelChat.isChatKind(event.kind)) return false
        if (note.inGatherers?.any { it is ConcordChannel && it.channelId == channelId } == true) return true
        return session.holdsRumorOn(channelId.channelId, note.idHex)
    }
}
