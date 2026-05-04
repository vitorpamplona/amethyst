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
package com.vitorpamplona.quartz.experimental.nests.admin

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
 * "admin" on the active kind-30312 — relays don't enforce this,
 * the client must.
 *
 * Wire format matches the nostrnests reference (`useAdminCommands.ts`)
 * and EGG-07: the verb lives in an `["action", "kick"]` tag with
 * empty `content`. An older Amethyst build briefly emitted the verb
 * in `content` instead — the reader still accepts that format so
 * any in-flight events deploy cleanly, but emission only writes the
 * tag form.
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
     * The verb (e.g. "kick"). Reads the spec-correct
     * `["action", <verb>]` tag first; falls back to `content` for
     * compatibility with the legacy Amethyst layout.
     */
    fun action(): Action? {
        val tagAction = tags.firstOrNull { it.firstOrNull() == ACTION_TAG }?.getOrNull(1)
        if (tagAction != null) return Action.fromCode(tagAction)
        if (content.isNotBlank()) return Action.fromCode(content)
        return null
    }

    enum class Action(
        val code: String,
    ) {
        KICK("kick"),

        /**
         * Force the target speaker's mic to muted. Honor-based: the
         * target's client receives the command and flips its own
         * presence-side mic-mute. A non-cooperating client could
         * ignore the event and keep broadcasting — same trust model
         * as KICK, which a misbehaving client could also ignore by
         * not disconnecting.
         */
        MUTE("mute"),
        ;

        companion object {
            fun fromCode(code: String): Action? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }

    companion object {
        const val KIND = 4312
        const val ALT = "Audio room admin command"
        const val ACTION_TAG = "action"

        /**
         * Build a kick command tagged for [room] and [target]. The
         * verb travels in the `["action", "kick"]` tag (matching
         * nostrnests / EGG-07) with empty `content`, so future
         * actions (mute, …) extend the same envelope without a
         * wire schema bump.
         */
        fun kick(
            room: ATag,
            target: HexKey,
            createdAt: Long = TimeUtils.now(),
        ) = build(room, target, Action.KICK, createdAt)

        /**
         * Build a force-mute command. Amethyst extension — nostrnests
         * doesn't emit or honor this verb today, so it's effectively
         * a no-op when reaching a nostrnests listener.
         */
        fun forceMute(
            room: ATag,
            target: HexKey,
            createdAt: Long = TimeUtils.now(),
        ) = build(room, target, Action.MUTE, createdAt)

        private fun build(
            room: ATag,
            target: HexKey,
            action: Action,
            createdAt: Long,
        ) = eventTemplate<AdminCommandEvent>(KIND, "", createdAt) {
            aTag(room)
            add(PTag(target).toTagArray())
            add(arrayOf(ACTION_TAG, action.code))
        }
    }
}
