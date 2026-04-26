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

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlin.coroutines.cancellation.CancellationException

class UrlUserTagOutputTransformation(
    val color: Color,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val text = asCharSequence().toString()

        // Reverse so earlier indices remain valid after each replace.
        val matches = MENTION_REGEX.findAll(text).toList().reversed()

        // Phase 1: Replace all mentions (reverse order keeps indices valid for replace).
        // Collect replacement info because addStyle must be called after all text mutations.
        // (originalStart, originalMatchLength, displayNameLength)
        val replacements = mutableListOf<Triple<Int, Int, Int>>()

        for (match in matches) {
            try {
                val bech32 =
                    match.value
                        .removePrefix("@")
                        .removePrefix("nostr:")
                val hex = decodePublicKeyAsHexOrNull(bech32) ?: continue
                val user = LocalCache.getOrCreateUser(hex)
                val displayName = "@${user.toBestDisplayName()}"

                replace(match.range.first, match.range.last + 1, displayName)
                replacements.add(Triple(match.range.first, match.range.last + 1 - match.range.first, displayName.length))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        // Phase 2: Apply styles after all text mutations are finalized.
        // Iterate in forward document order, tracking cumulative shift from prior replacements.
        val style = SpanStyle(color = color, textDecoration = TextDecoration.None)
        var cumulativeShift = 0
        for ((originalStart, originalLen, newLen) in replacements.reversed()) {
            val adjustedStart = originalStart + cumulativeShift
            addStyle(style, adjustedStart, adjustedStart + newLen)
            cumulativeShift += newLen - originalLen
        }

        // Highlight URLs in remaining text
        highlightUrls(color)
    }
}

private fun TextFieldBuffer.highlightUrls(color: Color) {
    val text = asCharSequence().toString()
    val urlPattern = android.util.Patterns.WEB_URL

    val matcher = urlPattern.matcher(text)
    while (matcher.find()) {
        addStyle(
            SpanStyle(color = color, textDecoration = TextDecoration.None),
            matcher.start(),
            matcher.end(),
        )
    }
}
