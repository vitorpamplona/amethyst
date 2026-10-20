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
package com.vitorpamplona.quartz.nip13Pow

import com.vitorpamplona.crypto.sha2.Sha256Midstate
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.miner.PoWHashMode
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import com.vitorpamplona.quartz.nip13Pow.miner.indexOf
import com.vitorpamplona.quartz.nip13Pow.miner.prefersMidstate
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sha256MidstateTest {
    private val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    @Test
    fun matchesSha256AtEverySplitAndLength() {
        val random = Random(42)
        // covers the padding edge cases: 55/56/63/64 bytes into the last block
        for (length in 0..300) {
            val message = random.nextBytes(length)
            val expected = sha256(message).toHexKey()
            for (split in 0..length) {
                val midstate = Sha256Midstate(message, split)
                assertEquals(expected, midstate.digestInto(ByteArray(32)).toHexKey(), "length $length split $split")
            }
        }
    }

    @Test
    fun leadingZeroBitsAgreeWithTheEvaluator() {
        val random = Random(7)
        repeat(2_000) {
            val message = random.nextBytes(random.nextInt(1, 400))
            val midstate = Sha256Midstate(message, random.nextInt(0, message.size + 1))
            val rank = PoWRankEvaluator.calculatePowRankOf(sha256(message))
            assertTrue(midstate.hasLeadingZeroBits(rank))
            assertTrue(!midstate.hasLeadingZeroBits(rank + 1))
        }
    }

    private fun reply(content: String) =
        EventTemplate<TextNoteEvent>(
            1683596206,
            TextNoteEvent.KIND,
            arrayOf(
                arrayOf("e", "27ac621d7dc4a932e1a79f984308e7d20656dd6fddb2ce9cdfcb6a67b9a7bcc3", "", "root"),
                arrayOf("e", "be7245af96210a0dd048cab4ad38e52dbd6c09a53ea21a7edb6be8898e5727cc", "", "reply"),
                arrayOf("p", "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"),
            ),
            content,
        )

    private val note = EventTemplate<TextNoteEvent>(1683596206, 1, emptyArray(), "gm")

    @Test
    fun everyHashModeMinesTheSameNonce() {
        for (template in listOf(reply("+1"), reply("a longer reply ".repeat(20)), note)) {
            val full = PoWMiner.run(template, pubKey, 14, hashMode = PoWHashMode.FULL)
            val mid = PoWMiner.run(template, pubKey, 14, hashMode = PoWHashMode.MIDSTATE)
            // 16 bits so AUTO takes the timed trial, not the block-count shortcut
            val auto = PoWMiner.run(template, pubKey, 16)
            val fullAt16 = PoWMiner.run(template, pubKey, 16, hashMode = PoWHashMode.FULL)

            assertEquals(full.tags.last().toList(), mid.tags.last().toList())
            assertEquals(fullAt16.tags.last().toList(), auto.tags.last().toList())
            val id = EventHasher.hashId(pubKey, mid.createdAt, mid.kind, mid.tags, mid.content)
            assertTrue(PoWRankEvaluator.calculatePowRankOf(id) >= 14)
        }
    }

    private fun prefers(
        template: EventTemplate<TextNoteEvent>,
        desiredPoW: Int,
    ): Boolean {
        val nonce = "00000"
        val bytes = EventHasherSerializer.fastMakeJsonForId(pubKey, template.createdAt, template.kind, template.tags + PoWTag.assemble(nonce, desiredPoW), template.content)
        return prefersMidstate(bytes, bytes.indexOf(nonce.encodeToByteArray()), nonce.length, desiredPoW)
    }

    @Test
    fun lowDifficultyDecidesByBlockCount() {
        // one block before the nonce, 34 after
        assertFalse(prefers(EventTemplate(1683596206, 1, emptyArray(), "A long form thought. ".repeat(100)), 8))
        // five blocks of tags before the nonce, one after
        assertTrue(prefers(reply("+1"), 8))
        // five before, eight after
        assertFalse(prefers(reply("a longer reply ".repeat(30)), 8))
    }
}
