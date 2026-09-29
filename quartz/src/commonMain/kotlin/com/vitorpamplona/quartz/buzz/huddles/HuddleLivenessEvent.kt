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
package com.vitorpamplona.quartz.buzz.huddles

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz huddle-liveness snapshot (`kind:48104`): the relay's authoritative statement that a
 * huddle session is live right now, **synthesized and signed by the relay** per REQ and
 * never stored. One event per live session: `["d",<session uuid>]` (the ephemeral audio
 * channel), `["h",<parent channel uuid>]`, and a [HuddleLivenessContent] body. A session
 * with no live room (or no mesh lease) simply gets no event, so absence from a snapshot is
 * "not live".
 *
 * The relay only serves it to a REQ whose every filter has `kinds` exactly `[48104]` and
 * that names at least one authorized `#h` channel — build it with [filter]. Clients never
 * publish this kind; [build] exists for fixtures/tests. Ground truth:
 * `filters_are_huddle_liveness_only`, `huddle_liveness_session_ids` and
 * `handle_huddle_liveness_req` in Buzz's `buzz-relay/src/handlers/req.rs`.
 */
@Immutable
class HuddleLivenessEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The live huddle session (its ephemeral audio channel UUID) — the `d` tag. */
    fun sessionId(): String? = tags.firstTagValue(DTag.TAG_NAME)

    /** The parent (timeline) channel UUID — the `h` tag. */
    fun channelId(): String? = tags.huddleChannel()

    /** The parsed body, or null when malformed (a malformed snapshot is not authoritative). */
    fun liveness(): HuddleLivenessContent? = HuddleLivenessContent.decodeFromJsonOrNull(content)

    /** The session's liveness generation, or null when missing/empty. */
    fun generation(): String? = liveness()?.generation?.ifEmpty { null }

    companion object {
        const val KIND = 48104

        /** The relay truncates `#d` session ids (and bounds `#h` channels) to this many values. */
        const val MAX_EXPLICIT_VALUES = 128

        /**
         * The liveness REQ filter: `kinds` exactly `[48104]`, the parent channels as `#h`
         * (required — the relay CLOSEs a liveness REQ without an authorized `#h`), and
         * optionally the sessions of interest as `#d` with a matching `limit`, the shape
         * Buzz's desktop sends. Without [sessionIds] the relay reports every live session it
         * can link to those channels. Mixing any other kind into the filter turns it into an
         * ordinary REQ that returns no liveness at all.
         */
        fun filter(
            channelIds: List<String>,
            sessionIds: List<String>? = null,
        ): Filter {
            require(channelIds.isNotEmpty()) { "huddle liveness requires at least one #h channel" }
            require(channelIds.size <= MAX_EXPLICIT_VALUES) { "at most $MAX_EXPLICIT_VALUES channels per liveness REQ" }
            require(sessionIds == null || sessionIds.size <= MAX_EXPLICIT_VALUES) { "at most $MAX_EXPLICIT_VALUES sessions per liveness REQ" }

            val tags =
                if (sessionIds.isNullOrEmpty()) {
                    mapOf(GroupIdTag.TAG_NAME to channelIds)
                } else {
                    mapOf(GroupIdTag.TAG_NAME to channelIds, DTag.TAG_NAME to sessionIds)
                }

            return Filter(
                kinds = listOf(KIND),
                tags = tags,
                limit = sessionIds?.size?.takeIf { it > 0 },
            )
        }

        fun build(
            sessionId: String,
            channelId: String,
            generation: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<HuddleLivenessEvent>.() -> Unit = {},
        ) = eventTemplate<HuddleLivenessEvent>(KIND, HuddleLivenessContent(sessionId, generation).encodeToJson(), createdAt) {
            add(DTag.assemble(sessionId))
            huddleChannel(channelId)
            initializer()
        }
    }
}
