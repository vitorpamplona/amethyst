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
package com.vitorpamplona.quartz.buzz.moderation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz community-moderation unban command (`kind:9041`): mod-signed, lifts a community ban
 * on [target]. `p`-tags the target; `content` is empty and there is no `h` tag (tenant bound
 * by the connection host). Validated + executed by the relay, never stored. Ground truth:
 * `buzz-sdk/src/builders.rs::build_moderation_unban`.
 *
 * WARNING — KIND COLLISION: `9041` is also
 * [com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent]'s kind in Quartz. This class MUST NOT
 * be registered in `utils/EventFactory.kt` (the NIP-75 GoalEvent owns 9041 there). Buzz
 * relays dispatch moderation commands by connection context, not by the shared `EventFactory`
 * kind switch, so leaving 9041 unregistered is correct — construct/parse this type explicitly.
 */
@Immutable
class ModerationUnbanEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The unbanned pubkey — the single `p` tag. */
    fun target() = tags.moderationTarget()

    companion object {
        /** Kind 9041 — COLLIDES with NIP-75 GoalEvent. Do not register in EventFactory. */
        const val KIND = 9041

        fun build(
            target: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ModerationUnbanEvent>.() -> Unit = {},
        ) = eventTemplate<ModerationUnbanEvent>(KIND, "", createdAt) {
            addUnique(PTag.assemble(target, null))
            initializer()
        }
    }
}
