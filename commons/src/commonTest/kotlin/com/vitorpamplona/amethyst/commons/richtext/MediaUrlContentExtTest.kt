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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaUrlContentExtTest {
    private val sha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"

    @Test
    fun bridgeOffReturnsOriginalUrl() {
        val image = MediaUrlImage(url = "https://cdn.example.com/$sha.jpg", hash = sha)
        assertEquals("https://cdn.example.com/$sha.jpg", image.toCoilModel(useLocalBlossomBridge = false))
    }

    @Test
    fun nullHashReturnsOriginalUrl() {
        val image = MediaUrlImage(url = "https://cdn.example.com/foo.jpg", hash = null)
        assertEquals("https://cdn.example.com/foo.jpg", image.toCoilModel(useLocalBlossomBridge = true))
    }

    @Test
    fun invalidHashReturnsOriginalUrl() {
        val image = MediaUrlImage(url = "https://cdn.example.com/foo.jpg", hash = "not-hex")
        assertEquals("https://cdn.example.com/foo.jpg", image.toCoilModel(useLocalBlossomBridge = true))
    }

    @Test
    fun blossomUriReturnedUnchanged() {
        val image = MediaUrlImage(url = "blossom:$sha.jpg?xs=https://cdn.example.com", hash = sha)
        assertEquals("blossom:$sha.jpg?xs=https://cdn.example.com", image.toCoilModel(useLocalBlossomBridge = true))
    }

    @Test
    fun liveStreamReturnsOriginalUrl() {
        val video = MediaUrlVideo(url = "https://stream.example.com/play.m3u8", hash = sha, isLiveStream = true)
        assertEquals("https://stream.example.com/play.m3u8", video.toCoilModel(useLocalBlossomBridge = true))
    }

    @Test
    fun bridgeOnRewritesPlainHttpsUrl() {
        val image = MediaUrlImage(url = "https://nostr.build/i/abc/$sha.jpg", hash = sha)
        val result = image.toCoilModel(useLocalBlossomBridge = true)
        assertEquals("blossom:$sha.jpg?xs=https://nostr.build/i/abc", result)
    }

    @Test
    fun bridgeOnPreservesNostrBuildPathPrefix() {
        val image = MediaUrlImage(url = "https://cdn.nostr.build/i/$sha.jpg", hash = sha)
        val result = image.toCoilModel(useLocalBlossomBridge = true)
        assertEquals("blossom:$sha.jpg?xs=https://cdn.nostr.build/i", result)
    }

    @Test
    fun bridgeOnFlatBlossomPathYieldsHostOnlyXs() {
        val image = MediaUrlImage(url = "https://blossom.primal.net/$sha.jpg", hash = sha)
        val result = image.toCoilModel(useLocalBlossomBridge = true)
        assertEquals("blossom:$sha.jpg?xs=https://blossom.primal.net", result)
    }

    @Test
    fun bridgeOnInfersExtensionFromMimeType() {
        val image = MediaUrlImage(url = "https://nostr.build/i/abc", hash = sha, mimeType = "image/png")
        val result = image.toCoilModel(useLocalBlossomBridge = true)
        assertTrue(result.startsWith("blossom:$sha.png?xs="), "expected png extension from mime, got $result")
    }

    @Test
    fun bridgeOnFallsBackToBinExtension() {
        val image = MediaUrlImage(url = "https://nostr.build/i/abc", hash = sha)
        val result = image.toCoilModel(useLocalBlossomBridge = true)
        assertTrue(result.startsWith("blossom:$sha.bin?xs="), "expected bin extension fallback, got $result")
    }

    @Test
    fun nonHttpUrlReturnsOriginal() {
        val image = MediaUrlImage(url = "ftp://example.com/file", hash = sha)
        assertEquals("ftp://example.com/file", image.toCoilModel(useLocalBlossomBridge = true))
    }

    @Test
    fun uppercaseHashNormalisedToLowercase() {
        val image = MediaUrlImage(url = "https://cdn.example.com/foo.jpg", hash = sha.uppercase())
        val result = image.toCoilModel(useLocalBlossomBridge = true)
        assertTrue(result.startsWith("blossom:$sha.jpg?xs="), "expected lowercase sha, got $result")
    }

    @Test
    fun authorPubKeyAddedAsAsParam() {
        val authorPub = "a8f3721a0dc1b4d5c12f4cc7c54ae14071eb9c1b4f9b2cf0d4ab22c0e9f0c7e5"
        val image =
            MediaUrlImage(
                url = "https://cdn.example.com/foo.jpg",
                hash = sha,
                authorPubKey = authorPub,
            )
        val result = image.toCoilModel(useLocalBlossomBridge = true)
        assertEquals("blossom:$sha.jpg?xs=https://cdn.example.com&as=$authorPub", result)
    }

    @Test
    fun invalidAuthorPubKeyDropped() {
        val image =
            MediaUrlImage(
                url = "https://cdn.example.com/foo.jpg",
                hash = sha,
                authorPubKey = "not-a-pubkey",
            )
        val result = image.toCoilModel(useLocalBlossomBridge = true)
        assertEquals("blossom:$sha.jpg?xs=https://cdn.example.com", result)
    }

    @Test
    fun bridgeProfilePictureUrlNullReturnsNull() {
        assertEquals(null, bridgeProfilePictureUrl(null, useBridge = true))
    }

    @Test
    fun bridgeProfilePictureUrlOffReturnsOriginal() {
        assertEquals(
            "https://cdn.example.com/avatar.jpg",
            bridgeProfilePictureUrl("https://cdn.example.com/avatar.jpg", useBridge = false),
        )
    }

    @Test
    fun bridgeProfilePictureUrlExtractsShaFromPath() {
        val url = "https://nostr.build/i/$sha.jpg"
        val authorPub = "a8f3721a0dc1b4d5c12f4cc7c54ae14071eb9c1b4f9b2cf0d4ab22c0e9f0c7e5"
        assertEquals(
            "http://127.0.0.1:24242/$sha.jpg?xs=https%3A%2F%2Fnostr.build%2Fi&as=$authorPub",
            bridgeProfilePictureUrl(url, useBridge = true, authorPubKey = authorPub),
        )
    }

    @Test
    fun bridgeProfilePictureUrlPreservesNostrBuildPath() {
        val url = "https://cdn.nostr.build/i/$sha.jpg"
        assertEquals(
            "http://127.0.0.1:24242/$sha.jpg?xs=https%3A%2F%2Fcdn.nostr.build%2Fi",
            bridgeProfilePictureUrl(url, useBridge = true),
        )
    }

    @Test
    fun bridgeProfilePictureUrlNoShaInPathReturnsOriginal() {
        val url = "https://nostr.build/avatar.jpg"
        assertEquals(url, bridgeProfilePictureUrl(url, useBridge = true))
    }

    @Test
    fun bridgeProfilePictureUrlBlossomUriReturnedUnchanged() {
        val uri = "blossom:$sha.jpg?xs=https://nostr.build"
        assertEquals(uri, bridgeProfilePictureUrl(uri, useBridge = true))
    }

    @Test
    fun bridgeOnRewritesShaInLastPathSegmentWithHexPrefix() {
        // share.yabu.me layout: <cache-prefix-sha>/<blob-sha>.<ext>
        val image =
            MediaUrlImage(
                url = "https://share.yabu.me/84b0c46ab699ac35eb2ca286470b85e081db2087cdef63932236c397417782f5/28fa4d999af6ae3e4e11bfc2727130ef1b3a13cc0f981e5a93c3996cb2f524e5.webp",
                hash = null,
            )
        assertEquals(
            "blossom:28fa4d999af6ae3e4e11bfc2727130ef1b3a13cc0f981e5a93c3996cb2f524e5.webp?xs=https://share.yabu.me/84b0c46ab699ac35eb2ca286470b85e081db2087cdef63932236c397417782f5",
            image.toCoilModel(useLocalBlossomBridge = true),
        )
    }

    @Test
    fun bridgeProfilePictureUrlRewritesShaInLastPathSegmentWithHexPrefix() {
        assertEquals(
            "http://127.0.0.1:24242/28fa4d999af6ae3e4e11bfc2727130ef1b3a13cc0f981e5a93c3996cb2f524e5.webp?xs=https%3A%2F%2Fshare.yabu.me%2F84b0c46ab699ac35eb2ca286470b85e081db2087cdef63932236c397417782f5",
            bridgeProfilePictureUrl(
                "https://share.yabu.me/84b0c46ab699ac35eb2ca286470b85e081db2087cdef63932236c397417782f5/28fa4d999af6ae3e4e11bfc2727130ef1b3a13cc0f981e5a93c3996cb2f524e5.webp",
                useBridge = true,
            ),
        )
    }

    @Test
    fun bridgeOnSkipsWhenLastSegmentIsNotSha() {
        // Per BUD-01 the last segment is the blob; if it isn't a sha256, the
        // URL isn't a Blossom blob even if an earlier segment is hex.
        val url = "https://example.com/$sha/avatar.jpg"
        val image = MediaUrlImage(url = url, hash = null)
        assertEquals(url, image.toCoilModel(useLocalBlossomBridge = true))
    }

    @Test
    fun bridgeProfilePictureUrlSkipsWhenLastSegmentIsNotSha() {
        val url = "https://example.com/$sha/avatar.jpg"
        assertEquals(url, bridgeProfilePictureUrl(url, useBridge = true))
    }
}
