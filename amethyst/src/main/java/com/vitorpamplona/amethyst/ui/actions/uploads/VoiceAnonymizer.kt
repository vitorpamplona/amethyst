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

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.WaveformSimilarityBasedOverlapAdd
import be.tarsos.dsp.io.TarsosDSPAudioFloatConverter
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.resample.RateTransposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Result of voice anonymization processing.
 *
 * @property file The output audio file (AAC in MP4 container)
 * @property waveform Amplitude data for waveform visualization (one value per second)
 * @property duration Audio duration in seconds
 */
data class AnonymizedResult(
    val file: File,
    val waveform: List<Float>,
    val duration: Int,
)

/**
 * Processes audio files to alter voice characteristics for privacy.
 *
 * Uses TarsosDSP's WSOLA (Waveform Similarity Overlap-Add) algorithm combined with
 * rate transposition to shift pitch while preserving duration. Note that in TarsosDSP,
 * pitch factors work inversely: factor < 1 raises pitch, factor > 1 lowers pitch.
 */
class VoiceAnonymizer {
    companion object {
        private const val TAG = "VoiceAnonymizer"
        private const val CHANNELS = 1
        private const val BIT_RATE = 128000
    }

    /**
     * Applies voice anonymization to an audio file.
     *
     * The process involves three stages:
     * 1. Decode input audio to PCM (0-30% progress)
     * 2. Apply pitch shifting with TarsosDSP (30-70% progress)
     * 3. Encode processed audio to AAC (70-100% progress)
     *
     * @param inputFile Source audio file (supports formats decodable by MediaCodec)
     * @param preset Voice transformation preset (NONE is not allowed)
     * @param onProgress Callback invoked with progress value from 0.0 to 1.0
     * @return [Result.success] with [AnonymizedResult] containing the output file,
     *         waveform data, and duration; or [Result.failure] with the exception
     */
    suspend fun anonymize(
        inputFile: File,
        preset: VoicePreset,
        onProgress: (Float) -> Unit = {},
    ): Result<AnonymizedResult> =
        withContext(Dispatchers.IO) {
            if (preset == VoicePreset.NONE) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot anonymize with NONE preset"),
                )
            }

            try {
                val outputFile = createOutputFile(inputFile, preset)
                val (pcmData, sampleRate, duration) =
                    decodeAudioToPcm(inputFile) { progress ->
                        onProgress(progress * 0.3f)
                    }

                val processedPcm =
                    processPcmWithTarsos(pcmData, preset, sampleRate) { progress ->
                        onProgress(0.3f + progress * 0.4f)
                    }

                val waveform = extractWaveform(processedPcm, sampleRate)

                encodePcmToAac(processedPcm, sampleRate, outputFile) { progress ->
                    onProgress(0.7f + progress * 0.3f)
                }

                onProgress(1f)
                Result.success(AnonymizedResult(outputFile, waveform, duration))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to anonymize audio", e)
                Result.failure(e)
            }
        }

    private fun createOutputFile(
        inputFile: File,
        preset: VoicePreset,
    ): File {
        val baseName = inputFile.nameWithoutExtension
        val presetSuffix = preset.name.lowercase()
        return File(inputFile.parentFile, "${baseName}_$presetSuffix.mp4")
    }

    private data class DecodedAudio(
        val pcmData: FloatArray,
        val sampleRate: Int,
        val duration: Int,
    )

    private suspend fun decodeAudioToPcm(
        inputFile: File,
        onProgress: (Float) -> Unit,
    ): DecodedAudio {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                throw IllegalStateException("No audio track found in file")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val duration = (durationUs / 1_000_000).toInt()

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmSamples = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && currentCoroutineContext().isActive) {
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            decoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                0,
                            )
                            extractor.advance()
                            if (durationUs > 0) {
                                onProgress((presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                            }
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                    val shortBuffer = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                    while (shortBuffer.hasRemaining()) {
                        pcmSamples.add(shortBuffer.get() / 32768f)
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }

            return DecodedAudio(pcmSamples.toFloatArray(), sampleRate, duration)
        } finally {
            try {
                decoder?.stop()
            } catch (_: IllegalStateException) {
                // Decoder was never started
            }
            decoder?.release()
            extractor.release()
        }
    }

    private fun processPcmWithTarsos(
        pcmData: FloatArray,
        preset: VoicePreset,
        sampleRate: Int,
        onProgress: (Float) -> Unit,
    ): FloatArray {
        val baseFactor = preset.pitchFactor
        val factor =
            when (preset) {
                VoicePreset.DEEP, VoicePreset.HIGH -> {
                    // Add ±10% random variation
                    val randomShift = 0.9 + (Math.random() * 0.2)
                    baseFactor * randomShift
                }
                else -> baseFactor
            }
        val processedSamples = mutableListOf<Float>()
        val totalSamples = pcmData.size

        val wsola =
            WaveformSimilarityBasedOverlapAdd(
                WaveformSimilarityBasedOverlapAdd.Parameters.musicDefaults(
                    factor,
                    sampleRate.toDouble(),
                ),
            )
        val rateTransposer = RateTransposer(factor)

        val bufferSize = wsola.inputBufferSize
        val overlap = wsola.overlap

        val tarsosDspFormat =
            TarsosDSPAudioFormat(
                sampleRate.toFloat(),
                16,
                1,
                true,
                false,
            )

        val collector =
            object : AudioProcessor {
                override fun process(audioEvent: AudioEvent): Boolean {
                    val buffer = audioEvent.floatBuffer
                    for (i in 0 until audioEvent.bufferSize) {
                        processedSamples.add(buffer[i])
                    }
                    return true
                }

                override fun processingFinished() {}
            }

        val dispatcher =
            AudioDispatcher(
                FloatArrayAudioInputStream(pcmData, tarsosDspFormat, pcmData.size.toLong()),
                bufferSize,
                overlap,
            )

        wsola.setDispatcher(dispatcher)
        dispatcher.addAudioProcessor(wsola)
        dispatcher.addAudioProcessor(rateTransposer)
        dispatcher.addAudioProcessor(collector)

        var samplesProcessed = 0
        val progressProcessor =
            object : AudioProcessor {
                override fun process(audioEvent: AudioEvent): Boolean {
                    samplesProcessed += audioEvent.bufferSize
                    onProgress((samplesProcessed.toFloat() / totalSamples).coerceIn(0f, 1f))
                    return true
                }

                override fun processingFinished() {}
            }
        dispatcher.addAudioProcessor(progressProcessor)

        dispatcher.run()

        return processedSamples.toFloatArray()
    }

    private fun extractWaveform(
        pcmData: FloatArray,
        sampleRate: Int,
    ): List<Float> {
        val waveform = mutableListOf<Float>()
        var offset = 0

        while (offset < pcmData.size) {
            val end = minOf(offset + sampleRate, pcmData.size)
            var maxAmplitude = 0f
            for (i in offset until end) {
                val amplitude = abs(pcmData[i])
                if (amplitude > maxAmplitude) {
                    maxAmplitude = amplitude
                }
            }
            waveform.add(maxAmplitude * 32768f)
            offset += sampleRate
        }

        return waveform
    }

    private fun encodePcmToAac(
        pcmData: FloatArray,
        sampleRate: Int,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, CHANNELS)
        format.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC,
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            var audioTrackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()
            var inputOffset = 0
            var inputDone = false
            var outputDone = false
            val totalSamples = pcmData.size

            while (!outputDone) {
                if (!inputDone) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)!!
                        inputBuffer.clear()

                        val samplesToWrite = minOf((inputBuffer.capacity() / 2), pcmData.size - inputOffset)
                        if (samplesToWrite <= 0) {
                            encoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            for (i in 0 until samplesToWrite) {
                                val sample =
                                    (pcmData[inputOffset + i] * 32767)
                                        .toInt()
                                        .coerceIn(-32768, 32767)
                                        .toShort()
                                inputBuffer.putShort(sample)
                            }
                            val presentationTimeUs = (inputOffset * 1_000_000L) / sampleRate
                            encoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                inputBuffer.position(),
                                presentationTimeUs,
                                0,
                            )
                            inputOffset += samplesToWrite
                            onProgress(inputOffset.toFloat() / totalSamples)
                        }
                    }
                }

                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        audioTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    outputBufferIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)!!
                        if (muxerStarted && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            try {
                encoder.stop()
            } catch (_: IllegalStateException) {
                // Encoder was never started
            }
            encoder.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }
    }
}

private class FloatArrayAudioInputStream(
    private val floatArray: FloatArray,
    private val format: TarsosDSPAudioFormat,
    private val frameLength: Long,
) : be.tarsos.dsp.io.TarsosDSPAudioInputStream {
    private var position = 0

    override fun getFormat(): TarsosDSPAudioFormat = format

    override fun getFrameLength(): Long = frameLength

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val converter = TarsosDSPAudioFloatConverter.getConverter(format)
        val floatBuffer = FloatArray(length / 2)
        val samplesToRead = minOf(floatBuffer.size, floatArray.size - position)

        if (samplesToRead <= 0) return -1

        System.arraycopy(floatArray, position, floatBuffer, 0, samplesToRead)
        position += samplesToRead

        converter.toByteArray(floatBuffer, samplesToRead, buffer, offset)
        return samplesToRead * 2
    }

    override fun skip(bytesToSkip: Long): Long {
        val samplesToSkip = (bytesToSkip / 2).toInt()
        val actualSkip = minOf(samplesToSkip, floatArray.size - position)
        position += actualSkip
        return actualSkip.toLong() * 2
    }

    override fun close() {}
}
