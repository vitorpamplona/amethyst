/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.encoders

import com.vitorpamplona.quartz.events.ImmutableListOfLists
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class Nip30Test {
    @Test()
    fun parseEmoji() {
        val tags = mapOf(":soapbox:" to "http://soapbox")
        val input = "Alex Gleason :soapbox:"

        val result = Nip30CustomEmoji.assembleAnnotatedList(input, tags)

        assertEquals(2, result!!.size)

        assertEquals(
            "Alex Gleason ",
            (result[0] as Nip30CustomEmoji.TextType).text,
        )

        assertEquals(
            "http://soapbox",
            (result[1] as Nip30CustomEmoji.ImageUrlType).url,
        )
    }

    @Test()
    fun parseEmojiInverted() {
        val tags = mapOf(":soapbox:" to "http://soapbox")
        val input = ":soapbox:Alex Gleason"

        val result = Nip30CustomEmoji.assembleAnnotatedList(input, tags)

        assertEquals(2, result!!.size)

        assertEquals(
            "http://soapbox",
            (result[0] as Nip30CustomEmoji.ImageUrlType).url,
        )

        assertEquals(
            "Alex Gleason",
            (result[1] as Nip30CustomEmoji.TextType).text,
        )
    }

    @Test()
    fun parseEmoji2() {
        val tags =
            mapOf(
                ":gleasonator:" to "http://gleasonator",
                ":ablobcatrainbow:" to "http://ablobcatrainbow",
                ":disputed:" to "http://disputed",
            )
        val input = "Hello :gleasonator: \uD83D\uDE02 :ablobcatrainbow: :disputed: yolo"

        val result = Nip30CustomEmoji.assembleAnnotatedList(input, tags)

        assertEquals(7, result!!.size)

        assertEquals("Hello ", (result[0] as Nip30CustomEmoji.TextType).text)

        assertEquals("http://gleasonator", (result[1] as Nip30CustomEmoji.ImageUrlType).url)

        assertEquals(" 😂 ", (result[2] as Nip30CustomEmoji.TextType).text)

        assertEquals("http://ablobcatrainbow", (result[3] as Nip30CustomEmoji.ImageUrlType).url)

        assertEquals(" ", (result[4] as Nip30CustomEmoji.TextType).text)

        assertEquals("http://disputed", (result[5] as Nip30CustomEmoji.ImageUrlType).url)

        assertEquals(" yolo", (result[6] as Nip30CustomEmoji.TextType).text)
    }

    @Test()
    fun parseEmoji3() {
        val tags = emptyMap<String, String>()
        val input = "hello vitor: how can I help:"

        val result = Nip30CustomEmoji.assembleAnnotatedList(input, tags)

        assertNull(result)
    }

    @Test()
    fun parseEmoji4() {
        val tags = mapOf(":vitor:" to "http://vitor")
        val input = "hello :vitor: how :can I help:"

        val result = Nip30CustomEmoji.assembleAnnotatedList(input, tags)

        assertEquals(3, result!!.size)

        assertEquals("hello ", (result[0] as Nip30CustomEmoji.TextType).text)

        assertEquals("http://vitor", (result[1] as Nip30CustomEmoji.ImageUrlType).url)

        assertEquals(" how :can I help:", (result[2] as Nip30CustomEmoji.TextType).text)
    }

    @Test()
    fun parseJapanese() {
        val tags = mapOf(":x30EDE:" to "http://x30EDE", ":\uD883\uDEDE:" to "http://\uD883\uDEDE")
        val input = "\uD883\uDEDE\uD883\uDEDE麺の:x30EDE:。:\uD883\uDEDE:(Violation of NIP-30)"

        val result = Nip30CustomEmoji.assembleAnnotatedList(input, tags)

        assertEquals(3, result!!.size)

        assertEquals("\uD883\uDEDE\uD883\uDEDE麺の", (result[0] as Nip30CustomEmoji.TextType).text)
        assertEquals("http://x30EDE", (result[1] as Nip30CustomEmoji.ImageUrlType).url)
        assertEquals("。:\uD883\uDEDE:(Violation of NIP-30)", (result[2] as Nip30CustomEmoji.TextType).text)
    }

    @Test()
    fun parseJapanese2() {
        val tags =
            arrayOf(
                arrayOf("t", "ioメシヨソイゲーム"),
                arrayOf("emoji", "_ri", "https://media.misskeyusercontent.com/emoji/_ri.png"),
                arrayOf("emoji", "petthex_japanesecake", "https://media.misskeyusercontent.com/emoji/petthex_japanesecake.gif"),
                arrayOf("emoji", "ai_nomming", "https://media.misskeyusercontent.com/misskey/f6294900-f678-43cc-bc36-3ee5deeca4c2.gif"),
                arrayOf("proxy", "https://misskey.io/notes/9q0x6gtdysir03qh", "activitypub"),
            )
        val input =
            "\u200B:_ri:\u200B\u200B:_ri:\u200Bはﾍﾞｲｸﾄﾞﾓﾁｮﾁｮ\u200B:petthex_japanesecake:\u200Bを食べました\u200B:ai_nomming:\u200B\n" +
                "#ioメシヨソイゲーム\n" +
                "https://misskey.io/play/9g3qza4jow"

        val result = Nip30CustomEmoji.assembleAnnotatedList(input, ImmutableListOfLists(tags))

        assertEquals(9, result!!.size)

        var i = 0
        assertEquals("\u200B", (result[i++] as Nip30CustomEmoji.TextType).text)
        assertEquals("https://media.misskeyusercontent.com/emoji/_ri.png", (result[i++] as Nip30CustomEmoji.ImageUrlType).url)
        assertEquals("\u200B\u200B", (result[i++] as Nip30CustomEmoji.TextType).text)
        assertEquals("https://media.misskeyusercontent.com/emoji/_ri.png", (result[i++] as Nip30CustomEmoji.ImageUrlType).url)
        assertEquals("\u200Bはﾍﾞｲｸﾄﾞﾓﾁｮﾁｮ\u200B", (result[i++] as Nip30CustomEmoji.TextType).text)
        assertEquals("https://media.misskeyusercontent.com/emoji/petthex_japanesecake.gif", (result[i++] as Nip30CustomEmoji.ImageUrlType).url)
        assertEquals("\u200Bを食べました\u200B", (result[i++] as Nip30CustomEmoji.TextType).text)
        assertEquals("https://media.misskeyusercontent.com/misskey/f6294900-f678-43cc-bc36-3ee5deeca4c2.gif", (result[i++] as Nip30CustomEmoji.ImageUrlType).url)
        assertEquals("\u200B\n#ioメシヨソイゲーム\nhttps://misskey.io/play/9g3qza4jow", (result[i] as Nip30CustomEmoji.TextType).text)
    }
}
