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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceStates
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A Buzz workspace **canvas** (NIP kind 40100): the newest shared markdown document for a channel.
 * The canvas is overlay state held in [BuzzWorkspaceStates] (never a timeline row — a workspace has
 * one live canvas, last-write-wins), so this reads the registry by the channel's `h` id and
 * re-composes off `canvasUpdates` when a newer revision lands.
 *
 * The edit FAB flips into a plain markdown editor; saving publishes a fresh [CanvasEvent] to the
 * channel's host [relayUrl] (last-write-wins on the relay too), and the consume path folds the new
 * revision back into [BuzzWorkspaceStates] so the view updates without a manual refresh.
 */
@Composable
fun BuzzCanvasScreen(
    channelId: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val state = remember(channelId) { BuzzWorkspaceStates.getOrCreate(channelId) }
    // Re-read canvasNote whenever a newer canvas is consumed.
    val version by state.canvasUpdates.collectAsStateWithLifecycle()
    val canvas = remember(version) { state.canvasNote }
    val content = canvas?.event?.content

    var editing by remember { mutableStateOf(false) }

    if (editing) {
        CanvasEditor(
            channelId = channelId,
            relayUrl = relayUrl,
            initial = content.orEmpty(),
            accountViewModel = accountViewModel,
            onClose = { editing = false },
        )
        return
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.buzz_canvas_title), nav) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = true }) {
                Icon(symbol = MaterialSymbols.Edit, contentDescription = stringRes(R.string.buzz_canvas_edit))
            }
        },
    ) { padding ->
        if (content.isNullOrBlank()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringRes(R.string.buzz_canvas_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        val bgColor = remember { mutableStateOf(Color.Transparent) }
        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            TranslatableRichTextViewer(
                content = content,
                canPreview = true,
                quotesLeft = 1,
                modifier = Modifier,
                tags = EmptyTagList,
                backgroundColor = bgColor,
                id = canvas.idHex,
                callbackUri = canvas.toNostrUri(),
                authorPubKey = canvas.author?.pubkeyHex,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

/** The markdown editor: a full-height text field for the canvas body with a Save action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasEditor(
    channelId: String,
    relayUrl: String,
    initial: String,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Back cancels the edit and returns to the rendered canvas rather than leaving the screen.
    BackHandler(enabled = !saving, onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.buzz_canvas_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !saving) {
                        Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(R.string.cancel))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val relay =
                        RelayUrlNormalizer.normalizeOrNull(relayUrl) ?: run {
                            error = "Invalid relay url"
                            return@FloatingActionButton
                        }
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                accountViewModel.account.signAndSendPrivatelyOrBroadcast(
                                    CanvasEvent.build(channelId, text),
                                ) { listOf(relay) }
                            }
                            onClose()
                        } catch (e: Exception) {
                            saving = false
                            error = "Failed to save: ${e.message ?: e::class.simpleName}"
                        }
                    }
                },
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.padding(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(symbol = MaterialSymbols.Check, contentDescription = stringRes(R.string.buzz_canvas_save))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                modifier = Modifier.fillMaxSize(),
                enabled = !saving,
                label = { Text(stringRes(R.string.buzz_canvas_body_label)) },
            )
            error?.let {
                SelectionContainer(Modifier.align(Alignment.BottomStart)) {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
