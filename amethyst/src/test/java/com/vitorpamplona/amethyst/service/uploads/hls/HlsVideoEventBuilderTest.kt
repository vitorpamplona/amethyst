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
package com.vitorpamplona.amethyst.service.uploads.hls

import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HlsVideoEventBuilderTest {
    private val landscapeMasterPlaylist =
        """
        #EXTM3U
        #EXT-X-VERSION:7

        #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360,CODECS="avc1.64001e,mp4a.40.2"
        360p/media.m3u8

        #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
        720p/media.m3u8
        """.trimIndent()

    private val portraitMasterPlaylist =
        """
        #EXTM3U
        #EXT-X-VERSION:7

        #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=360x640,CODECS="avc1.64001e,mp4a.40.2"
        360p/media.m3u8
        """.trimIndent()

    private fun bundle(master: String): HlsBundle {
        val workDir = File("/tmp/unused-builder-test")
        val labels = Regex("""(\d+p)/media\.m3u8""").findAll(master).map { it.groupValues[1] }.toList()
        val renditions =
            labels.mapIndexed { i, label ->
                HlsBundleRendition(
                    label = label,
                    combinedFile = File(workDir, "$label.mp4"),
                    mediaPlaylist = "", // not needed by the builder
                    bitrateKbps = 500 + i * 2000,
                )
            }
        return HlsBundle(workDir, master, renditions)
    }

    private fun uploadResult(renditions: List<HlsBundleRendition>): HlsUploadResult =
        HlsUploadResult(
            masterUrl = "https://cdn.test/master.m3u8",
            masterSha256 = "master-sha",
            renditions =
                renditions.map {
                    HlsUploadedRendition(
                        label = it.label,
                        combinedUrl = "https://cdn.test/${it.label}.mp4",
                        combinedSha256 = "${it.label}-sha",
                        combinedSize = 1_000_000L,
                        playlistUrl = "https://cdn.test/${it.label}-media.m3u8",
                        bitrateKbps = it.bitrateKbps,
                    )
                },
        )

    private fun input(
        master: String,
        title: String = "My HD Video",
        description: String = "A cool video",
        alt: String? = null,
        duration: Int? = null,
        contentWarning: String? = null,
        dTag: String? = "fixed-d-tag",
    ): HlsVideoPublishInput {
        val b = bundle(master)
        return HlsVideoPublishInput(
            bundle = b,
            uploadResult = uploadResult(b.renditions),
            title = title,
            description = description,
            alt = alt,
            durationSeconds = duration,
            contentWarning = contentWarning,
            dTag = dTag,
            createdAt = 1_700_000_000L,
        )
    }

    private fun Array<Array<String>>.findTag(name: String): Array<String>? = firstOrNull { it.isNotEmpty() && it[0] == name }

    private fun Array<Array<String>>.findAllTags(name: String): List<Array<String>> = filter { it.isNotEmpty() && it[0] == name }

    @Test
    fun landscapeMasterBuildsHorizontalTemplateKind34235() {
        val result = HlsVideoEventBuilder.build(input(landscapeMasterPlaylist))

        assertTrue("expected Horizontal template", result is HlsVideoEventTemplate.Horizontal)
        val template = (result as HlsVideoEventTemplate.Horizontal).template
        assertEquals(VideoHorizontalEvent.KIND, template.kind)
        assertEquals("A cool video", template.content)
    }

    @Test
    fun portraitMasterBuildsVerticalTemplateKind34236() {
        val result = HlsVideoEventBuilder.build(input(portraitMasterPlaylist))

        assertTrue("expected Vertical template", result is HlsVideoEventTemplate.Vertical)
        val template = (result as HlsVideoEventTemplate.Vertical).template
        assertEquals(VideoVerticalEvent.KIND, template.kind)
    }

    @Test
    fun horizontalTemplateHasTitleAndDTag() {
        val result = HlsVideoEventBuilder.build(input(landscapeMasterPlaylist))
        val tags = (result as HlsVideoEventTemplate.Horizontal).template.tags

        val title = tags.findTag("title")
        assertNotNull(title)
        assertEquals("My HD Video", title!![1])

        val d = tags.findTag("d")
        assertNotNull(d)
        assertEquals("fixed-d-tag", d!![1])
    }

    @Test
    fun templateContainsOneImetaForMasterAndOnePerRendition() {
        val result = HlsVideoEventBuilder.build(input(landscapeMasterPlaylist))
        val tags = (result as HlsVideoEventTemplate.Horizontal).template.tags

        val imetas = tags.findAllTags("imeta")
        // 1 master + 2 renditions
        assertEquals(3, imetas.size)

        // First imeta is the master
        val masterImeta = imetas[0].joinToString("|")
        assertTrue(masterImeta.contains("url https://cdn.test/master.m3u8"))
        assertTrue(masterImeta.contains("m application/vnd.apple.mpegurl"))

        // Subsequent imetas are per-rendition playlist URLs
        val r360Imeta = imetas[1].joinToString("|")
        assertTrue("360p imeta: $r360Imeta", r360Imeta.contains("url https://cdn.test/360p-media.m3u8"))
        assertTrue(r360Imeta.contains("m application/vnd.apple.mpegurl"))
        assertTrue("360p dim: $r360Imeta", r360Imeta.contains("dim 640x360"))
        assertTrue(r360Imeta.contains("x 360p-sha"))

        val r720Imeta = imetas[2].joinToString("|")
        assertTrue("720p imeta: $r720Imeta", r720Imeta.contains("url https://cdn.test/720p-media.m3u8"))
        assertTrue(r720Imeta.contains("dim 1280x720"))
    }

    @Test
    fun durationTagWhenDurationProvided() {
        val result =
            HlsVideoEventBuilder.build(
                input(landscapeMasterPlaylist, duration = 123),
            )
        val tags = (result as HlsVideoEventTemplate.Horizontal).template.tags

        val duration = tags.findTag("duration")
        assertNotNull(duration)
        assertEquals("123", duration!![1])
    }

    @Test
    fun noDurationTagWhenNotProvided() {
        val result = HlsVideoEventBuilder.build(input(landscapeMasterPlaylist))
        val tags = (result as HlsVideoEventTemplate.Horizontal).template.tags
        assertNull(tags.findTag("duration"))
    }

    @Test
    fun contentWarningTagWhenProvided() {
        val result =
            HlsVideoEventBuilder.build(
                input(landscapeMasterPlaylist, contentWarning = "NSFW"),
            )
        val tags = (result as HlsVideoEventTemplate.Horizontal).template.tags

        val warning = tags.findTag("content-warning")
        assertNotNull(warning)
        assertEquals("NSFW", warning!![1])
    }

    @Test
    fun noContentWarningTagWhenNull() {
        val result = HlsVideoEventBuilder.build(input(landscapeMasterPlaylist))
        val tags = (result as HlsVideoEventTemplate.Horizontal).template.tags
        assertNull(tags.findTag("content-warning"))
    }

    @Test
    fun horizontalTemplateCarriesAutoGeneratedAltTag() {
        val result = HlsVideoEventBuilder.build(input(landscapeMasterPlaylist))
        val tags = (result as HlsVideoEventTemplate.Horizontal).template.tags
        val alt = tags.findTag("alt")
        assertNotNull(alt)
        assertEquals(VideoHorizontalEvent.ALT_DESCRIPTION, alt!![1])
    }

    @Test
    fun verticalTemplateCarriesVerticalAltTag() {
        val result = HlsVideoEventBuilder.build(input(portraitMasterPlaylist))
        val tags = (result as HlsVideoEventTemplate.Vertical).template.tags
        val alt = tags.findTag("alt")
        assertNotNull(alt)
        assertEquals(VideoVerticalEvent.ALT_DESCRIPTION, alt!![1])
    }
}
