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

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.AudioFormat as AndroidAudioFormat

/**
 * [AudioPlayer] backed by Android's [AudioTrack] in `MODE_STREAM`. Targets the
 * voice-call usage stream so the OS treats audio-room playback like a phone
 * call (volume rocker controls call volume, ducks notifications, etc.).
 *
 * Buffer sizing: 4× minimum so the producer can fall behind by ~80 ms before
 * dropouts, which roughly matches the jitter the WebTransport datagram path
 * introduces over typical mobile networks.
 */
class AudioTrackPlayer(
    private val usage: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION,
    private val contentType: Int = AudioAttributes.CONTENT_TYPE_SPEECH,
) : AudioPlayer {
    private var track: AudioTrack? = null

    override fun start() {
        if (track != null) return

        val channelMask =
            when (AudioFormat.CHANNELS) {
                1 -> AndroidAudioFormat.CHANNEL_OUT_MONO
                2 -> AndroidAudioFormat.CHANNEL_OUT_STEREO
                else -> error("unsupported channel count ${AudioFormat.CHANNELS}")
            }

        val minBuffer =
            AudioTrack.getMinBufferSize(
                AudioFormat.SAMPLE_RATE_HZ,
                channelMask,
                AndroidAudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBuffer <= 0) {
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "AudioTrack.getMinBufferSize returned $minBuffer for ${AudioFormat.SAMPLE_RATE_HZ} Hz",
            )
        }
        val bufferBytes = minBuffer * 4

        val newTrack =
            try {
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(usage)
                            .setContentType(contentType)
                            .build(),
                    ).setAudioFormat(
                        AndroidAudioFormat
                            .Builder()
                            .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AudioFormat.SAMPLE_RATE_HZ)
                            .setChannelMask(channelMask)
                            .build(),
                    ).setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (t: Throwable) {
                throw AudioException(
                    AudioException.Kind.DeviceUnavailable,
                    "Failed to construct AudioTrack",
                    t,
                )
            }

        try {
            newTrack.play()
        } catch (t: Throwable) {
            runCatching { newTrack.release() }
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "AudioTrack.play() rejected start",
                t,
            )
        }
        track = newTrack
    }

    override suspend fun enqueue(pcm: ShortArray) {
        val t = track ?: throw AudioException(AudioException.Kind.PlaybackFailed, "player not started")
        // AudioTrack.write blocks if the internal buffer is full. Run on IO so
        // we don't stall a coroutine dispatcher backed by a small thread pool.
        withContext(Dispatchers.IO) {
            val written = t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                throw AudioException(
                    AudioException.Kind.PlaybackFailed,
                    "AudioTrack.write returned error code $written",
                )
            }
        }
    }

    override fun stop() {
        val t = track ?: return
        track = null
        runCatching { t.pause() }
        runCatching { t.flush() }
        runCatching { t.stop() }
        runCatching { t.release() }
    }

    @Suppress("unused")
    val voiceCallUsage: Int get() = AudioManager.STREAM_VOICE_CALL // kept for documentation
}
