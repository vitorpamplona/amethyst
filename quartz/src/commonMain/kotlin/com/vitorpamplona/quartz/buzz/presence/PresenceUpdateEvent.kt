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
package com.vitorpamplona.quartz.buzz.presence

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz presence update (`kind:20001`): an ephemeral online/away/offline signal.
 *
 * The status string lives in both the event `content` (where the relay reads it) and
 * a `["status", ...]` tag for structured access. Ephemeral (20000–29999) — the relay
 * fans it out over Redis pub/sub and never stores it. Ground truth:
 * `buzz-sdk/src/builders.rs::build_presence_update` and `buzz-core/src/presence.rs`.
 */
@Immutable
class PresenceUpdateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The raw presence status string — the `status` tag, or the `content` fallback. */
    fun status(): String = tags.presenceStatus() ?: content

    /** The presence status mapped to the curated enum, or `null` if unrecognized. */
    fun presenceStatus(): PresenceStatus? = PresenceStatus.fromWire(status())

    companion object {
        const val KIND = 20001

        fun build(
            status: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PresenceUpdateEvent>.() -> Unit = {},
        ) = eventTemplate<PresenceUpdateEvent>(KIND, status, createdAt) {
            status(status)
            initializer()
        }

        fun build(
            status: PresenceStatus,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PresenceUpdateEvent>.() -> Unit = {},
        ) = build(status.code, createdAt, initializer)
    }
}
