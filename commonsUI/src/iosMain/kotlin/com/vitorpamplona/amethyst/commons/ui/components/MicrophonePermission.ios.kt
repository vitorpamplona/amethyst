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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private class IosMicrophonePermission : PlatformPermission {
    override var isGranted: Boolean by mutableStateOf(currentlyGranted())
        private set

    // AVAudioSession answers on a queue of its own; the state is written back on the main one.
    // Once the user has answered, iOS does not ask again and answers at once with that choice.
    override fun launchRequest() {
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            dispatch_async(dispatch_get_main_queue()) { isGranted = granted }
        }
    }

    private fun currentlyGranted() = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted
}

@Composable
actual fun rememberMicrophonePermission(): PlatformPermission = remember { IosMicrophonePermission() }
