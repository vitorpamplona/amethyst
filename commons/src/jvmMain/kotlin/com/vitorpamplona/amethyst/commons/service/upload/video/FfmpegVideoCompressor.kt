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
package com.vitorpamplona.amethyst.commons.service.upload.video

import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.service.upload.AmethystTempDir
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoCompressionRules
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Transcodes with a system `ffmpeg` using Android's resolution/bitrate table, trying the
 * encoders of [FfmpegEncoders] in turn. Stays on the caller's dispatcher: only [runner] and
 * the probe block on a child process, which keeps cancellation and temp-file behaviour
 * testable under virtual time.
 */
class FfmpegVideoCompressor(
    private val locator: FfmpegLocator = FfmpegLocator.default,
    private val encoders: suspend (ffmpeg: String) -> Set<String> = FfmpegEncoders::listAvailable,
    private val probe: (ffprobe: String?) -> FfmpegProbe = ::FfmpegProbe,
    private val stallTimeoutMs: Long = 60_000,
    private val runner: ProcessRunner = FfmpegProcesses,
    private val gate: Semaphore = FfmpegProcesses.encodeGate,
    private val tempFile: () -> File = { AmethystTempDir.createTempFile("amethyst_video_", ".mp4") },
) : VideoCompressor {
    override val available: Boolean get() = locator.available

    override suspend fun probe(source: File): VideoInfo? = probe.invoke(locator.ffprobe).probe(source)?.info

    override suspend fun compress(
        source: File,
        quality: CompressorQuality,
        useH265: Boolean,
        maxBytes: Long?,
        onProgress: (Double) -> Unit,
    ): VideoCompressionResult {
        val ffmpeg = locator.ffmpeg ?: return VideoCompressionResult.Original(null)
        val probed = probe.invoke(locator.ffprobe).probe(source)
        val rule = probed?.let { VideoCompressionRules.ruleFor(quality, it.info.resolution.getStandard()) }
        val fps = probed?.info?.framerate ?: 30f
        val notices: List<UserNotice> = if (probed?.isHdr == true) listOf(UploadNotice.HdrSource) else emptyList()
        val bitrateFor = { hevc: Boolean -> rule?.getBitrateBps(fps, hevc) ?: VideoCompressionRules.PROBE_FAILED_BITRATE_BPS }

        val duration = probed?.durationSeconds
        if (maxBytes != null && duration != null) {
            val estimate = ((bitrateFor(useH265) + AUDIO_BITRATE_BPS) / 8.0) * duration
            if (estimate > maxBytes) {
                Log.i(TAG) { "Refusing ${source.name}: estimated ${estimate.toLong()} bytes exceeds $maxBytes" }
                return VideoCompressionResult.TooBig
            }
        }

        val candidates = FfmpegEncoders.candidates(encoders(ffmpeg), useH265)
        if (candidates.isEmpty()) return failed(source, notices, "no encoder")

        val outcome = encodeWith(ffmpeg, source, candidates, rule?.shortSide, bitrateFor, duration, onProgress)
        val out = outcome.file ?: return failed(source, notices, outcome.lastStderr)
        if (out.length() >= source.length()) {
            Log.i(TAG) { "Encoded ${source.name} is not smaller (${out.length()} ≥ ${source.length()}); keeping the original" }
            out.deleteOrWarn(TAG, "oversized encode")
            return VideoCompressionResult.Original(strip(source), notices)
        }
        val hevcMissed = useH265 && outcome.encoder?.let { !FfmpegEncoders.isHevc(it) } == true
        return VideoCompressionResult.Compressed(out, if (hevcMissed) notices + UploadNotice.HevcUnavailable else notices)
    }

    override suspend fun convertGif(
        source: File,
        onProgress: (Double) -> Unit,
    ): VideoCompressionResult {
        val ffmpeg = locator.ffmpeg ?: return VideoCompressionResult.Original(null)
        val probed = probe.invoke(locator.ffprobe).probe(source)
        // The MEDIUM row's bitrate for the GIF's standard, at the GIF's own size, as Android's converter.
        val rule = probed?.let { VideoCompressionRules.ruleFor(CompressorQuality.MEDIUM, it.info.resolution.getStandard()) }
        val fps = probed?.info?.framerate ?: 30f
        val notices: List<UserNotice> = if (probed?.isHdr == true) listOf(UploadNotice.HdrSource) else emptyList()
        val bitrateFor = { _: Boolean -> rule?.getBitrateBps(fps, false) ?: VideoCompressionRules.PROBE_FAILED_BITRATE_BPS }

        val candidates = FfmpegEncoders.candidates(encoders(ffmpeg), useH265 = false)
        if (candidates.isEmpty()) return VideoCompressionResult.Original(null, notices + UploadNotice.VideoCompressionFailed("no encoder"))

        val outcome = encodeWith(ffmpeg, source, candidates, null, bitrateFor, probed?.durationSeconds, onProgress)
        // A GIF is never remuxed: on failure it goes up untouched.
        val out = outcome.file ?: return VideoCompressionResult.Original(null, notices + UploadNotice.VideoCompressionFailed(outcome.lastStderr))
        return VideoCompressionResult.Compressed(out, notices)
    }

    override suspend fun strip(source: File): File? {
        val ffmpeg = locator.ffmpeg ?: return null
        var out: File? = null
        try {
            out = tempFile()
            val outcome = runner.run(FfmpegCommandBuilder.remux(ffmpeg, source, out), {}, stallTimeoutMs)
            if (outcome.exitCode == 0) return out
            Log.w(TAG) { "Remux of ${source.name} exited ${outcome.exitCode}: ${outcome.stderrTail.trim()}" }
            out.deleteOrWarn(TAG, "failed remux")
            return null
        } catch (e: CancellationException) {
            withContext(NonCancellable) { out?.deleteOrWarn(TAG, "cancelled remux") }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Remux of ${source.name} failed", e)
            out?.deleteOrWarn(TAG, "failed remux")
            return null
        }
    }

    private class EncodeOutcome(
        val file: File?,
        val encoder: String?,
        val lastStderr: String?,
    )

    /** Tries each candidate in turn under the gate; the bitrate and the HEVC tag follow the candidate. */
    private suspend fun encodeWith(
        ffmpeg: String,
        source: File,
        candidates: List<String>,
        shortSide: Int?,
        bitrateFor: (hevc: Boolean) -> Int,
        durationSeconds: Double?,
        onProgress: (Double) -> Unit,
    ): EncodeOutcome =
        gate.withPermit {
            var lastStderr: String? = null
            for (candidate in candidates) {
                val bitrate = bitrateFor(FfmpegEncoders.isHevc(candidate))
                var out: File? = null
                try {
                    // Created inside the try so a factory that throws leaves nothing behind.
                    out = tempFile()
                    val parser = FfmpegProgressParser(durationSeconds)
                    val command = FfmpegCommandBuilder.encode(ffmpeg, source, out, candidate, bitrate, shortSide)
                    Log.d(TAG) { "Encoding ${source.name} with $candidate at $bitrate bps, short side ${shortSide ?: "kept"}" }
                    val outcome = runner.run(command, { line -> parser.onLine(line)?.let(onProgress) }, stallTimeoutMs)
                    if (outcome.exitCode == 0) return@withPermit EncodeOutcome(out, candidate, null)
                    lastStderr = outcome.stderrTail.trim().ifEmpty { "exit ${outcome.exitCode}" }
                    Log.w(TAG) { "$candidate exited ${outcome.exitCode} on ${source.name}: $lastStderr" }
                    out.deleteOrWarn(TAG, "failed encode")
                } catch (e: CancellationException) {
                    withContext(NonCancellable) { out?.deleteOrWarn(TAG, "cancelled encode") }
                    throw e
                } catch (e: Exception) {
                    lastStderr = e.message ?: e.toString()
                    Log.w(TAG, "$candidate failed on ${source.name}", e)
                    out?.deleteOrWarn(TAG, "failed encode")
                }
            }
            EncodeOutcome(null, null, lastStderr)
        }

    /** Every candidate failed: the original goes up, remuxed when possible, with the notice. */
    private suspend fun failed(
        source: File,
        notices: List<UserNotice>,
        reason: String?,
    ): VideoCompressionResult = VideoCompressionResult.Original(strip(source), notices + UploadNotice.VideoCompressionFailed(reason))

    private companion object {
        const val TAG = "FfmpegVideoCompressor"
        const val AUDIO_BITRATE_BPS = 128_000
    }
}
