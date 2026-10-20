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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import com.vitorpamplona.amethyst.commons.devtools.FrameDirection
import com.vitorpamplona.amethyst.commons.devtools.RelayConsoleViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth
import com.vitorpamplona.amethyst.commons.resources.devtools_console_auth_sign
import com.vitorpamplona.amethyst.commons.resources.devtools_console_clear
import com.vitorpamplona.amethyst.commons.resources.devtools_console_closed
import com.vitorpamplona.amethyst.commons.resources.devtools_console_connect
import com.vitorpamplona.amethyst.commons.resources.devtools_console_connected
import com.vitorpamplona.amethyst.commons.resources.devtools_console_connecting
import com.vitorpamplona.amethyst.commons.resources.devtools_console_disconnect
import com.vitorpamplona.amethyst.commons.resources.devtools_console_empty
import com.vitorpamplona.amethyst.commons.resources.devtools_console_failed
import com.vitorpamplona.amethyst.commons.resources.devtools_console_invalid_url
import com.vitorpamplona.amethyst.commons.resources.devtools_console_message
import com.vitorpamplona.amethyst.commons.resources.devtools_console_send_failed
import com.vitorpamplona.amethyst.commons.resources.devtools_console_title
import com.vitorpamplona.amethyst.commons.resources.devtools_console_tor
import com.vitorpamplona.amethyst.commons.resources.devtools_console_url
import com.vitorpamplona.amethyst.commons.resources.send
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * A websocketking.com for relays: one raw socket, every frame both ways as the relay sent it.
 * The templates fill in the frames a developer types most; AUTH is offered, never automatic.
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

    var url by rememberSaveable { mutableStateOf(initialUrl ?: "wss://") }
    var message by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

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
                        TextButton(onClick = viewModel::answerAuth) { Text(stringRes(Res.string.devtools_console_auth_sign)) }
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
                AssistChip(onClick = { message = RelayConsoleViewModel.reqTemplate() }, label = { Text("REQ") })
                AssistChip(onClick = { message = RelayConsoleViewModel.countTemplate() }, label = { Text("COUNT") })
                AssistChip(onClick = { message = viewModel.closeTemplate() }, label = { Text("CLOSE") })
                AssistChip(onClick = { message = RelayConsoleViewModel.eventTemplate() }, label = { Text("EVENT") })
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
                    enabled = connection == ConsoleConnection.CONNECTED && message.isNotBlank(),
                ) {
                    Text(stringRes(Res.string.send))
                }
            }
        }
    }
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
        "OK" -> MaterialTheme.colorScheme.tertiary
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
