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
package com.vitorpamplona.amethyst.ios.playback

import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.audio.downsampledWaveform
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Path.Companion.toPath
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.coroutines.resume
import kotlin.math.pow

/**
 * Records a voice message as AAC in an `.m4a`, like Android's `VoiceMessageRecorder`, sampling the
 * peak level [SAMPLE_INTERVAL_MS] apart for the waveform (scaled to MediaRecorder's 0..32767 so the
 * two platforms draw the same bars).
 */
@OptIn(ExperimentalForeignApi::class)
class IosVoiceRecorder {
    private var recorder: AVAudioRecorder? = null
    private var path: String? = null
    private var startedAt = 0L
    private var sampling: Job? = null
    private val amplitudes = mutableListOf<Float>()

    /** Starts recording; false when the microphone could not be opened. */
    fun start(scope: CoroutineScope): Boolean {
        AudioSessionMode.record()
        val folder = NSTemporaryDirectory() + "voice/"
        NSFileManager.defaultManager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
        val file = folder + NSUUID().UUIDString + ".m4a"
        val settings =
            mapOf<Any?, Any?>(
                AVFormatIDKey to kAudioFormatMPEG4AAC,
                AVSampleRateKey to 44100.0,
                AVNumberOfChannelsKey to 1,
                AVEncoderAudioQualityKey to AVAudioQualityHigh,
            )
        val created = AVAudioRecorder(uRL = NSURL.fileURLWithPath(file), settings = settings, error = null)
        created.meteringEnabled = true
        if (!created.record()) {
            Log.w(TAG) { "The recorder did not start" }
            AudioSessionMode.release()
            return false
        }
        recorder = created
        path = file
        startedAt = TimeUtils.now()
        amplitudes.clear()
        sampling =
            scope.launch {
                while (isActive) {
                    delay(SAMPLE_INTERVAL_MS)
                    val current = recorder ?: break
                    current.updateMeters()
                    val decibels = current.peakPowerForChannel(0u)
                    amplitudes += (10.0.pow(decibels / 20.0) * MAX_AMPLITUDE).toFloat()
                }
            }
        return true
    }

    /** Stops and returns the recording, or null when it was under a second or failed. */
    fun stop(): RecordingResult? {
        val current = recorder ?: return null
        sampling?.cancel()
        sampling = null
        current.stop()
        recorder = null
        AudioSessionMode.release()
        val duration = (TimeUtils.now() - startedAt).toInt()
        val file = path ?: return null
        return if (duration >= 1) RecordingResult(file.toPath(), "audio/aac", amplitudes.toList().downsampledWaveform(), duration) else null
    }

    companion object {
        private const val TAG = "IosVoiceRecorder"
        const val SAMPLE_INTERVAL_MS = 100L
        private const val MAX_AMPLITUDE = 32767.0

        val hasPermission: Boolean get() = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

        suspend fun requestPermission(): Boolean =
            suspendCancellableCoroutine { cont ->
                AVAudioSession.sharedInstance().requestRecordPermission { granted -> if (cont.isActive) cont.resume(granted) }
            }
    }
}
