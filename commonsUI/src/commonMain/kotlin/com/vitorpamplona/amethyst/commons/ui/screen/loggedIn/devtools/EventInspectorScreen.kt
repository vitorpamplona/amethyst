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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.devtools.EventCodes
import com.vitorpamplona.amethyst.commons.devtools.EventInspectResult
import com.vitorpamplona.amethyst.commons.devtools.EventInspection
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devtools_event_find_on_relays
import com.vitorpamplona.amethyst.commons.resources.devtools_event_id_mismatch
import com.vitorpamplona.amethyst.commons.resources.devtools_event_input_label
import com.vitorpamplona.amethyst.commons.resources.devtools_event_sig_bad
import com.vitorpamplona.amethyst.commons.resources.devtools_event_title
import com.vitorpamplona.amethyst.commons.resources.devtools_event_unparseable
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_usage_title
import com.vitorpamplona.amethyst.commons.resources.devtools_open_in_app
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.toPrettyJson
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * The event inspector. Opened from a note's menu it shows that note's event as read-only,
 * syntax-coloured JSON with its nevent, naddr and npub: the app verified the event before it got
 * here, and every other field is right there in the JSON. Opened from the dev tools hub it takes a
 * pasted event (or a relay frame, or an nembed) and only speaks up about the id or signature when
 * one of them fails, since a pasted event has not been verified by anyone.
 */
@Composable
fun EventInspectorScreen(
    noteKey: String?,
    nav: INav,
) {
    val cached = remember(noteKey) { noteKey?.let { LocalCache.getNoteIfExists(it)?.event } }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.devtools_event_title), nav) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
        ) {
            if (cached != null) {
                CachedEventDetails(cached, nav)
            } else {
                PastedEventInspector(nav)
            }
        }
    }
}

@Composable
private fun CachedEventDetails(
    event: Event,
    nav: INav,
) {
    val json = remember(event) { event.toPrettyJson() }
    val codes = remember(event) { EventCodes.of(event) }

    DevToolsJsonBlock(json)
    EventCodeRows(codes, Modifier.padding(top = 8.dp))
    EventActions(event, canOpen = true, nav)
}

@Composable
private fun PastedEventInspector(nav: INav) {
    var input by rememberSaveableText()
    val result = rememberEventInspection(input).value

    DevToolsInput(input, { input = it }, stringRes(Res.string.devtools_event_input_label), minLines = 4, maxLines = 12)

    when (result) {
        EventInspectResult.Empty -> {}

        is EventInspectResult.Unparseable ->
            DevToolsCheckRow(
                MaterialSymbols.ErrorOutline,
                stringRes(Res.string.devtools_event_unparseable, result.message),
                MaterialTheme.colorScheme.error,
            )

        is EventInspectResult.Parsed -> PastedEventDetails(result.inspection, nav)
    }
}

@Composable
private fun PastedEventDetails(
    inspection: EventInspection,
    nav: INav,
) {
    val bad = MaterialTheme.colorScheme.error
    if (!inspection.idValid) {
        DevToolsCheckRow(MaterialSymbols.Error, stringRes(Res.string.devtools_event_id_mismatch, inspection.computedId), bad)
    }
    if (!inspection.signatureValid) {
        DevToolsCheckRow(MaterialSymbols.Error, stringRes(Res.string.devtools_event_sig_bad), bad)
    }

    EventCodeRows(inspection.codes)
    EventActions(inspection.event, canOpen = inspection.isValid, nav)
}

@Composable
private fun EventCodeRows(
    codes: EventCodes,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        DevToolsValueRow("nevent", codes.nevent)
        codes.naddr?.let { DevToolsValueRow("naddr", it) }
        DevToolsValueRow("npub", codes.npub)
    }
}

@Composable
private fun EventActions(
    event: Event,
    canOpen: Boolean,
    nav: INav,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedButton(onClick = { nav.nav(Route.DevToolsEventFinder(event.id)) }) {
            Text(stringRes(Res.string.devtools_event_find_on_relays))
        }
        OutlinedButton(onClick = { nav.nav(Route.DevToolsKindUsage(event.kind)) }) {
            Text(stringRes(Res.string.devtools_kind_usage_title, event.kind))
        }
        if (canOpen) {
            OutlinedButton(onClick = { nav.nav(Route.Note(event.id)) }) {
                Text(stringRes(Res.string.devtools_open_in_app))
            }
        }
    }
}
