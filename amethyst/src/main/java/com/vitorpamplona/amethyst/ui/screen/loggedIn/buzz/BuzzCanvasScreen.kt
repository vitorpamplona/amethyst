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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceStates
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Renders a Buzz workspace **canvas** (NIP kind 40100): the newest shared markdown
 * document for a channel. The canvas is overlay state held in [BuzzWorkspaceStates]
 * (never a timeline row — a workspace has one live canvas, last-write-wins), so this
 * reads the registry by the channel's `h` id and re-composes off `canvasUpdates` when a
 * newer revision lands.
 */
@Composable
fun BuzzCanvasScreen(
    channelId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val state = remember(channelId) { BuzzWorkspaceStates.getOrCreate(channelId) }
    // Re-read canvasNote whenever a newer canvas is consumed.
    val version by state.canvasUpdates.collectAsStateWithLifecycle()
    val canvas = remember(version) { state.canvasNote }
    val content = canvas?.event?.content

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.buzz_canvas_title), nav) },
    ) { padding ->
        if (content.isNullOrBlank() || canvas == null) {
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
