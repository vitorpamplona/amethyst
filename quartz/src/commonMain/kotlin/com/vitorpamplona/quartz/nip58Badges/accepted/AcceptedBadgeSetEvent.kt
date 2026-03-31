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
package com.vitorpamplona.quartz.nip58Badges.accepted

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip58Badges.accepted.tags.AcceptedBadge
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class AcceptedBadgeSetEvent(
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

    fun acceptedBadges() = tags.acceptedBadges()

    fun badgeAwardEvents() = tags.badgeAwardEvents()

    fun badgeAwardDefinitions() = tags.badgeAwardDefinitions()

    companion object {
        const val KIND = 30008
        private const val STANDARD_D_TAG = "profile_badges"
        const val ALT_DESCRIPTION = "List of accepted badges by the author"

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, STANDARD_D_TAG)

        fun createAddressTag(pubKey: HexKey): ATag = ATag(KIND, pubKey, STANDARD_D_TAG, null)

        fun build(
            acceptedBadges: List<AcceptedBadge> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AcceptedBadgeSetEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(STANDARD_D_TAG)
            alt(ALT_DESCRIPTION)
            if (acceptedBadges.isNotEmpty()) {
                acceptedBadges(acceptedBadges)
            }
            initializer()
        }
    }
}
