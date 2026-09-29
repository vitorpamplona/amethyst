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

import com.vitorpamplona.quartz.graph.LinkBuilder
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip71Video.credits.CreditTarget
import com.vitorpamplona.quartz.nip71Video.credits.VideoCredit
import com.vitorpamplona.quartz.nip71Video.tags.TextTrackTag

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
        when (val target = credit.target) {
            is CreditTarget.Person ->
                when (label) {
                    null -> user(Relation.PARTICIPANT, target.pubKey, PTag.TAG_NAME)
                    VideoCredit.MENTION_LABEL -> user(Relation.MENTION, target.pubKey, PTag.TAG_NAME)
                    VideoCredit.INSPIRED_BY_LABEL -> user(Relation.CREDITED, target.pubKey, PTag.TAG_NAME, credit.creditProps())
                    else -> user(Relation.PARTICIPANT, target.pubKey, PTag.TAG_NAME, credit.roleProps())
                }
            is CreditTarget.Video ->
                if (label == null || label == VideoCredit.MENTION_LABEL) {
                    address(Relation.MENTION, target.address, ATag.TAG_NAME)
                } else {
                    address(Relation.CREDITED, target.address, ATag.TAG_NAME, credit.creditProps())
                }
            is CreditTarget.Event ->
                if (label == VideoCredit.MENTION_LABEL) {
                    event(Relation.MENTION, target.eventId, ETag.TAG_NAME)
                } else {
                    event(Relation.CREDITED, target.eventId, ETag.TAG_NAME, credit.creditProps())
                }
        }
    }
    video.textTrack().forEach {
        address(Relation.TEXT_TRACK, it.address(), TextTrackTag.TAG_NAME)
        event(Relation.TEXT_TRACK, it.eventId(), TextTrackTag.TAG_NAME)
    }
    hashtags(tags)
}
