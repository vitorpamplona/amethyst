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
package com.vitorpamplona.quartz.nip19Bech32

import kotlin.test.Test
import kotlin.test.assertEquals

class Bech32TranscriptionTest {
    private val nsec = "nsec1ptuhw9cg9ayszgxsnr6zm5akhs9juw2r9eujrha0r5q6fzwghjhsl4vqtl"

    @Test
    fun splitsAnNsecIntoThreeRowsOf54444() {
        assertEquals(
            listOf(
                listOf("nsec1", "ptuh", "w9cg", "9ays", "zgxs"),
                listOf("nr6zm", "5akh", "s9ju", "w2r9", "eujr"),
                listOf("ha0r5", "q6fz", "wghj", "hsl4", "vqtl"),
            ),
            Bech32Transcription.groups(nsec),
        )
    }

    @Test
    fun groupsRejoinToTheOriginal() {
        assertEquals(nsec, Bech32Transcription.groups(nsec).flatten().joinToString(""))
    }

    @Test
    fun shortInputLeavesAPartialLastRow() {
        assertEquals(
            listOf(listOf("nsec1", "ptuh", "w9cg", "9ays", "zgxs"), listOf("nr")),
            Bech32Transcription.groups("nsec1ptuhw9cg9ayszgxsnr"),
        )
    }

    @Test
    fun uppercaseHyphenatedTranscriptionDecodesToTheSameKey() {
        val written =
            """
            NSEC1-PTUH-W9CG-9AYS-ZGXS
            NR6ZM-5AKH-S9JU-W2R9-EUJR
            HA0R5-Q6FZ-WGHJ-HSL4-VQTL
            """.trimIndent()

        val normalized = Bech32Transcription.normalize(written)
        assertEquals(nsec, normalized)
        assertEquals(decodePrivateKeyAsHexOrNull(nsec), decodePrivateKeyAsHexOrNull(normalized))
    }

    @Test
    fun spacesAndMixedCaseAreAccepted() {
        assertEquals(nsec, Bech32Transcription.normalize("  Nsec1 ptuh w9cg 9ays zgxs nr6zm 5akh s9ju w2r9 eujr ha0r5 q6fz wghj hsl4 vqtl "))
    }

    @Test
    fun nonBech32InputIsOnlyTrimmed() {
        assertEquals("alice@example.com", Bech32Transcription.normalize(" alice@example.com "))
        assertEquals("abandon ability able", Bech32Transcription.normalize("abandon ability able"))
        assertEquals("ABCDEF-01", Bech32Transcription.normalize("ABCDEF-01"))
    }
}
