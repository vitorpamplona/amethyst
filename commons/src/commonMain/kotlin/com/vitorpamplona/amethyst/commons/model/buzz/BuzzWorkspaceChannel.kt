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
package com.vitorpamplona.amethyst.commons.model.buzz

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A `block/buzz` workspace channel: a [RelayGroupChannel] on a relay that speaks the
 * Buzz dialect of NIP-29 (see [BuzzRelayDialect]).
 *
 * Buzz channels keep the whole NIP-29 machinery — the `h`-scoped [groupId], relay-signed
 * 39000-39003 state, membership — and add channel content the vanilla renderer must not
 * ignore, because skipping it misrepresents the conversation:
 *
 * - **kind 40003 message edits**: an overlay replacing an earlier message's content.
 *   Rendering the original without the edit shows *stale text as current*, so the
 *   overlay is tracked here ([editFor]) rather than left as a loose note.
 * - **kind 40100 canvas**: the channel's collaborative document; only the newest
 *   matters ([canvasNote]).
 * - Stream messages (40002), diffs (40008), system rows (40099), forum posts and agent
 *   job cards flow through the base timeline exactly like kind-9 chat — they need no
 *   extra state here, just kind-aware rendering.
 *
 * One timeline per group: kind-9 chat from vanilla NIP-29 clients and 40002 from Buzz
 * clients land in the SAME channel, so mixed-dialect conversations stay whole.
 */
@Stable
class BuzzWorkspaceChannel(
    groupId: GroupId,
) : RelayGroupChannel(groupId) {
    /**
     * Newest 40003 edit per target message id. Keyed by the edited message's event id;
     * the value is the edit note (whose content is the replacement text).
     */
    private val editsByTarget = LargeCache<HexKey, Note>()

    private val editVersion = MutableStateFlow(0)

    /** Bumps when any edit overlay changes, so rows can re-read [editFor]. */
    val editUpdates: StateFlow<Int> = editVersion

    /** The newest canvas (kind 40100) note for this channel, or null when none seen. */
    var canvasNote: Note? = null
        private set

    /**
     * Records a 40003 edit note. Keeps only the newest edit per target (Buzz's own UI
     * applies last-write-wins by `created_at`). The note may still be loading when the
     * cache attaches it; callers pass the already-loaded note.
     */
    fun addEdit(
        targetId: HexKey,
        editNote: Note,
    ) {
        val current = editsByTarget.get(targetId)
        if (current == null || (editNote.createdAt() ?: 0L) > (current.createdAt() ?: 0L)) {
            editsByTarget.put(targetId, editNote)
            editVersion.value++
        }
    }

    /** The newest edit note overlaying [targetId], or null when the message is unedited. */
    fun editFor(targetId: HexKey): Note? = editsByTarget.get(targetId)

    /** The effective display content for a message: its newest edit's text, or null when unedited. */
    fun effectiveContentFor(targetId: HexKey): String? = editsByTarget.get(targetId)?.event?.content

    fun updateCanvas(note: Note) {
        if ((note.createdAt() ?: 0L) > (canvasNote?.createdAt() ?: 0L)) {
            canvasNote = note
        }
    }

    companion object {
        /** Marker so generic code can ask "is this a Buzz edit?" without importing quartz kinds. */
        const val EDIT_KIND = StreamMessageEditEvent.KIND
    }
}
