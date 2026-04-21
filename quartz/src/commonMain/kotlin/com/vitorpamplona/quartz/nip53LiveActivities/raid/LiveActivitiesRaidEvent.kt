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
package com.vitorpamplona.quartz.nip53LiveActivities.raid

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-53 live stream raid (zap.stream convention, kind 1312).
 *
 * A raid is authored by the source streamer to redirect viewers to another live
 * stream. The event carries two `a` tags referencing NIP-53 Live Activities
 * (kind 30311) differentiated by NIP-10-style markers at position 3:
 *   - "root"    -> the source stream (the raid is being sent FROM)
 *   - "mention" -> the target stream (the raid is being sent TO)
 *
 * The `content` is a free-text raid message from the source streamer.
 */
@Immutable
class LiveActivitiesRaidEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider {
    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseAddressId)

    fun fromActivity(): ATag? = findActivity(MARKER_ROOT)

    fun toActivity(): ATag? = findActivity(MARKER_MENTION)

    fun fromAddress(): Address? = fromActivity()?.let { Address(it.kind, it.pubKeyHex, it.dTag) }

    fun toAddress(): Address? = toActivity()?.let { Address(it.kind, it.pubKeyHex, it.dTag) }

    private fun findActivity(marker: String): ATag? =
        tags
            .asSequence()
            .filter { it.size > 3 && it[0] == ATag.TAG_NAME && it[3] == marker }
            .mapNotNull(ATag::parse)
            .firstOrNull { it.kind == LiveActivitiesEvent.KIND }

    companion object {
        const val KIND = 1312
        const val ALT = "Live activity raid"
        const val MARKER_ROOT = "root"
        const val MARKER_MENTION = "mention"

        /**
         * Builds an event template for a raid. Sender must be the host of the `from` stream.
         *
         * @param from    source stream (the one currently live that is being ended/redirected)
         * @param to      target stream (where viewers should be redirected)
         * @param message raid announcement text
         */
        fun build(
            from: EventHintBundle<LiveActivitiesEvent>,
            to: EventHintBundle<LiveActivitiesEvent>,
            message: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LiveActivitiesRaidEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            addMarkedATag(from, MARKER_ROOT)
            addMarkedATag(to, MARKER_MENTION)
            initializer()
        }

        private fun TagArrayBuilder<LiveActivitiesRaidEvent>.addMarkedATag(
            bundle: EventHintBundle<LiveActivitiesEvent>,
            marker: String,
        ) {
            val relayUrl = bundle.relay?.url.orEmpty()
            val addressId =
                Address.assemble(
                    LiveActivitiesEvent.KIND,
                    bundle.event.pubKey,
                    bundle.event.dTag(),
                )
            add(arrayOf(ATag.TAG_NAME, addressId, relayUrl, marker))
        }
    }
}
