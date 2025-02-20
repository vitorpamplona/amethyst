/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip03Timestamp

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kind
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.Base64

@Immutable
class OtsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    fun digestEventId() = tags.firstNotNullOfOrNull(ETag::parseId)

    fun otsByteArray(): ByteArray = decodeOtsState(content)

    fun cacheVerify(): VerificationState = VerificationStateCache.cacheVerify(this)

    fun verifyState(): VerificationState = digestEventId()?.let { verify(otsByteArray(), it) } ?: VerificationState.Error("Digest Not found")

    fun verify(): Long? = (verifyState() as? VerificationState.Verified)?.verifiedTime

    companion object {
        const val KIND = 1040
        const val ALT = "Opentimestamps Attestation"

        fun stamp(eventId: HexKey) = OtsResolver.stamp(eventId.hexToByteArray())

        fun upgrade(
            otsState: ByteArray,
            eventId: HexKey,
        ) = OtsResolver.upgrade(otsState, eventId.hexToByteArray())

        fun verify(
            otsState: ByteArray,
            eventId: HexKey,
        ) = OtsResolver.verify(otsState, eventId.hexToByteArray())

        fun encodeOtsState(otsState: ByteArray) = Base64.getEncoder().encodeToString(otsState)

        fun decodeOtsState(content: String) = Base64.getDecoder().decode(content)

        fun build(
            eventId: HexKey,
            otsState: ByteArray,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<OtsEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, encodeOtsState(otsState), createdAt) {
            alt(ALT)
            eTag(ETag(eventId))

            initializer()
        }

        fun build(
            event: EventHintBundle<Event>,
            otsState: ByteArray,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<OtsEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, encodeOtsState(otsState), createdAt) {
            alt(ALT)

            eTag(event.toETag())
            kind(event.event.kind)

            initializer()
        }
    }
}
