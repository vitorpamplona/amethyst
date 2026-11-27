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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterInputStatus_NoDataNow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.setActive
import kotlin.concurrent.AtomicInt

/**
 * The audio session a room needs: playback and recording at once, voice-chat processing (echo
 * cancellation), the speaker rather than the earpiece, Bluetooth headsets allowed. The `audio`
 * background mode in Info.plist keeps a room playing with the screen off.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun activateRoomAudioSession() {
    val session = AVAudioSession.sharedInstance()
    session.setCategory(
        AVAudioSessionCategoryPlayAndRecord,
        mode = AVAudioSessionModeVoiceChat,
        options = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetooth,
        error = null,
    )
    session.setActive(true, error = null)
}

/**
 * The microphone on AVAudioEngine, the iOS counterpart of AudioRecordCapture: the input node's
 * native format converted to 48 kHz 16-bit [channelCount]-channel PCM and cut into 20 ms frames.
 * iOS asks for the microphone the first time (NSMicrophoneUsageDescription).
 */
@OptIn(ExperimentalForeignApi::class)
class AVAudioEngineCapture(
    private val channelCount: Int = AudioFormat.DEFAULT_CHANNELS,
) : AudioCapture {
    private val engine = AVAudioEngine()

    // The tap runs on an audio thread; the reader is behind by at most a second, then old frames go.
    private val frames = Channel<ShortArray>(capacity = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val frameSamples = AudioFormat.FRAME_SIZE_SAMPLES * channelCount
    private var pending = ShortArray(0)

    override fun start() {
        activateRoomAudioSession()
        val input = engine.inputNode
        val inputFormat = input.outputFormatForBus(0u)
        val target =
            AVAudioFormat(
                commonFormat = AVAudioPCMFormatInt16,
                sampleRate = AudioFormat.SAMPLE_RATE_HZ.toDouble(),
                channels = channelCount.toUInt(),
                interleaved = true,
            )
        val converter =
            AVAudioConverter(fromFormat = inputFormat, toFormat = target)
                ?: throw AudioException(AudioException.Kind.DeviceUnavailable, "No converter from the microphone's format")
        input.installTapOnBus(0u, bufferSize = TAP_BUFFER_FRAMES, format = inputFormat) { buffer, _ ->
            if (buffer != null) convertAndQueue(converter, buffer, target, inputFormat.sampleRate)
        }
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            input.removeTapOnBus(0u)
            throw AudioException(AudioException.Kind.DeviceUnavailable, "The microphone could not start (permission denied, or in use)")
        }
    }

    private fun convertAndQueue(
        converter: AVAudioConverter,
        buffer: AVAudioPCMBuffer,
        target: AVAudioFormat,
        inputRate: Double,
    ) {
        val capacity = (buffer.frameLength.toDouble() * AudioFormat.SAMPLE_RATE_HZ / inputRate).toUInt() + 32u
        val out = AVAudioPCMBuffer(pCMFormat = target, frameCapacity = capacity) ?: return
        var supplied = false
        converter.convertToBuffer(out, error = null) { _, status ->
            if (supplied) {
                status!!.pointed.value = AVAudioConverterInputStatus_NoDataNow
                null
            } else {
                supplied = true
                status!!.pointed.value = AVAudioConverterInputStatus_HaveData
                buffer
            }
        }
        val samples = out.frameLength.toInt() * channelCount
        val data = out.int16ChannelData?.get(0) ?: return
        val chunk = ShortArray(samples) { data[it] }
        pending += chunk
        while (pending.size >= frameSamples) {
            frames.trySend(pending.copyOf(frameSamples))
            pending = pending.copyOfRange(frameSamples, pending.size)
        }
    }

    override suspend fun readFrame(): ShortArray? = frames.receiveCatching().getOrNull()

    override fun stop() {
        engine.inputNode.removeTapOnBus(0u)
        engine.stop()
        frames.close()
    }

    private companion object {
        const val TAP_BUFFER_FRAMES: UInt = 4_800u
    }
}

/**
 * The speaker on AVAudioEngine, the iOS counterpart of AudioTrackPlayer: decoded frames scheduled
 * on a player node, at most [MAX_QUEUED_BUFFERS] ahead (enqueue waits past that, as a full
 * AudioTrack blocks), playing from [beginPlayback] so the jitter buffer primes first.
 */
@OptIn(ExperimentalForeignApi::class)
class AVAudioEnginePlayer(
    private val channelCount: Int = AudioFormat.DEFAULT_CHANNELS,
    sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
) : AudioPlayer {
    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private val format =
        AVAudioFormat(standardFormatWithSampleRate = sampleRate.toDouble(), channels = channelCount.toUInt())
            ?: throw AudioException(AudioException.Kind.DeviceUnavailable, "No playback format for $sampleRate Hz")
    private val queued = AtomicInt(0)
    private var volume = 1.0f
    private var muted = false

    override fun start() {
        activateRoomAudioSession()
        engine.attachNode(player)
        engine.connect(player, to = engine.mainMixerNode, format = format)
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            throw AudioException(AudioException.Kind.PlaybackFailed, "The audio engine could not start")
        }
    }

    override fun beginPlayback() {
        if (!player.isPlaying()) player.play()
    }

    override suspend fun enqueue(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        val frames = pcm.size / channelCount
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames.toUInt()) ?: return
        buffer.frameLength = frames.toUInt()
        val channels = buffer.floatChannelData ?: return
        for (channel in 0 until channelCount) {
            val samples = channels[channel] ?: continue
            for (i in 0 until frames) samples[i] = pcm[i * channelCount + channel] / 32768f
        }
        while (queued.value >= MAX_QUEUED_BUFFERS) delay(QUEUE_POLL_MS)
        queued.incrementAndGet()
        player.scheduleBuffer(buffer) { queued.decrementAndGet() }
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        player.volume = if (muted) 0f else volume
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        if (!muted) player.volume = this.volume
    }

    override fun stop() {
        player.stop()
        engine.stop()
    }

    private companion object {
        /** 10 frames, 200 ms: enough to ride out a late packet without growing the delay. */
        const val MAX_QUEUED_BUFFERS = 10
        const val QUEUE_POLL_MS = 5L
    }
}
