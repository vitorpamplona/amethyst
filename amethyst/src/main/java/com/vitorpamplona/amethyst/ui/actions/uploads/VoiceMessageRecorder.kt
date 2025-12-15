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

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.media3.common.MimeTypes
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class RecordingResult(
    val file: File,
    val mimeType: String,
    val amplitudes: List<Float>,
    val duration: Int,
)

class VoiceMessageRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0
    private var job: Job? = null
    private var amplitudes: MutableList<Float> = mutableListOf()

    private fun createRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    fun start(
        context: Context,
        scope: CoroutineScope,
    ) {
        val fileName = RandomInstance.randomChars(16) + ".mp4"
        val outputFile = File(context.cacheDir, "/voice/$fileName")
        outputFile.parentFile?.mkdirs()
        this.outputFile = outputFile
        this.startTime = TimeUtils.now()
        this.amplitudes.clear()

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

        job?.cancel()
        job =
            scope.launch {
                while (recorder != null) {
                    amplitudes.add(recorder?.maxAmplitude?.toFloat() ?: 0f)
                    delay(1000)
                }
            }
    }

    fun stop(): RecordingResult? {
        job?.cancel()
        job = null

        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            Log.w("VoiceMessageRecorder", "Failed to stop recording... Too short?", e)
        }
        recorder?.reset()
        recorder = null
        val currentTime = TimeUtils.now()
        val file = outputFile
        return if (currentTime - startTime >= 1 && file != null) {
            RecordingResult(
                file,
                MimeTypes.AUDIO_AAC,
                amplitudes,
                (currentTime - startTime).toInt(),
            )
        } else {
            null
        }
    }
}
