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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode

import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [QrEncoder] draws, module for module, the code ZXing's encoder draws: the same mode, version,
 * padding, interleaving and mask, so the code iOS shows is the one Android showed.
 */
class QrEncoderZxingParityTest {
    private val levels =
        listOf(
            QrEncoder.Level.L to ErrorCorrectionLevel.L,
            QrEncoder.Level.M to ErrorCorrectionLevel.M,
            QrEncoder.Level.Q to ErrorCorrectionLevel.Q,
            QrEncoder.Level.H to ErrorCorrectionLevel.H,
        )

    private fun zxing(
        contents: String,
        level: ErrorCorrectionLevel,
    ): QrMatrix? =
        try {
            val code = Encoder.encode(contents, level, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
            val m = code.matrix
            QrMatrix(m.width, m.height, BooleanArray(m.width * m.height) { m[it % m.width, it / m.width] == 1.toByte() })
        } catch (e: WriterException) {
            null
        }

    private fun assertSame(
        contents: String,
        level: QrEncoder.Level,
        zxingLevel: ErrorCorrectionLevel,
    ) {
        val expected = zxing(contents, zxingLevel)
        val actual = QrEncoder.encode(contents, level)
        if (expected == null) {
            assertNull(actual, "ZXing could not fit ${contents.length} chars at $level, we did")
            return
        }
        assertNotNull(actual, "ZXing fit ${contents.length} chars at $level, we did not")
        assertEquals(expected.width, actual.width, "size of ${contents.take(40)} (${contents.length}) at $level")
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                if (expected[x, y] != actual[x, y]) fail("module ($x, $y) differs for ${contents.take(40)} (${contents.length} chars) at $level")
            }
        }
    }

    @Test
    fun realPayloadsMatch() {
        val payloads =
            listOf(
                "npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z",
                "nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z",
                "nostrconnect://83f3b2ae6aa368e8275397b9c26cf550101d63ebaab900d19dd4a4429f5ad8f5?relay=wss%3A%2F%2Frelay.nsec.app&secret=0s8j2djs&perms=sign_event%3A1",
                "lnbc1500n1pj9nr6mpp5x5j0fcq9v0dsn9qtkq9vn8s0hryc8n3h6uyqtmdgvdqcpzwd4v0sdq2fejx2um5wpshjgrnw4hhyxqyjw5qcqpjsp5udn5jajlkuyh0xkkq3m0kcyqj53y2yfrqg8t8fwq9kqfgyhaw0ks9qyyssqh3k5nvwyhkd0s7a3a7gxw8wgd5z6v7ggjpyu0psth9jfe3lsdx6xyv4jzvcqyj0vjq2cmtvvsh7t5vqpckwulfjyfguwrnlxtkfzspwdcv8z",
                "LNBC1500N1PJ9NR6MPP5X5J0FCQ9V0DSN9QTKQ9VN8S0HRYC8N3H6UYQTMDGVDQCPZWD4V0SDQ2FEJX2UM5WPSHJGRNW4HHYXQYJW5QCQPJSP5UDN5JAJLKUYH0XKKQ3M0KCYQJ53Y2YFRQG8T8FWQ9KQFGYHAW0KS9QYYSSQH3K5NVWYHKD0S7A3A7GXW8WGD5Z6V7GGJPYU0PSTH9JFE3LSDX6XYV4JZVCQYJ0VJQ2CMTVVSH7T5VQPCKWULFJYFGUWRNLXTKFZSPWDCV8Z",
                "bitcoin:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq?amount=0.001",
                "https://github.com/vitorpamplona/amethyst",
                "01234567890123456789",
                "Olá, mundo! \uD83D\uDE00 日本語",
                "a",
                "1",
                "A",
            )
        for (payload in payloads) for ((ours, theirs) in levels) assertSame(payload, ours, theirs)
    }

    /** Lengths that land on every version at every level, in each of the three modes. */
    @Test
    fun everyVersionMatches() {
        val alphabets = listOf("0123456789", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 $%*+-./:", "abcdefghijklmnopqrstuvwxyz0123456789")
        val random = Random(42)
        for (alphabet in alphabets) {
            var length = 1
            while (length <= 7100) {
                val text = buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
                for ((ours, theirs) in levels) assertSame(text, ours, theirs)
                length += maxOf(1, length / 12)
            }
        }
    }

    @Test
    fun theFallbackEncoderUsesItAtEveryLevel() {
        // encodeQrMatrix's Q -> M -> L fallback still fits what only L holds.
        val longest = "a".repeat(2_500)
        assertNull(QrEncoder.encode(longest, QrEncoder.Level.Q))
        assertTrue(QrEncoder.encode(longest, QrEncoder.Level.L) != null)
    }
}
