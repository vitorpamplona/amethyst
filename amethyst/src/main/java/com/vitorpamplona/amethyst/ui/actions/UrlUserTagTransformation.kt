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
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import kotlin.coroutines.cancellation.CancellationException

private val WORD_BOUNDARY = Regex("[\\s]+")

class UrlUserTagTransformation(
    val color: Color,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val text = asCharSequence().toString()
        // Collect replacements and highlights: (start, end, replacement?)
        val actions = mutableListOf<Triple<Int, Int, String?>>()

        var searchStart = 0
        for (match in WORD_BOUNDARY.findAll(text)) {
            val wordStart = searchStart
            val wordEnd = match.range.first
            if (wordEnd > wordStart) {
                processWord(text.substring(wordStart, wordEnd), wordStart, wordEnd, actions)
            }
            searchStart = match.range.last + 1
        }
        // Last word
        if (searchStart < text.length) {
            processWord(text.substring(searchStart), searchStart, text.length, actions)
        }

        // Apply in reverse to preserve indices for replacements
        for ((start, end, replacement) in actions.reversed()) {
            if (replacement != null) {
                replace(start, end, replacement)
                addStyle(
                    SpanStyle(color = color, textDecoration = TextDecoration.None),
                    start,
                    start + replacement.length,
                )
            } else {
                addStyle(
                    SpanStyle(color = color, textDecoration = TextDecoration.None),
                    start,
                    end,
                )
            }
        }
    }

    private fun processWord(
        word: String,
        start: Int,
        end: Int,
        actions: MutableList<Triple<Int, Int, String?>>,
    ) {
        try {
            if (word.startsWith("@npub") && word.length >= 64) {
                val keyB32 = word.substring(0, 64)
                val restOfWord = word.substring(64)
                val key = decodePublicKey(keyB32.removePrefix("@"))
                val user = LocalCache.getOrCreateUser(key.toHexKey())
                val displayName = "@${user.toBestDisplayName()}$restOfWord"
                actions.add(Triple(start, end, displayName))
            } else if (Patterns.WEB_URL.matcher(word).matches()) {
                actions.add(Triple(start, end, null))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }
}
