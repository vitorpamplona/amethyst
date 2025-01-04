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
package com.vitorpamplona.quartz.events

import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.IMetaTag
import com.vitorpamplona.quartz.encoders.Nip92MediaAttachments

open class InteractiveStoryBaseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun title() = firstTag("title")

    fun summary() = firstTag("summary")

    fun image() = firstTag("image")

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
            findHashtags(content).forEach {
                val lowercaseTag = it.lowercase()
                tags.add(arrayOf("t", it))
                if (it != lowercaseTag) {
                    tags.add(arrayOf("t", it.lowercase()))
                }
            }
            findURLs(content).forEach { tags.add(arrayOf("r", it)) }

            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let { tags.add(arrayOf("zapraiser", "$it")) }
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
                    arrayOf("alt", alt),
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
