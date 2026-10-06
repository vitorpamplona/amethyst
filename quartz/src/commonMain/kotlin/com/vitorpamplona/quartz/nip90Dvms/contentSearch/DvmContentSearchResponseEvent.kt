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
package com.vitorpamplona.quartz.nip90Dvms.contentSearch

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip90Dvms.DvmResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.tags.parseDvmResultTags
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class DvmContentSearchResponseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : DvmResponseEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    var events: List<HexKey>? = null

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var resultTagsCache: TagArray? = null

    /** The result list: `content` is a JSON tag array of `e` / `a` references. Parsed once. */
    fun resultTags(): TagArray = resultTagsCache ?: parseDvmResultTags(content, "DvmContentSearchResponseEvent").also { resultTagsCache = it }

    fun innerTags(): List<HexKey> {
        events?.let { return it }

        return resultTags()
            .mapNotNull {
                if (it.size > 1 && (it[0] == "e" || it[0] == "a")) {
                    it[1]
                } else {
                    null
                }
            }.also { events = it }
    }

    // The results are public references, just carried in content instead of tags.
    override fun eventHints(): List<EventIdHint> = super.eventHints() + resultTags().mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds(): List<HexKey> = super.linkedEventIds() + resultTags().mapNotNull(ETag::parseId)

    override fun addressHints(): List<AddressHint> = resultTags().mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = resultTags().mapNotNull(ATag::parseValidAddress)

    companion object {
        const val KIND = 6302

        fun build(
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DvmContentSearchResponseEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            initializer()
        }
    }
}

@Deprecated(
    "Renamed to DvmContentSearchResponseEvent. NIP-90 Data Vending Machine events use the Dvm prefix.",
    ReplaceWith("DvmContentSearchResponseEvent", "com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchResponseEvent"),
)
typealias NIP90ContentSearchResponseEvent = DvmContentSearchResponseEvent
