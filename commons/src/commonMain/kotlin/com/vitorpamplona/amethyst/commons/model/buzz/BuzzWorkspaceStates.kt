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

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Buzz-only overlay state for one workspace channel: the kind-40003 edit overlay
 * (newest edit per message — rendering the original without it shows stale text as
 * current) and the newest kind-40100 canvas.
 *
 * This lives OUTSIDE the channel object on purpose. Screens, feed filters, and
 * composers capture their `RelayGroupChannel` instance once and hold it for the whole
 * session, so a channel can never be swapped for a "Buzz-typed" replacement when the
 * dialect is discovered mid-session — every live reference would keep rendering the
 * orphaned instance. Keeping the overlay in a registry keyed by the channel id makes
 * dialect discovery a non-event for object identity.
 *
 * All mutations are guarded by a per-state lock: consume runs on multiple relay
 * dispatcher threads, and unsynchronized check-then-act would let an older edit
 * overwrite a newer one.
 */
class BuzzWorkspaceState {
    private val lock = KmpLock()
    private val editsByTarget = LargeCache<HexKey, Note>()
    private val editVersion = MutableStateFlow(0)

    /** Bumps when any overlay entry changes, so rows re-read [editFor]. */
    val editUpdates: StateFlow<Int> = editVersion

    /** The newest canvas (kind 40100) note for this channel, or null when none seen. */
    var canvasNote: Note? = null
        private set

    /** Records a 40003 edit; keeps only the newest per target (last-write-wins by created_at). */
    fun addEdit(
        targetId: HexKey,
        editNote: Note,
    ) = lock.withLock {
        val current = editsByTarget.get(targetId)
        if (current == null || (editNote.createdAt() ?: 0L) > (current.createdAt() ?: 0L)) {
            editsByTarget.put(targetId, editNote)
            editVersion.value = editVersion.value + 1
        }
    }

    /** The newest edit note overlaying [targetId], or null when the message is unedited. */
    fun editFor(targetId: HexKey): Note? = editsByTarget.get(targetId)

    /** The effective display content for a message: its newest edit's text, or null when unedited. */
    fun effectiveContentFor(targetId: HexKey): String? = editsByTarget.get(targetId)?.event?.content

    fun updateCanvas(note: Note) =
        lock.withLock {
            if ((note.createdAt() ?: 0L) > (canvasNote?.createdAt() ?: 0L)) {
                canvasNote = note
            }
        }

    /** Drops overlay entries whose target message id is not in [aliveTargetIds] (memory pruning). */
    fun pruneEdits(aliveTargetIds: Set<HexKey>) =
        lock.withLock {
            val dead = editsByTarget.keys().filter { it !in aliveTargetIds }
            if (dead.isNotEmpty()) {
                dead.forEach { editsByTarget.remove(it) }
                editVersion.value = editVersion.value + 1
            }
        }
}

/**
 * Registry of [BuzzWorkspaceState] keyed by the channel's `h` id. Buzz channel ids are
 * relay-generated UUIDs (NIP-11 `h_grammar: uuid-v4-lowercase`), so the id alone is a
 * sound key — no relay scoping needed, which also lets own-authored edits (consumed
 * with no provenance relay) land in the right state.
 *
 * Like `LocalCache`, a process-wide singleton.
 */
object BuzzWorkspaceStates {
    private val states = LargeCache<String, BuzzWorkspaceState>()

    fun getOrCreate(channelId: String): BuzzWorkspaceState = states.getOrCreate(channelId) { BuzzWorkspaceState() }

    fun getIfExists(channelId: String): BuzzWorkspaceState? = states.get(channelId)

    /** Test-only: clears all state so unit tests don't leak into each other. */
    fun clearForTesting() {
        states.clear()
    }
}
