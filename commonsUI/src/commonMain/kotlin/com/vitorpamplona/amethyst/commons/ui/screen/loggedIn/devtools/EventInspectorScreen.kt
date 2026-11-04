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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.devtools.EventInspectResult
import com.vitorpamplona.amethyst.commons.devtools.EventInspection
import com.vitorpamplona.amethyst.commons.devtools.EventIssue
import com.vitorpamplona.amethyst.commons.devtools.EventSource
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devtools_event_content
import com.vitorpamplona.amethyst.commons.resources.devtools_event_created_at
import com.vitorpamplona.amethyst.commons.resources.devtools_event_find_on_relays
import com.vitorpamplona.amethyst.commons.resources.devtools_event_future
import com.vitorpamplona.amethyst.commons.resources.devtools_event_id_mismatch
import com.vitorpamplona.amethyst.commons.resources.devtools_event_id_ok
import com.vitorpamplona.amethyst.commons.resources.devtools_event_input_label
import com.vitorpamplona.amethyst.commons.resources.devtools_event_invalid
import com.vitorpamplona.amethyst.commons.resources.devtools_event_json
import com.vitorpamplona.amethyst.commons.resources.devtools_event_kind_nip
import com.vitorpamplona.amethyst.commons.resources.devtools_event_sig_bad
import com.vitorpamplona.amethyst.commons.resources.devtools_event_sig_ok
import com.vitorpamplona.amethyst.commons.resources.devtools_event_source_command
import com.vitorpamplona.amethyst.commons.resources.devtools_event_source_nembed
import com.vitorpamplona.amethyst.commons.resources.devtools_event_source_relay
import com.vitorpamplona.amethyst.commons.resources.devtools_event_tags
import com.vitorpamplona.amethyst.commons.resources.devtools_event_title
import com.vitorpamplona.amethyst.commons.resources.devtools_event_unknown_kind
import com.vitorpamplona.amethyst.commons.resources.devtools_event_unparseable
import com.vitorpamplona.amethyst.commons.resources.devtools_event_valid
import com.vitorpamplona.amethyst.commons.resources.devtools_field_author
import com.vitorpamplona.amethyst.commons.resources.devtools_field_event_id
import com.vitorpamplona.amethyst.commons.resources.devtools_field_kind
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_usage_title
import com.vitorpamplona.amethyst.commons.resources.devtools_open_in_app
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsoluteNoDot
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.toPrettyJson

/**
 * The nak-web-style event verifier: paste an event (or a relay frame, or an nembed) and see
 * whether its id matches the content and its signature verifies, then read it field by field.
 * Opened from a note's menu, it starts with that note's event.
 */
@Composable
fun EventInspectorScreen(
    noteKey: String?,
    nav: INav,
) {
    var input by rememberSaveableText { noteKey?.let { LocalCache.getNoteIfExists(it)?.event?.toPrettyJson() } ?: "" }
    val result = rememberEventInspection(input).value

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
            DevToolsInput(input, { input = it }, stringRes(Res.string.devtools_event_input_label), minLines = 4, maxLines = 12)

            when (result) {
                EventInspectResult.Empty -> {}

                is EventInspectResult.Unparseable ->
                    DevToolsCheckRow(
                        MaterialSymbols.ErrorOutline,
                        stringRes(Res.string.devtools_event_unparseable, result.message),
                        MaterialTheme.colorScheme.error,
                    )

                is EventInspectResult.Parsed -> InspectionDetails(result.inspection, nav)
            }
        }
    }
}

@Composable
private fun InspectionDetails(
    inspection: EventInspection,
    nav: INav,
) {
    val event = inspection.event
    val ok = MaterialTheme.colorScheme.primary
    val bad = MaterialTheme.colorScheme.error

    Text(
        stringRes(if (inspection.isValid) Res.string.devtools_event_valid else Res.string.devtools_event_invalid),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (inspection.isValid) ok else bad,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )

    when (inspection.source) {
        EventSource.RELAY_MESSAGE -> Res.string.devtools_event_source_relay
        EventSource.CLIENT_COMMAND -> Res.string.devtools_event_source_command
        EventSource.NEMBED -> Res.string.devtools_event_source_nembed
        EventSource.JSON -> null
    }?.let {
        Text(stringRes(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (inspection.idValid) {
        DevToolsCheckRow(MaterialSymbols.CheckCircle, stringRes(Res.string.devtools_event_id_ok), ok)
    } else {
        DevToolsCheckRow(MaterialSymbols.Error, stringRes(Res.string.devtools_event_id_mismatch, inspection.computedId), bad)
    }
    if (inspection.signatureValid) {
        DevToolsCheckRow(MaterialSymbols.CheckCircle, stringRes(Res.string.devtools_event_sig_ok), ok)
    } else {
        DevToolsCheckRow(MaterialSymbols.Error, stringRes(Res.string.devtools_event_sig_bad), bad)
    }
    if (EventIssue.CREATED_IN_FUTURE in inspection.issues) {
        DevToolsCheckRow(MaterialSymbols.Warning, stringRes(Res.string.devtools_event_future), MaterialTheme.colorScheme.tertiary)
    }
    if (EventIssue.UNKNOWN_KIND in inspection.issues) {
        DevToolsCheckRow(MaterialSymbols.Info, stringRes(Res.string.devtools_event_unknown_kind, event.kind), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val kindLabel =
        inspection.kindNip?.let { stringRes(Res.string.devtools_event_kind_nip, kindName(event.kind), it) } ?: kindName(event.kind)
    DevToolsValueRow(stringRes(Res.string.devtools_field_kind) + " · " + kindLabel, event.kind.toString())
    DevToolsValueRow(stringRes(Res.string.devtools_field_event_id), event.id)
    DevToolsValueRow("nevent", inspection.nevent)
    DevToolsValueRow(stringRes(Res.string.devtools_field_author), event.pubKey)
    DevToolsValueRow("npub", inspection.npub)
    DevToolsValueRow(stringRes(Res.string.devtools_event_created_at) + " · " + timeAbsoluteNoDot(event.createdAt), event.createdAt.toString())

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedButton(onClick = { nav.nav(Route.DevToolsEventFinder(event.id)) }) {
            Text(stringRes(Res.string.devtools_event_find_on_relays))
        }
        OutlinedButton(onClick = { nav.nav(Route.DevToolsKindUsage(event.kind)) }) {
            Text(stringRes(Res.string.devtools_kind_usage_title, event.kind))
        }
        if (inspection.isValid) {
            OutlinedButton(onClick = { nav.nav(Route.Note(event.id)) }) {
                Text(stringRes(Res.string.devtools_open_in_app))
            }
        }
    }

    DevToolsSectionTitle(stringRes(Res.string.devtools_event_tags, event.tags.size))
    event.tags.forEach { tag -> TagRow(tag) }

    if (event.content.isNotEmpty()) {
        DevToolsSectionTitle(stringRes(Res.string.devtools_event_content))
        DevToolsCodeBlock(event.content, maxHeight = 240)
    }

    DevToolsSectionTitle(stringRes(Res.string.devtools_event_json))
    DevToolsCodeBlock(inspection.prettyJson)
}

/** One tag as chips: the name stands out, each value follows in order. */
@Composable
private fun TagRow(tag: Array<String>) {
    Row(Modifier.padding(vertical = 2.dp)) {
        SelectionContainer {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tag.forEachIndexed { index, value ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
