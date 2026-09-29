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
package com.vitorpamplona.quartz.nip52Calendar.appt

import com.vitorpamplona.quartz.graph.LinkBuilder
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.hashtags
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.references.ReferenceTag
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.SlotParticipantTag

/**
 * The links of a NIP-52 date or time slot (31922, 31923): its participants, with the optional
 * role NIP-52 puts in the `p` tag's fourth slot; the calendars (31924) it asks to be included in
 * (`a`); its topics, geohash and web references.
 */
internal fun LinkBuilder.calendarSlotLinks(tags: TagArray) {
    each(tags, SlotParticipantTag::parse) { user(Relation.PARTICIPANT, it, SlotParticipantTag.TAG_NAME, it.linkProps()) }
    each(tags, ATag::parse) { address(Relation.CALENDAR, it, ATag.TAG_NAME) }
    hashtags(tags)
    each(tags, GeoHashTag::parse) { tag(Relation.TAG, GeoHashTag.TAG_NAME, it) }
    each(tags, ReferenceTag::parse) { tag(Relation.TAG, ReferenceTag.TAG_NAME, it) }
}
