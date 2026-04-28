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
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
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

    val newText =
        buildAnnotatedString {
            val builderBefore = StringBuilder() // important to correctly measure Tag start and end
            val builderAfter = StringBuilder() // important to correctly measure Tag start and end
            append(
                text.text.split('\n').joinToString("\n") { paragraph: String ->
                    paragraph.split(' ').joinToString(" ") { word: String ->
                        try {
                            val mention = parseUserMention(word)
                            if (mention != null) {
                                val (keyPortion, restOfWord, hexKey) = mention

                                val startIndex = builderBefore.toString().length

                                builderBefore.append(
                                    "$keyPortion$restOfWord ",
                                ) // accounts for the \n at the end of each paragraph

                                val endIndex = startIndex + keyPortion.length

                                val user = LocalCache.getOrCreateUser(hexKey)

                                val newWord = "@${user.toBestDisplayName()}"
                                val startNew = builderAfter.toString().length

                                builderAfter.append(
                                    "$newWord$restOfWord ",
                                ) // accounts for the \n at the end of each paragraph

                                substitutions.add(
                                    RangesChanges(
                                        TextRange(startIndex, endIndex),
                                        TextRange(startNew, startNew + newWord.length),
                                    ),
                                )
                                newWord + restOfWord
                            } else if (Patterns.WEB_URL.matcher(word).matches()) {
                                val startIndex = builderBefore.toString().length
                                val endIndex = startIndex + word.length

                                val startNew = builderAfter.toString().length
                                val endNew = startNew + word.length

                                substitutions.add(
                                    RangesChanges(
                                        TextRange(startIndex, endIndex),
                                        TextRange(startNew, endNew),
                                    ),
                                )

                                builderBefore.append("$word ")
                                builderAfter.append("$word ")
                                word
                            } else {
                                builderBefore.append("$word ")
                                builderAfter.append("$word ")
                                word
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            // if it can't parse the key, don't try to change.
                            builderBefore.append("$word ")
                            builderAfter.append("$word ")
                            word
                        }
                    }
                },
            )

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

    val numberOffsetTranslator =
        object : OffsetMapping {
            // Treat each substitution as an atomic wedge: any cursor position that falls
            // strictly inside a substituted range snaps to the wedge's trailing edge.
            // Without this, an IME (e.g. SwiftKey in extracted-text mode) can place the
            // cursor in the middle of an "@npub1..." mention, and a subsequent backspace
            // deletes a char from inside the bech32, breaking the npub and "expanding"
            // the collapsed mention.
            override fun originalToTransformed(offset: Int): Int {
                val inInsideRange =
                    substitutions.firstOrNull { offset > it.original.start && offset < it.original.end }

                if (inInsideRange != null) {
                    return inInsideRange.modified.end
                }

                val lastRangeThrough = substitutions.lastOrNull { offset >= it.original.end }

                return if (lastRangeThrough != null) {
                    lastRangeThrough.modified.end + (offset - lastRangeThrough.original.end)
                } else {
                    offset
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val inInsideRange =
                    substitutions.firstOrNull { offset > it.modified.start && offset < it.modified.end }

                if (inInsideRange != null) {
                    return inInsideRange.original.end
                }

                val lastRangeThrough = substitutions.lastOrNull { offset >= it.modified.end }

                return if (lastRangeThrough != null) {
                    lastRangeThrough.original.end + (offset - lastRangeThrough.modified.end)
                } else {
                    offset
                }
            }
        }

    return TransformedText(
        newText,
        numberOffsetTranslator,
    )
}

private data class UserMention(
    val keyPortion: String,
    val restOfWord: String,
    val hexKey: String,
)

private fun parseUserMention(word: String): UserMention? {
    var key = word
    val prefix: String

    if (key.startsWith("nostr:", true)) {
        prefix = key.substring(0, 6)
        key = key.substring(6)
    } else if (key.startsWith("@")) {
        prefix = "@"
        key = key.substring(1)
    } else {
        return null
    }

    if (key.startsWith("npub1", true) && key.length >= 63) {
        val keyB32 = key.substring(0, 63)
        val restOfWord = key.substring(63)
        val hex = decodePublicKeyAsHexOrNull(keyB32) ?: return null
        return UserMention("$prefix$keyB32", restOfWord, hex)
    } else if (key.startsWith("nprofile1", true)) {
        val parsed = Nip19Parser.uriToRoute(key) ?: return null
        val entity = parsed.entity
        if (entity !is NProfile && entity !is NPub) return null
        val hex =
            when (entity) {
                is NProfile -> entity.hex
                is NPub -> entity.hex
                else -> return null
            }
        val bech32Len = parsed.nip19raw.length
        val restOfWord = key.substring(bech32Len)
        return UserMention("$prefix${parsed.nip19raw}", restOfWord, hex)
    }

    return null
}
