/**
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
package com.vitorpamplona.quartz.nip30CustomEmoji

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class CustomEmoji {
    companion object {
        val customEmojiPattern: Regex =
            Regex("\\:([A-Za-z0-9_\\-]+)\\:", RegexOption.IGNORE_CASE)

        fun fastMightContainEmoji(
            input: String,
            allTags: Array<Array<String>>?,
        ): Boolean {
            if (allTags == null) return false
            if (allTags.any { it.size > 2 && it[0] == EmojiUrlTag.TAG_NAME }) return true
            return input.contains(":")
        }

        fun fastMightContainEmoji(
            input: String,
            emojiPairs: Map<String, String>,
        ): Boolean {
            if (emojiPairs.isEmpty()) return false
            return input.contains(":")
        }

        fun createEmojiMap(tags: Array<Array<String>>): Map<String, String> = tags.filter { it.size > 2 && it[0] == EmojiUrlTag.TAG_NAME }.associate { ":${it[1]}:" to it[2] }

        fun findAllEmojis(input: String): List<String> {
            val matcher = customEmojiPattern.findAll(input)
            return matcher
                .mapNotNull {
                    it.groups[0]?.value
                }.toList()
        }

        fun findAllEmojiCodes(input: String): List<String> {
            val matcher = customEmojiPattern.findAll(input)
            return matcher
                .mapNotNull {
                    it.groups[1]?.value
                }.toList()
        }

        fun assembleAnnotatedList(
            input: String,
            tags: Array<Array<String>>?,
        ): ImmutableList<Renderable>? {
            if (tags == null || tags.isEmpty()) return null

            val emojiPairs = createEmojiMap(tags)

            return assembleAnnotatedList(input, emojiPairs)
        }

        fun assembleAnnotatedList(
            input: String,
            emojiPairs: Map<String, String>,
        ): ImmutableList<Renderable>? {
            val emojiNamesInOrder = findAllEmojis(input)
            if (emojiNamesInOrder.isEmpty()) {
                return null
            }

            val regularCharsInOrder = input.split(customEmojiPattern)

            val finalList = mutableListOf<Renderable>()

            // Merge the two lists in Order.
            var index = 0
            for (word in regularCharsInOrder) {
                if (word.isNotEmpty()) {
                    finalList.add(TextType(word))
                }
                if (index < emojiNamesInOrder.size) {
                    val url = emojiPairs[emojiNamesInOrder[index]]

                    if (url != null) {
                        finalList.add(ImageUrlType(url))
                    } else {
                        if (word.isNotEmpty()) {
                            finalList.add(TextType(word))
                        }
                    }
                }
                index++
            }

            return finalList.toImmutableList()
        }
    }

    @Immutable open class Renderable

    @Immutable class TextType(
        val text: String,
    ) : Renderable()

    @Immutable class ImageUrlType(
        val url: String,
    ) : Renderable()
}
