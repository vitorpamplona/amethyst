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
package com.vitorpamplona.quartz.nip65RelayList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class AdvertisedRelayListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun relays() = tags.mapNotNull(AdvertisedRelayInfo::parse)

    fun readRelays() = tags.mapNotNull(AdvertisedRelayInfo::parseRead).ifEmpty { null }

    fun readRelaysNorm() = tags.mapNotNull(AdvertisedRelayInfo::parseReadNorm).ifEmpty { null }

    fun writeRelays() = tags.mapNotNull(AdvertisedRelayInfo::parseWrite).ifEmpty { null }

    fun writeRelaysNorm() = tags.mapNotNull(AdvertisedRelayInfo::parseWriteNorm).ifEmpty { null }

    companion object {
        const val KIND = 10002
        const val ALT = "Relay list to discover the user's content"

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        suspend fun replaceRelayListWith(
            earlierVersion: AdvertisedRelayListEvent,
            newRelays: List<AdvertisedRelayInfo>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): AdvertisedRelayListEvent {
            val tags = earlierVersion.tags.filter(AdvertisedRelayInfo::notMatch)
            val relayTags = newRelays.map { it.toTagArray() }

            return signer.sign(createdAt, KIND, (tags + relayTags).toTypedArray(), earlierVersion.content)
        }

        suspend fun createFromScratch(
            relays: List<AdvertisedRelayInfo>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ) = create(relays, signer, createdAt)

        suspend fun create(
            list: List<AdvertisedRelayInfo>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): AdvertisedRelayListEvent {
            val tags = list.map { it.toTagArray() }.toTypedArray()

            return signer.sign(createdAt, KIND, tags, "")
        }

        fun create(
            list: List<AdvertisedRelayInfo>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): AdvertisedRelayListEvent {
            val tags = list.map { it.toTagArray() }.toTypedArray()

            return signer.sign(createdAt, KIND, tags, "")
        }
    }
}
