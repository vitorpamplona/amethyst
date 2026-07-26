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
package com.vitorpamplona.quartz.buzz.notifications

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log

/**
 * The JSON body a Buzz relay puts on a kind-44100/44101 membership notification:
 *
 * ```json
 * {"type":"member_added","channel_id":"<uuid>","actor":"<pubkey hex>"}
 * ```
 *
 * Ground truth: `emit_membership_notification` in Buzz's `buzz-relay/src/handlers/side_effects.rs`.
 *
 * [actor] is the decisive field. The relay emits the *same* kind whether somebody added you or you
 * joined yourself — a self-join (kind 9021) is reported with `actor == target`. Without reading it,
 * "I joined this" and "a stranger put me in this" are indistinguishable, which is what let channels
 * appear silently.
 */
class MembershipNotificationContent(
    val type: String?,
    val channelId: String?,
    val actor: HexKey?,
) {
    /** True when this records somebody *else* adding [viewer] — i.e. it needs the viewer's consent. */
    fun addedBySomeoneElse(viewer: HexKey): Boolean = actor != null && !actor.equals(viewer, ignoreCase = true)

    companion object {
        const val TYPE_MEMBER_ADDED = "member_added"
        const val TYPE_MEMBER_REMOVED = "member_removed"

        private val TYPE = Regex("\"type\"\\s*:\\s*\"([^\"]*)\"")
        private val CHANNEL_ID = Regex("\"channel_id\"\\s*:\\s*\"([^\"]*)\"")
        private val ACTOR = Regex("\"actor\"\\s*:\\s*\"([0-9a-fA-F]{64})\"")

        /**
         * Parses the notification body, or null when [content] is blank/unparseable. Regex rather than a
         * JSON parse because this runs on the consume path for every membership notification and the
         * body is a fixed three-field object written by the relay; a malformed one must degrade to
         * "unknown actor" (treated as needing consent) rather than throw.
         */
        fun parse(content: String): MembershipNotificationContent? {
            if (content.isBlank()) return null
            return try {
                MembershipNotificationContent(
                    type = TYPE.find(content)?.groupValues?.get(1),
                    channelId = CHANNEL_ID.find(content)?.groupValues?.get(1),
                    actor =
                        ACTOR
                            .find(content)
                            ?.groupValues
                            ?.get(1)
                            ?.lowercase(),
                )
            } catch (e: Exception) {
                Log.w("MembershipNotification") { "Could not parse membership notification: ${e.message}" }
                null
            }
        }
    }
}
