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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaTag
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstOrNull

/**
 * Which of a cordn group's messages are rows, beyond what cordn-kmp's `CordnAnnotationIndex`
 * already decides (annotations, poll votes, RSVPs, superseded calendar revisions), and what a row
 * that is not plain text says.
 *
 * - **Calls** ([CordnCallAnnouncement], kind 30312): every revision of a call shares its `d`, and
 *   only the newest is a row. cordn-web's `buildCallProjection` exactly: addressed by `d` alone
 *   (any member may close a call), the greatest `created_at` wins, then the greater id. An
 *   announcement that does not parse takes no part and keeps its row, which says it is
 *   unsupported, as cordn-web's does.
 * - **Other addressable kinds** (30000–39999): NIP-01's rule, `(kind, author, d)`, the newest
 *   `created_at` and then the lower id. The calendar kinds are left to cordn-kmp's own fold, which
 *   validates them first, and the RSVP is never a row anyway.
 * - **Kinds this client does not draw**, with nothing in `content`: a row says its NIP-31 `alt`,
 *   or there is no row. An empty bubble tells a reader nothing, and as the room's newest it
 *   became the inbox's preview.
 *
 * All of this is what every cordn client must agree on to show a group the same way, so it
 * belongs in cordn-kmp (`spec02Envelopes`, beside `CordnCalendar`); it lives here until a cordn-kmp
 * release carries it.
 */
object CordnStreamRows {
    /** What a row shows instead of its `content`. */
    @Immutable
    sealed interface Display {
        /** The message's own text (or its edit), as any chat message. */
        data object Regular : Display

        /** The current revision of a call. */
        data class Call(
            val announcement: CordnCallAnnouncement,
        ) : Display

        /** A kind 30312 that is not a valid announcement. */
        data object UnsupportedCall : Display

        /** A kind this client does not draw, empty, that carries a NIP-31 `alt`. */
        data class Alt(
            val text: String,
        ) : Display
    }

    fun isAddressable(kind: Int): Boolean = kind in 30000..39999

    /** Kinds the cordn chat draws as their own rows. Anything else falls back to its content or `alt`. */
    fun isDrawnKind(kind: Int): Boolean =
        kind == CordnMessageKinds.TEXT ||
            kind == CordnMessageKinds.THREAD_REPLY ||
            kind == CordnMessageKinds.POLL ||
            kind == CordnCallAnnouncement.KIND ||
            CordnMessageKinds.isCalendarEvent(kind) ||
            CordnMessageKinds.isSystem(kind)

    /** What the row for [envelope] says, when it is a row at all. */
    fun display(envelope: CordnEnvelope): Display {
        if (envelope.kind == CordnCallAnnouncement.KIND) {
            return CordnCallAnnouncement.parse(envelope.kind, envelope.tags)?.let { Display.Call(it) } ?: Display.UnsupportedCall
        }
        if (isDrawnKind(envelope.kind) || envelope.content.isNotBlank()) return Display.Regular
        return altOf(envelope.tags)?.let { Display.Alt(it) } ?: Display.Regular
    }

    /**
     * Whether [envelope] can never be a row, judged on its own: a kind that is never one, or a
     * kind this client does not draw with no `content`, no `alt` and no attachment.
     */
    fun isNeverARow(envelope: CordnEnvelope): Boolean {
        if (CordnMessageKinds.isStreamHidden(envelope.kind)) return true
        if (isDrawnKind(envelope.kind) || envelope.content.isNotBlank()) return false
        return altOf(envelope.tags) == null && CordnMediaTag.parseAll(envelope.tags).isEmpty()
    }

    /** The ids of [messages] that are not rows: older revisions, and blank unknown kinds. */
    fun hidden(messages: Collection<CordnDeliveredMessage>): Set<HexKey> {
        val hidden = mutableSetOf<HexKey>()
        val latestCall = mutableMapOf<String, CordnEnvelope>()
        val latestAddressable = mutableMapOf<String, CordnEnvelope>()

        messages.forEach { message ->
            val envelope = message.envelope
            when {
                envelope.kind == CordnCallAnnouncement.KIND -> {
                    val callId = CordnCallAnnouncement.parse(envelope.kind, envelope.tags)?.callId ?: return@forEach
                    val current = latestCall[callId]
                    if (current == null) {
                        latestCall[callId] = envelope
                    } else if (isNewerCall(envelope, current)) {
                        hidden += current.id
                        latestCall[callId] = envelope
                    } else {
                        hidden += envelope.id
                    }
                }

                isAddressable(envelope.kind) && !CordnMessageKinds.isCalendarEvent(envelope.kind) && !CordnMessageKinds.isStreamHidden(envelope.kind) -> {
                    val address = "${envelope.kind}:${envelope.pubKey}:${dOf(envelope.tags)}"
                    val current = latestAddressable[address]
                    if (current == null) {
                        latestAddressable[address] = envelope
                    } else if (isNewerAddressable(envelope, current)) {
                        hidden += current.id
                        latestAddressable[address] = envelope
                    } else {
                        hidden += envelope.id
                    }
                }
            }
            if (isNeverARow(envelope)) hidden += envelope.id
        }
        return hidden
    }

    /** cordn-web's `buildCallProjection`: greatest `created_at`, then the greater id. */
    private fun isNewerCall(
        next: CordnEnvelope,
        current: CordnEnvelope,
    ): Boolean = next.createdAt > current.createdAt || (next.createdAt == current.createdAt && next.id > current.id)

    /** NIP-01: the newest `created_at`, then the lower id. */
    private fun isNewerAddressable(
        next: CordnEnvelope,
        current: CordnEnvelope,
    ): Boolean = next.createdAt > current.createdAt || (next.createdAt == current.createdAt && next.id < current.id)

    /** NIP-01: the first `d` tag's value, or the empty string when there is none. */
    private fun dOf(tags: TagArray): String = tags.fastFirstOrNull { it.isNotEmpty() && it[0] == "d" }?.getOrNull(1) ?: ""

    /** The NIP-31 `alt`, when it says anything. */
    fun altOf(tags: TagArray): String? =
        tags
            .fastFirstOrNull { it.size >= 2 && it[0] == "alt" }
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
