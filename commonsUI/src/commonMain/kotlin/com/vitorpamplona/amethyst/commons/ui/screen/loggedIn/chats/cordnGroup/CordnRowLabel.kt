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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_calendar_event
import com.vitorpamplona.amethyst.commons.resources.cordn_calendar_event_cancelled
import com.vitorpamplona.amethyst.commons.resources.cordn_call_ended
import com.vitorpamplona.amethyst.commons.resources.cordn_call_ended_titled
import com.vitorpamplona.amethyst.commons.resources.cordn_call_started
import com.vitorpamplona.amethyst.commons.resources.cordn_call_started_titled
import com.vitorpamplona.amethyst.commons.resources.cordn_call_unsupported
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.cordn.spec02Envelopes.CordnAnnotationIndex
import com.vitorpamplona.cordn.spec02Envelopes.CordnCalendar
import com.vitorpamplona.cordn.spec02Envelopes.CordnCallAnnouncement
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.cordn.spec02Envelopes.CordnStreamRows

/**
 * What a cordn row, its pinned entry and the room's inbox preview say for a message whose
 * `content` is not what to show: a call, a calendar event, or a kind this client does not draw
 * that carries a NIP-31 `alt`. Null for a message whose text (or its edit) is the row.
 *
 * One function for all three places, so the inbox cannot preview a call as an empty line while
 * the room draws it as "Call ended".
 */
@Composable
fun cordnRowLabel(
    envelope: CordnEnvelope,
    annotations: CordnAnnotationIndex,
    display: CordnStreamRows.Display = remember(envelope) { CordnStreamRows.display(envelope) },
): String? {
    // A calendar event's content is its description, often empty; the row names the event, and a
    // cancelled one stays visible as cancelled (calendar-events.md §6).
    if (CordnMessageKinds.isCalendarEvent(envelope.kind)) {
        return stringRes(
            if (annotations.isCancelled(envelope.id)) Res.string.cordn_calendar_event_cancelled else Res.string.cordn_calendar_event,
            CordnCalendar.titleOf(envelope),
        )
    }

    return when (display) {
        CordnStreamRows.Display.Regular -> null
        is CordnStreamRows.Display.Call -> cordnCallLine(display.announcement)
        CordnStreamRows.Display.UnsupportedCall -> stringRes(Res.string.cordn_call_unsupported)
        is CordnStreamRows.Display.Alt -> display.text
    }
}

/**
 * "Call started · Standup" / "Call ended". Worded from the parsed status rather than from the
 * announcement's `alt`: the `alt` is English, written for clients that cannot read the call, and
 * a sender-chosen string that can disagree with the `status` tag it summarises.
 */
@Composable
fun cordnCallLine(announcement: CordnCallAnnouncement): String {
    val title = announcement.title
    return when {
        announcement.isOpen && title != null -> stringRes(Res.string.cordn_call_started_titled, title)
        announcement.isOpen -> stringRes(Res.string.cordn_call_started)
        title != null -> stringRes(Res.string.cordn_call_ended_titled, title)
        else -> stringRes(Res.string.cordn_call_ended)
    }
}
