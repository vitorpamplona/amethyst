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
package com.vitorpamplona.quartz.nip30CustomEmoji

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.regex.Pattern

class CustomEmoji {
    companion object {
        val customEmojiPattern: Pattern =
            Pattern.compile("\\:([A-Za-z0-9_\\-]+)\\:", Pattern.CASE_INSENSITIVE)

        fun fastMightContainEmoji(
            input: String,
            allTags: ImmutableListOfLists<String>?,
        ): Boolean {
            if (allTags == null) return false
            if (allTags.lists.any { it.size > 2 && it[0] == EmojiUrlTag.TAG_NAME }) return true
            return input.contains(":")
        }

        fun fastMightContainEmoji(
            input: String,
            emojiPairs: Map<String, String>,
        ): Boolean {
            if (emojiPairs.isEmpty()) return false
            return input.contains(":")
        }

        fun createEmojiMap(tags: ImmutableListOfLists<String>): Map<String, String> = tags.lists.filter { it.size > 2 && it[0] == EmojiUrlTag.TAG_NAME }.associate { ":${it[1]}:" to it[2] }

        fun findAllEmojis(input: String): List<String> {
            val matcher = customEmojiPattern.matcher(input)
            val emojiNamesInOrder = mutableListOf<String>()
            while (matcher.find()) {
                emojiNamesInOrder.add(matcher.group())
            }
            return emojiNamesInOrder
        }

        fun findAllEmojiCodes(input: String): List<String> {
            val matcher = customEmojiPattern.matcher(input)
            val emojiNamesInOrder = mutableListOf<String>()
            while (matcher.find()) {
                matcher.group(1)?.let { emojiNamesInOrder.add(it) }
            }
            return emojiNamesInOrder
        }

        fun assembleAnnotatedList(
            input: String,
            allTags: ImmutableListOfLists<String>?,
        ): ImmutableList<Renderable>? {
            if (allTags == null || allTags.lists.isEmpty()) return null

            val emojiPairs = createEmojiMap(allTags)

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

            val regularCharsInOrder = input.split(customEmojiPattern.toRegex())

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
