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
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.tags.ReasonTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz community-moderation timeout command (`kind:9042`): mod-signed, write-blocks
 * [target] until the required `expiration` timestamp. `p`-tags the target, carries a
 * mandatory `expiration` (unix seconds) and an optional [ReasonTag] code. Tenant-scoped by
 * the connection host, so there is no `h` tag; `content` is empty. Validated + executed by
 * the relay, never stored. Ground truth: `buzz-sdk/src/builders.rs::build_moderation_timeout`.
 */
@Immutable
class ModerationTimeoutEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The timed-out pubkey — the single `p` tag. */
    fun target() = tags.moderationTarget()

    /** The unix-seconds timeout expiry (required for a timeout). */
    fun expiresAt() = tags.expiration()

    /** The machine-readable `reason` code, if present. */
    fun reason() = tags.moderationReason()

    companion object {
        const val KIND = 9042

        fun build(
            target: HexKey,
            expiresAt: Long,
            reason: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ModerationTimeoutEvent>.() -> Unit = {},
        ) = eventTemplate<ModerationTimeoutEvent>(KIND, "", createdAt) {
            addUnique(PTag.assemble(target, null))
            expiration(expiresAt)
            reason?.let { addUnique(ReasonTag.assemble(it)) }
            initializer()
        }
    }
}
