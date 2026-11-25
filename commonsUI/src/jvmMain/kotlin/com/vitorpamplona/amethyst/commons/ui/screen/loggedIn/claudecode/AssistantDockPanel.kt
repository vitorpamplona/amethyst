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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.claudecode.ChatStatus
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dock_panel_assistant
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelScope
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelSpec
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.PanelBadge

/**
 * The Amethyst assistant as a dock panel, beside the screen it can see and act on: the same chat as
 * its screen, resumed where it was. What it opens shows in the main window, and its screen tools
 * describe that window. While folded, its strip icon flags an approval waiting, so a post or a
 * payment never waits unseen.
 */
val AssistantDockPanel =
    DockPanelSpec(
        id = PanelId.ASSISTANT,
        icon = MaterialSymbols.AutoAwesome,
        title = Res.string.dock_panel_assistant,
        defaultWidth = 420f,
        hidesOn = setOf(Route.ClaudeCodeSettingsAssistant::class),
        badge = { if (assistantWaiting()) PanelBadge.Attention else PanelBadge.None },
        content = { scope -> AssistantPanel(scope) },
    )

@Composable
private fun assistantWaiting(): Boolean {
    val rows by ClaudeCodeService.shared.rows.collectAsStateWithLifecycle()
    val key = ClaudeCodeService.shared.assistantKey ?: return false
    return rows.any { it.key == key && it.appStatus == ChatStatus.NEEDS_APPROVAL }
}

@Composable
private fun AssistantPanel(scope: DockPanelScope) {
    // A question asked elsewhere (Settings' "ask the assistant") arrives through the dock.
    var question by rememberSaveable { mutableStateOf<String?>(null) }
    val waiting = scope.dock.hasRequest(PanelId.ASSISTANT)
    LaunchedEffect(waiting) {
        if (waiting) scope.dock.takeRequest(PanelId.ASSISTANT)?.let { question = it }
    }
    ClaudeCodeSettingsAssistantScreen(question, scope.accountViewModel, scope.nav)
}
