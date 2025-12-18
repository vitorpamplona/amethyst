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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordAudioBox
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceMessagePreview
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import kotlinx.collections.immutable.toImmutableList

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
    viewModel.init(accountViewModel)

    LaunchedEffect(replyToNoteId, recordingFilePath) {
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
            VoiceUploadingProgress(orchestrator)
        } ?: run {
            viewModel.getVoicePreviewMetadata()?.let { metadata ->
                VoiceMessagePreview(
                    voiceMetadata = metadata,
                    localFile = viewModel.voiceLocalFile,
                    onRemove = {
                        viewModel.cancel()
                        nav.popBack()
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Server selection
        ServerSelectionRow(viewModel, accountViewModel)

        Spacer(modifier = Modifier.weight(1f))

        // Re-record button at bottom
        ReRecordButton(viewModel)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ServerSelectionRow(
    viewModel: VoiceReplyViewModel,
    accountViewModel: AccountViewModel,
) {
    val nip95description = stringRes(id = R.string.upload_server_relays_nip95)
    val fileServersState =
        accountViewModel.account.serverLists.liveServerList
            .collectAsState()
    val fileServers = fileServersState.value

    val fileServerOptions =
        remember(fileServers) {
            fileServers
                .map {
                    if (it.type == ServerType.NIP95) {
                        TitleExplainer(it.name, nip95description)
                    } else {
                        TitleExplainer(it.name, it.baseUrl)
                    }
                }.toImmutableList()
        }

    SettingsRow(R.string.file_server, R.string.file_server_description) {
        TextSpinner(
            label = "",
            placeholder =
                fileServers
                    .firstOrNull { it == (viewModel.voiceSelectedServer ?: accountViewModel.account.settings.defaultFileServer) }
                    ?.name
                    ?: fileServers.firstOrNull()?.name
                    ?: "",
            options = fileServerOptions,
            onSelect = { viewModel.voiceSelectedServer = fileServers[it] },
        )
    }
}

@Composable
private fun ReRecordButton(viewModel: VoiceReplyViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordAudioBox(
            modifier = Modifier,
            onRecordTaken = { recording ->
                viewModel.selectRecording(recording)
            },
        ) { isRecording, _ ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringRes(id = R.string.record_a_message),
                    tint =
                        if (isRecording) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                )
                Text(
                    text = stringRes(id = R.string.re_record),
                    color =
                        if (isRecording) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                )
            }
        }
    }
}

@Composable
private fun VoiceUploadingProgress(orchestrator: UploadOrchestrator) {
    val progressValue = orchestrator.progress.collectAsState().value
    val progressStatusValue = orchestrator.progressState.collectAsState().value

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(55.dp),
            contentAlignment = Alignment.Center,
        ) {
            val animatedProgress =
                animateFloatAsState(
                    targetValue = progressValue.toFloat(),
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                ).value

            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier =
                    Size55Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                strokeWidth = 5.dp,
            )

            val txt =
                when (progressStatusValue) {
                    is com.vitorpamplona.amethyst.service.uploads.UploadingState.Ready -> stringRes(R.string.uploading_state_ready)
                    is com.vitorpamplona.amethyst.service.uploads.UploadingState.Compressing -> stringRes(R.string.uploading_state_compressing)
                    is com.vitorpamplona.amethyst.service.uploads.UploadingState.Uploading -> stringRes(R.string.uploading_state_uploading)
                    is com.vitorpamplona.amethyst.service.uploads.UploadingState.ServerProcessing -> stringRes(R.string.uploading_state_server_processing)
                    is com.vitorpamplona.amethyst.service.uploads.UploadingState.Downloading -> stringRes(R.string.uploading_state_downloading)
                    is com.vitorpamplona.amethyst.service.uploads.UploadingState.Hashing -> stringRes(R.string.uploading_state_hashing)
                    is com.vitorpamplona.amethyst.service.uploads.UploadingState.Finished -> stringRes(R.string.uploading_state_finished)
                    is com.vitorpamplona.amethyst.service.uploads.UploadingState.Error -> stringRes(R.string.uploading_state_error)
                }

            Text(
                txt,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
