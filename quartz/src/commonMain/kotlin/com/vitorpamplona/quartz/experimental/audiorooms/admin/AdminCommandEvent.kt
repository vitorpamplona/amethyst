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
package com.vitorpamplona.quartz.experimental.audiorooms.admin

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Ephemeral host-issued admin command for nostrnests audio rooms
 * (kind 4312). Carries one of [Action] targeting a single
 * [PTag] within the room identified by [ATag].
 *
 * Recipients filter on `kinds=[4312], #a=[room], #p=[me]` and only
 * honour commands whose signer is a participant marked HOST or
 * MODERATOR on the active kind-30312 — relays don't enforce this,
 * the client must.
 *
 * The tag layout matches the action / role-mutation events
 * nostrnests emits today; the action keyword goes in `content`
 * rather than as a separate tag so we can extend it with new
 * verbs without re-versioning the spec.
 */
@Immutable
class AdminCommandEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The room this command applies to, if a single `a`-tag is present. */
    fun room(): String? = tags.firstOrNull { it.firstOrNull() == "a" }?.getOrNull(1)

    /** The pubkey the host is acting on. */
    fun targetPubkey(): HexKey? = tags.firstOrNull { it.firstOrNull() == "p" }?.getOrNull(1)

    /**
     * The verb (e.g. "kick"). Returned uppercase so callers can
     * `when (event.action()) { Action.KICK -> ... }` cleanly; the
     * raw content is preserved on the event so a future verb can
     * be inspected as a string.
     */
    fun action(): Action? = Action.fromCode(content)

    enum class Action(
        val code: String,
    ) {
        KICK("kick"),
        ;

        companion object {
            fun fromCode(code: String): Action? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }

    companion object {
        const val KIND = 4312
        const val ALT = "Audio room admin command"

        /**
         * Build a kick command tagged for [room] and [target]. The
         * content carries the verb so the same kind can grow new
         * actions (mute, ban, …) without a wire schema bump.
         */
        fun kick(
            room: ATag,
            target: HexKey,
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate<AdminCommandEvent>(KIND, Action.KICK.code, createdAt) {
            aTag(room)
            add(PTag(target).toTagArray())
        }
    }
}
