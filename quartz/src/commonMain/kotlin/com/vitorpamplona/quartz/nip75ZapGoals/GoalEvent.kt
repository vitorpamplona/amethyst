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
package com.vitorpamplona.quartz.nip75ZapGoals

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
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
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.reference
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip75ZapGoals.tags.AmountTag
import com.vitorpamplona.quartz.nip75ZapGoals.tags.ClosedAtTag
import com.vitorpamplona.quartz.nip75ZapGoals.tags.RelayListTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class GoalEvent(
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
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    fun topics() = hashtags()

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun closedAt() = tags.firstNotNullOfOrNull(ClosedAtTag::parse)

    fun amount() = tags.firstNotNullOfOrNull(AmountTag::parse)

    fun relays() = tags.firstNotNullOfOrNull(RelayListTag::parse)

    companion object {
        const val KIND = 9041
        const val ALT_DESCRIPTION = "Zap Goal"

        fun build(
            description: String,
            amount: Long,
            relays: List<String>,
            closedAt: Long? = null,
            image: String? = null,
            summary: String? = null,
            websiteUrl: String? = null,
            linkedEvent: EventHintBundle<Event>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GoalEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            amount(amount)
            relays(relays)
            closedAt?.let { closedAt(it) }
            image?.let { image(it) }
            summary?.let { summary(it) }
            websiteUrl?.let { reference(it) }
            linkedEvent?.let {
                if (it.event is AddressableEvent) {
                    linked(it.toATag())
                }
                linked(it.toETag())
            }
            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
