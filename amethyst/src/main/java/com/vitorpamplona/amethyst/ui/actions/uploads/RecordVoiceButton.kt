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
package com.vitorpamplona.amethyst.ui.actions.uploads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun RecordVoiceButton(
    onVoiceTaken: (RecordingResult) -> Unit,
    maxDurationSeconds: Int? = null,
) {
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    Column(
        verticalArrangement = Arrangement.Center,
    ) {
        // Floating recording indicator at the top
        FloatingRecordingIndicator(
            modifier = Modifier.height(50.dp),
            isRecording = isRecording,
            elapsedSeconds = elapsedSeconds,
        )

        RecordAudioBox(
            modifier = Modifier.fillMaxSize(),
            onRecordTaken = { recording ->
                isRecording = false
                elapsedSeconds = 0
                onVoiceTaken(recording)
            },
            maxDurationSeconds = maxDurationSeconds,
        ) { recordingState, elapsed ->
            // Update parent state after composition completes
            SideEffect {
                if (isRecording != recordingState) {
                    isRecording = recordingState
                }
                if (elapsedSeconds != elapsed) {
                    elapsedSeconds = elapsed
                }
            }

            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Expanding circles background animation
                ExpandingCirclesAnimation(
                    modifier = Modifier.size(42.dp),
                    isRecording = recordingState,
                    primaryColor = MaterialTheme.colorScheme.primary,
                )

                // Microphone icon
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringRes(id = R.string.record_a_message),
                    modifier = Modifier.height(22.dp),
                    tint =
                        if (recordingState) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                )
            }
        }

        // Empty space at the bottom for layout balance
        Box(
            modifier = Modifier.height(50.dp),
        )
    }
}
