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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.intent

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.toATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.toETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.AmountTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.OfferTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.ZapIdTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-XX: BOLT12 Zaps — the **zap intent** (kind 9737).
 *
 * The payer creates and signs this event *before* paying the BOLT12 offer. It
 * binds the payer's Nostr key to the recipient, the target, the amount and the
 * offer. The payer then references this event's id in the BOLT12
 * `invreq_payer_note` (`nostr:nipXX:<zap-intent-event-id>`), and finally embeds
 * the whole serialized intent inside the kind 9736 zap event's `description` tag
 * — the same embedding pattern NIP-57 uses for the zap request.
 *
 * A zap intent is never counted on its own; only the kind 9736 zap that carries
 * both this intent and a settled payer proof is.
 */
@Immutable
class Bolt12ZapIntentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    /** The recipient pubkey (`p` tag). */
    fun recipient() = tags.firstNotNullOfOrNull(PTag::parseKey)

    /** The amount in millisatoshis (`amount` tag). */
    fun amount() = tags.firstNotNullOfOrNull(AmountTag::parse)

    /** The canonical raw BOLT12 offer (`offer` tag). */
    fun offer() = tags.firstNotNullOfOrNull(OfferTag::parse)

    /** The random `zap_id`. */
    fun zapId() = tags.firstNotNullOfOrNull(ZapIdTag::parse)

    /** The event being zapped, if any (`e` tag). */
    fun zappedEvent() = tags.firstNotNullOfOrNull(ETag::parseId)

    /** The addressable event being zapped, if any (`a` tag). */
    fun zappedAddress() = tags.firstNotNullOfOrNull(ATag::parseAddressId)

    /** The kind of the target event, if declared (`k` tag). */
    fun zappedKind() = tags.firstNotNullOfOrNull(KindTag::parse)

    /** True when neither `e` nor `a` is present — the intent targets the recipient's profile. */
    fun isProfileZap() = zappedEvent() == null && zappedAddress() == null

    companion object {
        const val KIND = 9737

        /** Build a zap intent that targets a specific event. */
        fun build(
            recipientPubKey: HexKey,
            amountInMillisats: Long,
            offer: String,
            zapId: String,
            zappedEvent: EventHintBundle<out Event>,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<Bolt12ZapIntentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, comment, createdAt) {
            recipient(recipientPubKey)
            amountInMillisats(amountInMillisats)
            offer(offer)
            zapId(zapId)
            if (zappedEvent.event is AddressableEvent) {
                zappedAddress(zappedEvent.toATag())
            } else {
                zappedEvent(zappedEvent.toETag())
            }
            zappedKind(zappedEvent.event.kind)
            initializer()
        }

        /** Build a zap intent that targets a recipient's profile (no event / address). */
        fun buildProfileZap(
            recipientPubKey: HexKey,
            amountInMillisats: Long,
            offer: String,
            zapId: String,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<Bolt12ZapIntentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, comment, createdAt) {
            recipient(recipientPubKey)
            amountInMillisats(amountInMillisats)
            offer(offer)
            zapId(zapId)
            initializer()
        }
    }
}
