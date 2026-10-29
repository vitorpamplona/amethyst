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
package com.vitorpamplona.amethyst.desktop.napplet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletConsentCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_engine_downloading
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_engine_starting
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_engine
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_fetch
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_fetching
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_permission_title
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.desktop.platform.AppWindow
import com.vitorpamplona.amethyst.desktop.platform.IconResources

/**
 * Every open napplet's window. Composed inside the main window, so each one gets the app's theme
 * and the same composition locals. Also keeps the napplets' `theme` in step with the app's colors.
 */
@Composable
fun DesktopNappletWindows(host: DesktopNappletHost) {
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(colors.background, colors.onBackground, colors.primary) {
        host.updateTheme(NappletThemeColors(colors.background.hex(), colors.onBackground.hex(), colors.primary.hex()))
    }

    val sessions by host.sessions.collectAsState()
    for (session in sessions) {
        key(session) { NappletWindow(session, host) }
    }
}

@Composable
private fun NappletWindow(
    session: DesktopNappletSession,
    host: DesktopNappletHost,
) {
    val windowState = rememberWindowState(size = DpSize(1024.dp, 720.dp), position = WindowPosition.Aligned(Alignment.Center))
    AppWindow(
        onCloseRequest = { host.close(session) },
        state = windowState,
        title = session.launch.title,
        icon = IconResources.adaptedBitmapPainter,
    ) {
        val state by session.state.collectAsState()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val current = state) {
                // Chromium's own window, embedded: it draws over Compose, so nothing else goes here.
                is DesktopNappletSession.State.Showing -> SwingPanel(factory = { current.component }, modifier = Modifier.fillMaxSize())
                is DesktopNappletSession.State.Loading -> LoadingPane(host, session.launch.title)
                is DesktopNappletSession.State.Failed ->
                    MessagePane(
                        when (current.reason) {
                            DesktopNappletSession.Failure.UNREACHABLE -> stringRes(Res.string.napplet_desktop_failed_fetch, session.launch.title)
                            DesktopNappletSession.Failure.ENGINE -> stringRes(Res.string.napplet_desktop_failed_engine)
                        },
                    )
            }
        }

        // Its own window, above Chromium's: one question at a time, the oldest first.
        val pending by host.consent.pending.collectAsState()
        val question = pending.firstOrNull { it.coordinate == session.launch.identity.coordinate }
        if (question != null) {
            key(question) { ConsentWindow(question, session.launch.title) }
        }
    }
}

@Composable
private fun ConsentWindow(
    question: DesktopNappletConsent.Request,
    appTitle: String,
) {
    DialogWindow(
        onCloseRequest = { question.answer(GrantState.ASK) },
        state = rememberDialogState(size = DpSize(440.dp, 640.dp), position = WindowPosition.Aligned(Alignment.Center)),
        title = stringRes(Res.string.napplet_desktop_permission_title, appTitle),
        icon = IconResources.adaptedBitmapPainter,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            NappletConsentCard(question.info, onDecision = { question.answer(it) })
        }
    }
}

@Composable
private fun LoadingPane(
    host: DesktopNappletHost,
    title: String,
) {
    val engine by host.engine.state.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val current = engine) {
                is DesktopCefEngine.State.Downloading -> {
                    LinearProgressIndicator(progress = { current.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(stringRes(Res.string.napplet_desktop_engine_downloading, "${current.percent.toInt()}%"), textAlign = TextAlign.Center)
                }
                DesktopCefEngine.State.Starting -> {
                    CircularProgressIndicator()
                    Text(stringRes(Res.string.napplet_desktop_engine_starting), textAlign = TextAlign.Center)
                }
                else -> {
                    CircularProgressIndicator()
                    Text(stringRes(Res.string.napplet_desktop_fetching, title), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun MessagePane(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
    }
}

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
