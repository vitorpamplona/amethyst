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
package com.vitorpamplona.quartz.nip30CustomEmoji.pack

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.NameTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
class EmojiPackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : PrivateTagArrayEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    @Deprecated("NIP-51 has deprecated name. Use title instead", ReplaceWith("title()"))
    fun name() = tags.firstNotNullOfOrNull(NameTag::parse)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    @Suppress("DEPRECATION")
    fun titleOrName() = title() ?: name()

    fun description() = tags.firstNotNullOfOrNull(DescriptionTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun publicEmojis(): List<EmojiUrlTag> = tags.emojis()

    suspend fun privateEmojis(signer: NostrSigner): List<EmojiUrlTag>? = privateTags(signer)?.emojis()

    suspend fun allEmojis(signer: NostrSigner): List<EmojiUrlTag> {
        val public = publicEmojis()
        val private = privateEmojis(signer) ?: return public
        return public + private
    }

    companion object {
        const val KIND = 30030
        const val ALT_DESCRIPTION = "Emoji pack"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            name: String,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EmojiPackEvent>.() -> Unit = {},
        ) = eventTemplate<EmojiPackEvent>(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(dTag)
            title(name)
            initializer()
        }
    }
}
