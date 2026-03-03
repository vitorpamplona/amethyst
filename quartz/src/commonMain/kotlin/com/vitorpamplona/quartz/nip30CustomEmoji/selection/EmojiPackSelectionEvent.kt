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
package com.vitorpamplona.quartz.nip30CustomEmoji.selection

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventUpdate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.toATag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class EmojiPackSelectionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider {
    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    fun emojiPacks() = tags.mapNotNull(ATag::parseAddress)

    fun emojiPackIds() = tags.mapNotNull(ATag::parseAddressId)

    companion object {
        const val KIND = 10030
        const val ALT_DESCRIPTION = "Emoji selection"
        const val FIXED_D_TAG = ""

        fun createAddress(pubKey: HexKey) = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressTag(pubKey: HexKey) = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        fun add(
            currentSelection: EmojiPackSelectionEvent,
            packToAdd: EventHintBundle<EmojiPackEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EmojiPackSelectionEvent>.() -> Unit = {},
        ) = eventUpdate(currentSelection, createdAt) {
            pack(packToAdd.toATag())
            initializer()
        }

        fun remove(
            currentSelection: EmojiPackSelectionEvent,
            packToRemove: EmojiPackEvent,
            createdAt: Long = TimeUtils.now(),
            updater: TagArrayBuilder<EmojiPackSelectionEvent>.() -> Unit = {},
        ) = eventUpdate(currentSelection, createdAt) {
            removePack(packToRemove.address())
            updater()
        }

        fun build(
            listOfEmojiPacks: List<EventHintBundle<EmojiPackEvent>>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EmojiPackSelectionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            packs(listOfEmojiPacks.map { it.toATag() })
            initializer()
        }
    }
}
