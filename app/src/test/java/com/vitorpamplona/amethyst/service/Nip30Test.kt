package com.vitorpamplona.amethyst.service

import junit.framework.TestCase.assertEquals
import org.junit.Test

class Nip30Test {
    @Test()
    fun parseEmoji() {
        val input = "Alex Gleason :soapbox:"

        assertEquals(
            listOf("Alex Gleason ", ":soapbox:", ""),
            Nip30CustomEmoji().buildArray(input)
        )
    }

    @Test()
    fun parseEmojiInverted() {
        val input = ":soapbox:Alex Gleason"

        assertEquals(
            listOf("", ":soapbox:", "Alex Gleason"),
            Nip30CustomEmoji().buildArray(input)
        )
    }

    @Test()
    fun parseEmoji2() {
        val input = "Hello :gleasonator: \uD83D\uDE02 :ablobcatrainbow: :disputed: yolo"

        assertEquals(
            listOf("Hello ", ":gleasonator:", " 😂 ", ":ablobcatrainbow:", " ", ":disputed:", " yolo"),
            Nip30CustomEmoji().buildArray(input)
        )

        println(Nip30CustomEmoji().buildArray(input).joinToString(","))
    }

    @Test()
    fun parseEmoji3() {
        val input = "hello vitor: how can I help:"

        assertEquals(
            listOf("hello vitor: how can I help:"),
            Nip30CustomEmoji().buildArray(input)
        )
    }

    @Test()
    fun parseJapanese() {
        val input = "\uD883\uDEDE\uD883\uDEDE麺の:x30EDE:。:\uD883\uDEDE:(Violation of NIP-30)"

        assertEquals(
            listOf("\uD883\uDEDE\uD883\uDEDE麺の", ":x30EDE:", "。:\uD883\uDEDE:(Violation of NIP-30)"),
            Nip30CustomEmoji().buildArray(input)
        )
    }
}
