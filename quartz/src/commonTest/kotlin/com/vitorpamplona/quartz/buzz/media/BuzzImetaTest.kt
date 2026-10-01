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
package com.vitorpamplona.quartz.buzz.media

import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import kotlin.test.Test
import kotlin.test.assertEquals

class BuzzImetaTest {
    @Test
    fun dropsKeysBuzzRejectsAndKeepsTheRest() {
        val tag =
            IMetaTag(
                "https://ws.example/media/abc.jpg",
                mapOf(
                    "x" to listOf("abc"),
                    "ox" to listOf("def"),
                    "m" to listOf("image/jpeg"),
                    "size" to listOf("2029"),
                    "dim" to listOf("96x96"),
                    "blurhash" to listOf("U1Ku"),
                    "alt" to listOf("caption"),
                    "content-warning" to listOf("nsfw"),
                ),
            )

        val clean = BuzzImeta.sanitize(tag)

        assertEquals("https://ws.example/media/abc.jpg", clean.url)
        assertEquals(setOf("x", "m", "size", "dim", "blurhash", "alt"), clean.properties.keys)
    }

    @Test
    fun keepsOneValueForSingletonsButEveryFallback() {
        val tag =
            IMetaTag(
                "https://ws.example/media/abc.jpg",
                mapOf(
                    "alt" to listOf("one", "two"),
                    "fallback" to listOf("https://a/1", "https://b/1"),
                ),
            )

        val clean = BuzzImeta.sanitize(tag)

        assertEquals(listOf("one"), clean.properties["alt"])
        assertEquals(listOf("https://a/1", "https://b/1"), clean.properties["fallback"])
    }

    @Test
    fun bareAttachmentUrlsBecomeBuzzMarkdown() {
        val img = IMetaTag("https://ws.example/media/a.jpg", mapOf("m" to listOf("image/jpeg")))
        val vid = IMetaTag("https://ws.example/media/b.mp4", mapOf("m" to listOf("video/mp4")))

        assertEquals(
            "look ![image](https://ws.example/media/a.jpg) and ![video](https://ws.example/media/b.mp4)",
            BuzzImeta.markdownMediaBody("look https://ws.example/media/a.jpg and https://ws.example/media/b.mp4", listOf(img, vid)),
        )
    }

    @Test
    fun urlsAlreadyInMarkdownStayAsTheyAre() {
        val img = IMetaTag("https://ws.example/media/a.jpg", mapOf("m" to listOf("image/jpeg")))
        val body = "![image](https://ws.example/media/a.jpg)"

        assertEquals(body, BuzzImeta.markdownMediaBody(body, listOf(img)))
    }
}
