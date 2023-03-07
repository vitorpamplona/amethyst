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
import com.vitorpamplona.amethyst.model.decodePublicKey
import com.vitorpamplona.amethyst.model.toHexKey
import kotlin.math.roundToInt

data class RangesChanges(val original: TextRange, val modified: TextRange)

class UrlUserTagTransformation(val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return buildAnnotatedStringWithUrlHighlighting(text, color)
    }
}

fun buildAnnotatedStringWithUrlHighlighting(text: AnnotatedString, color: Color): TransformedText {
    val substitutions = mutableListOf<RangesChanges>()

    val newText = buildAnnotatedString {
        val builderBefore = StringBuilder() // important to correctly measure Tag start and end
        val builderAfter = StringBuilder() // important to correctly measure Tag start and end
        append(
            text.split('\n').map { paragraph: String ->
                paragraph.split(' ').map { word: String ->
                    try {
                        if (word.startsWith("@npub") && word.length >= 64) {
                            val keyB32 = word.substring(0, 64)
                            val restOfWord = word.substring(64)

                            val startIndex = builderBefore.toString().length

                            builderBefore.append("$keyB32$restOfWord ") // accounts for the \n at the end of each paragraph

                            val endIndex = startIndex + keyB32.length

                            val key = decodePublicKey(keyB32.removePrefix("@"))
                            val user = LocalCache.getOrCreateUser(key.toHexKey())

                            val newWord = "@${user.toBestDisplayName()}"
                            val startNew = builderAfter.toString().length

                            builderAfter.append("$newWord$restOfWord ") // accounts for the \n at the end of each paragraph

                            substitutions.add(
                                RangesChanges(
                                    TextRange(startIndex, endIndex),
                                    TextRange(startNew, startNew + newWord.length)
                                )
                            )
                            newWord + restOfWord
                        } else {
                            builderBefore.append(word + " ")
                            builderAfter.append(word + " ")
                            word
                        }
                    } catch (e: Exception) {
                        // if it can't parse the key, don't try to change.
                        builderBefore.append(word + " ")
                        builderAfter.append(word + " ")
                        word
                    }
                }.joinToString(" ")
            }.joinToString("\n")
        )

        val newText = toAnnotatedString()

        newText.split("\\s+".toRegex()).filter { word ->
            Patterns.WEB_URL.matcher(word).matches()
        }.forEach {
            val startIndex = text.indexOf(it)
            val endIndex = startIndex + it.length
            addStyle(
                style = SpanStyle(
                    color = color,
                    textDecoration = TextDecoration.None
                ),
                start = startIndex,
                end = endIndex
            )
        }

        substitutions.forEach {
            addStyle(
                style = SpanStyle(
                    color = color,
                    textDecoration = TextDecoration.None
                ),
                start = it.modified.start,
                end = it.modified.end
            )
        }
    }

    val numberOffsetTranslator = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val inInsideRange = substitutions.filter { offset > it.original.start && offset < it.original.end }.firstOrNull()

            if (inInsideRange != null) {
                val percentInRange = (offset - inInsideRange.original.start) / (inInsideRange.original.length.toFloat())
                return (inInsideRange.modified.start + inInsideRange.modified.length * percentInRange).roundToInt()
            }

            val lastRangeThrough = substitutions.lastOrNull { offset >= it.original.end }

            if (lastRangeThrough != null) {
                return lastRangeThrough.modified.end + (offset - lastRangeThrough.original.end)
            } else {
                return offset
            }
        }

        override fun transformedToOriginal(offset: Int): Int {
            val inInsideRange = substitutions.filter { offset > it.modified.start && offset < it.modified.end }.firstOrNull()

            if (inInsideRange != null) {
                val percentInRange = (offset - inInsideRange.modified.start) / (inInsideRange.modified.length.toFloat())
                return (inInsideRange.original.start + inInsideRange.original.length * percentInRange).roundToInt()
            }

            val lastRangeThrough = substitutions.lastOrNull { offset >= it.modified.end }

            if (lastRangeThrough != null) {
                return lastRangeThrough.original.end + (offset - lastRangeThrough.modified.end)
            } else {
                return offset
            }
        }
    }

    return TransformedText(
        newText,
        numberOffsetTranslator
    )
}
