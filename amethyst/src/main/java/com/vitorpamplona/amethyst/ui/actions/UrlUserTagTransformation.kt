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
package com.vitorpamplona.amethyst.ui.actions

import android.util.Patterns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import kotlin.coroutines.cancellation.CancellationException

data class RangesChanges(
    val original: TextRange,
    val modified: TextRange,
)

class UrlUserTagTransformation(
    val color: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = buildAnnotatedStringWithUrlHighlighting(text, color)
}

fun buildAnnotatedStringWithUrlHighlighting(
    text: AnnotatedString,
    color: Color,
): TransformedText {
    val substitutions = mutableListOf<RangesChanges>()
    var originalPos = 0
    var transformedPos = 0

    val newText =
        buildAnnotatedString {
            val paragraphs = text.text.split('\n')

            for ((paragraphIndex, paragraph) in paragraphs.withIndex()) {
                if (paragraphIndex > 0) {
                    append('\n')
                    originalPos++
                    transformedPos++
                }

                val words = paragraph.split(' ')

                for ((wordIndex, word) in words.withIndex()) {
                    if (wordIndex > 0) {
                        append(' ')
                        originalPos++
                        transformedPos++
                    }

                    try {
                        if (word.startsWith("@npub") && word.length >= 64) {
                            val keyB32 = word.substring(0, 64)
                            val restOfWord = word.substring(64)

                            val key = decodePublicKey(keyB32.removePrefix("@"))
                            val user = LocalCache.getOrCreateUser(key.toHexKey())
                            val displayName = "@${user.toBestDisplayName()}"

                            substitutions.add(
                                RangesChanges(
                                    TextRange(originalPos, originalPos + keyB32.length),
                                    TextRange(transformedPos, transformedPos + displayName.length),
                                ),
                            )

                            append(displayName)
                            append(restOfWord)
                            originalPos += word.length
                            transformedPos += displayName.length + restOfWord.length
                        } else if (Patterns.WEB_URL.matcher(word).matches()) {
                            substitutions.add(
                                RangesChanges(
                                    TextRange(originalPos, originalPos + word.length),
                                    TextRange(transformedPos, transformedPos + word.length),
                                ),
                            )

                            append(word)
                            originalPos += word.length
                            transformedPos += word.length
                        } else {
                            append(word)
                            originalPos += word.length
                            transformedPos += word.length
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // if it can't parse the key, don't try to change.
                        append(word)
                        originalPos += word.length
                        transformedPos += word.length
                    }
                }
            }

            substitutions.forEach {
                addStyle(
                    style =
                        SpanStyle(
                            color = color,
                            textDecoration = TextDecoration.None,
                        ),
                    start = it.modified.start,
                    end = it.modified.end,
                )
            }
        }

    return TransformedText(
        newText,
        ProportionalOffsetMapping(substitutions),
    )
}

/**
 * Maps cursor offsets between original and transformed text using proportional
 * interpolation within substituted ranges (e.g. @npub... -> @DisplayName).
 *
 * For offsets inside a substitution, the cursor position is mapped proportionally
 * so it lands at a visually corresponding position in the other text.
 * For offsets outside substitutions, a 1:1 shift is applied based on the
 * cumulative length difference from preceding substitutions.
 */
private class ProportionalOffsetMapping(
    private val substitutions: List<RangesChanges>,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val inside = substitutions.find { offset > it.original.start && offset < it.original.end }
        if (inside != null) {
            val fraction = (offset - inside.original.start) / inside.original.length.toFloat()
            return (inside.modified.start + inside.modified.length * fraction).toInt()
        }

        val preceding = substitutions.lastOrNull { offset >= it.original.end }
        return if (preceding != null) {
            preceding.modified.end + (offset - preceding.original.end)
        } else {
            offset
        }
    }

    override fun transformedToOriginal(offset: Int): Int {
        val inside = substitutions.find { offset > it.modified.start && offset < it.modified.end }
        if (inside != null) {
            val fraction = (offset - inside.modified.start) / inside.modified.length.toFloat()
            return (inside.original.start + inside.original.length * fraction).toInt()
        }

        val preceding = substitutions.lastOrNull { offset >= it.modified.end }
        return if (preceding != null) {
            preceding.original.end + (offset - preceding.modified.end)
        } else {
            offset
        }
    }
}
