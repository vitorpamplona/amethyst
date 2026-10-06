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
package com.vitorpamplona.quartz.buzz.arArtifacts

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.PrevTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.ReasonTag
import com.vitorpamplona.quartz.buzz.arArtifacts.tags.VersionTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz NIP-AR artifact removal (`kind:45011`), **signed by the relay** and stored in the
 * *source* channel when an artifact moves out of it, in the same transaction that accepts the
 * `op=move` revision. It says only that artifact `d` left channel `h`, replacing revision
 * `prev` — never the destination, title or content. Tags, in order: `["ar","1"]`,
 * `["d",<artifact uuid>]`, `["h",<source channel uuid>]`, `["reason","moved"]`,
 * `["prev",<replaced revision id>]`; `content` is empty.
 *
 * Clients never publish this kind; [build] exists for fixtures/tests. Ground truth:
 * `removal_marker` in Buzz's `buzz-db/src/store/artifact.rs`.
 */
@Immutable
class ArtifactRemovalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider {
    // `prev` carries a bare event id with no relay slot.
    override fun eventHints(): List<EventIdHint> = emptyList()

    override fun linkedEventIds(): List<HexKey> = tags.mapNotNull(PrevTag::parseId)

    /** The artifact that left the channel — the `d` tag. */
    fun artifactId() = tags.artifactId()

    /** The source channel the artifact was removed from — the `h` tag. */
    fun sourceChannel() = tags.artifactHome()

    /** Why it was removed — the `reason` tag (`moved`). */
    fun reason() = tags.artifactRemovalReason()

    /** The revision the move replaced (the last one readable in the source) — the `prev` tag. */
    fun replacedRevision() = tags.artifactPrev()

    /** True when the marker has the exact shape the relay emits. */
    fun isWellFormed(): Boolean {
        if (content.isNotEmpty()) return false
        if (tags.artifactVersion() != VersionTag.CURRENT) return false
        val id = artifactId() ?: return false
        val source = sourceChannel() ?: return false
        val prev = replacedRevision() ?: return false
        return ArtifactIds.isCanonicalUuid(id) && ArtifactIds.isCanonicalUuid(source) && ArtifactIds.isEventId(prev) && reason() != null
    }

    companion object {
        const val KIND = 45011

        fun build(
            artifactId: String,
            sourceChannelId: String,
            replacedRevision: HexKey,
            reason: String = ReasonTag.MOVED,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ArtifactRemovalEvent>.() -> Unit = {},
        ) = eventTemplate<ArtifactRemovalEvent>(KIND, "", createdAt) {
            artifactVersion()
            artifactId(artifactId)
            artifactHome(sourceChannelId)
            artifactRemovalReason(reason)
            artifactPrev(replacedRevision)
            initializer()
        }
    }
}
