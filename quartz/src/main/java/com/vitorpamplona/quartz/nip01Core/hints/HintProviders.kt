/**
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
package com.vitorpamplona.quartz.nip01Core.hints

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys

interface EventHintProvider {
    fun eventHints(): List<EventIdHint>

    fun linkedEventIds(): List<HexKey>
}

interface AddressHintProvider {
    fun addressHints(): List<AddressHint>

    fun linkedAddressIds(): List<String>
}

interface PubKeyHintProvider {
    fun pubKeyHints(): List<PubKeyHint>

    fun linkedPubKeys(): List<HexKey>
}

interface StandardHintProvider :
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    private val event: Event get() = this as Event

    private val baseNoteEvent: BaseNoteEvent get() = this as BaseNoteEvent

    override fun eventHints(): List<EventIdHint> {
        val eHints = event.tags.mapNotNull(MarkedETag::parseAsHint)
        val qHints = event.tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = baseNoteEvent.citedNIP19().eventHints()

        return eHints + qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val eHints = event.tags.mapNotNull(MarkedETag::parseId)
        val qHints = event.tags.mapNotNull(QTag::parseEventId)
        val nip19Hints = baseNoteEvent.citedNIP19().eventIds()

        return eHints + qHints + nip19Hints
    }

    override fun addressHints(): List<AddressHint> {
        val qHints = event.tags.mapNotNull(QTag::parseAddressAsHint)
        val nip19Hints = baseNoteEvent.citedNIP19().addressHints()

        return qHints + nip19Hints + getAdditionalAddressHints()
    }

    override fun linkedAddressIds(): List<String> {
        val qHints = event.tags.mapNotNull(QTag::parseAddressId)
        val nip19Hints = baseNoteEvent.citedNIP19().addressIds()

        return qHints + nip19Hints + getAdditionalAddressIds()
    }

    override fun pubKeyHints(): List<PubKeyHint> {
        val pHints = event.tags.mapNotNull(PTag::parseAsHint)
        val nip19Hints = baseNoteEvent.citedNIP19().pubKeyHints()

        return pHints + nip19Hints
    }

    override fun linkedPubKeys(): List<HexKey> {
        val pHints = event.tags.mapNotNull(PTag::parseKey)
        val nip19Hints = baseNoteEvent.citedNIP19().pubKeys()

        return pHints + nip19Hints
    }

    fun getAdditionalAddressHints(): List<AddressHint> = emptyList()

    fun getAdditionalAddressIds(): List<String> = emptyList()
}

interface ETagHintProvider :
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    private val event: Event get() = this as Event

    private val baseNoteEvent: BaseNoteEvent get() = this as BaseNoteEvent

    override fun eventHints(): List<EventIdHint> {
        val eHints = event.tags.mapNotNull(ETag::parseAsHint)
        val qHints = event.tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = baseNoteEvent.citedNIP19().eventHints()

        return eHints + qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val eHints = event.tags.mapNotNull(ETag::parseId)
        val qHints = event.tags.mapNotNull(QTag::parseEventId)
        val nip19Hints = baseNoteEvent.citedNIP19().eventIds()

        return eHints + qHints + nip19Hints
    }

    override fun addressHints(): List<AddressHint> {
        val aHints = event.tags.mapNotNull(ATag::parseAsHint)
        val qHints = event.tags.mapNotNull(QTag::parseAddressAsHint)
        val nip19Hints = baseNoteEvent.citedNIP19().addressHints()

        return aHints + qHints + nip19Hints
    }

    override fun linkedAddressIds(): List<String> {
        val aHints = event.tags.mapNotNull(ATag::parseAddressId)
        val qHints = event.tags.mapNotNull(QTag::parseAddressId)
        val nip19Hints = baseNoteEvent.citedNIP19().addressIds()

        return aHints + qHints + nip19Hints
    }

    override fun pubKeyHints(): List<PubKeyHint> {
        val pHints = event.tags.mapNotNull(PTag::parseAsHint)
        val nip19Hints = baseNoteEvent.citedNIP19().pubKeyHints()

        return pHints + nip19Hints
    }

    override fun linkedPubKeys(): List<HexKey> {
        val pHints = event.tags.mapNotNull(PTag::parseKey)
        val nip19Hints = baseNoteEvent.citedNIP19().pubKeys()

        return pHints + nip19Hints
    }
}

interface ExtendedHintProvider : StandardHintProvider {
    override fun getAdditionalAddressHints(): List<AddressHint> {
        val event = this as Event
        return event.tags.mapNotNull(ATag::parseAsHint)
    }

    override fun getAdditionalAddressIds(): List<String> {
        val event = this as Event
        return event.tags.mapNotNull(ATag::parseAddressId)
    }
}

interface BasicHintProvider :
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    private val event: Event get() = this as Event

    override fun eventHints(): List<EventIdHint> = event.tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds(): List<HexKey> = event.tags.mapNotNull(ETag::parseId)

    override fun addressHints(): List<AddressHint> = event.tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = event.tags.mapNotNull(ATag::parseAddressId)

    override fun pubKeyHints(): List<PubKeyHint> = event.tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys(): List<HexKey> = event.tags.mapNotNull(PTag::parseKey)
}
