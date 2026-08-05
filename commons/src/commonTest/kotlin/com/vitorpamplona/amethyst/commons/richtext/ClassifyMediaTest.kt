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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClassifyMediaTest {
    @Test
    fun webxdcAppIsNotMedia() {
        // Regression: a NIP-94 header for a webxdc app (a zip bundle) used to reach the
        // ExoPlayer branch, because the only test was `isImage` and everything else fell
        // through to video. https://blossom.ditto.pub/<sha256>.xdc, m=application/x-webxdc
        assertNull(
            RichTextParser.classifyMedia(
                "https://blossom.ditto.pub/d810ba7873d710b197fc402c0573cd95ce7d44fff7f904e8f58e48af3a47c107.xdc",
                "application/x-webxdc",
            ),
        )
    }

    @Test
    fun unknownTypesAreNotMedia() {
        assertNull(RichTextParser.classifyMedia("https://x.com/app.apk", "application/vnd.android.package-archive"))
        assertNull(RichTextParser.classifyMedia("https://x.com/archive.zip", "application/zip"))
        assertNull(RichTextParser.classifyMedia("https://x.com/notes.txt", "text/plain"))
        // No mime at all and an extension we don't render.
        assertNull(RichTextParser.classifyMedia("https://x.com/file.xdc", null))
        assertNull(RichTextParser.classifyMedia("https://x.com/no-extension-at-all", null))
    }

    @Test
    fun declaredMimeTypesClassify() {
        assertEquals(MediaContentKind.IMAGE, RichTextParser.classifyMedia("https://x.com/a", "image/png"))
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/a", "video/mp4"))
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/a", "audio/mpeg"))
        assertEquals(MediaContentKind.PDF, RichTextParser.classifyMedia("https://x.com/a", "application/pdf"))
    }

    @Test
    fun hlsPlaylistMimesAreVideo() {
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/a", "application/vnd.apple.mpegurl"))
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/a", "application/x-mpegURL"))
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/a", "audio/mpegurl"))
    }

    @Test
    fun extensionIsUsedWhenMimeIsAbsent() {
        assertEquals(MediaContentKind.IMAGE, RichTextParser.classifyMedia("https://x.com/a.jpg", null))
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/a.mp4", null))
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/a.m3u8", null))
        assertEquals(MediaContentKind.PDF, RichTextParser.classifyMedia("https://x.com/a.pdf", null))
        assertEquals(MediaContentKind.IMAGE, RichTextParser.classifyMedia("https://x.com/a.PNG", null))
    }

    @Test
    fun aDeclaredMimeBeatsAContradictingExtension() {
        // The check this replaced was an OR — `mime.startsWith("image/") || isImageUrl(url)` —
        // so a poster-named video URL classified as an image. A declared MIME is the publisher
        // stating the type; the extension is only a guess for when they didn't. Pins the
        // precedence against a future "simplification" back to OR-semantics.
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/thumb.jpg", "video/mp4"))
        assertEquals(MediaContentKind.IMAGE, RichTextParser.classifyMedia("https://x.com/clip.mp4", "image/png"))
        assertEquals(MediaContentKind.PDF, RichTextParser.classifyMedia("https://x.com/scan.png", "application/pdf"))
    }

    @Test
    fun anUnrecognisedMimeDefersToTheExtensionRatherThanVetoingIt() {
        // Precedence applies only to MIMEs we recognise. An unrecognised one means "no usable
        // declaration", not "declared unrenderable" — the two are indistinguishable here, and
        // treating them alike is what lets [extensionRescuesAMalformedMime] work. So a real
        // video mislabelled `application/x-webxdc` still plays…
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/bundle.mp4", "application/x-webxdc"))
        // …while the webxdc app that motivated this class stays unrenderable, because nothing
        // rescues it: `.xdc` is in no extension list either.
        assertNull(RichTextParser.classifyMedia("https://x.com/bundle.xdc", "application/x-webxdc"))
    }

    @Test
    fun extensionRescuesAMalformedMime() {
        // Primal iOS emits `m jpeg` instead of `m image/jpeg`; the extension must still win
        // over "unknown". Preserves the behaviour createMediaContent already documented.
        assertEquals(MediaContentKind.IMAGE, RichTextParser.classifyMedia("https://x.com/a.jpg", "jpeg"))
    }

    @Test
    fun queryStringsAndFragmentsAreStripped() {
        assertEquals(MediaContentKind.IMAGE, RichTextParser.classifyMedia("https://x.com/a.jpg?token=1", null))
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("https://x.com/a.mp4#t=10", null))
        assertNull(RichTextParser.classifyMedia("https://x.com/a.xdc?token=1", null))
    }

    @Test
    fun dataUrisAreClassifiedByTheirPrefixOnly() {
        assertEquals(MediaContentKind.IMAGE, RichTextParser.classifyMedia("data:image/png;base64,AAAA", null))
        assertEquals(MediaContentKind.VIDEO, RichTextParser.classifyMedia("data:video/mp4;base64,AAAA", null))
        assertEquals(MediaContentKind.PDF, RichTextParser.classifyMedia("data:application/pdf;base64,AAAA", null))
        // A data: URI carries its type in the prefix, so a miss there is genuine — the
        // payload must never be extension-probed (base64 can end in any letters).
        assertNull(RichTextParser.classifyMedia("data:application/zip;base64,AAAAmp4", null))
    }

    @Test
    fun classifyMediaAgreesWithCreateMediaContent() {
        // createMediaContent is the long-standing reference for this decision; the two must
        // not drift, since half the renderers call one and half the other.
        val cases =
            listOf(
                "https://x.com/a.jpg" to null,
                "https://x.com/a" to "image/png",
                "https://x.com/a" to "video/mp4",
                "https://x.com/a" to "audio/mpeg",
                "https://x.com/a" to "application/pdf",
                "https://x.com/a" to "application/vnd.apple.mpegurl",
                "https://x.com/a.xdc" to "application/x-webxdc",
                "https://x.com/a.zip" to "application/zip",
                "https://x.com/a.jpg" to "jpeg",
                "data:image/png;base64,AAAA" to null,
                "data:application/zip;base64,AAAAmp4" to null,
            )

        cases.forEach { (url, mime) ->
            val tags = mime?.let { mapOf(url to imeta(url, it)) } ?: emptyMap()
            val expected =
                when (RichTextParser().createMediaContent(url, tags, null)) {
                    is MediaUrlImage -> MediaContentKind.IMAGE
                    is MediaUrlVideo -> MediaContentKind.VIDEO
                    is MediaUrlPdf -> MediaContentKind.PDF
                    null -> null
                    else -> error("unexpected content type for $url / $mime")
                }

            assertEquals<MediaContentKind?>(expected, RichTextParser.classifyMedia(url, mime), "disagreement on $url / $mime")
        }
    }

    private fun imeta(
        url: String,
        mimeType: String,
    ) = IMetaTag(url = url, properties = mapOf("m" to listOf(mimeType)))
}
