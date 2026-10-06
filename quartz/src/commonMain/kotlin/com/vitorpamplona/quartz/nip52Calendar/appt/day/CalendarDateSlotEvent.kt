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
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
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
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    SearchableEvent {
    // The free-text `location` names and the `t` topics are how people look an event
    // up ("meetup Lisbon", "#bitcoin"), so they follow the body, as kind 1111 does.
    override fun indexableContent() = (listOfNotNull(title(), summary(), content) + locations() + hashtags()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away. It runs per event per search
    // keystroke, so the tag-backed fields are read off the tags in place, not via list getters.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(summary())) return
        if (!visitor.visit(content)) return
        for (tag in tags) {
            val location = LocationTag.parse(tag) ?: continue
            if (!visitor.visit(location)) return
        }
        for (tag in tags) {
            val hashtag = HashtagTag.parse(tag) ?: continue
            if (!visitor.visit(hashtag)) return
        }
    }

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

    // NIP-52 `p` tags are the invitees/hosts of this appointment. Exposing them as pubkey
    // hints lets the broadcaster route the appointment - and anything that a-tags it, like an
    // RSVP - into every participant's inbox relays instead of just the author's outbox.
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    fun references() = tags.references()

    companion object {
        const val KIND = 31922

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
            initializer()
        }
    }
}
