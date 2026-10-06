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
package com.vitorpamplona.quartz.buzz.cwChannelWindow

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz NIP-CW thread-summary overlay (`kind:39005`), **signed by the relay** and
 * synthesized at query time (never stored). Appended to a bridge window response, one per
 * root event that has replies. Addressable with `d` = the root event id; it also `e`-tags
 * the same root and `h`-tags the channel. `content` is a [ThreadSummaryContent] JSON blob.
 * Clients never publish this kind; [build] exists for fixtures/tests. Ground truth:
 * `buzz-relay/src/api/bridge.rs` (thread-summary overlays).
 */
@Immutable
class ThreadSummaryEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    EventHintProvider {
    // Participants live in the relay-synthesized JSON body, with no relay hints.
    override fun pubKeyHints(): List<PubKeyHint> = emptyList()

    override fun linkedPubKeys(): List<HexKey> = summaryOrNull()?.participants?.filter { it.isValid() } ?: emptyList()

    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds(): List<HexKey> = tags.mapNotNull(ETag::parseId)

    /** The thread root event id — the `d` tag (equal to the `e` tag). */
    fun rootId() = dTag()

    /** The channel id — the `h` tag. */
    fun channelId() = tags.firstNotNullOfOrNull(GroupIdTag::parse)

    /** The root event id from the `e` tag. */
    fun rootEventTag() = tags.firstNotNullOfOrNull(ETag::parseId)

    // linkedPubKeys() runs for every relay copy of every event, so the body is decoded once
    // per instance — a failure included. Events are immutable; a race only decodes twice.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var summaryCache: Result<ThreadSummaryContent>? = null

    private fun parsedSummary(): Result<ThreadSummaryContent> =
        summaryCache ?: try {
            Result.success(ThreadSummaryContent.decodeFromJson(content))
        } catch (e: Exception) {
            Result.failure(e)
        }.also { summaryCache = it }

    /** Parses the JSON summary [content] (once). Throws on malformed content; use [summaryOrNull]. */
    fun summary(): ThreadSummaryContent = parsedSummary().getOrThrow()

    fun summaryOrNull(): ThreadSummaryContent? = parsedSummary().getOrNull()

    companion object {
        const val KIND = 39005

        fun build(
            rootId: HexKey,
            channelId: String,
            summary: ThreadSummaryContent,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ThreadSummaryEvent>.() -> Unit = {},
        ) = eventTemplate<ThreadSummaryEvent>(KIND, summary.encodeToJson(), createdAt) {
            addUnique(ETag.assemble(rootId, null, null))
            dTag(rootId)
            addUnique(GroupIdTag.assemble(channelId))
            initializer()
        }
    }
}
