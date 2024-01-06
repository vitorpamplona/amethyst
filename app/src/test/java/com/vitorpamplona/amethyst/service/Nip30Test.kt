/**
 * Copyright (c) 2023 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

import junit.framework.TestCase.assertEquals
import org.junit.Test

class Nip30Test {
    @Test()
    fun parseEmoji() {
        val input = "Alex Gleason :soapbox:"

        assertEquals(
            listOf("Alex Gleason ", ":soapbox:", ""),
            Nip30CustomEmoji().buildArray(input),
        )
    }

    @Test()
    fun parseEmojiInverted() {
        val input = ":soapbox:Alex Gleason"

        assertEquals(
            listOf("", ":soapbox:", "Alex Gleason"),
            Nip30CustomEmoji().buildArray(input),
        )
    }

    @Test()
    fun parseEmoji2() {
        val input = "Hello :gleasonator: \uD83D\uDE02 :ablobcatrainbow: :disputed: yolo"

        assertEquals(
            listOf("Hello ", ":gleasonator:", " 😂 ", ":ablobcatrainbow:", " ", ":disputed:", " yolo"),
            Nip30CustomEmoji().buildArray(input),
        )

        println(Nip30CustomEmoji().buildArray(input).joinToString(","))
    }

    @Test()
    fun parseEmoji3() {
        val input = "hello vitor: how can I help:"

        assertEquals(
            listOf("hello vitor: how can I help:"),
            Nip30CustomEmoji().buildArray(input),
        )
    }

    @Test()
    fun parseJapanese() {
        val input = "\uD883\uDEDE\uD883\uDEDE麺の:x30EDE:。:\uD883\uDEDE:(Violation of NIP-30)"

        assertEquals(
            listOf("\uD883\uDEDE\uD883\uDEDE麺の", ":x30EDE:", "。:\uD883\uDEDE:(Violation of NIP-30)"),
            Nip30CustomEmoji().buildArray(input),
        )
    }
}
