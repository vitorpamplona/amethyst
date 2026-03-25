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
package com.vitorpamplona.quartz.nip52Calendar.rsvp

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.firstTaggedAddress
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.firstTaggedEvent
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.rsvp.tags.FreeBusyTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class CalendarRSVPEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    fun status() = tags.firstNotNullOfOrNull(RSVPStatusTag.Companion::parse)

    fun statusValue() = tags.firstNotNullOfOrNull(RSVPStatusTag.Companion::parseValue)

    fun freebusy() = tags.firstNotNullOfOrNull(FreeBusyTag.Companion::parse)

    fun calendarEventAddress() = firstTaggedAddress()

    fun calendarEventId() = firstTaggedEvent()

    fun calendarEventAuthor() = tags.firstNotNullOfOrNull(PTag.Companion::parse)

    companion object {
        const val KIND = 31925
        const val ALT = "Calendar event's invitation response"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            calendarEventAddress: ATag,
            status: RSVPStatusTag.STATUS,
            content: String = "",
            calendarEventId: ETag? = null,
            calendarEventAuthor: PTag? = null,
            freebusy: FreeBusyTag.STATUS? = null,
            dTag: String = Uuid.Companion.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CalendarRSVPEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            dTag(dTag)
            aTag(calendarEventAddress)
            status(status)
            calendarEventId?.let { add(it.toTagArray()) }
            calendarEventAuthor?.let { add(it.toTagArray()) }
            freebusy?.let { freebusy(it) }
            alt(ALT)
            initializer()
        }
    }
}
