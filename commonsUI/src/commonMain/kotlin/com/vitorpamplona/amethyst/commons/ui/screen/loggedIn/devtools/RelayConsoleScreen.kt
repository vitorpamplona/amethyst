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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.devtools.ConsoleConnection
import com.vitorpamplona.amethyst.commons.devtools.ConsoleFrame
import com.vitorpamplona.amethyst.commons.devtools.ConsoleSystemEvent
import com.vitorpamplona.amethyst.commons.devtools.EventInspectResult
import com.vitorpamplona.amethyst.commons.devtools.EventInspector
import com.vitorpamplona.amethyst.commons.devtools.FilterDraft
import com.vitorpamplona.amethyst.commons.devtools.FilterField
import com.vitorpamplona.amethyst.commons.devtools.FrameDirection
import com.vitorpamplona.amethyst.commons.devtools.RelayConsoleViewModel
import com.vitorpamplona.amethyst.commons.devtools.SinceWindow
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth_body
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth_review
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth_sign
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth_title
import com.vitorpamplona.amethyst.commons.resources.devtools_console_clear
import com.vitorpamplona.amethyst.commons.resources.devtools_console_closed
import com.vitorpamplona.amethyst.commons.resources.devtools_console_connect
import com.vitorpamplona.amethyst.commons.resources.devtools_console_connected
import com.vitorpamplona.amethyst.commons.resources.devtools_console_connecting
import com.vitorpamplona.amethyst.commons.resources.devtools_console_count_title
import com.vitorpamplona.amethyst.commons.resources.devtools_console_disconnect
import com.vitorpamplona.amethyst.commons.resources.devtools_console_edit_as_text
import com.vitorpamplona.amethyst.commons.resources.devtools_console_empty
import com.vitorpamplona.amethyst.commons.resources.devtools_console_event_field
import com.vitorpamplona.amethyst.commons.resources.devtools_console_event_title
import com.vitorpamplona.amethyst.commons.resources.devtools_console_failed
import com.vitorpamplona.amethyst.commons.resources.devtools_console_invalid_url
import com.vitorpamplona.amethyst.commons.resources.devtools_console_message
import com.vitorpamplona.amethyst.commons.resources.devtools_console_no_open_subs
import com.vitorpamplona.amethyst.commons.resources.devtools_console_req_title
import com.vitorpamplona.amethyst.commons.resources.devtools_console_send_failed
import com.vitorpamplona.amethyst.commons.resources.devtools_console_title
import com.vitorpamplona.amethyst.commons.resources.devtools_console_tor
import com.vitorpamplona.amethyst.commons.resources.devtools_console_url
import com.vitorpamplona.amethyst.commons.resources.devtools_event_invalid
import com.vitorpamplona.amethyst.commons.resources.devtools_event_unparseable
import com.vitorpamplona.amethyst.commons.resources.devtools_event_valid
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_any
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_authors
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_day
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_hour
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_ids
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_invalid
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_kinds
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_limit
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_me
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_month
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_search
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_since
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_tag
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_tag_values
import com.vitorpamplona.amethyst.commons.resources.devtools_filter_week
import com.vitorpamplona.amethyst.commons.resources.send
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import org.jetbrains.compose.resources.StringResource

private enum class ConsoleDialog {
    REQ,
    COUNT,
    EVENT,
    AUTH,
}

/**
 * A websocketking.com for relays: one raw socket, every frame both ways as the relay sent it.
 * The action row builds REQ and COUNT from a form, closes open subscriptions, publishes a pasted
 * signed event and answers AUTH, which always asks first. The text field sends anything else.
 */
@Composable
fun RelayConsoleScreen(
    initialUrl: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel =
        rememberViewModel(key = "RelayConsole") {
            RelayConsoleViewModel(accountViewModel.host.websocketBuilder, accountViewModel.account.signer)
        }
    val frames by viewModel.frames.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val challenge by viewModel.pendingChallenge.collectAsStateWithLifecycle()
    val openSubs by viewModel.openSubs.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf(initialUrl ?: "wss://") }
    var message by rememberSaveable { mutableStateOf("") }
    var dialog by remember { mutableStateOf<ConsoleDialog?>(null) }
    var draft by remember { mutableStateOf(FilterDraft(kinds = "1", limit = "5")) }
    var closeMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val connected = connection == ConsoleConnection.CONNECTED
    val myPubKey = accountViewModel.account.signer.pubKey

    LaunchedEffect(frames.size) {
        if (frames.isNotEmpty()) listState.animateScrollToItem(frames.lastIndex)
    }

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.devtools_console_title), nav) {
                IconButton(onClick = viewModel::clear) {
                    Icon(MaterialSymbols.DeleteSweep, contentDescription = stringRes(Res.string.devtools_console_clear))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionDot(connection)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringRes(Res.string.devtools_console_url)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                if (connection == ConsoleConnection.DISCONNECTED) {
                    Button(onClick = { viewModel.connect(url) }) { Text(stringRes(Res.string.devtools_console_connect)) }
                } else {
                    OutlinedButton(onClick = viewModel::disconnect) { Text(stringRes(Res.string.devtools_console_disconnect)) }
                }
            }

            if (challenge != null && viewModel.canSign) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringRes(Res.string.devtools_console_auth), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { dialog = ConsoleDialog.AUTH }) { Text(stringRes(Res.string.devtools_console_auth_review)) }
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            ) {
                if (frames.isEmpty()) {
                    Text(
                        stringRes(Res.string.devtools_console_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(frames, key = { it.id }) { FrameRow(it) }
                }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(enabled = connected, onClick = { dialog = ConsoleDialog.REQ }, label = { Text("REQ") })
                AssistChip(enabled = connected, onClick = { dialog = ConsoleDialog.COUNT }, label = { Text("COUNT") })
                Box {
                    AssistChip(
                        enabled = connected && openSubs.isNotEmpty(),
                        onClick = { if (openSubs.size == 1) viewModel.close(openSubs.first()) else closeMenu = true },
                        label = { Text(if (openSubs.isEmpty()) "CLOSE" else "CLOSE (${openSubs.size})") },
                    )
                    DropdownMenu(expanded = closeMenu, onDismissRequest = { closeMenu = false }) {
                        if (openSubs.isEmpty()) {
                            DropdownMenuItem(text = { Text(stringRes(Res.string.devtools_console_no_open_subs)) }, onClick = { closeMenu = false }, enabled = false)
                        }
                        openSubs.reversed().forEach { sub ->
                            DropdownMenuItem(
                                text = { Text(sub, fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    viewModel.close(sub)
                                    closeMenu = false
                                },
                            )
                        }
                    }
                }
                AssistChip(enabled = connected, onClick = { dialog = ConsoleDialog.EVENT }, label = { Text("EVENT") })
                AssistChip(
                    enabled = connected && challenge != null && viewModel.canSign,
                    onClick = { dialog = ConsoleDialog.AUTH },
                    label = { Text("AUTH") },
                    colors =
                        if (challenge != null) {
                            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringRes(Res.string.devtools_console_message)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 6,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { if (viewModel.send(message)) message = "" },
                    enabled = connected && message.isNotBlank(),
                ) {
                    Text(stringRes(Res.string.send))
                }
            }
        }
    }

    when (dialog) {
        ConsoleDialog.REQ, ConsoleDialog.COUNT -> {
            val isReq = dialog == ConsoleDialog.REQ
            FilterBuilderDialog(
                title = if (isReq) Res.string.devtools_console_req_title else Res.string.devtools_console_count_title,
                draft = draft,
                myPubKey = myPubKey,
                onDraftChange = { draft = it },
                onSend = { filter ->
                    if (isReq) viewModel.sendReq(filter) else viewModel.sendCount(filter)
                    dialog = null
                },
                onEditAsText = { filter ->
                    message = (if (isReq) "[\"REQ\",\"console\"," else "[\"COUNT\",\"console\",") + filter.toJson() + "]"
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }

        ConsoleDialog.EVENT ->
            EventPublishDialog(
                onSend = { event ->
                    viewModel.sendEvent(event)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )

        ConsoleDialog.AUTH -> {
            val current = challenge
            if (current == null) {
                dialog = null
            } else {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(stringRes(Res.string.devtools_console_auth_title)) },
                    text = { Text(stringRes(Res.string.devtools_console_auth_body, url.trim(), current, NPub.create(myPubKey))) },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.answerAuth()
                            dialog = null
                        }) { Text(stringRes(Res.string.devtools_console_auth_sign)) }
                    },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringRes(Res.string.cancel)) } },
                )
            }
        }

        null -> {}
    }
}

/** REQ/COUNT from a form: every field optional, the built frame previewed as it will be sent. */
@Composable
private fun FilterBuilderDialog(
    title: StringResource,
    draft: FilterDraft,
    myPubKey: String,
    onDraftChange: (FilterDraft) -> Unit,
    onSend: (Filter) -> Unit,
    onEditAsText: (Filter) -> Unit,
    onDismiss: () -> Unit,
) {
    val result = remember(draft) { draft.build() }
    val filter = (result as? FilterDraft.Result.Ok)?.filter

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormField(Res.string.devtools_filter_kinds, draft.kinds, result, FilterField.KINDS) { onDraftChange(draft.copy(kinds = it)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormField(Res.string.devtools_filter_authors, draft.authors, result, FilterField.AUTHORS, Modifier.weight(1f)) {
                        onDraftChange(draft.copy(authors = it))
                    }
                    TextButton(onClick = { onDraftChange(draft.copy(authors = (draft.authors.trim() + " " + NPub.create(myPubKey)).trim())) }) {
                        Text(stringRes(Res.string.devtools_filter_me))
                    }
                }
                FormField(Res.string.devtools_filter_ids, draft.ids, result, FilterField.IDS) { onDraftChange(draft.copy(ids = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormField(Res.string.devtools_filter_tag, draft.tagName, result, FilterField.TAG, Modifier.width(72.dp)) {
                        onDraftChange(draft.copy(tagName = it.take(2)))
                    }
                    FormField(Res.string.devtools_filter_tag_values, draft.tagValues, null, FilterField.TAG, Modifier.weight(1f)) {
                        onDraftChange(draft.copy(tagValues = it))
                    }
                }
                Text(stringRes(Res.string.devtools_filter_since), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SinceChip(Res.string.devtools_filter_any, draft.since == null) { onDraftChange(draft.copy(since = null)) }
                    SinceChip(Res.string.devtools_filter_hour, draft.since == SinceWindow.HOUR) { onDraftChange(draft.copy(since = SinceWindow.HOUR)) }
                    SinceChip(Res.string.devtools_filter_day, draft.since == SinceWindow.DAY) { onDraftChange(draft.copy(since = SinceWindow.DAY)) }
                    SinceChip(Res.string.devtools_filter_week, draft.since == SinceWindow.WEEK) { onDraftChange(draft.copy(since = SinceWindow.WEEK)) }
                    SinceChip(Res.string.devtools_filter_month, draft.since == SinceWindow.MONTH) { onDraftChange(draft.copy(since = SinceWindow.MONTH)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormField(Res.string.devtools_filter_limit, draft.limit, result, FilterField.LIMIT, Modifier.width(96.dp)) {
                        onDraftChange(draft.copy(limit = it.filter(Char::isDigit)))
                    }
                    FormField(Res.string.devtools_filter_search, draft.search, null, FilterField.KINDS, Modifier.weight(1f)) {
                        onDraftChange(draft.copy(search = it))
                    }
                }

                when (result) {
                    is FilterDraft.Result.Invalid ->
                        Text(
                            stringRes(Res.string.devtools_filter_invalid, result.token),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )

                    is FilterDraft.Result.Ok -> DevToolsCodeBlock(result.filter.toJson(), maxHeight = 120)
                }
            }
        },
        confirmButton = {
            Button(onClick = { filter?.let(onSend) }, enabled = filter != null) { Text(stringRes(Res.string.send)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
                TextButton(onClick = { filter?.let(onEditAsText) }, enabled = filter != null) {
                    Text(stringRes(Res.string.devtools_console_edit_as_text))
                }
            }
        },
    )
}

@Composable
private fun FormField(
    label: StringResource,
    value: String,
    result: FilterDraft.Result?,
    field: FilterField,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringRes(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        isError = (result as? FilterDraft.Result.Invalid)?.field == field,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SinceChip(
    label: StringResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringRes(label)) })
}

/** Publish an already-signed event: checked with the inspector before the Send button lights up. */
@Composable
private fun EventPublishDialog(
    onSend: (Event) -> Unit,
    onDismiss: () -> Unit,
) {
    var json by rememberSaveable { mutableStateOf("") }
    val result = remember(json) { EventInspector.inspect(json) }
    val parsed = (result as? EventInspectResult.Parsed)?.inspection

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.devtools_console_event_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DevToolsInput(json, { json = it }, stringRes(Res.string.devtools_console_event_field), minLines = 4, maxLines = 10)
                when {
                    result is EventInspectResult.Unparseable ->
                        Text(stringRes(Res.string.devtools_event_unparseable, result.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                    parsed != null ->
                        DevToolsCheckRow(
                            if (parsed.isValid) MaterialSymbols.CheckCircle else MaterialSymbols.Error,
                            stringRes(if (parsed.isValid) Res.string.devtools_event_valid else Res.string.devtools_event_invalid),
                            if (parsed.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                }
            }
        },
        // An invalid event still goes out if the developer insists: seeing the relay reject it is the point.
        confirmButton = { Button(onClick = { parsed?.let { onSend(it.event) } }, enabled = parsed != null) { Text(stringRes(Res.string.send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

@Composable
private fun ConnectionDot(connection: ConsoleConnection) {
    val color =
        when (connection) {
            ConsoleConnection.CONNECTED -> MaterialTheme.colorScheme.primary
            ConsoleConnection.CONNECTING -> MaterialTheme.colorScheme.tertiary
            ConsoleConnection.DISCONNECTED -> MaterialTheme.colorScheme.outline
        }
    Box(Modifier.size(12.dp).background(color, CircleShape))
}

/** One frame; long ones show six lines until tapped. */
@Composable
private fun FrameRow(frame: ConsoleFrame) {
    var expanded by rememberSaveable(frame.id) { mutableStateOf(false) }

    if (frame.direction == FrameDirection.SYSTEM) {
        Text(
            systemText(frame),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        return
    }

    val sent = frame.direction == FrameDirection.SENT
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 3.dp),
    ) {
        Icon(
            if (sent) MaterialSymbols.ArrowUpward else MaterialSymbols.ArrowDownward,
            contentDescription = null,
            tint = if (sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp).padding(top = 2.dp),
        )
        frame.label?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor(it),
                modifier = Modifier.padding(start = 4.dp, end = 6.dp),
            )
        }
        Text(
            frame.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (expanded) CopyIconButton(frame.text)
    }
}

@Composable
private fun labelColor(label: String): Color =
    when (label) {
        "EVENT" -> MaterialTheme.colorScheme.primary
        "EOSE", "COUNT" -> MaterialTheme.colorScheme.secondary
        "OK", "AUTH" -> MaterialTheme.colorScheme.tertiary
        "CLOSED", "NOTICE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun systemText(frame: ConsoleFrame): String =
    when (frame.system) {
        ConsoleSystemEvent.CONNECTING -> stringRes(Res.string.devtools_console_connecting, frame.text)
        ConsoleSystemEvent.CONNECTED -> stringRes(Res.string.devtools_console_connected, frame.text)
        ConsoleSystemEvent.CLOSED -> stringRes(Res.string.devtools_console_closed, frame.text)
        ConsoleSystemEvent.FAILED -> stringRes(Res.string.devtools_console_failed, frame.text)
        ConsoleSystemEvent.INVALID_URL -> stringRes(Res.string.devtools_console_invalid_url, frame.text)
        ConsoleSystemEvent.SEND_FAILED -> stringRes(Res.string.devtools_console_send_failed)
        ConsoleSystemEvent.WAITING_FOR_TOR -> stringRes(Res.string.devtools_console_tor, frame.text)
        null -> frame.text
    }
