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
package com.vitorpamplona.quartz.nip17Dm.settings

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip17Dm.settings.tags.RelayTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ChatMessageRelayListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun relays(): List<NormalizedRelayUrl> = tags.mapNotNull(RelayTag::parse)

    companion object {
        const val KIND = 10050

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressATag(pubKey: HexKey): ATag = ATag(KIND, pubKey, FIXED_D_TAG, null)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        fun createTagArray(relays: List<NormalizedRelayUrl>): Array<Array<String>> =
            relays
                .map { RelayTag.assemble(it) }
                .plusElement(
                    AltTag.assemble("Relay list to receive private messages"),
                ).toTypedArray()

        suspend fun updateRelayList(
            earlierVersion: ChatMessageRelayListEvent,
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ChatMessageRelayListEvent {
            val tags =
                earlierVersion.tags
                    .filter(RelayTag::notMatch)
                    .plus(
                        relays.map {
                            RelayTag.assemble(it)
                        },
                    ).toTypedArray()

            return signer.sign(createdAt, KIND, tags, earlierVersion.content)
        }

        suspend fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ChatMessageRelayListEvent = signer.sign(createdAt, KIND, createTagArray(relays), "")

        fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): ChatMessageRelayListEvent = signer.sign(createdAt, KIND, createTagArray(relays), "")
    }
}
