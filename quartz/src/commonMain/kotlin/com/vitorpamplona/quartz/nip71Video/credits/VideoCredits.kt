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
package com.vitorpamplona.quartz.nip71Video.credits

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag

/**
 * Who and what a video credits, beyond its author.
 *
 * NIP-71 only defines `p` as "a participant in the video". Everything richer is convention:
 * divine.video labels each reference with what it is for — a person who inspired the piece, the
 * video whose audio was reused, a collaborator who accepted a credit — and puts that word in the
 * tag's marker slot. Reading the marker is what separates "featuring @x" from a bare mention.
 */
@Immutable
data class VideoCredit(
    val target: CreditTarget,
    /**
     * The marker as published: `inspired-by`, `mention`, `audio`, or a role like `Collaborator`.
     * Null when the tag names a participant without saying in what capacity.
     */
    val label: String?,
)

@Immutable
sealed interface CreditTarget {
    @Immutable
    data class Person(
        val pubKey: HexKey,
        val relay: NormalizedRelayUrl? = null,
    ) : CreditTarget

    @Immutable
    data class Video(
        val address: ATag,
    ) : CreditTarget

    @Immutable
    data class Event(
        val eventId: HexKey,
        val relay: NormalizedRelayUrl? = null,
    ) : CreditTarget
}

object VideoCredits {
    // NIP-10's own markers on an `e`/`a` tag describe threading, not authorship. A video is never
    // a reply, so any of these is either a stray or something other than a credit — reading them
    // as one would print "root" under the player as though it were a person's role.
    private val THREADING_MARKERS = setOf("root", "reply")

    /**
     * Reads every credited reference off a video's tags, in published order.
     *
     * The marker's *position* is not fixed, because two conventions collide: divine-mobile writes
     * `["p", <pubkey>, <relay>, "inspired-by"]` while divine-web's collaborator invite writes
     * `["p", <pubkey>, "Collaborator"]`. Both are unambiguous once you ask whether slot 2 parses
     * as a relay — the same test [PTag] already uses to decide whether it holds a hint.
     */
    fun parse(tags: TagArray): List<VideoCredit> =
        tags.mapNotNull { tag ->
            when {
                tag.size > 1 && tag[0] == PTag.TAG_NAME && tag[1].length == 64 ->
                    VideoCredit(CreditTarget.Person(tag[1], relayHintAt(tag, 2)), labelAfterOptionalRelay(tag))

                tag.size > 1 && tag[0] == ATag.TAG_NAME ->
                    ATag.parse(tag[1], tag.getOrNull(2))?.let { address ->
                        val label = labelAfterOptionalRelay(tag)
                        if (label in THREADING_MARKERS) null else VideoCredit(CreditTarget.Video(address), label)
                    }

                tag.size > 1 && tag[0] == ETag.TAG_NAME && tag[1].length == 64 -> {
                    // Unlike `p` and `a`, a bare `e` tag on a video says nothing — it is only a
                    // credit when something names what the reference is for (divine.video writes
                    // "audio" for a reused soundtrack). No marker, no credit.
                    val label = labelAfterOptionalRelay(tag)
                    if (label == null || label in THREADING_MARKERS) {
                        null
                    } else {
                        VideoCredit(CreditTarget.Event(tag[1], relayHintAt(tag, 2)), label)
                    }
                }

                else -> null
            }
        }

    private fun relayHintAt(
        tag: Array<String>,
        index: Int,
    ): NormalizedRelayUrl? {
        if (!tag.has(index)) return null
        if (tag[index].length <= 7 || !RelayUrlNormalizer.isRelayUrl(tag[index])) return null
        return RelayUrlNormalizer.normalizeOrNull(tag[index])
    }

    // Slot 2 is either a relay hint or the label itself; the label follows it when it is a relay.
    private fun labelAfterOptionalRelay(tag: Array<String>): String? {
        val slot2 = tag.getOrNull(2)
        val label =
            if (slot2 != null && slot2.length > 7 && RelayUrlNormalizer.isRelayUrl(slot2)) {
                tag.getOrNull(3)
            } else {
                slot2
            }
        return label?.takeIf { it.isNotBlank() }
    }
}
