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
package com.vitorpamplona.quartz.nip09Deletions

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.isTaggedAddressableKind
import com.vitorpamplona.quartz.nip01Core.tags.aTag.taggedAddresses
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEventIds
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kinds
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTagIds
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class DeletionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider {
    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    fun deleteEvents() = taggedEvents()

    fun deleteEventIds() = taggedEventIds()

    fun deletesAnyEventIn(eventIds: Set<HexKey>) = isTaggedEvents(eventIds)

    fun deleteAddressesWithKind(kind: Int) = isTaggedAddressableKind(kind)

    fun deleteAddresses() = taggedAddresses()

    fun deleteAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    companion object {
        const val KIND = 5
        const val ALT = "Deletion event"

        fun build(
            deleteEvents: List<Event>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DeletionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT)

            deleteEvents.forEach {
                eTag(ETag(it.id))
                if (it is AddressableEvent) {
                    aTag(it.address())
                }
            }

            pTagIds(deleteEvents.mapTo(HashSet()) { it.pubKey })
            kinds(deleteEvents.mapTo(HashSet()) { it.kind })

            initializer()
        }

        fun buildAddressOnly(
            deleteEvents: List<Event>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DeletionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT)

            deleteEvents.forEach {
                if (it is AddressableEvent) {
                    aTag(it.address())
                }
            }

            pTagIds(deleteEvents.mapTo(HashSet()) { it.pubKey })
            kinds(deleteEvents.mapTo(HashSet()) { it.kind })

            initializer()
        }

        fun buildForVersionOnly(
            deleteEvents: List<Event>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DeletionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT)

            deleteEvents.forEach {
                pTag(PTag(it.pubKey))
                eTag(ETag(it.id))
            }

            kinds(deleteEvents.mapTo(HashSet()) { it.kind })

            initializer()
        }
    }
}
