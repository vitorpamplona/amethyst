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
package com.vitorpamplona.quartz.nip30CustomEmoji

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class EmojiPackSelectionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun dTag() = FIXED_D_TAG

    companion object {
        const val KIND = 10030
        const val FIXED_D_TAG = ""
        const val ALT = "Emoji selection"

        fun createAddressATag(pubKey: HexKey): ATag =
            ATag(
                KIND,
                pubKey,
                AdvertisedRelayListEvent.FIXED_D_TAG,
                null,
            )

        fun createAddressTag(pubKey: HexKey): String =
            ATag.assembleATag(
                KIND,
                pubKey,
                AdvertisedRelayListEvent.FIXED_D_TAG,
            )

        fun create(
            listOfEmojiPacks: List<ATag>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (EmojiPackSelectionEvent) -> Unit,
        ) {
            val msg = ""
            val tags = mutableListOf<Array<String>>()

            listOfEmojiPacks?.forEach { tags.add(arrayOf("a", it.toTag())) }

            tags.add(AltTagSerializer.toTagArray(ALT))

            signer.sign(createdAt, KIND, tags.toTypedArray(), msg, onReady)
        }
    }
}
