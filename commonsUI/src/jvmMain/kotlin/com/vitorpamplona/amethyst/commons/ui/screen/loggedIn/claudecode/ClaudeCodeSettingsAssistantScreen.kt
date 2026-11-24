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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.claudecode.AccountToolServer
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeChat
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.claudecode.OnlineAgentBackend
import com.vitorpamplona.amethyst.commons.claudecode.SettingsToolServer
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.amethyst_assistant
import com.vitorpamplona.amethyst.commons.settings.registry.AmethystSettings
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The Amethyst assistant: an agent whose only tools are Amethyst's settings and the user's
 * account. It reads freely; every change, post and payment waits for the user's approval on the
 * chat's approval card. It is
 * one conversation that outlives the app: the chat resumes where it was, and a [question] asked
 * from Settings goes into it.
 */
@Composable
fun ClaudeCodeSettingsAssistantScreen(
    question: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = stringRes(Res.string.amethyst_assistant)
    val tools = rememberSettingsTools(accountViewModel, nav)
    val accountTools = remember(accountViewModel) { AccountToolServer(accountViewModel) }
    val access = remember(accountViewModel) { OnlineAgentBackend.Access.of(accountViewModel) }
    val chat by produceState<ClaudeChat?>(null, tools, accountTools) { value = ClaudeCodeService.shared.settingsAssistant(tools, title, accountTools, access) }
    // Asked once per visit to this screen: coming back to it must not ask again.
    var asked by rememberSaveable(question) { mutableStateOf(false) }
    LaunchedEffect(chat, question) {
        val open = chat ?: return@LaunchedEffect
        if (!asked && !question.isNullOrBlank()) {
            asked = true
            open.send(question)
        }
    }
    val open = chat
    if (open == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        ClaudeCodeChatScreen(open.key, accountViewModel, nav)
    }
}

/** The settings tools for this account, opening screens through [nav]. */
@Composable
fun rememberSettingsTools(
    accountViewModel: AccountViewModel,
    nav: INav,
): SettingsToolServer =
    remember(accountViewModel, nav) {
        SettingsToolServer(
            registry = { AmethystSettings.registry(accountViewModel.settings.uiSettingsFlow, accountViewModel.account.settings) },
            // Tools run off the main thread; navigation happens on it.
            openScreen = { route -> uiScope.launch { nav.nav(route) } },
        )
    }

/** Points the assistant's chats at this screen's account and navigation while it shows one. */
@Composable
fun BindAssistantTools(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val tools = rememberSettingsTools(accountViewModel, nav)
    val accountTools = remember(accountViewModel) { AccountToolServer(accountViewModel) }
    val access = remember(accountViewModel) { OnlineAgentBackend.Access.of(accountViewModel) }
    SideEffect { ClaudeCodeService.shared.bindAssistant(tools, accountTools, access) }
}

private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
