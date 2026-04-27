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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.AudioRoomsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.room.AudioRoomActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.room.AudioRoomBridge
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.launch

/**
 * Bottom sheet that lets the logged-in user start a new NIP-53 kind 30312
 * audio room. On submit, [CreateAudioRoomViewModel] builds and signs the
 * MeetingSpaceEvent (with the user as `host`), broadcasts it to the
 * account's relays, and then launches [AudioRoomActivity] against the
 * fresh address — the user lands inside the room as host with the
 * Talk button enabled.
 *
 * Hidden by default; surfaced by the "Start space" FAB on
 * [AudioRoomsScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAudioRoomSheet(
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val viewModelKey = remember(accountViewModel) { accountViewModel.account.userProfile().pubkeyHex }
    val viewModel: CreateAudioRoomViewModel =
        viewModel(key = "CreateAudioRoom-$viewModelKey")
    LaunchedEffect(viewModel) { viewModel.bindAccountIfMissing(accountViewModel) }

    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringRes(R.string.audio_room_create_title),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = state.roomName,
                onValueChange = viewModel::onRoomNameChange,
                label = { Text(stringRes(R.string.audio_room_create_field_room)) },
                singleLine = true,
                isError = state.error != null && state.roomName.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.summary,
                onValueChange = viewModel::onSummaryChange,
                label = { Text(stringRes(R.string.audio_room_create_field_summary)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            OutlinedTextField(
                value = state.serviceUrl,
                onValueChange = viewModel::onServiceUrlChange,
                label = { Text(stringRes(R.string.audio_room_create_field_service)) },
                singleLine = true,
                isError = state.error != null && state.serviceUrl.isBlank(),
                supportingText = { Text(stringRes(R.string.audio_room_create_field_service_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.endpointUrl,
                onValueChange = viewModel::onEndpointUrlChange,
                label = { Text(stringRes(R.string.audio_room_create_field_endpoint)) },
                singleLine = true,
                isError = state.error != null && state.endpointUrl.isBlank(),
                supportingText = { Text(stringRes(R.string.audio_room_create_field_endpoint_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.imageUrl,
                onValueChange = viewModel::onImageUrlChange,
                label = { Text(stringRes(R.string.audio_room_create_field_image)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Schedule toggle + start-time picker. Hidden picker when
            // toggled off so a "Start now" room doesn't waste vertical
            // space on a date row that isn't relevant.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Switch(
                    checked = state.scheduled,
                    onCheckedChange = viewModel::onScheduledToggle,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringRes(R.string.audio_room_create_schedule_toggle))
            }
            if (state.scheduled) {
                ScheduleStartPicker(
                    unixSeconds = state.scheduledStartUnix,
                    onChange = viewModel::onScheduledStartChange,
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss, enabled = !state.isPublishing) {
                    Text(stringRes(R.string.audio_room_create_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val launchInfo = viewModel.publishAndBuildLaunchInfo() ?: return@launch
                            // Hand the active AccountViewModel to the
                            // separately-tasked AudioRoomActivity (mirrors
                            // the join-card flow).
                            AudioRoomBridge.set(accountViewModel)
                            AudioRoomActivity.launch(
                                context = context,
                                addressValue = launchInfo.addressValue,
                                authBaseUrl = launchInfo.authBaseUrl,
                                endpoint = launchInfo.endpoint,
                                hostPubkey = launchInfo.hostPubkey,
                                roomId = launchInfo.roomId,
                                kind = launchInfo.kind,
                            )
                            onDismiss()
                        }
                    },
                    enabled = !state.isPublishing && state.canSubmit,
                ) {
                    if (state.isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringRes(R.string.audio_room_create_submit))
                    }
                }
            }
        }
    }
}

/**
 * Schedule-start picker. Shows the currently-selected start time
 * as a button; tapping it opens a Material3 DatePicker → TimePicker
 * chain (same shape as [com.vitorpamplona.amethyst.ui.note.creators.expiration.ExpirationDatePicker]
 * — hour + minute resolved in the local zone, then converted to
 * UTC unix seconds via the system zone offset).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleStartPicker(
    unixSeconds: Long,
    onChange: (Long) -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val pretty =
        if (unixSeconds <= 0L) {
            stringRes(R.string.audio_room_create_when)
        } else {
            val instant = java.util.Date(unixSeconds * 1000L)
            java.text.DateFormat
                .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(instant)
        }

    val initialMillis = if (unixSeconds > 0L) unixSeconds * 1000L else System.currentTimeMillis()
    val initialLocal =
        java.time.Instant
            .ofEpochMilli(initialMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()

    val datePickerState =
        androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
    val timePickerState =
        androidx.compose.material3.rememberTimePickerState(
            initialHour = initialLocal.hour,
            initialMinute = initialLocal.minute,
            is24Hour = false,
        )

    androidx.compose.material3.OutlinedButton(
        onClick = { showDate = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(pretty)
    }

    if (showDate) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    showTime = true
                }) { Text(stringRes(R.string.next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringRes(R.string.audio_room_create_cancel))
                }
            },
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }

    if (showTime) {
        androidx.compose.material3.TimePickerDialog(
            title = { Text(stringRes(R.string.audio_room_create_when)) },
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val dayMillisUtc = datePickerState.selectedDateMillis
                    if (dayMillisUtc != null) {
                        // DatePicker hands back UTC midnight of the selected
                        // calendar date. Add the picked hour:minute (local)
                        // to its seconds, then subtract the local offset to
                        // land on the correct UTC unix second — same shape
                        // as ExpirationDatePicker.
                        val localSeconds =
                            dayMillisUtc / 1000L +
                                timePickerState.hour * 3600L +
                                timePickerState.minute * 60L
                        val offsetSec =
                            java.time.ZoneId
                                .systemDefault()
                                .rules
                                .getOffset(java.time.Instant.now())
                                .totalSeconds
                                .toLong()
                        onChange(localSeconds - offsetSec)
                    }
                    showTime = false
                }) { Text(stringRes(R.string.audio_room_create_submit)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringRes(R.string.audio_room_create_cancel))
                }
            },
        ) {
            androidx.compose.material3.TimePicker(state = timePickerState)
        }
    }
}
