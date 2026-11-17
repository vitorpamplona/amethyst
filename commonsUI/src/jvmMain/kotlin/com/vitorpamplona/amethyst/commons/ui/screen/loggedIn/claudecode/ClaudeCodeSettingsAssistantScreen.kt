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
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.SettingsToolServer
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_settings_assistant
import com.vitorpamplona.amethyst.commons.settings.registry.AmethystSettings
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The settings assistant: Claude Code with Amethyst's settings as its only tools. It reads
 * settings freely; every change waits for the user's approval on the chat's approval card.
 */
@Composable
fun ClaudeCodeSettingsAssistantScreen(
    question: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = stringRes(Res.string.claude_code_settings_assistant)
    val chat =
        remember(accountViewModel) {
            val tools =
                SettingsToolServer(
                    registry = { AmethystSettings.registry(accountViewModel.settings.uiSettingsFlow, accountViewModel.account.settings) },
                    // Tools run off the main thread; navigation happens on it.
                    openScreen = { route -> uiScope.launch { nav.nav(route) } },
                )
            ClaudeCodeService.shared.settingsAssistant(tools, title)
        }
    LaunchedEffect(chat, question) {
        if (!question.isNullOrBlank() &&
            chat.state.value.items
                .isEmpty()
        ) {
            chat.send(question)
        }
    }
    ClaudeCodeChatScreen(chat.key, accountViewModel, nav)
}

private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
