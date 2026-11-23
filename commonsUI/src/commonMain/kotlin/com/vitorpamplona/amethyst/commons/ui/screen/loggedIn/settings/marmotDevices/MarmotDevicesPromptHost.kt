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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceGroupAttention
import com.vitorpamplona.amethyst.commons.marmot.devices.MarmotDevicesViewModel
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkPrompt
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SignIn
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_unlinked_body
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_unlinked_title
import com.vitorpamplona.amethyst.commons.resources.marmot_link_request_body
import com.vitorpamplona.amethyst.commons.resources.marmot_link_request_compare
import com.vitorpamplona.amethyst.commons.resources.marmot_link_request_ignore
import com.vitorpamplona.amethyst.commons.resources.marmot_link_request_title
import com.vitorpamplona.amethyst.commons.resources.marmot_sign_in_body
import com.vitorpamplona.amethyst.commons.resources.marmot_sign_in_mine
import com.vitorpamplona.amethyst.commons.resources.marmot_sign_in_not_me
import com.vitorpamplona.amethyst.commons.resources.marmot_sign_in_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsoluteNoDot
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The device-group prompts that can show on any screen, one at a time: a sibling's link request
 * (leading into the emoji comparison), a sign-in this device does not know, and the notice that a
 * sibling removed this device. Draws nothing while device groups are off.
 */
@Composable
fun MarmotDevicesPromptHost(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val devices = accountViewModel.account.marmotDevices ?: return
    val viewModel: MarmotDevicesViewModel =
        viewModel(
            key = "MarmotDevicesPrompts" + accountViewModel.account.signer.pubKey,
            factory = MarmotDevicesViewModel.Factory(accountViewModel.account),
        )
    val attentionState = accountViewModel.marmotDeviceAttention
    val attentionFlow = remember(devices, attentionState) { attentionState.attention(devices.state) }
    val attention by attentionFlow.collectAsStateWithLifecycle(DeviceGroupAttention.NONE)

    MarmotDevicesPrompts(
        attention = attention,
        onCompare = { prompt ->
            attentionState.dismissRequest(prompt.sessionId)
            nav.nav(Route.MarmotDeviceLink(asNewDevice = false, sessionId = prompt.sessionId))
        },
        onIgnore = { prompt ->
            attentionState.dismissRequest(prompt.sessionId)
            viewModel.rejectLink(prompt.sessionId)
        },
        onSignInMine = { attentionState.dismissSignIn(it.slot) },
        onSignInNotMe = {
            attentionState.dismissSignIn(it.slot)
            viewModel.rejectSignIn(it.slot)
        },
        onUnlinkedSeen = attentionState::dismissUnlinked,
    )
}

/** The dialogs for [attention], the most urgent first. */
@Composable
fun MarmotDevicesPrompts(
    attention: DeviceGroupAttention,
    onCompare: (DeviceLinkPrompt) -> Unit,
    onIgnore: (DeviceLinkPrompt) -> Unit,
    onSignInMine: (SignIn) -> Unit,
    onSignInNotMe: (SignIn) -> Unit,
    onUnlinkedSeen: () -> Unit,
) {
    val request = attention.linkRequest
    val signIn = attention.signIn
    when {
        attention.unlinked ->
            AlertDialog(
                onDismissRequest = onUnlinkedSeen,
                icon = { Icon(symbol = MaterialSymbols.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringRes(Res.string.marmot_devices_unlinked_title)) },
                text = { Text(stringRes(Res.string.marmot_devices_unlinked_body)) },
                confirmButton = { TextButton(onClick = onUnlinkedSeen) { Text(stringRes(Res.string.dismiss)) } },
            )
        request != null ->
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(symbol = MaterialSymbols.PhonelinkLock, contentDescription = null) },
                title = { Text(stringRes(Res.string.marmot_link_request_title)) },
                text = { Text(stringRes(Res.string.marmot_link_request_body)) },
                confirmButton = { TextButton(onClick = { onCompare(request) }) { Text(stringRes(Res.string.marmot_link_request_compare)) } },
                dismissButton = { TextButton(onClick = { onIgnore(request) }) { Text(stringRes(Res.string.marmot_link_request_ignore)) } },
            )
        signIn != null ->
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(symbol = MaterialSymbols.Security, contentDescription = null) },
                title = { Text(stringRes(Res.string.marmot_sign_in_title)) },
                text = { Text(stringRes(Res.string.marmot_sign_in_body, timeAbsoluteNoDot(signIn.publishedAt))) },
                confirmButton = { TextButton(onClick = { onSignInMine(signIn) }) { Text(stringRes(Res.string.marmot_sign_in_mine)) } },
                dismissButton = {
                    TextButton(onClick = { onSignInNotMe(signIn) }) { Text(stringRes(Res.string.marmot_sign_in_not_me), color = MaterialTheme.colorScheme.error) }
                },
            )
    }
}
