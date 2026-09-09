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

/** A citation of something on the web (kind 31): a URL, optionally timestamped. */
@Immutable
class ExternalCitationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : CitationEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * The cited URL.
     *
     * The tag is `u`. The reference implementation's own manifest names it `url` while its card
     * reads `u`; `u` is what the draft builder actually writes, so that is the wire truth and
     * `url` is accepted only so a publisher who followed the manifest is not dropped.
     */
    fun url() = value(CitationTags.URL) ?: value("url")

    /** The id of a NIP-03 kind-1040 timestamp attesting when the page was seen. */
    fun openTimestamp() = value(CitationTags.OPEN_TIMESTAMP)

    override fun displayTitle(): String? = title() ?: url()

    override fun hasSource(): Boolean = url() != null || super.hasSource()

    companion object {
        const val KIND = 31

        fun build(
            url: String,
            accessedOn: String,
            title: String? = null,
            author: String? = null,
            summary: String? = null,
            note: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ExternalCitationEvent>.() -> Unit = {},
        ): EventTemplate<ExternalCitationEvent> =
            eventTemplate(KIND, note, createdAt) {
                add(CitationTags.assemble(CitationTags.URL, url))
                add(CitationTags.assemble(CitationTags.ACCESSED_ON, accessedOn))
                title?.let { add(TitleTag.assemble(it)) }
                author?.let { add(CitationTags.assemble(CitationTags.AUTHOR, it)) }
                summary?.let { add(CitationTags.assemble(CitationTags.SUMMARY, it)) }

                initializer()
            }
    }
}
