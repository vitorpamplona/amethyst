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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.addressTags
import com.vitorpamplona.quartz.nip01Core.links.hashtags
import com.vitorpamplona.quartz.nip01Core.links.valueTags

/**
 * The links of a NIP-52 date or time slot (31922, 31923): its participants, with the optional
 * role NIP-52 puts in the `p` tag's fourth slot; the calendars (31924) it asks to be included in
 * (`a`); its topics, geohash and web references.
 */
internal fun LinkBuilder.calendarSlotLinks(tags: TagArray) {
    tags.fastForEach {
        if (it.size < 2 || it[0] != "p") return@fastForEach
        val role = it.getOrNull(3)?.ifBlank { null }
        user(Relation.PARTICIPANT, it[1], "p", role?.let { mapOf("role" to role) })
    }
    addressTags(Relation.CALENDAR, tags)
    hashtags(tags)
    valueTags(Relation.TAG, tags, "g")
    valueTags(Relation.TAG, tags, "r")
}
