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
package com.vitorpamplona.amethyst.commons.compose

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.max
import kotlin.math.min

// ── TextFieldState extensions (new BasicTextField) ──────────────────────────────────────────

fun TextFieldState.updateText(newText: String) {
    edit {
        replace(0, length, newText)
        placeCursorBeforeCharAt(length)
    }
}

fun TextFieldState.insertUrlAtCursor(url: String) {
    edit {
        var toInsert = url.trim()
        val text = toString()
        val selStart = selection.start
        val selEnd = selection.end

        if (selStart > 0 && text[selStart - 1] != ' ' && text[selStart - 1] != '\n') {
            toInsert = " $toInsert"
        }

        val endOfUrlIndex = selStart + toInsert.length

        if (selEnd < text.length && text[selEnd] != ' ' && text[selEnd] != '\n') {
            toInsert = "$toInsert "
        }

        replace(selStart, selEnd, toInsert)
        placeCursorBeforeCharAt(endOfUrlIndex)
    }
}

fun TextFieldState.replaceCurrentWord(wordToInsert: String) {
    edit {
        val text = toString()
        val lastWordStart = currentWordStartIdx(text, selection.start)
        val lastWordEnd = currentWordEndIdx(text, selection.start, selection.end)
        val cursor = lastWordStart + wordToInsert.length
        replace(lastWordStart, lastWordEnd, wordToInsert)
        placeCursorBeforeCharAt(cursor)
    }
}

fun TextFieldState.currentWord(): String {
    var result = ""
    edit {
        if (selection.end != selection.start) return@edit
        val str = toString()
        val start = currentWordStartIdx(str, selection.start)
        val end = currentWordEndIdx(str, selection.start, selection.end)
        result = if (start < end) str.substring(start, end) else ""
    }
    return result
}

private fun currentWordStartIdx(
    text: String,
    selectionStart: Int,
): Int {
    val previousNewLine = text.lastIndexOf('\n', selectionStart - 1)
    val previousSpace = text.lastIndexOf(' ', selectionStart - 1)
    return max(previousNewLine, previousSpace) + 1
}

private fun currentWordEndIdx(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
): Int {
    val nextNewLine = text.indexOf('\n', selectionEnd)
    val nextSpace = text.indexOf(' ', selectionEnd)

    if (nextSpace < 0 && nextNewLine < 0) return selectionEnd
    if (nextSpace > 0 && nextNewLine > 0) return min(nextNewLine, nextSpace)
    if (nextSpace > 0) return nextSpace
    return nextNewLine
}

// ── TextFieldValue extensions (legacy) ──────────────────────────────────────────────

fun TextFieldValue.insertUrlAtCursor(url: String): TextFieldValue {
    var toInsert = url.trim()
    if (selection.start > 0 && text[selection.start - 1] != ' ' && text[selection.start - 1] != '\n') {
        toInsert = " $toInsert"
    }

    // takes the position before adding an empty char after the url
    val endOfUrlIndex = selection.start + toInsert.length

    if (selection.end < text.length && text[selection.end] != ' ' && text[selection.end] != '\n') {
        toInsert = "$toInsert "
    }

    return TextFieldValue(
        text.replaceRange(selection.start, selection.end, toInsert),
        TextRange(endOfUrlIndex, endOfUrlIndex),
    )
}

fun TextFieldValue.replaceCurrentWord(wordToInsert: String): TextFieldValue {
    val lastWordStart = currentWordStartIdx()
    val lastWordEnd = currentWordEndIdx()
    val cursor = lastWordStart + wordToInsert.length
    return TextFieldValue(
        text.replaceRange(lastWordStart, lastWordEnd, wordToInsert),
        TextRange(cursor, cursor),
    )
}

fun TextFieldValue.currentWordStartIdx(): Int {
    val previousNewLine = text.lastIndexOf('\n', selection.start - 1)
    val previousSpace = text.lastIndexOf(' ', selection.start - 1)

    return max(
        previousNewLine,
        previousSpace,
    ) + 1
}

fun TextFieldValue.currentWordEndIdx(): Int {
    val nextNewLine = text.indexOf('\n', selection.end)
    val nextSpace = text.indexOf(' ', selection.end)

    if (nextSpace < 0 && nextNewLine < 0) return selection.end
    if (nextSpace > 0 && nextNewLine > 0) {
        return min(
            nextNewLine,
            nextSpace,
        )
    }
    if (nextSpace > 0) {
        return nextSpace
    }
    return nextNewLine
}

fun TextFieldValue.currentWord(): String {
    if (selection.end != selection.start) return ""

    val start = currentWordStartIdx()
    val end = currentWordEndIdx()

    return if (start < end) {
        val word = text.substring(start, end)
        word
    } else {
        ""
    }
}
