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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AudioToolbox.AudioConverterDispose
import platform.AudioToolbox.AudioConverterFillComplexBuffer
import platform.AudioToolbox.AudioConverterNew
import platform.AudioToolbox.AudioConverterRef
import platform.AudioToolbox.AudioConverterRefVar
import platform.AudioToolbox.AudioConverterReset
import platform.AudioToolbox.AudioConverterSetProperty
import platform.AudioToolbox.kAudioConverterEncodeBitRate
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.AudioStreamPacketDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatFlagIsSignedInteger
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kAudioFormatOpus

/**
 * Opus through AudioToolbox's AudioConverter, iOS's own codec (no libopus to ship): 48 kHz,
 * 20 ms (960-sample) packets, as Android's MediaCodec pair. One converter per direction.
 *
 * AudioConverter pulls its input through a C callback; [ConverterInput] hands it exactly one
 * buffer per call (a PCM frame to encode, or one Opus packet with its description to decode),
 * then reports that there is no more, so each call converts one frame.
 */
@OptIn(ExperimentalForeignApi::class)
private class OpusAudioConverter(
    encode: Boolean,
    private val channels: Int,
    pcmSampleRate: Int,
    bitrate: Int?,
) {
    private val converter: AudioConverterRef
    private val input = ConverterInput()
    private val inputRef = StableRef.create(input)

    init {
        converter =
            memScoped {
                val pcm = alloc<AudioStreamBasicDescription>()
                pcm.mSampleRate = pcmSampleRate.toDouble()
                pcm.mFormatID = kAudioFormatLinearPCM
                pcm.mFormatFlags = kAudioFormatFlagIsSignedInteger or kAudioFormatFlagIsPacked
                pcm.mBytesPerPacket = (2 * channels).convert()
                pcm.mFramesPerPacket = 1u
                pcm.mBytesPerFrame = (2 * channels).convert()
                pcm.mChannelsPerFrame = channels.convert()
                pcm.mBitsPerChannel = 16u
                val opus = alloc<AudioStreamBasicDescription>()
                opus.mSampleRate = AudioFormat.SAMPLE_RATE_HZ.toDouble()
                opus.mFormatID = kAudioFormatOpus
                opus.mFramesPerPacket = AudioFormat.FRAME_SIZE_SAMPLES.convert()
                opus.mChannelsPerFrame = channels.convert()
                val out = alloc<AudioConverterRefVar>()
                val status = if (encode) AudioConverterNew(pcm.ptr, opus.ptr, out.ptr) else AudioConverterNew(opus.ptr, pcm.ptr, out.ptr)
                if (status != 0 || out.value == null) {
                    throw AudioException(
                        if (encode) AudioException.Kind.EncoderError else AudioException.Kind.DecoderError,
                        "AudioConverterNew for Opus failed: $status",
                    )
                }
                val ref = out.value!!
                if (encode && bitrate != null) {
                    val rate = alloc<UIntVar>()
                    rate.value = bitrate.convert()
                    AudioConverterSetProperty(ref, kAudioConverterEncodeBitRate, sizeOf<UIntVar>().convert(), rate.ptr)
                }
                ref
            }
    }

    /**
     * Converts [inputBytes] ([inputPackets] packets: PCM frames, or one Opus packet with its
     * [inputDescription]) into [output], at most [outputPackets] packets; returns the bytes written
     * and the packets produced.
     */
    fun convert(
        inputBytes: CPointer<ByteVar>,
        inputSize: Int,
        inputPackets: Int,
        inputDescription: CPointer<AudioStreamPacketDescription>?,
        output: CPointer<ByteVar>,
        outputCapacity: Int,
        outputPackets: Int,
        outputDescription: CPointer<AudioStreamPacketDescription>?,
    ): Pair<Int, Int> =
        memScoped {
            input.data = inputBytes
            input.size = inputSize
            input.packets = inputPackets
            input.description = inputDescription
            input.consumed = false
            val list = alloc<AudioBufferList>()
            list.mNumberBuffers = 1u
            list.mBuffers[0].mNumberChannels = channels.convert()
            list.mBuffers[0].mDataByteSize = outputCapacity.convert()
            list.mBuffers[0].mData = output
            val packets = alloc<UIntVar>()
            packets.value = outputPackets.convert()
            val status = AudioConverterFillComplexBuffer(converter, inputProc, inputRef.asCPointer(), packets.ptr, list.ptr, outputDescription)
            if (status != 0 && status != NO_MORE_INPUT) {
                AudioConverterReset(converter)
                throw IllegalStateException("AudioConverterFillComplexBuffer failed: $status")
            }
            list.mBuffers[0].mDataByteSize.toInt() to packets.value.toInt()
        }

    fun release() {
        AudioConverterDispose(converter)
        inputRef.dispose()
    }
}

/** What the input callback hands the converter next. */
@OptIn(ExperimentalForeignApi::class)
private class ConverterInput {
    var data: CPointer<ByteVar>? = null
    var size: Int = 0
    var packets: Int = 0
    var description: CPointer<AudioStreamPacketDescription>? = null
    var consumed: Boolean = true
}

/** The input callback's "nothing more for now": any non-zero status ends one fill without an error. */
private const val NO_MORE_INPUT: Int = 0x6E6F6D72 // 'nomr'

@OptIn(ExperimentalForeignApi::class)
private val inputProc =
    staticCFunction {
        _: AudioConverterRef?,
        ioNumberDataPackets: CPointer<UIntVar>?,
        ioData: CPointer<AudioBufferList>?,
        outPacketDescriptions: CPointer<CPointerVar<AudioStreamPacketDescription>>?,
        userData: COpaquePointer?,
        ->
        val state = userData!!.asStableRef<ConverterInput>().get()
        if (state.consumed) {
            ioNumberDataPackets!!.pointed.value = 0u
            return@staticCFunction NO_MORE_INPUT
        }
        val buffer = ioData!!.pointed.mBuffers[0]
        buffer.mData = state.data
        buffer.mDataByteSize = state.size.convert()
        ioNumberDataPackets!!.pointed.value = state.packets.convert()
        if (outPacketDescriptions != null) outPacketDescriptions.pointed.value = state.description
        state.consumed = true
        0
    }

/** The longest Opus packet a 20 ms frame encodes to, with room to spare. */
private const val MAX_OPUS_PACKET_BYTES = 4000

/** Opus encoder on AudioToolbox, the iOS counterpart of MediaCodecOpusEncoder (~32 kbit/s VBR). */
@OptIn(ExperimentalForeignApi::class)
class AudioToolboxOpusEncoder(
    private val channelCount: Int = AudioFormat.DEFAULT_CHANNELS,
    targetBitrate: Int = DEFAULT_BITRATE_BPS,
) : OpusEncoder {
    init {
        require(channelCount in 1..2) { "AudioToolboxOpusEncoder supports mono (1) or stereo (2) only, got $channelCount" }
    }

    private val converter = OpusAudioConverter(encode = true, channels = channelCount, pcmSampleRate = AudioFormat.SAMPLE_RATE_HZ, bitrate = targetBitrate)
    private val output = ByteArray(MAX_OPUS_PACKET_BYTES)

    /** One 960-sample frame (per channel, interleaved) to one Opus packet; empty while the codec primes. */
    override fun encode(pcm: ShortArray): ByteArray =
        try {
            memScoped {
                val description = alloc<AudioStreamPacketDescription>()
                val (bytes, packets) =
                    pcm.usePinned { inPinned ->
                        output.usePinned { outPinned ->
                            converter.convert(
                                inputBytes = inPinned.addressOf(0).reinterpret(),
                                inputSize = pcm.size * 2,
                                inputPackets = pcm.size / channelCount,
                                inputDescription = null,
                                output = outPinned.addressOf(0),
                                outputCapacity = output.size,
                                outputPackets = 1,
                                outputDescription = description.ptr,
                            )
                        }
                    }
                if (packets == 0) ByteArray(0) else output.copyOf(bytes)
            }
        } catch (e: IllegalStateException) {
            throw AudioException(AudioException.Kind.EncoderError, e.message ?: "Opus encode failed", e)
        }

    override fun release() = converter.release()

    companion object {
        const val DEFAULT_BITRATE_BPS: Int = 32_000
    }
}

/** Opus decoder on AudioToolbox, the iOS counterpart of MediaCodecOpusDecoder. */
@OptIn(ExperimentalForeignApi::class)
class AudioToolboxOpusDecoder(
    private val channelCount: Int = AudioFormat.DEFAULT_CHANNELS,
    sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
) : OpusDecoder {
    init {
        require(channelCount in 1..2) { "AudioToolboxOpusDecoder supports mono (1) or stereo (2) only, got $channelCount" }
        require(sampleRate > 0) { "AudioToolboxOpusDecoder sampleRate must be positive, got $sampleRate" }
    }

    // A 20 ms packet at the output rate, per channel.
    private val framesPerPacket = AudioFormat.FRAME_SIZE_SAMPLES * sampleRate / AudioFormat.SAMPLE_RATE_HZ
    private val converter = OpusAudioConverter(encode = false, channels = channelCount, pcmSampleRate = sampleRate, bitrate = null)

    /** One Opus packet to its interleaved PCM; an empty array for an empty or undecodable packet. */
    override fun decode(opusPacket: ByteArray): ShortArray {
        if (opusPacket.isEmpty()) return ShortArray(0)
        val out = ShortArray(framesPerPacket * channelCount)
        return try {
            memScoped {
                val description = alloc<AudioStreamPacketDescription>()
                description.mStartOffset = 0
                description.mVariableFramesInPacket = 0u
                description.mDataByteSize = opusPacket.size.convert()
                val (_, frames) =
                    opusPacket.usePinned { inPinned ->
                        out.usePinned { outPinned ->
                            converter.convert(
                                inputBytes = inPinned.addressOf(0),
                                inputSize = opusPacket.size,
                                inputPackets = 1,
                                inputDescription = description.ptr,
                                output = outPinned.addressOf(0).reinterpret(),
                                outputCapacity = out.size * 2,
                                outputPackets = framesPerPacket,
                                outputDescription = null,
                            )
                        }
                    }
                if (frames == out.size / channelCount) out else out.copyOf(frames * channelCount)
            }
        } catch (e: IllegalStateException) {
            throw AudioException(AudioException.Kind.DecoderError, e.message ?: "Opus decode failed", e)
        }
    }

    override fun release() = converter.release()
}
