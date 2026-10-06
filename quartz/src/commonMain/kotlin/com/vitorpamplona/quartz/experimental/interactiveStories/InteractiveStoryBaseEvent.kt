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
package com.vitorpamplona.quartz.experimental.interactiveStories

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.experimental.interactiveStories.tags.StoryOptionTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent

@Stable
open class InteractiveStoryBaseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig),
    AddressHintProvider,
    SearchableEvent {
    // The option labels are the author's own prose (the choices a reader picks between), so
    // each one follows the body on its own line, the way poll options do.
    override fun indexableContent() = (listOfNotNull(title(), summary(), content) + optionLabels()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(summary())) return
        if (!visitor.visit(content)) return
        for (tag in tags) {
            val label = StoryOptionTag.parseLabel(tag) ?: continue
            if (!visitor.visit(label)) return
        }
    }

    // The story graph: every option points at the next scene's coordinate, usually with a relay.
    override fun addressHints(): List<AddressHint> = tags.mapNotNull(StoryOptionTag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(StoryOptionTag::parseAddressId)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun options() = tags.mapNotNull(StoryOptionTag::parse)

    /** The non-blank choice texts of [options], in tag order. */
    fun optionLabels() = tags.mapNotNull(StoryOptionTag::parseLabel)
}
