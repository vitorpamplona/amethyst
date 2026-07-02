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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Shared upload + media-probe mechanics for the Podcasting-2.0 composers (episode, show, trailer).
 * Mirrors the music-track composer's approach but kept in one place so all three authoring
 * ViewModels resolve a picked file to a hosted URL the same way.
 */
object PodcastComposerMedia {
    class UploadException(
        kind: String,
        details: String,
    ) : Exception("$kind upload failed: $details")

    /**
     * Uploads a single picked file (cover image or audio) through the user's media server and
     * returns the hosted URL. Throws [UploadException] if the server rejects it or returns no URL.
     */
    suspend fun upload(
        orchestrator: MultiOrchestrator,
        kind: String,
        account: Account,
        server: ServerName,
        quality: CompressorQuality,
        stripMetadata: Boolean,
        alt: String?,
        context: Context,
        onStrippingFailed: suspend () -> Boolean,
    ): String {
        val res =
            orchestrator.upload(
                alt = alt,
                contentWarningReason = null,
                mediaQuality = quality,
                server = server,
                account = account,
                context = context,
                useH265 = false,
                stripMetadata = stripMetadata,
                onStrippingFailed = onStrippingFailed,
            )
        if (!res.allGood) throw UploadException(kind, formatUploadErrors(res.errors, context))
        return firstUploadedUrl(res.successful)
            ?: throw UploadException(kind, "Server didn't return a URL for the uploaded $kind.")
    }

    private fun firstUploadedUrl(successful: List<UploadingState.Finished>): String? =
        successful
            .firstNotNullOfOrNull { it.result as? UploadOrchestrator.OrchestratorResult.ServerResult }
            ?.url

    private fun formatUploadErrors(
        errors: List<UploadingState.Error>,
        context: Context,
    ): String =
        errors
            .map { context.getString(it.errorResource, *it.params) }
            .distinct()
            .joinToString(".\n")

    /** Audio metadata read off a picked file, used to auto-fill the composer. Any field may be null. */
    class ProbedAudio(
        val durationSeconds: Int?,
        val title: String?,
    )

    /**
     * Reads duration + title from a picked audio file via [MediaMetadataRetriever] (heavy — call off
     * the main thread). Returns null if the provider rejects it or the file isn't a real container.
     */
    fun probeAudio(
        context: Context,
        uri: Uri,
    ): ProbedAudio? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ProbedAudio(
                durationSeconds = durationMs?.let { (it / 1000).toInt().takeIf { secs -> secs > 0 } },
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.ifBlank { null },
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** Current time as an RFC2822 date string (`Tue, 24 Jun 2025 12:00:00 GMT`) — the spec's `pubdate`. */
    fun rfc2822Now(): String = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("GMT")))

    /** A fresh, stable `d` tag for a new addressable episode/trailer. */
    fun generateDTag(prefix: String): String = "$prefix-${System.currentTimeMillis() / 1000}-${UUID.randomUUID().toString().take(8)}"

    /** Splits a comma-separated text field into a clean list (trimmed, no blanks). */
    fun parseCsv(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
