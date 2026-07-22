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

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One materialized Buzz DM conversation, as confirmed by the relay-signed
 * `DmCreatedEvent` (`kind:41001`). A Buzz DM is a relay-authoritative NIP-29 group
 * whose `h`/group id is a relay-generated UUID; its timeline (kind-9/40002 messages) is
 * read and written through the very same relay-group chat stack as any workspace
 * channel. This record is the missing "this UUID is a DM" fact that stack needs.
 */
data class BuzzDmConversation(
    /** The DM channel id — the relay-generated group UUID (`d` tag of the 41001). */
    val channelId: String,
    /** Every participant (`p` tags of the 41001), including me. */
    val participants: List<HexKey>,
    /** The 41001 `created_at`, used as a tie-break when no messages exist yet. */
    val createdAt: Long,
    /** The workspace relay this DM lives on (the 41001's provenance relay). */
    val relay: NormalizedRelayUrl,
)

/**
 * Process-wide registry of Buzz DM conversations, fed by `LocalCache` as it consumes the
 * relay's confirmations:
 * - [record] on each `DmCreatedEvent` (`kind:41001`, `#p` = me) → the conversation set.
 * - [recordHidden] on each per-viewer `DmVisibilityEvent` (`kind:30622`) → the viewer's
 *   hidden-DM set, so a hidden DM drops out of the list until re-opened.
 *
 * The channel id alone is a sound key (Buzz `h_grammar: uuid-v4-lowercase`), matching
 * `BuzzWorkspaceStates`. Hidden sets are kept per-viewer because the 30622 snapshot is
 * `#p`-gated to its owner and the process can switch accounts. Mutations are lock-guarded
 * because consume runs across several relay reader threads.
 *
 * Like [BuzzRelayDialect] / `BuzzWorkspaceStates`, a singleton (one copy per process).
 */
object BuzzDmRegistry {
    private val lock = KmpLock()
    private val conversationsById = HashMap<String, BuzzDmConversation>()
    private val hiddenByViewer = HashMap<HexKey, Set<String>>()

    private val mutableConversations = MutableStateFlow<Map<String, BuzzDmConversation>>(emptyMap())
    private val mutableHidden = MutableStateFlow<Map<HexKey, Set<String>>>(emptyMap())

    /** All known DM conversations, keyed by channel id; UI collects this. */
    val conversations: StateFlow<Map<String, BuzzDmConversation>> = mutableConversations

    /** Per-viewer hidden-DM channel ids; UI collects this to filter the list. */
    val hidden: StateFlow<Map<HexKey, Set<String>>> = mutableHidden

    /**
     * Records a materialized DM. Keeps the newest confirmation per channel (a re-open can
     * re-emit the 41001 with a later `created_at`), so re-materialization never regresses
     * the participant set.
     */
    fun record(conversation: BuzzDmConversation) =
        lock.withLock {
            val prev = conversationsById[conversation.channelId]
            if (prev == null || conversation.createdAt >= prev.createdAt) {
                conversationsById[conversation.channelId] = conversation
                mutableConversations.value = conversationsById.toMap()
            }
        }

    /** Replaces [viewer]'s hidden-DM set with [channelIds] (the whole 30622 snapshot). */
    fun recordHidden(
        viewer: HexKey,
        channelIds: Set<String>,
    ) = lock.withLock {
        if (hiddenByViewer[viewer] == channelIds) return@withLock
        if (channelIds.isEmpty()) {
            hiddenByViewer.remove(viewer)
        } else {
            hiddenByViewer[viewer] = channelIds
        }
        mutableHidden.value = hiddenByViewer.toMap()
    }

    /** The channel ids [viewer] has hidden (possibly empty). */
    fun hiddenFor(viewer: HexKey): Set<String> = mutableHidden.value[viewer] ?: emptySet()

    /** True when [channelId] is a known DM channel — lets the workspace list exclude DMs. */
    fun isDm(channelId: String): Boolean = channelId in mutableConversations.value

    /**
     * [viewer]'s visible DM conversations (all known minus the viewer's hidden set),
     * newest-DM-first. The UI may re-sort by last message time; this order is the sound
     * fallback when a freshly-opened DM has no messages yet.
     */
    fun visibleFor(viewer: HexKey): List<BuzzDmConversation> {
        val hiddenSet = hiddenFor(viewer)
        return mutableConversations.value.values
            .filter { it.channelId !in hiddenSet }
            .sortedByDescending { it.createdAt }
    }

    /** Test-only: clears all registry state so unit tests don't leak into each other. */
    fun clearForTesting() =
        lock.withLock {
            conversationsById.clear()
            hiddenByViewer.clear()
            mutableConversations.value = emptyMap()
            mutableHidden.value = emptyMap()
        }
}
