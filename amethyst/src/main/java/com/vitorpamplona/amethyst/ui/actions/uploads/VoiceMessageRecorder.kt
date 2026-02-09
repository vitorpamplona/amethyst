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

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.media3.common.MimeTypes
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class RecordingResult(
    val file: File,
    val mimeType: String,
    val amplitudes: List<Float>,
    val duration: Int,
)

class VoiceMessageRecorder {
    @Volatile
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0

    // Own scope to manage lifecycle independently from caller
    private var recorderScope: CoroutineScope? = null

    @Volatile
    private var amplitudeSamplingJob: Job? = null
    private var amplitudes: MutableList<Float> = mutableListOf()

    private fun createRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context.applicationContext)
        } else {
            MediaRecorder()
        }

    @Synchronized
    fun start(
        context: Context,
        parentScope: CoroutineScope,
    ) {
        // Clean up any existing recording first
        cleanup()

        val fileName = RandomInstance.randomChars(16) + ".mp4"
        val outputFile = File(context.cacheDir, "voice/$fileName")
        outputFile.parentFile?.mkdirs()
        this.outputFile = outputFile
        this.startTime = TimeUtils.now()
        this.amplitudes.clear()

        // Create own scope with SupervisorJob so failures don't cascade
        val scopeJob = SupervisorJob(parentScope.coroutineContext[Job])
        recorderScope = CoroutineScope(Dispatchers.Main.immediate + scopeJob)

        createRecorder(context).apply {
            setAudioEncodingBitRate(16 * 44100)
            setAudioSamplingRate(44100) // Set the desired audio sampling rate (e.g., 44.1 kHz)
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile)

            prepare()
            start()

            recorder = this
        }

        // Launch amplitude sampling in our own scope
        amplitudeSamplingJob =
            recorderScope?.launch {
                while (isActive) {
                    val recorderRef = recorder ?: break
                    try {
                        val amplitude = recorderRef.maxAmplitude.toFloat()
                        synchronized(amplitudes) {
                            amplitudes.add(amplitude)
                        }
                    } catch (e: IllegalStateException) {
                        // MediaRecorder might be in invalid state, stop sampling
                        Log.w("VoiceMessageRecorder", "MediaRecorder in invalid state during amplitude sampling", e)
                        break
                    }
                    delay(1000)
                }
            }
    }

    @Synchronized
    fun stop(): RecordingResult? {
        if (recorder == null) {
            cleanup()
            return null
        }
        val currentTime = TimeUtils.now()
        val file = outputFile

        // Capture amplitudes before cleanup
        val amplitudesCopy =
            synchronized(amplitudes) {
                amplitudes.toList()
            }
        val duration = (currentTime - startTime).toInt()

        // Clean up recorder and scope
        cleanup()

        return if (duration >= 1 && file != null) {
            RecordingResult(
                file,
                MimeTypes.AUDIO_AAC,
                amplitudesCopy,
                duration,
            )
        } else {
            null
        }
    }

    /**
     * Cleans up all resources: stops recorder, cancels jobs, cancels scope.
     * Safe to call multiple times.
     */
    @Synchronized
    private fun cleanup() {
        // Cancel amplitude sampling job
        amplitudeSamplingJob?.cancel()
        amplitudeSamplingJob = null

        // Stop any remaining coroutines before touching the recorder
        recorderScope?.cancel()
        recorderScope = null

        // Swap local reference so we always null out the volatile field
        val recorderToRelease = recorder
        recorder = null

        recorderToRelease?.let { mediaRecorder ->
            try {
                mediaRecorder.stop()
            } catch (e: IllegalStateException) {
                Log.w("VoiceMessageRecorder", "Failed to stop MediaRecorder due to illegal state", e)
            } catch (e: RuntimeException) {
                // MediaRecorder.stop() can throw RuntimeException if the recording is too short
                // or if no valid audio data was captured. This is a known Android issue.
                Log.w("VoiceMessageRecorder", "Failed to stop MediaRecorder (recording may be too short or invalid)", e)
            } finally {
                try {
                    mediaRecorder.reset()
                } catch (resetError: Exception) {
                    Log.w("VoiceMessageRecorder", "Failed to reset MediaRecorder before release", resetError)
                }
                try {
                    mediaRecorder.release()
                } catch (releaseError: Exception) {
                    Log.w("VoiceMessageRecorder", "Failed to release MediaRecorder resources", releaseError)
                }
            }
        }

        // Reset transient state so a fresh recording always starts cleanly
        outputFile = null
        startTime = 0
        synchronized(amplitudes) {
            amplitudes.clear()
        }
    }
}
