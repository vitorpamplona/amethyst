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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz relay-signed notification that a pubkey was added to a channel (`kind:44100`).
 * The `p` tag names the added member, the `h` tag names the channel UUID. Stored globally
 * (no channel scope) so the target can discover it. Emitted by the relay - the author is
 * the relay keypair, not the added member. Ground truth: `KIND_MEMBER_ADDED_NOTIFICATION`
 * in Buzz's `buzz-core/src/kind.rs`.
 */
@Immutable
class MemberAddedNotificationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The pubkey that was added - the `p` tag. */
    fun target() = tags.notificationTarget()

    /** The channel UUID the member was added to - the `h` tag. */
    fun channel() = tags.notificationChannel()

    companion object {
        const val KIND = 44100

        fun build(
            channelId: String,
            targetPubKey: HexKey,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MemberAddedNotificationEvent>.() -> Unit = {},
        ) = eventTemplate<MemberAddedNotificationEvent>(KIND, content, createdAt) {
            notificationTarget(targetPubKey)
            notificationChannel(channelId)
            initializer()
        }
    }
}
