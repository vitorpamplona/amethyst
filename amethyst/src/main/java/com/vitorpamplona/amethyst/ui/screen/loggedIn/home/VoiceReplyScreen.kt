/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.mediaServers.FileServerSelectionRow
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordAudioBox
import com.vitorpamplona.amethyst.ui.actions.uploads.UploadProgressIndicator
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceMessagePreview
import com.vitorpamplona.amethyst.ui.actions.uploads.VoicePresetSelector
import com.vitorpamplona.amethyst.ui.actions.uploads.formatSecondsToTime
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.replyModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceReplyScreen(
    replyToNoteId: String,
    recordingFilePath: String,
    mimeType: String,
    duration: Int,
    amplitudesJson: String,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val viewModel: VoiceReplyViewModel = viewModel()

    LaunchedEffect(replyToNoteId, recordingFilePath, accountViewModel) {
        viewModel.init(accountViewModel)
        viewModel.load(replyToNoteId, recordingFilePath, mimeType, duration, amplitudesJson)
    }

    BackHandler {
        viewModel.cancel()
        nav.popBack()
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                isActive = viewModel::canSend,
                onPost = {
                    viewModel.sendVoiceReply {
                        nav.popBack()
                    }
                },
                onCancel = {
                    viewModel.cancel()
                    nav.popBack()
                },
            )
        },
        bottomBar = {
            ReRecordButton(viewModel)
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad),
        ) {
            VoiceReplyScreenBody(viewModel, accountViewModel, nav)
        }
    }
}

@Composable
private fun VoiceReplyScreenBody(
    viewModel: VoiceReplyViewModel,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Size10dp),
    ) {
        // Show the note being replied to
        viewModel.replyToNote?.let { note ->
            Spacer(modifier = StdVertSpacer)
            NoteCompose(
                baseNote = note,
                modifier = MaterialTheme.colorScheme.replyModifier,
                isQuotedNote = true,
                unPackReply = false,
                makeItShort = true,
                quotesLeft = 1,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            Spacer(modifier = StdVertSpacer)
        }

        // Voice preview or upload progress
        viewModel.voiceOrchestrator?.let { orchestrator ->
            UploadProgressIndicator(orchestrator)
        } ?: run {
            viewModel.getVoicePreviewMetadata()?.let { metadata ->
                val displayMetadata =
                    metadata.copy(
                        waveform = viewModel.activeWaveform ?: metadata.waveform,
                    )
                VoiceMessagePreview(
                    voiceMetadata = displayMetadata,
                    localFile = viewModel.activeFile,
                    onRemove = {
                        viewModel.cancel()
                        nav.popBack()
                    },
                )
            }

            // Voice preset selector (only show when not uploading)
            Spacer(modifier = Modifier.height(12.dp))
            VoicePresetSelector(
                selectedPreset = viewModel.selectedPreset,
                isProcessing = viewModel.isProcessingPreset,
                onPresetSelected = { viewModel.selectPreset(it) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Server selection
        val fileServers =
            accountViewModel.account.serverLists.liveServerList
                .collectAsState()
                .value
        FileServerSelectionRow(
            fileServers = fileServers,
            selectedServer = viewModel.voiceSelectedServer ?: accountViewModel.account.settings.defaultFileServer,
            onSelect = { viewModel.voiceSelectedServer = it },
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ReRecordButton(viewModel: VoiceReplyViewModel) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 16.dp, horizontal = Size10dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (viewModel.isUploading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringRes(id = R.string.record_a_message),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringRes(id = R.string.re_record),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            return
        }

        RecordAudioBox(
            modifier = Modifier,
            onRecordTaken = { recording ->
                viewModel.selectRecording(recording)
            },
        ) { isRecording, elapsedSeconds ->
            val contentColor =
                if (isRecording) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            val icon =
                if (isRecording) {
                    Icons.Default.Stop
                } else {
                    Icons.Default.Mic
                }
            val label =
                if (isRecording) {
                    formatSecondsToTime(elapsedSeconds)
                } else {
                    stringRes(id = R.string.re_record)
                }
            val iconDescription =
                if (isRecording) {
                    stringRes(id = R.string.recording_indicator_description)
                } else {
                    stringRes(id = R.string.record_a_message)
                }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconDescription,
                    tint = contentColor,
                )
                Text(
                    text = label,
                    color = contentColor,
                )
            }
        }
    }
}
