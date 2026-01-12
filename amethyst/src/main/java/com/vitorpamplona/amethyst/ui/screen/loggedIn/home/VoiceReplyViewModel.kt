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
import com.vitorpamplona.amethyst.ui.actions.uploads.AnonymizedResult
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordingResult
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceAnonymizer
import com.vitorpamplona.amethyst.ui.actions.uploads.VoicePreset
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.tags.people.toPTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip10Notes.tags.prepareETagsAsReplyTo
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    var selectedPreset: VoicePreset by mutableStateOf(VoicePreset.NONE)
    var processingPreset: VoicePreset? by mutableStateOf(null)
    var distortedFiles: Map<VoicePreset, AnonymizedResult> by mutableStateOf(emptyMap())
    private var processingJob: Job? = null

    val activeFile: File?
        get() =
            if (selectedPreset == VoicePreset.NONE) {
                voiceLocalFile
            } else {
                distortedFiles[selectedPreset]?.file
            }

    val activeWaveform: List<Float>?
        get() =
            if (selectedPreset == VoicePreset.NONE) {
                voiceRecording?.amplitudes
            } else {
                distortedFiles[selectedPreset]?.waveform
            }

    private var uploadJob: Job? = null

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
        cancelUpload()
        processingJob?.cancel()
        processingPreset = null
        deleteVoiceLocalFile()
        voiceRecording = recording
        voiceLocalFile = recording.file
        voiceMetadata = null
        selectedPreset = VoicePreset.NONE
    }

    private fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
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

        distortedFiles.values.forEach { result ->
            try {
                if (result.file.exists()) {
                    result.file.delete()
                    Log.d("VoiceReplyViewModel", "Deleted distorted file: ${result.file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w("VoiceReplyViewModel", "Failed to delete distorted file: ${result.file.absolutePath}", e)
            }
        }
        distortedFiles = emptyMap()
    }

    fun canSend(): Boolean = voiceRecording != null && !isUploading && processingPreset == null

    fun selectPreset(preset: VoicePreset) {
        if (processingPreset != null || preset == selectedPreset) return

        if (preset == VoicePreset.NONE) {
            selectedPreset = preset
            return
        }

        if (distortedFiles.containsKey(preset)) {
            selectedPreset = preset
            return
        }

        val originalFile = voiceLocalFile ?: return

        processingJob?.cancel()
        processingJob =
            viewModelScope.launch {
                processingPreset = preset
                try {
                    val anonymizer = VoiceAnonymizer()
                    val result = anonymizer.anonymize(originalFile, preset)

                    result
                        .onSuccess { anonymizedResult ->
                            distortedFiles = distortedFiles + (preset to anonymizedResult)
                            selectedPreset = preset
                        }.onFailure { error ->
                            Log.w("VoiceReplyViewModel", "Failed to anonymize voice", error)
                            accountViewModel.toastManager.toast(
                                stringRes(Amethyst.instance.appContext, R.string.error),
                                error.message ?: "Voice anonymization failed",
                            )
                        }
                } finally {
                    processingPreset = null
                    processingJob = null
                }
            }
    }

    fun sendVoiceReply(onSuccess: () -> Unit) {
        val note = replyToNote ?: return
        val recording = voiceRecording ?: return
        val fileToUpload = activeFile ?: recording.file
        val serverToUse = voiceSelectedServer ?: accountViewModel.account.settings.defaultFileServer

        cancelUpload()
        uploadJob =
            viewModelScope.launch {
                isUploading = true
                val orchestrator = UploadOrchestrator()
                voiceOrchestrator = orchestrator

                try {
                    val result =
                        withContext(Dispatchers.IO) {
                            val uri = android.net.Uri.fromFile(fileToUpload)
                            orchestrator.upload(
                                uri = uri,
                                mimeType = recording.mimeType,
                                alt = null,
                                contentWarningReason = null,
                                compressionQuality = CompressorQuality.UNCOMPRESSED,
                                server = serverToUse,
                                account = accountViewModel.account,
                                context = Amethyst.instance.appContext,
                                useH265 = false,
                            )
                        }

                    handleUploadResult(note, recording, activeWaveform ?: recording.amplitudes, serverToUse, result, onSuccess)
                } catch (e: CancellationException) {
                    Log.w("VoiceReplyViewModel", "User canceled, or ViewModel cleared", e)
                } catch (e: Exception) {
                    val appContext = Amethyst.instance.appContext
                    val uploadErrorTitle = stringRes(appContext, R.string.upload_error_title)
                    val uploadVoiceExceptionMessage: (String) -> String = { detail ->
                        stringRes(appContext, R.string.upload_error_voice_message_exception, detail)
                    }
                    accountViewModel.toastManager.toast(
                        uploadErrorTitle,
                        uploadVoiceExceptionMessage(e.message ?: e.javaClass.simpleName),
                    )
                } finally {
                    isUploading = false
                    voiceOrchestrator = null
                    uploadJob = null
                }
            }
    }

    private suspend fun handleUploadResult(
        note: Note,
        recording: RecordingResult,
        waveform: List<Float>,
        server: ServerName,
        result: UploadingState,
        onSuccess: () -> Unit,
    ) {
        val appContext = Amethyst.instance.appContext
        val uploadErrorTitle = stringRes(appContext, R.string.upload_error_title)
        val uploadVoiceNip95NotSupported = stringRes(appContext, R.string.upload_error_voice_message_nip95_not_supported)
        val uploadVoiceFailed = stringRes(appContext, R.string.upload_error_voice_message_failed)

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
                                waveform = waveform,
                            )

                        // Check if replying to a voice event
                        val voiceHint = note.toEventHint<BaseVoiceEvent>()
                        if (voiceHint != null) {
                            // Create VoiceReplyEvent (KIND 1244) for voice-to-voice replies
                            accountViewModel.account.signAndComputeBroadcast(VoiceReplyEvent.build(audioMeta, voiceHint))
                        } else {
                            // Create TextNoteEvent (KIND 1) with audio IMeta for voice replies to regular notes
                            val textHint = note.toEventHint<TextNoteEvent>()
                            if (textHint == null) {
                                accountViewModel.toastManager.toast(uploadErrorTitle, uploadVoiceFailed)
                                return
                            }

                            val template =
                                TextNoteEvent.build(audioMeta.url) {
                                    val tags = prepareETagsAsReplyTo(textHint, null)
                                    accountViewModel.fixReplyTagHints(tags)
                                    markedETags(tags)
                                    notify(textHint.toPTag())
                                    // Add audio as IMeta attachment
                                    add(audioMeta.toIMetaArray())
                                }
                            accountViewModel.account.signAndComputeBroadcast(template)
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
            else -> {
                accountViewModel.toastManager.toast(uploadErrorTitle, uploadVoiceFailed)
            }
        }
    }

    fun cancel() {
        cancelUpload()
        processingJob?.cancel()
        deleteVoiceLocalFile()
        voiceRecording = null
        voiceLocalFile = null
        voiceMetadata = null
        voiceSelectedServer = null
        isUploading = false
        voiceOrchestrator = null
        selectedPreset = VoicePreset.NONE
        processingPreset = null
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
    }
}
