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
package com.vitorpamplona.nestsclient.audio

import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.AudioFormat as AndroidAudioFormat

/**
 * [AudioCapture] backed by Android's [AudioRecord] from the
 * VOICE_COMMUNICATION input source — same source LiveKit, WebRTC, and most
 * voice-chat libraries use, so it gets the platform's echo-cancellation and
 * noise-suppression filters when available.
 *
 * **Permission:** the caller is responsible for holding `RECORD_AUDIO` before
 * calling [start]; this class will throw [AudioException.Kind.DeviceUnavailable]
 * if the OS denies the resource.
 */
class AudioRecordCapture(
    private val source: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
) : AudioCapture {
    private var record: AudioRecord? = null
    private var stopped = false

    override fun start() {
        check(!stopped) { "capture already stopped" }
        if (record != null) return

        val channelMask =
            when (AudioFormat.CHANNELS) {
                1 -> AndroidAudioFormat.CHANNEL_IN_MONO
                2 -> AndroidAudioFormat.CHANNEL_IN_STEREO
                else -> error("unsupported channel count ${AudioFormat.CHANNELS}")
            }

        val minBuffer =
            AudioRecord.getMinBufferSize(
                AudioFormat.SAMPLE_RATE_HZ,
                channelMask,
                AndroidAudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBuffer <= 0) {
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "AudioRecord.getMinBufferSize returned $minBuffer for ${AudioFormat.SAMPLE_RATE_HZ} Hz",
            )
        }
        val bufferBytes =
            maxOf(minBuffer, AudioFormat.FRAME_SIZE_SAMPLES * AudioFormat.BYTES_PER_SAMPLE * 4)

        val rec =
            try {
                @Suppress("MissingPermission")
                AudioRecord(source, AudioFormat.SAMPLE_RATE_HZ, channelMask, AndroidAudioFormat.ENCODING_PCM_16BIT, bufferBytes)
            } catch (t: Throwable) {
                throw AudioException(
                    AudioException.Kind.DeviceUnavailable,
                    "Failed to construct AudioRecord (RECORD_AUDIO permission?)",
                    t,
                )
            }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "AudioRecord state=${rec.state} after construction (expected INITIALIZED)",
            )
        }
        try {
            rec.startRecording()
        } catch (t: Throwable) {
            runCatching { rec.release() }
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "AudioRecord.startRecording() failed",
                t,
            )
        }
        record = rec
    }

    override suspend fun readFrame(): ShortArray? {
        val rec = record ?: return null
        val frame = ShortArray(AudioFormat.FRAME_SIZE_SAMPLES)
        return withContext(Dispatchers.IO) {
            var read = 0
            while (read < frame.size) {
                if (stopped) return@withContext null
                val n = rec.read(frame, read, frame.size - read)
                if (n < 0) {
                    throw AudioException(
                        AudioException.Kind.PlaybackFailed,
                        "AudioRecord.read returned error code $n",
                    )
                }
                if (n == 0) {
                    // Underrun — wait briefly and retry. This avoids busy-waiting
                    // on a slow producer.
                    kotlinx.coroutines.delay(2)
                    continue
                }
                read += n
            }
            frame
        }
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        val rec = record ?: return
        record = null
        runCatching { rec.stop() }
        runCatching { rec.release() }
    }
}
