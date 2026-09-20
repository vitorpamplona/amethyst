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
package com.vitorpamplona.amethyst.commons.model.cordnGroups

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NotesGatherer
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnAnnotationIndex
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One cordn group, as a screen sees it.
 *
 * ## Keyed by envelope id, never by cursor
 *
 * `spec/02.md` §7 is explicit that the envelope id is the message identity and
 * the cursor is a delivery primitive. They are easy to confuse because both are
 * unique within a group and both arrive together, and confusing them is not a
 * cosmetic bug: a cursor is coordinator-local, so the same message re-delivered
 * after a re-sync carries a different one. Keying on it would duplicate every
 * message the client ever re-fetches, and dedupe would silently stop working
 * exactly when a group is recovering.
 *
 * ## Ordered by the cursor, not by the sender's clock
 *
 * `created_at` is a claim the sender makes about their own device. Sorting on
 * it is the classic Nostr failure: one wrong clock scatters that member's
 * messages through the room, and a sender who wants to can pin a message to
 * the top of the history permanently by backdating it. The cursor has neither
 * problem — the coordinator assigns it, so it is the one order every member
 * already agrees on, and no sender can choose their own.
 *
 * This does trust the coordinator to order the stream, which it is trusted for
 * anyway: `spec/00.md` §4 makes it the sole authority for a group's stream. It
 * is **not** trusted for content, and is not being trusted for any more here —
 * MLS authenticates what a message says and who sent it, the coordinator says
 * only when it arrived. `created_at` is still shown; it is just not what the
 * list is built from.
 *
 * ## Not a Marmot room with the names changed
 *
 * `MarmotGroupChatroom` holds `Note`s, carries Marmot's GroupContext state
 * (lifecycle, outbound gates, legacy-profile flags, encrypted-media policy) and
 * is fed from `LocalCache`. None of that exists here: cordn has no lifecycle
 * extension, no outbound gate, no profile split, and its messages arrive as
 * envelopes from a coordinator rather than events from a relay. The two look
 * alike only in that both are group chats — see §3.1 of
 * `amethyst/plans/2026-09-19-cordn-ui.md`, which the isolation guards enforce.
 */
@Stable
class CordnGroupChatroom(
    val gid: String,
    val coordinatorPubKey: HexKey,
) : NotesGatherer {
    private val byId = LinkedHashMap<HexKey, CordnDeliveredMessage>()

    private val _messages = MutableStateFlow<List<CordnDeliveredMessage>>(emptyList())

    /** Renderable messages, oldest first, annotations already removed. */
    val messages: StateFlow<List<CordnDeliveredMessage>> = _messages.asStateFlow()

    private val _annotations = MutableStateFlow(CordnAnnotationIndex.of(emptyList()))

    /** Reactions, edits, deletions and pins folded onto their targets. */
    val annotations: StateFlow<CordnAnnotationIndex> = _annotations.asStateFlow()

    val name = MutableStateFlow<String?>(null)
    val description = MutableStateFlow<String?>(null)
    val adminPubkeys = MutableStateFlow<List<HexKey>>(emptyList())

    /** Member account pubkeys, from each leaf's cordn credential (`spec/01.md`). */
    val members = MutableStateFlow<List<HexKey>>(emptyList())
    val epoch = MutableStateFlow(0L)

    /**
     * The newest message, for an inbox row.
     *
     * Annotations are excluded, so a room whose last traffic was a reaction
     * still previews the message that was reacted to rather than a "+" nobody
     * can read.
     */
    private val _newest = MutableStateFlow<CordnDeliveredMessage?>(null)
    val newest: StateFlow<CordnDeliveredMessage?> = _newest.asStateFlow()

    /**
     * Adds [delivered], returning false when this room already had it.
     *
     * Idempotent because a re-sync re-delivers: `catch_up` after a restart
     * walks the stream from a persisted cursor and hands back messages the
     * room may already hold.
     */
    fun add(delivered: CordnDeliveredMessage): Boolean {
        if (byId.containsKey(delivered.envelope.id)) return false
        byId[delivered.envelope.id] = delivered
        recompute()
        return true
    }

    /** Adds several, recomputing once. Returns how many were new. */
    fun addAll(delivered: Collection<CordnDeliveredMessage>): Int {
        val added = delivered.count { byId.putIfAbsentCompat(it.envelope.id, it) }
        if (added > 0) recompute()
        return added
    }

    /** Every message this room holds, annotations included. For the fold. */
    fun all(): List<CordnDeliveredMessage> = byId.values.toList()

    private fun recompute() {
        val everything = byId.values.toList()
        _annotations.value = CordnAnnotationIndex.of(everything)

        val visible =
            everything
                .filterNot { CordnMessageKinds.isAnnotation(it.envelope.kind) }
                .sortedWith(ORDER)

        _messages.value = visible
        _newest.value = visible.lastOrNull()
    }

    private var cachedRow: Note? = null

    /**
     * The `Note` that carries this room into the unified Messages inbox.
     *
     * The inbox is a list of `Note`s and cordn messages are not Notes — they
     * are MLS envelopes, and the whole point of `spec/02.md` is that they never
     * touch a relay. So rather than fabricate a Note per message and keep two
     * copies of every conversation, one Note stands for the whole room: the
     * inbox finds the room through [Note.inGatherers] and reads the name and
     * preview from the room itself, which is the live data.
     *
     * Marmot uses the same trick, but only for a group with no messages yet
     * (`MarmotGroupChatroom.placeholderNote`). For cordn it is the permanent
     * mechanism, because there is no second representation to fall back to —
     * and that is the point: nothing here puts a cordn message into
     * `LocalCache`, where it would become searchable, notifiable, and
     * indistinguishable from an event that was actually published somewhere.
     */
    fun inboxRow(): Note =
        cachedRow ?: Note(rowIdHex(coordinatorPubKey, gid)).also {
            it.addGatherer(this)
            cachedRow = it
        }

    /**
     * Nothing to do: the inbox row is not one of this room's messages, and
     * this room's messages are not Notes. Present because the inbox reaches a
     * room through [NotesGatherer].
     */
    override fun removeNote(note: Note) = Unit

    companion object {
        /** Distinct per (coordinator, gid), because a `gid` alone is not unique (§4). */
        fun rowIdHex(
            coordinatorPubKey: HexKey,
            gid: String,
        ): String = "cordn-$coordinatorPubKey-$gid"

        /**
         * The coordinator's order, with the sender's clock only as a tiebreak.
         *
         * See the class KDoc. The tiebreak should never fire — a coordinator
         * assigns each message its own cursor — but leaving the comparator
         * total costs nothing and keeps two clients from disagreeing if one
         * ever does.
         */
        val ORDER: Comparator<CordnDeliveredMessage> =
            compareBy<CordnDeliveredMessage> { it.cursor }.thenBy { it.envelope.createdAt }
    }
}

/** `putIfAbsent` returning whether it inserted, on every KMP target. */
private fun <K, V> MutableMap<K, V>.putIfAbsentCompat(
    key: K,
    value: V,
): Boolean {
    if (containsKey(key)) return false
    put(key, value)
    return true
}
