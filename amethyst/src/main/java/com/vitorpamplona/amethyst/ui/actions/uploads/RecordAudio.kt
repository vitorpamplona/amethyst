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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.Manifest
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.ClickAndHoldBoxComposable
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordAudioBox(
    modifier: Modifier,
    onRecordTaken: (RecordingResult) -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    val mediaRecorder = remember { mutableStateOf<VoiceMessageRecorder?>(null) }
    val context = LocalContext.current

    ClickAndHoldBoxComposable(
        modifier = modifier,
        onPress = {
            val recordPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                if (!recordPermissionState.status.isGranted) {
                    recordPermissionState.launchPermissionRequest()
                } else {
                    mediaRecorder.value = VoiceMessageRecorder()
                    mediaRecorder.value?.start(context, scope)
                }
            }
        },
        onRelease = {
            val result = mediaRecorder.value?.stop()
            if (result != null) {
                onRecordTaken(result)
            } else {
                // less disruptive than error messages
                Toast
                    .makeText(
                        context,
                        stringRes(context, R.string.record_a_message_description),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        },
        onCancel = {
            mediaRecorder.value?.stop()
        },
        content,
    )
}
