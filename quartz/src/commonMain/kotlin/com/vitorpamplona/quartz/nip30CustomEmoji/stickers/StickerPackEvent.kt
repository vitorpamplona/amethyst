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
package com.vitorpamplona.quartz.nip30CustomEmoji.stickers

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.tags.StickerTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sticker pack, kind 30031: the sticker sibling of the NIP-30 / NIP-51 emoji pack
 * ([EmojiPackEvent], kind 30030). No NIP defines it yet; this models what DEN Chat (a Discord-like
 * client) and Sonar (Signal/Telegram sticker imports) publish:
 *
 * ```
 * ["d", "<uuid>"], ["title", "<pack name>"], ["description", "…"]?, ["image", "<cover url>"]?,
 * ["sticker", "<shortcode>", "<url>", …] (repeated, see [StickerTag])
 * ```
 *
 * Unrelated apps also publish on 30031 (light-switch game state, YakiHonne smart widgets, polls);
 * they read as packs with no stickers. Use [isStickerPack] before showing one in a picker.
 *
 * Searchable like the emoji pack, minus its `content`: the pack's title and description and each
 * sticker's shortcode, all public tags. The `content` is never indexed: it is empty for real packs
 * and holds other apps' JSON on colliding events. No references, so no graph edges.
 */
@Immutable
class StickerPackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun indexableContent() = (listOfNotNull(title(), description()) + stickers().map { it.code }).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(description())) return
        stickers().forEach { if (!visitor.visit(it.code)) return }
    }

    fun title() = tags.stickerPackTitle()

    fun description() = tags.stickerPackDescription()

    fun image() = tags.stickerPackImage()

    fun stickers(): List<StickerTag> = tags.stickers()

    /** True when the event carries at least one well-formed `sticker` tag. */
    fun isStickerPack() = tags.any(StickerTag::isTag)

    companion object {
        const val KIND = 30031
        const val ALT_DESCRIPTION = "Sticker pack"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            title: String,
            stickers: List<StickerTag>,
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<StickerPackEvent>.() -> Unit = {},
        ) = eventTemplate<StickerPackEvent>(KIND, "", createdAt) {
            dTag(dTag)
            title(title)
            stickers(stickers)
            initializer()
        }
    }
}
