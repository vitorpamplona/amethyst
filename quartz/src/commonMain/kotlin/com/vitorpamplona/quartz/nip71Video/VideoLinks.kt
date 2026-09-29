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
package com.vitorpamplona.quartz.nip71Video

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.hashtags
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip71Video.credits.CreditTarget

private const val MENTION_LABEL = "mention"
private const val INSPIRED_BY_LABEL = "inspired-by"

/**
 * The links every NIP-71 video kind (21, 22, 34235, 34236) shares.
 *
 * NIP-71 defines `p` as "a participant in the video". divine.video labels its references in the
 * marker slot (see [com.vitorpamplona.quartz.nip71Video.credits.VideoCredits]): `mention` is a
 * passing mention, `inspired-by` and the labels on `a`/`e` (`audio`…) are credits, and any other
 * label on a `p` is the participant's role. The `text-track` names its captions either as a URL
 * (not modelled) or as an event or a 39307 address.
 */
internal fun LinkBuilder.videoLinks(
    video: VideoEvent,
    tags: TagArray,
) {
    video.credits().forEach { credit ->
        val label = credit.label
        val creditProps = label?.let { mapOf("credit" to it) }
        when (val target = credit.target) {
            is CreditTarget.Person ->
                when (label) {
                    null -> user(Relation.PARTICIPANT, target.pubKey, "p")
                    MENTION_LABEL -> user(Relation.MENTION, target.pubKey, "p")
                    INSPIRED_BY_LABEL -> user(Relation.CREDITED, target.pubKey, "p", creditProps)
                    else -> user(Relation.PARTICIPANT, target.pubKey, "p", mapOf("role" to label))
                }
            is CreditTarget.Video ->
                if (label == null || label == MENTION_LABEL) {
                    address(Relation.MENTION, target.address.toTag(), "a")
                } else {
                    address(Relation.CREDITED, target.address.toTag(), "a", creditProps)
                }
            is CreditTarget.Event ->
                if (label == MENTION_LABEL) {
                    event(Relation.MENTION, target.eventId, "e")
                } else {
                    event(Relation.CREDITED, target.eventId, "e", creditProps)
                }
        }
    }
    video.textTrack().forEach { textTrackLink(it.ref) }
    hashtags(tags)
}

private fun LinkBuilder.textTrackLink(ref: String) {
    val via = "text-track"
    when {
        LinkBuilder.normalizedAddress(ref) != null -> address(Relation.TEXT_TRACK, ref, via)
        ref.length == 64 -> event(Relation.TEXT_TRACK, ref, via)
        ref.startsWith("nostr:") || ref.startsWith("nevent1") || ref.startsWith("naddr1") || ref.startsWith("note1") ->
            when (val entity = Nip19Parser.uriToRoute(ref)?.entity) {
                is NEvent -> event(Relation.TEXT_TRACK, entity.hex, via)
                is NNote -> event(Relation.TEXT_TRACK, entity.hex, via)
                is NAddress -> address(Relation.TEXT_TRACK, entity.aTag(), via)
                else -> Unit
            }
    }
}
