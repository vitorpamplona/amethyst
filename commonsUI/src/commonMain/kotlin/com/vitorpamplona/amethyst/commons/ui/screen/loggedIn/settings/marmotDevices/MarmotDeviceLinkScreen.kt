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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceLinkRole
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceLinkScreen
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceLinkScreenState
import com.vitorpamplona.amethyst.commons.marmot.devices.MarmotDeviceLinkViewModel
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkEndReason
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.marmot_devices_remaining_chats
import com.vitorpamplona.amethyst.commons.resources.marmot_link_already_linked
import com.vitorpamplona.amethyst.commons.resources.marmot_link_compare_body
import com.vitorpamplona.amethyst.commons.resources.marmot_link_compare_title
import com.vitorpamplona.amethyst.commons.resources.marmot_link_competing
import com.vitorpamplona.amethyst.commons.resources.marmot_link_done
import com.vitorpamplona.amethyst.commons.resources.marmot_link_end_answered_elsewhere
import com.vitorpamplona.amethyst.commons.resources.marmot_link_end_cancelled
import com.vitorpamplona.amethyst.commons.resources.marmot_link_end_cancelled_elsewhere
import com.vitorpamplona.amethyst.commons.resources.marmot_link_end_cannot_link
import com.vitorpamplona.amethyst.commons.resources.marmot_link_end_codes_differ
import com.vitorpamplona.amethyst.commons.resources.marmot_link_end_link_failed
import com.vitorpamplona.amethyst.commons.resources.marmot_link_end_timed_out
import com.vitorpamplona.amethyst.commons.resources.marmot_link_end_verification_failed
import com.vitorpamplona.amethyst.commons.resources.marmot_link_failed_title
import com.vitorpamplona.amethyst.commons.resources.marmot_link_joining
import com.vitorpamplona.amethyst.commons.resources.marmot_link_label
import com.vitorpamplona.amethyst.commons.resources.marmot_link_label_default
import com.vitorpamplona.amethyst.commons.resources.marmot_link_linked
import com.vitorpamplona.amethyst.commons.resources.marmot_link_linked_existing_body
import com.vitorpamplona.amethyst.commons.resources.marmot_link_linked_new_body
import com.vitorpamplona.amethyst.commons.resources.marmot_link_match
import com.vitorpamplona.amethyst.commons.resources.marmot_link_no_match
import com.vitorpamplona.amethyst.commons.resources.marmot_link_preparing
import com.vitorpamplona.amethyst.commons.resources.marmot_link_rate_limited
import com.vitorpamplona.amethyst.commons.resources.marmot_link_start_failed
import com.vitorpamplona.amethyst.commons.resources.marmot_link_starting
import com.vitorpamplona.amethyst.commons.resources.marmot_link_title_existing
import com.vitorpamplona.amethyst.commons.resources.marmot_link_title_move
import com.vitorpamplona.amethyst.commons.resources.marmot_link_title_new
import com.vitorpamplona.amethyst.commons.resources.marmot_link_wait_to_confirm
import com.vitorpamplona.amethyst.commons.resources.marmot_link_waiting_answer
import com.vitorpamplona.amethyst.commons.resources.marmot_link_waiting_other
import com.vitorpamplona.amethyst.commons.resources.marmot_link_waiting_request
import com.vitorpamplona.amethyst.commons.resources.try_again
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

/**
 * Linking a device, either role: progress, then the seven emoji to compare with "They match" and
 * "They don't match", then the outcome. [sessionId] is the request an existing device answers.
 */
@Composable
fun MarmotDeviceLinkScreen(
    asNewDevice: Boolean,
    sessionId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
    /** This device is linked and moves to the group of the device that answers. */
    move: Boolean = false,
) {
    val role = if (asNewDevice) DeviceLinkRole.NEW_DEVICE else DeviceLinkRole.EXISTING_DEVICE
    val viewModel: MarmotDeviceLinkViewModel =
        viewModel(
            key = "MarmotDeviceLink" + accountViewModel.account.signer.pubKey + role + sessionId + move,
            factory = MarmotDeviceLinkViewModel.Factory(accountViewModel.account, role, sessionId, accountViewModel.marmotDeviceAttention, move),
        )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                stringRes(
                    when {
                        move -> Res.string.marmot_link_title_move
                        asNewDevice -> Res.string.marmot_link_title_new
                        else -> Res.string.marmot_link_title_existing
                    },
                ),
                nav,
            )
        },
    ) { padding ->
        MarmotDeviceLinkContent(
            role = role,
            state = state,
            onConfirm = viewModel::confirm,
            onReject = viewModel::reject,
            onCancel = {
                viewModel.cancel()
                nav.popBack()
            },
            onRetry = viewModel::start,
            onDone = { nav.popBack() },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun MarmotDeviceLinkContent(
    role: DeviceLinkRole,
    state: DeviceLinkScreenState,
    onConfirm: (label: String) -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (state) {
            DeviceLinkScreenState.Starting -> Progress(Res.string.marmot_link_starting)
            DeviceLinkScreenState.WaitingForRequest -> Progress(Res.string.marmot_link_waiting_request)
            DeviceLinkScreenState.WaitingForAnswer -> Progress(Res.string.marmot_link_waiting_answer)
            DeviceLinkScreenState.Preparing -> Progress(Res.string.marmot_link_preparing)
            DeviceLinkScreenState.WaitingForOtherDevice -> Progress(Res.string.marmot_link_waiting_other)
            DeviceLinkScreenState.Joining -> Progress(Res.string.marmot_link_joining)
            is DeviceLinkScreenState.Comparing -> Compare(role, state, onConfirm, onReject)
            DeviceLinkScreenState.CompetingRequests ->
                Outcome(MaterialSymbols.Warning, MaterialTheme.colorScheme.error, null, stringRes(Res.string.marmot_link_competing))
            is DeviceLinkScreenState.RateLimited ->
                Outcome(MaterialSymbols.Schedule, MaterialTheme.colorScheme.error, null, stringRes(Res.string.marmot_link_rate_limited))
            is DeviceLinkScreenState.Linked -> {
                Outcome(
                    MaterialSymbols.CheckCircle,
                    MaterialTheme.colorScheme.primary,
                    stringRes(Res.string.marmot_link_linked),
                    stringRes(if (role == DeviceLinkRole.NEW_DEVICE) Res.string.marmot_link_linked_new_body else Res.string.marmot_link_linked_existing_body),
                )
                state.remainingChats?.let {
                    Text(
                        pluralStringRes(Res.plurals.marmot_devices_remaining_chats, it.toInt(), it.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onDone) { Text(stringRes(Res.string.marmot_link_done)) }
            }
            is DeviceLinkScreenState.Ended -> {
                Outcome(MaterialSymbols.Error, MaterialTheme.colorScheme.error, stringRes(Res.string.marmot_link_failed_title), stringRes(endReasonText(state.reason)))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (role == DeviceLinkRole.NEW_DEVICE) OutlinedButton(onClick = onRetry) { Text(stringRes(Res.string.try_again)) }
                    Button(onClick = onDone) { Text(stringRes(Res.string.marmot_link_done)) }
                }
            }
            DeviceLinkScreenState.AlreadyLinked -> {
                Outcome(MaterialSymbols.CheckCircle, MaterialTheme.colorScheme.primary, null, stringRes(Res.string.marmot_link_already_linked))
                Button(onClick = onDone) { Text(stringRes(Res.string.marmot_link_done)) }
            }
            DeviceLinkScreenState.StartFailed -> {
                Outcome(MaterialSymbols.Error, MaterialTheme.colorScheme.error, stringRes(Res.string.marmot_link_failed_title), stringRes(Res.string.marmot_link_start_failed))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRetry) { Text(stringRes(Res.string.try_again)) }
                    Button(onClick = onDone) { Text(stringRes(Res.string.marmot_link_done)) }
                }
            }
        }

        if (DeviceLinkScreen.isCancellable(state)) {
            TextButton(onClick = onCancel) { Text(stringRes(Res.string.cancel)) }
        }
    }
}

@Composable
private fun Compare(
    role: DeviceLinkRole,
    state: DeviceLinkScreenState.Comparing,
    onConfirm: (String) -> Unit,
    onReject: () -> Unit,
) {
    val defaultLabel = stringRes(Res.string.marmot_link_label_default)
    var label by rememberSaveable(state.sessionId) { mutableStateOf(defaultLabel) }

    Text(stringRes(Res.string.marmot_link_compare_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        stringRes(Res.string.marmot_link_compare_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    SasCodeGrid(state.code, Modifier.widthIn(max = 420.dp).fillMaxWidth())

    if (role == DeviceLinkRole.EXISTING_DEVICE) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringRes(Res.string.marmot_link_label)) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
    }
    if (!state.canConfirm) {
        Text(stringRes(Res.string.marmot_link_wait_to_confirm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onReject,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringRes(Res.string.marmot_link_no_match)) }
        Button(
            onClick = { onConfirm(label.trim().ifEmpty { defaultLabel }) },
            enabled = state.canConfirm,
        ) { Text(stringRes(Res.string.marmot_link_match)) }
    }
}

@Composable
private fun Progress(text: StringResource) {
    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp).size(40.dp))
    Text(
        stringRes(text),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 480.dp),
    )
}

@Composable
private fun Outcome(
    symbol: MaterialSymbol,
    tint: Color,
    title: String?,
    body: String,
) {
    Icon(symbol = symbol, contentDescription = null, modifier = Modifier.padding(top = 24.dp).size(56.dp), tint = tint)
    title?.let { Text(it, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center) }
    Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
}

fun endReasonText(reason: DeviceLinkEndReason): StringResource =
    when (reason) {
        DeviceLinkEndReason.CANCELLED -> Res.string.marmot_link_end_cancelled
        DeviceLinkEndReason.CANCELLED_ELSEWHERE -> Res.string.marmot_link_end_cancelled_elsewhere
        DeviceLinkEndReason.CODES_DIFFER -> Res.string.marmot_link_end_codes_differ
        DeviceLinkEndReason.VERIFICATION_FAILED -> Res.string.marmot_link_end_verification_failed
        DeviceLinkEndReason.TIMED_OUT -> Res.string.marmot_link_end_timed_out
        DeviceLinkEndReason.ANSWERED_ELSEWHERE -> Res.string.marmot_link_end_answered_elsewhere
        DeviceLinkEndReason.CANNOT_LINK -> Res.string.marmot_link_end_cannot_link
        DeviceLinkEndReason.LINK_FAILED -> Res.string.marmot_link_end_link_failed
    }
