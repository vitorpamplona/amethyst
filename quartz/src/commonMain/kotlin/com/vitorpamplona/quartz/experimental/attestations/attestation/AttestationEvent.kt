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
package com.vitorpamplona.quartz.experimental.attestations.attestation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.AttestationStatus
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.Validity
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.json.JsonNull.content

@Immutable
class AttestationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds(): List<HexKey> = tags.mapNotNull(ETag::parseId)

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseAddressId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    fun validity() = tags.validity()

    fun status() = tags.status()

    fun validFrom() = tags.validFrom()

    fun validTo() = tags.validTo()

    fun request() = tags.request()

    fun requestId() = tags.requestId()

    fun requestAddress() = tags.requestAddress()

    fun isRevoked() = status() == AttestationStatus.REVOKED

    fun assertionAddrId() = tags.firstNotNullOfOrNull(ATag::parseAddressId)

    fun assertionAddress() = tags.firstNotNullOfOrNull(ATag::parseAddress)

    fun assertionATag() = tags.firstNotNullOfOrNull(ATag::parse)

    fun assertionEventId() = tags.firstNotNullOfOrNull(ETag::parseId)

    fun assertionETag() = tags.firstNotNullOfOrNull(ETag::parse)

    fun assertionPubkey() = tags.firstNotNullOfOrNull(PTag::parseKey)

    fun assertionPTag() = tags.firstNotNullOfOrNull(PTag::parse)

    companion object {
        const val KIND = 31871
        const val ALT_DESCRIPTION = "Attestation"

        fun buildEvent(
            dTagId: String,
            about: EventHintBundle<Event>,
            content: String = "",
            validity: Validity? = null,
            status: AttestationStatus? = null,
            validFrom: Long? = null,
            validTo: Long? = null,
            requestAddress: EventHintBundle<AttestationRequestEvent>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AttestationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(dTagId)
            about(about)
            validity?.let { validity(it) }
            status?.let { status(it) }
            validFrom?.let { validFrom(it) }
            validTo?.let { validTo(it) }
            requestAddress?.let { request(it) }
            initializer()
        }

        fun buildReplaceable(
            dTagId: String,
            about: EventHintBundle<BaseReplaceableEvent>,
            content: String = "",
            validity: Validity? = null,
            status: AttestationStatus? = null,
            validFrom: Long? = null,
            validTo: Long? = null,
            requestAddress: EventHintBundle<AttestationRequestEvent>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AttestationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(dTagId)
            aboutReplaceable(about)
            validity?.let { validity(it) }
            status?.let { status(it) }
            validFrom?.let { validFrom(it) }
            validTo?.let { validTo(it) }
            requestAddress?.let { request(it) }
            initializer()
        }

        fun buildAddress(
            dTagId: String,
            about: EventHintBundle<BaseAddressableEvent>,
            content: String = "",
            validity: Validity? = null,
            status: AttestationStatus? = null,
            validFrom: Long? = null,
            validTo: Long? = null,
            requestAddress: EventHintBundle<AttestationRequestEvent>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AttestationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(dTagId)
            aboutAddressable(about)
            validity?.let { validity(it) }
            status?.let { status(it) }
            validFrom?.let { validFrom(it) }
            validTo?.let { validTo(it) }
            requestAddress?.let { request(it) }
            initializer()
        }
    }
}
