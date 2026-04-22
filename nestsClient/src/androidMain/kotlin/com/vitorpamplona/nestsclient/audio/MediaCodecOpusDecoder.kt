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
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [OpusDecoder] backed by Android's [MediaCodec] (`audio/opus`, available on
 * API 21+). One instance per track — Opus carries forward predictor state
 * across packets, so sharing a decoder across speakers would cause clicks.
 *
 * Configuration:
 *   - 48 kHz mono signed 16-bit PCM output (matches [AudioFormat]).
 *   - CSD-0: Opus identification header per RFC 7845 §5.1, 19 bytes.
 *   - CSD-1 / CSD-2: pre-skip + seek pre-roll, both zero (we don't seek).
 */
class MediaCodecOpusDecoder : OpusDecoder {
    private val codec: MediaCodec =
        try {
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                configure(buildFormat(), null, null, 0)
                start()
            }
        } catch (t: Throwable) {
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "Failed to allocate MediaCodec audio/opus decoder",
                t,
            )
        }

    private val bufferInfo = MediaCodec.BufferInfo()
    private var presentationTimeUs: Long = 0L
    private var released = false

    override fun decode(opusPacket: ByteArray): ShortArray {
        check(!released) { "decoder released" }

        val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (inputIndex < 0) return ShortArray(0)
        val inputBuffer =
            codec.getInputBuffer(inputIndex)
                ?: throw AudioException(
                    AudioException.Kind.DecoderError,
                    "MediaCodec returned null input buffer at index $inputIndex",
                )
        inputBuffer.clear()
        inputBuffer.put(opusPacket)
        codec.queueInputBuffer(inputIndex, 0, opusPacket.size, presentationTimeUs, 0)
        // Advance presentation time by one 20 ms frame.
        presentationTimeUs += FRAME_DURATION_US

        // Drain whatever output is ready right now. A single Opus packet
        // typically yields exactly one output buffer, but on some devices the
        // first call returns INFO_OUTPUT_FORMAT_CHANGED before the PCM frame.
        val collected = ArrayList<Short>(AudioFormat.FRAME_SIZE_SAMPLES)
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer =
                        codec.getOutputBuffer(outputIndex)
                            ?: continue
                    if (bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shorts = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val tmp = ShortArray(shorts.remaining())
                        shorts.get(tmp)
                        for (s in tmp) collected.add(s)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    // No more buffered output for this packet.
                    if (bufferInfo.size == 0) break
                    if (collected.size >= AudioFormat.FRAME_SIZE_SAMPLES) break
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // The format change carries no audio data; loop to read
                    // the actual PCM frame.
                    continue
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    break
                }

                else -> {
                    break
                }
            }
        }
        return ShortArray(collected.size) { collected[it] }
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    companion object {
        private const val DEQUEUE_TIMEOUT_US = 10_000L // 10 ms
        private const val FRAME_DURATION_US = 20_000L // 20 ms

        private fun buildFormat(): MediaFormat {
            val format =
                MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_OPUS,
                    AudioFormat.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNELS,
                )
            format.setByteBuffer("csd-0", ByteBuffer.wrap(buildOpusIdHeader()))
            // Pre-skip + seek pre-roll: both zero, encoded as little-endian
            // 64-bit nanoseconds per Android's MediaCodec contract.
            format.setByteBuffer("csd-1", ByteBuffer.wrap(zeroLongLe()))
            format.setByteBuffer("csd-2", ByteBuffer.wrap(zeroLongLe()))
            return format
        }

        private fun buildOpusIdHeader(): ByteArray {
            // RFC 7845 §5.1 — 19 bytes for mono, mapping family 0.
            val buf = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("OpusHead".encodeToByteArray()) // 8 bytes magic
            buf.put(1.toByte()) // version
            buf.put(AudioFormat.CHANNELS.toByte()) // channel count
            buf.putShort(0) // pre-skip
            buf.putInt(AudioFormat.SAMPLE_RATE_HZ) // input sample rate
            buf.putShort(0) // output gain (Q7.8 dB)
            buf.put(0.toByte()) // mapping family 0
            return buf.array()
        }

        private fun zeroLongLe(): ByteArray =
            ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(0L)
                .array()
    }
}
