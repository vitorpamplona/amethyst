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
import android.media.AudioTrack
import android.os.Process
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.media.AudioFormat as AndroidAudioFormat

/**
 * [AudioPlayer] backed by Android's [AudioTrack] in `MODE_STREAM`.
 *
 * Routes through `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH` (= `STREAM_MUSIC`)
 * so audio-room playback comes out of the loudspeaker by default and the
 * volume rocker controls media volume — same approach Twitter/X Spaces
 * and Clubhouse use for hands-free audio rooms.
 *
 * Originally this used `USAGE_VOICE_COMMUNICATION` to get call-style
 * volume / ducking, but that routes through `STREAM_VOICE_CALL`, which
 * Android only services audibly while the device is in
 * `MODE_IN_COMMUNICATION`. Nests doesn't drive `AudioManager.mode`
 * (only the NIP-100 `CallAudioManager` does), so the playback either
 * dropped to the earpiece at near-zero volume or produced no audio at
 * all depending on the device — making rooms appear silent on both
 * phones. Echo cancellation still works on the capture side via
 * `MediaRecorder.AudioSource.VOICE_COMMUNICATION` regardless of the
 * playback usage.
 *
 * Buffer sizing: target ~250 ms of slack, computed as
 * `max(minBuffer * 16, 250 ms-equivalent)`. The previous 4× minimum (~80 ms
 * by the device-reported floor) underran on devices with very small
 * `getMinBufferSize` returns and on any handset whose decode loop got
 * stalled by Compose recomposition / GC on Main. 250 ms matches the
 * jitter-buffer depth Spaces / Clubhouse use for hands-free audio rooms,
 * and combined with the per-subscription pre-roll in [NestPlayer] keeps
 * the AudioTrack from underruning across typical mobile network jitter.
 *
 * Threading: writes go through a per-instance audio-priority single-thread
 * dispatcher (`HandlerThread` + [Process.THREAD_PRIORITY_AUDIO]) instead of
 * `Dispatchers.IO`. The previous shape did one IO dispatcher hop per Opus
 * frame (~50 hops/sec/speaker) and contended with whatever else `Dispatchers.IO`
 * was running; an audio-priority dedicated thread gets reliable scheduling
 * and removes the contention.
 *
 * Two-phase startup: [start] allocates the AudioTrack (in stopped state)
 * + spins up the writer thread. [beginPlayback] calls `AudioTrack.play()`
 * to flip the device into the playing state. Splitting the two lets
 * [NestPlayer] pre-roll several decoded frames into the AudioTrack's
 * internal buffer BEFORE the hardware starts pulling samples, so playback
 * begins with ~100 ms of buffered audio instead of underrunning on the
 * first frame. AudioTrack in `MODE_STREAM` explicitly supports `write()`
 * before `play()` per the platform docs — this is the intended pattern.
 */
class AudioTrackPlayer(
    private val usage: Int = AudioAttributes.USAGE_MEDIA,
    private val contentType: Int = AudioAttributes.CONTENT_TYPE_SPEECH,
) : AudioPlayer {
    private var track: AudioTrack? = null
    private var muted: Boolean = false
    private var volume: Float = 1f

    /**
     * Dedicated audio-priority single-thread executor for AudioTrack writes.
     * Lazily created on [start] and shut down on [stop] so a never-started
     * player doesn't leak a thread. `THREAD_PRIORITY_AUDIO` is the standard
     * Linux nice level for VoIP / WebRTC playback paths on Android — it sits
     * above background but below true audio-callback priority, so the OS
     * scheduler keeps it running through GC / Compose recomposition without
     * starving other threads.
     */
    private var audioExecutor: ExecutorService? = null
    private var audioDispatcher: ExecutorCoroutineDispatcher? = null

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
        // Target ~250 ms of audio: enough headroom so the decode loop can
        // miss its 20 ms cadence by an order of magnitude before the device
        // underruns. Take the larger of `minBuffer * 16` and an explicit
        // 250 ms-equivalent so devices that report a small minBuffer still
        // get the same wall-clock slack.
        val targetBytes250Ms =
            (AudioFormat.SAMPLE_RATE_HZ / 4) * AudioFormat.BYTES_PER_SAMPLE * AudioFormat.CHANNELS
        val bufferBytes = maxOf(minBuffer * 16, targetBytes250Ms)

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

        // NOTE: deliberately NOT calling `newTrack.play()` here — that's
        // [beginPlayback]'s job. AudioTrack.write is legal in the not-yet-
        // playing state for `MODE_STREAM`, which is the contract that lets
        // [NestPlayer] pre-roll decoded frames into the buffer before the
        // hardware starts consuming.
        applyMuteVolume(newTrack)
        // Spin up the audio-priority writer thread. The executor is private
        // to this player instance so per-speaker NestPlayer pumps don't
        // contend on a shared queue. Priority is set inside the thread's
        // Runnable because Linux thread priority is per-OS-thread, not
        // per-Java Thread; `Thread.setPriority` does NOT translate to a
        // Linux nice level on Android. `Process.setThreadPriority` does.
        val executor =
            Executors.newSingleThreadExecutor { r ->
                Thread(
                    {
                        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
                        r.run()
                    },
                    "nest-audio-writer",
                )
            }
        audioExecutor = executor
        audioDispatcher = executor.asCoroutineDispatcher()
        track = newTrack
    }

    override fun beginPlayback() {
        // Idempotent: AudioTrack.play() on an already-playing track is a
        // no-op, and we gate on the track itself being non-null so a
        // beginPlayback before start (or after stop) silently no-ops
        // rather than blowing up — matches the rest of the player's
        // tolerant-of-misordered-calls posture.
        val t = track ?: return
        try {
            t.play()
        } catch (e: Throwable) {
            // PLAY-on-uninitialized AudioTrack is the only realistic
            // failure here, and we already guard against an unallocated
            // track above. Throw so [NestPlayer]'s outer catch surfaces
            // it via `onError(AudioException.PlaybackFailed)` — same path
            // that handles every other mid-stream device failure.
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "AudioTrack.play() rejected start",
                e,
            )
        }
    }

    override suspend fun enqueue(pcm: ShortArray) {
        val t = track ?: throw AudioException(AudioException.Kind.PlaybackFailed, "player not started")
        val dispatcher =
            audioDispatcher
                ?: throw AudioException(AudioException.Kind.PlaybackFailed, "audio dispatcher not initialized")
        // AudioTrack.write blocks if the internal buffer is full. Run on the
        // per-instance audio-priority writer thread so the WRITE_BLOCKING
        // suspension is on a thread the OS schedules tightly, and so we
        // don't compete with whatever else `Dispatchers.IO` is running.
        withContext(dispatcher) {
            val written = t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                throw AudioException(
                    AudioException.Kind.PlaybackFailed,
                    "AudioTrack.write returned error code $written",
                )
            }
        }
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        track?.let { applyMuteVolume(it) }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        track?.let { applyMuteVolume(it) }
    }

    override fun stop() {
        val t = track ?: return
        track = null
        runCatching { t.pause() }
        runCatching { t.flush() }
        runCatching { t.stop() }
        runCatching { t.release() }
        // Tear down the audio-priority writer. close() on the
        // ExecutorCoroutineDispatcher shuts down the executor (and the
        // underlying single thread) — pending writes are abandoned rather
        // than blocked on, since the AudioTrack itself has already been
        // stopped + released two lines up so any further `write` would
        // throw IllegalStateException anyway.
        audioDispatcher?.close()
        audioDispatcher = null
        audioExecutor?.let { runCatching { it.shutdownNow() } }
        audioExecutor = null
    }

    private fun applyMuteVolume(track: AudioTrack) {
        // setVolume is preferred over pause(): it keeps the streaming pipeline
        // running so unmute is sample-accurate and there's no AudioTrack-restart
        // glitch. AudioTrack default gain is 1.0 (RFC: AudioTrack#setVolume).
        // Mute and per-stream volume compose multiplicatively — a muted stream
        // is silent regardless of volume, and a stream at volume 0 is silent
        // regardless of mute.
        val gain = if (muted) 0f else volume
        runCatching { track.setVolume(gain) }
    }
}
