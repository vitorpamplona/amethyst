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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists

fun String.isUTF16Char(pos: Int): Boolean = Character.charCount(this.codePointAt(pos)) == 2

fun String.firstFullCharOld(): String {
    return when (this.length) {
        0,
        1,
        -> return this
        2,
        3,
        -> return if (isUTF16Char(0)) this.take(2) else this.take(1)
        else -> {
            val first = isUTF16Char(0)
            val second = isUTF16Char(2)
            if (first && second) {
                this.take(4)
            } else if (first) {
                this.take(2)
            } else {
                this.take(1)
            }
        }
    }
}

fun String.firstFullChar(): String {
    var isInJoin = false
    var hasHadSecondChance = false
    var start = 0
    var previousCharLength = 0
    var next: Int
    var codePoint: Int

    var i = 0

    while (i < this.length) {
        codePoint = codePointAt(i)

        // Skips if it starts with the join char 0x200D
        if (codePoint == 0x200D && previousCharLength == 0) {
            next = offsetByCodePoints(i, 1)
            start = next
        } else {
            // If join, searches for the next char
            if (codePoint == 0xFE0F) {
            } else if (codePoint == 0x200D) {
                isInJoin = true
            } else {
                // stops when two chars are not joined together
                if (previousCharLength > 0 && !isInJoin) {
                    if (Character.charCount(codePoint) == 1 || hasHadSecondChance) {
                        break
                    } else {
                        hasHadSecondChance = true
                    }
                } else {
                    hasHadSecondChance = false
                }

                isInJoin = false
            }

            // next char to evaluate
            next = offsetByCodePoints(i, 1)
            previousCharLength += (next - i)
        }

        i = next
    }

    // if ends in join, then seachers backwards until a char is found.
    if (isInJoin) {
        i = previousCharLength - 1
        while (i > 0) {
            if (this[i].code == 0x200D) {
                previousCharLength -= 1
            } else {
                break
            }

            i -= 1
        }
    }

    return substring(start, start + previousCharLength)
}

fun String.firstFullCharOrEmoji(tags: ImmutableListOfLists<String>): String {
    if (length <= 2) {
        return firstFullChar()
    }

    if (this[0] == ':') {
        // makes sure an emoji exists
        val emojiParts = this.split(":", limit = 3)
        if (emojiParts.size >= 2) {
            val emojiName = emojiParts[1]
            val emojiUrl = tags.lists.firstOrNull { it.size > 1 && it[1] == emojiName }?.getOrNull(2)
            if (emojiUrl != null) {
                return ":$emojiName:$emojiUrl"
            }
        }
    }

    if (EmojiCoder.isCoded(this)) {
        return EmojiCoder.cropToFirstMessage(this)
    }

    return firstFullChar()
}
