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
package com.vitorpamplona.quartz.experimental.interactiveStories

import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashMipMap
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.buildHashtagTags
import com.vitorpamplona.quartz.nip10Notes.content.buildUrlRefs
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip19Bech32.parse
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrl
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningSerializer
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupSerializer
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.ZapRaiserSerializer
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.Nip92MediaAttachments

open class InteractiveStoryBaseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun title() = tags.firstTagValue("title")

    fun summary() = tags.firstTagValue("summary")

    fun image() = tags.firstTagValue("image")

    fun options() =
        tags
            .filter { it.size > 2 && it[0] == "option" }
            .mapNotNull { ATag.parse(it[2], it.getOrNull(3))?.let { aTag -> StoryOption(it[1], aTag) } }

    companion object {
        fun generalTags(
            content: String,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            geohash: String? = null,
            imetas: List<IMetaTag>? = null,
            emojis: List<EmojiUrl>? = null,
        ): Array<Array<String>> {
            val tags = mutableListOf<Array<String>>()

            tags.addAll(buildHashtagTags(findHashtags(content)))
            tags.addAll(buildUrlRefs(findURLs(content)))
            zapReceiver?.forEach { tags.add(ZapSplitSetupSerializer.toTagArray(it)) }
            zapRaiserAmount?.let { tags.add(ZapRaiserSerializer.toTagArray(it)) }

            if (markAsSensitive) {
                tags.add(ContentWarningSerializer.toTagArray())
            }

            geohash?.let { tags.addAll(geohashMipMap(it)) }
            imetas?.forEach {
                tags.add(Nip92MediaAttachments.createTag(it))
            }
            emojis?.forEach { tags.add(it.toTagArray()) }
            return tags.toTypedArray()
        }

        fun makeTags(
            baseId: String,
            alt: String,
            title: String,
            summary: String? = null,
            image: String? = null,
            options: List<StoryOption> = emptyList(),
        ): Array<Array<String>> =
            (
                listOfNotNull(
                    arrayOf("d", baseId),
                    arrayOf("title", title),
                    summary?.let { arrayOf("summary", it) },
                    image?.let { arrayOf("image", it) },
                    AltTagSerializer.toTagArray(alt),
                ) +
                    options.map {
                        val relayUrl = it.address.relay
                        if (relayUrl != null) {
                            arrayOf("option", it.option, it.address.toTag(), relayUrl)
                        } else {
                            arrayOf("option", it.option, it.address.toTag())
                        }
                    }
            ).toTypedArray()
    }
}

class StoryOption(
    val option: String,
    val address: ATag,
)
