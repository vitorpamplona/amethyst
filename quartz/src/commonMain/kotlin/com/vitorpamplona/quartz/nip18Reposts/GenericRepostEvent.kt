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
package com.vitorpamplona.quartz.nip18Reposts

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.kind
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.lastNotNullOfOrNull

@Immutable
class GenericRepostEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    fun boostedEvent() = tags.lastNotNullOfOrNull(ETag::parse)

    fun boostedATag() = tags.lastNotNullOfOrNull(ATag::parse)

    fun boostedAddress() = tags.lastNotNullOfOrNull(ATag::parseAddress)

    fun boostedEventId() = tags.lastNotNullOfOrNull(ETag::parseId)

    fun boostedAddressIds() = tags.lastNotNullOfOrNull(ATag::parseAddressId)

    fun originalAuthors() = tags.mapNotNull(PTag::parse)

    fun originalAuthorKeys() = tags.mapNotNull(PTag::parseKey)

    fun containedPost() =
        try {
            fromJson(content)
        } catch (e: Exception) {
            null
        }

    companion object {
        const val KIND = 16
        const val ALT = "Generic repost"

        fun build(
            boostedPost: Event,
            eventSourceRelay: NormalizedRelayUrl?,
            authorHomeRelay: NormalizedRelayUrl?,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GenericRepostEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, boostedPost.toJson(), createdAt) {
            alt(ALT)

            kind(boostedPost.kind)
            pTag(PTag(boostedPost.pubKey, authorHomeRelay))
            eTag(ETag(boostedPost.id, eventSourceRelay, boostedPost.pubKey))
            if (boostedPost is AddressableEvent) {
                aTag(boostedPost.aTag(eventSourceRelay))
            }

            initializer()
        }

        suspend fun create(
            boostedPost: Event,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): GenericRepostEvent {
            val content = boostedPost.toJson()

            val tags =
                mutableListOf(
                    arrayOf("e", boostedPost.id),
                    arrayOf("p", boostedPost.pubKey),
                )

            if (boostedPost is AddressableEvent) {
                tags.add(arrayOf("a", boostedPost.aTag().toTag()))
            }

            tags.add(arrayOf("k", "${boostedPost.kind}"))
            tags.add(AltTag.assemble(ALT))

            return signer.sign(createdAt, KIND, tags.toTypedArray(), content)
        }
    }
}
