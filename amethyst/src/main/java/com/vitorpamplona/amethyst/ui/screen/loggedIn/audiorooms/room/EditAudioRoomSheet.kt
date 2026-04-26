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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import kotlinx.coroutines.launch

/**
 * Bottom sheet that lets the host edit the current room — pre-fills
 * from the existing kind-30312 event, republishes with the same
 * `d`-tag on save (so the relay treats it as a replacement of the
 * original), or closes the room with a destructive button.
 *
 * Visibility gating (host-only) is the caller's job;
 * [EditAudioRoomViewModel] preserves every promoted participant
 * verbatim, so a save MUST NOT silently demote anyone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAudioRoomSheet(
    accountViewModel: AccountViewModel,
    event: MeetingSpaceEvent,
    onDismiss: () -> Unit,
) {
    val key = remember(event) { "EditAudioRoom-${event.dTag()}" }
    val viewModel: EditAudioRoomViewModel = viewModel(key = key)
    LaunchedEffect(viewModel, event) { viewModel.bind(accountViewModel, event) }

    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringRes(R.string.audio_room_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = state.roomName,
                onValueChange = viewModel::setRoomName,
                label = { Text(stringRes(R.string.audio_room_create_field_room)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.summary,
                onValueChange = viewModel::setSummary,
                label = { Text(stringRes(R.string.audio_room_create_field_summary)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.serviceUrl,
                onValueChange = viewModel::setServiceUrl,
                label = { Text(stringRes(R.string.audio_room_create_field_service)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.endpointUrl,
                onValueChange = viewModel::setEndpointUrl,
                label = { Text(stringRes(R.string.audio_room_create_field_endpoint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.imageUrl,
                onValueChange = viewModel::setImageUrl,
                label = { Text(stringRes(R.string.audio_room_create_field_image)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Destructive — flips status to CLOSED with the same d-tag
                // so subscribers see the room go dark.
                TextButton(
                    enabled = !state.isPublishing,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        scope.launch {
                            if (viewModel.closeRoom()) onDismiss()
                        }
                    },
                ) {
                    Text(stringRes(R.string.audio_room_close_action))
                }
                Row {
                    TextButton(onClick = onDismiss, enabled = !state.isPublishing) {
                        Text(stringRes(R.string.audio_room_create_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = state.canSubmit,
                        onClick = {
                            scope.launch {
                                if (viewModel.save()) onDismiss()
                            }
                        },
                    ) {
                        if (state.isPublishing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringRes(R.string.audio_room_edit_save))
                        }
                    }
                }
            }
        }
    }
}
