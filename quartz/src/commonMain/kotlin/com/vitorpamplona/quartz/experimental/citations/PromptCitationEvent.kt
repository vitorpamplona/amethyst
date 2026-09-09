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
package com.vitorpamplona.quartz.experimental.citations

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.citations.tags.CitationTags
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A citation of an LLM prompt (kind 33): which model was asked, and what was asked of it.
 *
 * The `content` is the prompt (and often the answer), which the reference implementation renders
 * as Markdown rather than as tokenized plain text — the one citation kind that does.
 */
@Immutable
class PromptCitationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : CitationEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The model that was prompted, as named by the citer. */
    fun llm() = value(CitationTags.LLM)

    /** A link to the conversation, when the citer published one. */
    fun url() = value(CitationTags.URL)

    override fun displayTitle(): String? = title() ?: llm()

    override fun hasSource(): Boolean = llm() != null || super.hasSource()

    companion object {
        const val KIND = 33

        fun build(
            llm: String,
            accessedOn: String,
            prompt: String,
            title: String? = null,
            url: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PromptCitationEvent>.() -> Unit = {},
        ): EventTemplate<PromptCitationEvent> =
            eventTemplate(KIND, prompt, createdAt) {
                add(CitationTags.assemble(CitationTags.LLM, llm))
                add(CitationTags.assemble(CitationTags.ACCESSED_ON, accessedOn))
                title?.let { add(TitleTag.assemble(it)) }
                url?.let { add(CitationTags.assemble(CitationTags.URL, it)) }

                initializer()
            }
    }
}
