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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.commons.search.ActivePicker
import com.vitorpamplona.amethyst.commons.search.EditableToken
import com.vitorpamplona.amethyst.commons.search.PartialToken
import com.vitorpamplona.amethyst.commons.search.PartialTokens
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchCalendar
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * The search field's editing state: the text, where the caret is, and which picker that position
 * calls for.
 *
 * Which picker is open is *derived* from the text and the caret, never stored: a field can be
 * blurred with a token half-written and refocused an hour later, and it has to pick up exactly
 * where it left off. Storing "the people picker is open" instead would mean a second source of
 * truth that the caret could silently drift away from.
 */
@Stable
class SearchFieldState(
    initial: String = "",
) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        private set

    /** True while the field has focus; a picker only belongs under a field being typed in. */
    var focused by mutableStateOf(false)
        private set

    /** The month the calendar is showing, once the reader has paged away from the token's own. */
    private var pagedMonth by mutableStateOf<SearchDate?>(null)

    /** The day the keyboard cursor is on inside the grid, or null while the pointer leads. */
    var calendarCursor by mutableStateOf<SearchDate?>(null)
        private set

    val text: String get() = value.text

    /**
     * Where a token would settle, for the renderer: the caret while the reader is typing, and
     * null once they have left, so every token pills.
     */
    val settleCaret: Int? get() = if (focused && value.selection.collapsed) value.selection.start else null

    /**
     * The finished token the caret is on, which the field offers to change or remove.
     *
     * Never both this and [activePicker]: a picker is for a token still being written, this is
     * for one already written. Suppressed while a picker is up so the two cannot argue over the
     * space under the field.
     */
    val editableToken: EditableToken?
        get() =
            if (!focused || !value.selection.collapsed || activePicker != null) {
                null
            } else {
                PartialTokens.tokenAt(value.text, value.selection.start)
            }

    /** Which picker the caret's position calls for, or null. */
    val activePicker: ActivePicker?
        get() =
            if (!focused || !value.selection.collapsed) {
                null
            } else {
                PartialTokens.activePicker(value.text, value.selection.start)
            }

    /**
     * The month the calendar draws: the one the reader paged to, else the one they half-typed,
     * else this month.
     */
    fun calendarMonth(token: PartialToken): SearchDate = pagedMonth ?: SearchCalendar.typedMonth(token.partial) ?: LocalClock.today().firstOfMonth()

    fun onValueChange(next: TextFieldValue) {
        val tokenMoved = PartialTokens.dateAt(next.text, next.selection.start)?.start != PartialTokens.dateAt(value.text, value.selection.start)?.start
        value = next
        // A different date token is a different calendar; the month the reader paged to belonged
        // to the token they left.
        if (tokenMoved) resetCalendar()
    }

    fun setText(
        next: String,
        caret: Int = next.length,
    ) {
        value = TextFieldValue(next, TextRange(caret.coerceIn(0, next.length)))
        resetCalendar()
    }

    fun onFocusChanged(hasFocus: Boolean) {
        focused = hasFocus
        if (!hasFocus) resetCalendar()
    }

    fun clear() = setText("")

    // ---- picking ---------------------------------------------------------------------------

    /** Splice a finished token over the partial the caret is in, and put the caret past it. */
    fun replace(
        token: PartialToken,
        replacement: String,
    ) {
        val (next, caret) = PartialTokens.replaceToken(value.text, token, replacement)
        setText(next, caret)
    }

    /** The people picker's pick: the key is always written back as an npub, never as hex. */
    fun pickPerson(
        picker: ActivePicker.People,
        pubkeyHex: String,
    ) {
        val npub =
            try {
                NPub.create(pubkeyHex)
            } catch (_: Exception) {
                return
            }
        replace(picker.token, "${picker.keyField.token}:$npub")
    }

    fun pickDay(
        picker: ActivePicker.Calendar,
        day: String,
    ) = replace(picker.token, "${picker.dateField.token}:$day")

    fun pickGroup(
        picker: ActivePicker.Group,
        id: String,
    ) = replace(picker.token, "group:$id")

    fun pickKind(
        picker: ActivePicker.Kind,
        alias: String,
    ) = replace(picker.token, "kind:$alias")

    // ---- changing and removing a token that is already written ------------------------------

    /**
     * Drop [token] and the one space it leaves behind.
     *
     * The space matters: removing `kind:article` from `a kind:article b` has to leave `a b`, not
     * `a  b`, or every removal widens the gap a little more and a round trip through the parser
     * starts producing different text than it was given.
     */
    fun removeToken(token: EditableToken) {
        val text = value.text
        var start = token.start
        var end = token.end.coerceAtMost(text.length)
        if (end < text.length && text[end] == ' ') {
            end++
        } else if (start > 0 && text[start - 1] == ' ') {
            start--
        }
        setText(text.substring(0, start) + text.substring(end), start)
    }

    /**
     * Ask to change [token]: cut it back to its prefix so its picker opens on the spot, or — for
     * a token with no picker — select it so the next keystroke replaces it.
     *
     * Reopening by truncation rather than by a separate "edit mode" is what keeps this honest:
     * the field ends up in exactly the state it would be in had the reader typed `kind:` and
     * stopped, so every rule about what a picker offers and what a pick splices in still holds.
     */
    fun changeToken(token: EditableToken) {
        val text = value.text
        val end = token.end.coerceAtMost(text.length)
        val prefix = token.editPrefix
        if (prefix == null) {
            value = TextFieldValue(text, TextRange(token.start, end))
            resetCalendar()
            return
        }
        val next = text.substring(0, token.start) + prefix + text.substring(end)
        setText(next, token.start + prefix.length)
    }

    // ---- walking the calendar with the keyboard --------------------------------------------

    fun stepMonth(
        token: PartialToken,
        months: Int,
    ) {
        val next = calendarMonth(token).firstOfMonth(months)
        pagedMonth = next
        // A cursor that fell off the month it was on is not on any day the grid draws.
        if (!next.sameMonth(calendarCursor)) calendarCursor = null
    }

    /** Move the keyboard cursor by [days], opening the month it lands in. */
    fun moveCalendarCursor(
        token: PartialToken,
        days: Int,
    ) {
        val today = LocalClock.today()
        val shown = calendarMonth(token)
        val next =
            calendarCursor?.plusDays(days)
                ?: if (today.sameMonth(shown)) today else shown.firstOfMonth()
        calendarCursor = next
        pagedMonth = next.firstOfMonth()
    }

    fun hoverCalendar(day: SearchDate?) {
        calendarCursor = day
    }

    private fun resetCalendar() {
        pagedMonth = null
        calendarCursor = null
    }
}
