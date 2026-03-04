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
package com.vitorpamplona.quartz.nip52Calendar.appt.day

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.LocationTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class CalendarDateSlotEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstNotNullOfOrNull(TitleTag.Companion::parse)

    fun location() = tags.firstNotNullOfOrNull(LocationTag.Companion::parse)

    fun locations() = tags.mapNotNull(LocationTag.Companion::parse)

    fun start() = tags.firstTagValue("start")

    fun end() = tags.firstTagValue("end")

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag.Companion::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag.Companion::parse)

    fun geohash() = tags.firstNotNullOfOrNull(GeoHashTag.Companion::parse)

    fun hashtags() = tags.hashtags()

    fun participants() = tags.mapNotNull(PTag.Companion::parse)

    fun references() = tags.references()

    companion object {
        const val KIND = 31922
        const val ALT = "Full-day calendar event"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            title: String,
            start: String,
            content: String = "",
            end: String? = null,
            dTag: String = Uuid.Companion.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CalendarDateSlotEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            dTag(dTag)
            titleDay(title)
            startDate(start)
            end?.let { endDate(it) }
            alt(ALT)
            initializer()
        }
    }
}
