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

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteOrder

/**
 * [OpusEncoder] backed by Android's [MediaCodec] (`audio/opus`, encoder
 * variant available on API 29+ — the decoder works back to API 21 but the
 * encoder shipped later). One instance per outgoing track.
 *
 * Configuration:
 *   - 48 kHz mono input PCM 16-bit (matches [AudioFormat]).
 *   - Target bitrate ~32 kbit/s VBR — high-quality wideband speech.
 *   - 20 ms frames (the encoder requires the input buffer to hold one frame
 *     at a time for low latency).
 */
class MediaCodecOpusEncoder(
    private val targetBitrate: Int = DEFAULT_BITRATE_BPS,
) : OpusEncoder {
    private val codec: MediaCodec =
        try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                configure(buildFormat(targetBitrate), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        } catch (t: Throwable) {
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "Failed to allocate MediaCodec audio/opus encoder",
                t,
            )
        }

    private val bufferInfo = MediaCodec.BufferInfo()
    private var presentationTimeUs: Long = 0L
    private var released = false

    override fun encode(pcm: ShortArray): ByteArray {
        check(!released) { "encoder released" }
        require(pcm.isNotEmpty()) { "PCM frame must not be empty" }

        val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (inputIndex < 0) return ByteArray(0)
        val inputBuffer =
            codec.getInputBuffer(inputIndex)
                ?: throw AudioException(
                    AudioException.Kind.EncoderError,
                    "MediaCodec returned null input buffer at index $inputIndex",
                )
        inputBuffer.clear()
        inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm)
        val byteCount = pcm.size * AudioFormat.BYTES_PER_SAMPLE
        codec.queueInputBuffer(inputIndex, 0, byteCount, presentationTimeUs, 0)
        presentationTimeUs += FRAME_DURATION_US

        // One PCM frame produces one Opus packet (sometimes after one warmup
        // round). Drain the output queue once.
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                    val opus = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.get(opus)
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (opus.isNotEmpty()) return opus
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    continue
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    return ByteArray(0)
                }

                else -> {
                    return ByteArray(0)
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return ByteArray(0)
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    companion object {
        const val DEFAULT_BITRATE_BPS: Int = 32_000
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val FRAME_DURATION_US = 20_000L

        private fun buildFormat(bitrate: Int): MediaFormat =
            MediaFormat
                .createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_OPUS,
                    AudioFormat.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNELS,
                ).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    // Encoder-side AAC/Opus profile selection: SignalingDelaySamples
                    // is implicit; nothing else required for mono speech.
                    setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                    )
                }
    }
}
