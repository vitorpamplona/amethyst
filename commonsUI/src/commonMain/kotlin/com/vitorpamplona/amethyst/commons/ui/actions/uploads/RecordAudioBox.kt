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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.record_a_message_description
import com.vitorpamplona.amethyst.commons.ui.components.ToggleableBox
import com.vitorpamplona.amethyst.commons.ui.components.rememberMicrophonePermission
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Records a voice message behind a caller-drawn trigger: [content] gets whether it is recording,
 * the elapsed seconds and a stop action, and the finished recording goes to [onRecordTaken]. A
 * tap starts (asking for the microphone first when needed) and the next one stops; recording
 * also stops by itself at [maxDurationSeconds].
 *
 * The recorder is the platform's ([com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform.rememberVoiceRecorder]);
 * platforms without one draw nothing, so no dead button shows.
 */
@Composable
fun RecordAudioBox(
    modifier: Modifier = Modifier,
    onRecordTaken: (RecordingResult) -> Unit,
    maxDurationSeconds: Int? = null,
    content: @Composable (isRecording: Boolean, elapsedSeconds: Int, onStop: () -> Unit) -> Unit,
) {
    val recorder = LocalNotePlatform.current.rememberVoiceRecorder() ?: return

    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var pendingPermissionStart by remember { mutableStateOf(false) }

    // Must be called at Composable scope, not in callback
    val recordPermission = rememberMicrophonePermission()
    val scope = rememberCoroutineScope()
    val notice = rememberShortNotice()

    DisposableEffect(recorder) {
        onDispose {
            recorder.stop()
        }
    }

    // Read in composition: both helpers run from onClick, where neither
    // string accessor can be called.
    val recordDescription = stringRes(Res.string.record_a_message_description)

    fun startRecording() {
        if (!isRecording) {
            elapsedSeconds = 0
            isRecording = recorder.start(scope)
            if (!isRecording) notice.show(recordDescription)
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        val result = recorder.stop()
        isRecording = false
        if (result != null) {
            onRecordTaken(result)
        } else {
            notice.show(recordDescription)
        }
    }

    // Start recording after permission is granted
    LaunchedEffect(recordPermission.isGranted) {
        if (recordPermission.isGranted && pendingPermissionStart) {
            pendingPermissionStart = false
            startRecording()
        }
    }

    // Track elapsed time while recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            // Loop while coroutine is active - LaunchedEffect will cancel when isRecording changes
            while (isActive) {
                delay(1000)
                elapsedSeconds++
                if (maxDurationSeconds != null && elapsedSeconds >= maxDurationSeconds) {
                    stopRecording()
                    break
                }
            }
        } else {
            // Reset elapsed time when not recording
            elapsedSeconds = 0
        }
    }

    ToggleableBox(
        modifier = modifier,
        isActive = isRecording,
        onClick = {
            if (isRecording) {
                stopRecording()
            } else {
                if (!recordPermission.isGranted) {
                    pendingPermissionStart = true
                    recordPermission.launchRequest()
                } else {
                    startRecording()
                }
            }
        },
        content = { active ->
            content(active, elapsedSeconds) {
                stopRecording()
            }
        },
    )
}
