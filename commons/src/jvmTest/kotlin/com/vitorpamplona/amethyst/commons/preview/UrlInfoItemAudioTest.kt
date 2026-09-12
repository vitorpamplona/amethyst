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
package com.vitorpamplona.amethyst.commons.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [UrlInfoItem.playableAudioUrl] — the gate between "the page mentioned an `og:audio`" and "we
 * are willing to point a media player at it".
 */
class UrlInfoItemAudioTest {
    private fun item(
        url: String = "https://e.nostr.build/a_ETvKzX2OdOGmFEp1avRlm5_mp3",
        image: String = "",
        audio: String = "",
        audioType: String = "",
    ) = UrlInfoItem(
        url = url,
        mimeType = "text/html",
        image = image,
        audio = audio,
        audioType = audioType,
    )

    @Test
    fun aDeclaredAudioMimeMakesTheFilePlayable() {
        val info =
            item(
                audio = "https://a.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp3",
                audioType = "audio/mpeg",
            )

        assertEquals("https://a.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp3", info.playableAudioUrl)
    }

    @Test
    fun anAudioExtensionCarriesAPageThatOmittedTheType() {
        assertEquals(
            "https://a.nostr.build/x.mp3",
            item(audio = "https://a.nostr.build/x.mp3").playableAudioUrl,
        )
    }

    @Test
    fun aNonAudioDeclarationIsRefused() {
        // A page is free to put anything under og:audio. Pointing the player at an HTML page or a
        // video is the exact failure this whole change is about, so an untyped non-audio URL and a
        // non-audio MIME both stay null and fall through to the link card.
        assertNull(item(audio = "https://example.com/player.html").playableAudioUrl)
        assertNull(item(audio = "https://example.com/x.mp3", audioType = "text/html").playableAudioUrl)
        assertNull(item(audio = "https://example.com/x.mp4", audioType = "video/mp4").playableAudioUrl)
    }

    @Test
    fun aPageWithoutAudioHasNothingToPlay() {
        assertNull(item().playableAudioUrl)
    }

    @Test
    fun aRelativeAudioPathResolvesAgainstThePage() {
        val info =
            item(
                url = "https://e.nostr.build/a_x_mp3",
                audio = "/media/x.mp3",
                audioType = "audio/mpeg",
            )

        assertEquals("https://e.nostr.build/media/x.mp3", info.playableAudioUrl)
    }

    @Test
    fun aTrackPageWithNoCoverArtStillCountsAsAFetchedPreview() {
        // fetchComplete() gates whether UrlCachedPreviewer keeps the result or files it as Empty
        // and falls back to a bare link. Playable audio is evidence on its own.
        val noArt = item(audio = "https://a.nostr.build/x.mp3", audioType = "audio/mpeg")

        assertEquals("", noArt.image)
        assertTrue(noArt.fetchComplete())

        assertFalse(item().fetchComplete())
    }

    @Test
    fun imageResolutionIsUnchanged() {
        assertEquals(
            "https://e.nostr.build/poster.jpg",
            item(url = "https://e.nostr.build/a_x_mp3", image = "/poster.jpg").imageUrlFullPath,
        )
    }
}
