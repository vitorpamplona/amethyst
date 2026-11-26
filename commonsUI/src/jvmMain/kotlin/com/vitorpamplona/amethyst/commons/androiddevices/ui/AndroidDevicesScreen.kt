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
package com.vitorpamplona.amethyst.commons.androiddevices.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.android_devices_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode.ChatTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import java.io.File

/**
 * The device panel on a screen of its own: for a window too narrow to put it beside the chat, or
 * to watch a device large. With [chatKey], it is that chat's device and the Run button runs its app.
 */
@Composable
fun AndroidDevicesScreen(
    chatKey: String?,
    nav: INav,
) {
    val chat = remember(chatKey) { chatKey?.let { ClaudeCodeService.shared.chat(it) } }
    val folder =
        remember(chat) {
            chat
                ?.state
                ?.value
                ?.cwd
                ?.let(::File)
        }
    Scaffold(topBar = { ChatTopBar(title = stringRes(Res.string.android_devices_title), subtitle = chat?.state?.value?.title, nav = nav) {} }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            DevicePanel(
                chatKey = chatKey,
                folder = folder,
                agentName = chat?.backend?.shortName ?: "Claude",
                // From a chat, its workstream's device and copies; alone, the AVDs themselves.
                owner = chatKey?.let { ClaudeCodeService.shared.deviceOwner(it, folder?.path) },
                ownerLabel = chatKey?.let { ClaudeCodeService.shared.deviceOwnerLabel(it, folder?.path) },
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight(),
            )
        }
    }
}
