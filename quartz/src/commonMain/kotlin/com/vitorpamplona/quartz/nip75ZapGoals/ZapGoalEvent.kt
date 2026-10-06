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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
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
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.BaseZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitHints
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip75ZapGoals.tags.AmountTag
import com.vitorpamplona.quartz.nip75ZapGoals.tags.ClosedAtTag
import com.vitorpamplona.quartz.nip75ZapGoals.tags.RelayListTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ZapGoalEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(summary(), content).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(summary())) return
        visitor.visit(content)
    }

    // NIP-75 lets a goal carry NIP-57 `zap` splits: their beneficiaries are linked people too.
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint) + tags.zapSplitHints()

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey) + beneficiaries().mapNotNull { (it as? ZapSplitSetup)?.pubKeyHex }

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    // NIP-75 links a goal to at most one event (`e`) and one addressable event (`a`).
    override fun linkedEventIds() = listOfNotNull(goalEventId())

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = listOfNotNull(goalAddressId())

    /**
     * NIP-75: who the goal's zaps go to, as NIP-57 `zap` splits. Only splits a zap would pay:
     * positive weight, and a 64-hex key when the beneficiary is a pubkey.
     */
    fun beneficiaries(): List<BaseZapSplitSetup> = tags.zapSplitSetup().filter { it !is ZapSplitSetup || (it.pubKeyHex.length == 64 && Hex.isHex64(it.pubKeyHex)) }

    /** NIP-75: the event this goal is linked to (`e`), if any. */
    fun goalEventId(): HexKey? = tags.firstNotNullOfOrNull(ETag::parseId)

    /** NIP-75: the addressable event this goal is linked to (`a`), if any. */
    fun goalAddressId(): String? = tags.firstNotNullOfOrNull(ATag::parseValidAddress)

    fun topics() = hashtags()

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun closedAt() = tags.firstNotNullOfOrNull(ClosedAtTag::parse)

    fun amount() = tags.firstNotNullOfOrNull(AmountTag::parse)

    fun relays() = tags.firstNotNullOfOrNull(RelayListTag::parse)

    companion object {
        const val KIND = 9041

        fun build(
            description: String,
            amount: Long,
            relays: List<NormalizedRelayUrl>,
            closedAt: Long? = null,
            image: String? = null,
            summary: String? = null,
            websiteUrl: String? = null,
            linkedEvent: EventHintBundle<Event>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ZapGoalEvent>.() -> Unit = {},
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
            initializer()
        }
    }
}

@Deprecated(
    "Renamed to ZapGoalEvent. NIP-75 names kind 9041 the zap goal.",
    ReplaceWith("ZapGoalEvent", "com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent"),
)
typealias GoalEvent = ZapGoalEvent
