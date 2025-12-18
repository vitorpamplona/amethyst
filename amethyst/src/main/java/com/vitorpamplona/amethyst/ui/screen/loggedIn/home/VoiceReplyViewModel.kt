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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordingResult
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

@Stable
class VoiceReplyViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel

    var replyToNote: Note? by mutableStateOf(null)

    var voiceRecording: RecordingResult? by mutableStateOf(null)
    var voiceLocalFile: File? by mutableStateOf(null)
    var voiceMetadata: AudioMeta? by mutableStateOf(null)
    var voiceSelectedServer: ServerName? by mutableStateOf(null)
    var voiceOrchestrator: UploadOrchestrator? by mutableStateOf(null)
    var isUploading: Boolean by mutableStateOf(false)

    fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
    }

    fun load(
        replyToNoteId: String,
        recordingFilePath: String,
        mimeType: String,
        duration: Int,
        amplitudesJson: String,
    ) {
        replyToNote = accountViewModel.getNoteIfExists(replyToNoteId)

        val amplitudes =
            try {
                Json.decodeFromString<List<Float>>(amplitudesJson)
            } catch (e: Exception) {
                Log.w("VoiceReplyViewModel", "Failed to parse amplitudes", e)
                emptyList()
            }

        val file = File(recordingFilePath)
        voiceLocalFile = file
        voiceRecording =
            RecordingResult(
                file = file,
                mimeType = mimeType,
                duration = duration,
                amplitudes = amplitudes,
            )
    }

    fun getVoicePreviewMetadata(): AudioMeta? =
        voiceRecording?.let { recording ->
            AudioMeta(
                url = "",
                mimeType = recording.mimeType,
                duration = recording.duration,
                waveform = recording.amplitudes,
            )
        }

    fun selectRecording(recording: RecordingResult) {
        deleteVoiceLocalFile()
        voiceRecording = recording
        voiceLocalFile = recording.file
        voiceMetadata = null
    }

    fun removeVoiceMessage() {
        deleteVoiceLocalFile()
        voiceRecording = null
        voiceLocalFile = null
        voiceMetadata = null
        voiceSelectedServer = null
        isUploading = false
        voiceOrchestrator = null
    }

    private fun deleteVoiceLocalFile() {
        voiceLocalFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Log.d("VoiceReplyViewModel", "Deleted voice file: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w("VoiceReplyViewModel", "Failed to delete voice file: ${file.absolutePath}", e)
            }
        }
    }

    fun canSend(): Boolean = (voiceRecording != null || voiceMetadata != null) && !isUploading

    fun sendVoiceReply(onSuccess: () -> Unit) {
        val note = replyToNote ?: return
        val recording = voiceRecording ?: return
        val serverToUse = voiceSelectedServer ?: accountViewModel.account.settings.defaultFileServer

        viewModelScope.launch(Dispatchers.IO) {
            uploadAndSend(note, recording, serverToUse, onSuccess)
        }
    }

    private suspend fun uploadAndSend(
        note: Note,
        recording: RecordingResult,
        server: ServerName,
        onSuccess: () -> Unit,
    ) {
        val appContext = Amethyst.instance.appContext
        val uploadErrorTitle = stringRes(appContext, R.string.upload_error_title)
        val uploadVoiceNip95NotSupported = stringRes(appContext, R.string.upload_error_voice_message_nip95_not_supported)
        val uploadVoiceFailed = stringRes(appContext, R.string.upload_error_voice_message_failed)
        val uploadVoiceExceptionMessage: (String) -> String = { detail ->
            stringRes(appContext, R.string.upload_error_voice_message_exception, detail)
        }

        isUploading = true

        try {
            val uri = android.net.Uri.fromFile(recording.file)
            val orchestrator = UploadOrchestrator()
            voiceOrchestrator = orchestrator

            val result =
                orchestrator.upload(
                    uri = uri,
                    mimeType = recording.mimeType,
                    alt = null,
                    contentWarningReason = null,
                    compressionQuality = CompressorQuality.UNCOMPRESSED,
                    server = server,
                    account = accountViewModel.account,
                    context = appContext,
                    useH265 = false,
                )

            when (result) {
                is UploadingState.Finished -> {
                    when (val orchestratorResult = result.result) {
                        is UploadOrchestrator.OrchestratorResult.ServerResult -> {
                            val audioMeta =
                                AudioMeta(
                                    url = orchestratorResult.url,
                                    mimeType = recording.mimeType,
                                    hash = orchestratorResult.fileHeader.hash,
                                    duration = recording.duration,
                                    waveform = recording.amplitudes,
                                )

                            val hint = note.toEventHint<VoiceEvent>()
                            if (hint != null) {
                                accountViewModel.account.signAndComputeBroadcast(
                                    VoiceReplyEvent.build(audioMeta, hint),
                                )
                            }

                            if (server.type != ServerType.NIP95) {
                                accountViewModel.account.settings.changeDefaultFileServer(server)
                            }

                            deleteVoiceLocalFile()
                            voiceLocalFile = null
                            voiceRecording = null
                            voiceMetadata = audioMeta

                            onSuccess()
                        }
                        is UploadOrchestrator.OrchestratorResult.NIP95Result -> {
                            accountViewModel.toastManager.toast(uploadErrorTitle, uploadVoiceNip95NotSupported)
                        }
                    }
                }
                is UploadingState.Error -> {
                    accountViewModel.toastManager.toast(uploadErrorTitle, uploadVoiceFailed)
                }
            }
        } catch (e: Exception) {
            accountViewModel.toastManager.toast(uploadErrorTitle, uploadVoiceExceptionMessage(e.message ?: e.javaClass.simpleName))
        } finally {
            isUploading = false
            voiceOrchestrator = null
        }
    }

    fun cancel() {
        deleteVoiceLocalFile()
        voiceRecording = null
        voiceLocalFile = null
        voiceMetadata = null
        voiceSelectedServer = null
        isUploading = false
        voiceOrchestrator = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
    }
}
