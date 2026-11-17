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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegVideoCompressor
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.refineMediaType
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Sound files on the desktop, with the real ffmpeg: a voice memo saved as .mp4 is audio, and its tags go. */
class DesktopAudioUploadTest {
    private val dir = Files.createTempDirectory("audio-upload").toFile()
    private val compressor = FfmpegVideoCompressor()
    private val uploader = DesktopMediaUploader(clientFor = { mockk<BlossomClient>() }, httpClients = mockk(relaxed = true), videoCompressor = compressor)

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun assumeFfmpeg() {
        assumeTrue("ffmpeg not installed", runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false))
        assumeTrue("ffmpeg not found", compressor.available)
    }

    /** A voice memo: AAC only, in an .mp4, with a title and a phone's location. */
    private fun voiceMemo(): File {
        val plain = File(dir, "plain.mp4")
        ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-c:a", "aac", plain.absolutePath)
        return File(dir, "memo.mp4").also { ffmpeg("-i", plain.absolutePath, "-c", "copy", "-metadata", "title=Secret title", "-metadata", "location=+40.7000-074.0000/", it.absolutePath) }
    }

    private fun song(): File = File(dir, "song.mp3").also { ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-c:a", "libmp3lame", "-metadata", "artist=Someone", it.absolutePath) }

    @Test
    fun aVoiceMemoSavedAsMp4IsAudio() {
        assumeFfmpeg()
        val memo = voiceMemo()
        val refined = runBlocking { uploader.refineMediaType(SelectedMedia(StringMediaUri(memo.absolutePath), "video/mp4")) }
        assertEquals("audio/mp4", refined.mimeType)
    }

    @Test
    fun aVoiceMemoGoesUpWithoutItsTagsAndWithoutAsking() {
        assumeFfmpeg()
        val memo = voiceMemo()
        val (sent, asked) = prepare(memo, "audio/mp4")
        assertFalse(asked, "no 'couldn't remove metadata' prompt")
        val tags = tags(assertSentCopy(sent, memo))
        assertFalse("location" in tags || "Secret title" in tags, "tags left: $tags")
    }

    @Test
    fun anMp3GoesUpWithoutItsTags() {
        assumeFfmpeg()
        val song = song()
        assertTrue("Someone" in tags(song), "fixture is tagged")
        val (sent, asked) = prepare(song, "audio/mpeg")
        assertFalse(asked)
        val stripped = assertSentCopy(sent, song)
        assertFalse("Someone" in tags(stripped), "artist removed: ${tags(stripped)}")
        assertTrue(stripped.name.endsWith(".mp3"))

        // The direct strip the chats use does the same.
        val direct = File(runBlocking { uploader.stripMetadata(StringMediaUri(song.absolutePath), "audio/mpeg") }.toString())
        try {
            assertNotEquals(song.absolutePath, direct.absolutePath)
            assertFalse("Someone" in tags(direct))
        } finally {
            direct.delete()
        }
    }

    /** Prepares [file] for upload with stripping on; returns a copy of what would be sent and whether the user was asked. */
    private fun prepare(
        file: File,
        mime: String,
    ): Pair<File?, Boolean> {
        var asked = false
        var sent: File? = null
        runBlocking {
            uploader.withVideoPrepared(UploadOrchestrator(), StringMediaUri(file.absolutePath), mime, CompressorQuality.UNCOMPRESSED, false, true, false, 0.2, onStrippingFailed = {
                asked = true
                true
            }) { ready, _ ->
                // The temp is deleted once this returns: keep a copy to look at.
                sent = File(dir, "sent-" + ready.name).also { ready.copyTo(it, overwrite = true) }
                UploadingState.Error(UploadError.UPLOAD_CANCELLED, emptyArray())
            }
        }
        return sent to asked
    }

    private fun assertSentCopy(
        sent: File?,
        original: File,
    ): File {
        val copy = sent ?: error("nothing sent")
        assertNotEquals(original.readBytes().size.toLong() to original.readBytes().contentHashCode(), copy.length() to copy.readBytes().contentHashCode(), "a stripped copy, not the original")
        return copy
    }

    private fun tags(file: File): String {
        val process = ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format_tags:stream_tags", "-of", "compact", file.absolutePath).redirectErrorStream(true).start()
        process.waitFor(30, TimeUnit.SECONDS)
        return process.inputStream.bufferedReader().readText()
    }

    private fun ffmpeg(vararg args: String) {
        val process = ProcessBuilder(listOf("ffmpeg", "-loglevel", "error", "-y") + args).redirectErrorStream(true).start()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { process.inputStream.bufferedReader().readText() }
    }
}
