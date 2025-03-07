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
package com.vitorpamplona.quartz.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will output the
 * result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class PoWBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    val baseTemplate =
        EventTemplate<TextNoteEvent>(
            1683596206,
            TextNoteEvent.KIND,
            arrayOf(
                arrayOf("e", "27ac621d7dc4a932e1a79f984308e7d20656dd6fddb2ce9cdfcb6a67b9a7bcc3", "", "root"),
                arrayOf("e", "be7245af96210a0dd048cab4ad38e52dbd6c09a53ea21a7edb6be8898e5727cc", "", "reply"),
                arrayOf("p", "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"),
                arrayOf("p", "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"),
                arrayOf("p", "3f770d65d3a764a9c5cb503ae123e62ec7598ad035d836e2a810f3877a745b24"),
                arrayOf("p", "ec4d241c334311b3a304433ee3442be29d0e88e7ec19b85edf2bba29b93565e2"),
                arrayOf("p", "0fe0b18b4dbf0e0aa40fcd47209b2a49b3431fc453b460efcf45ca0bd16bd6ac"),
                arrayOf("p", "8c0da4862130283ff9e67d889df264177a508974e2feb96de139804ea66d6168"),
                arrayOf("p", "63fe6318dc58583cfe16810f86dd09e18bfd76aabc24a0081ce2856f330504ed"),
                arrayOf("p", "4523be58d395b1b196a9b8c82b038b6895cb02b683d0c253a955068dba1facd0"),
                arrayOf("p", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
            ),
            "Astral:\n\nhttps://void.cat/d/A5Fba5B1bcxwEmeyoD9nBs.webp\n\nIris:\n\nhttps://void.cat/d/44hTcVvhRps6xYYs99QsqA.webp\n\nSnort:\n\nhttps://void.cat/d/4nJD5TRePuQChM5tzteYbU.webp\n\nAmethyst agrees with Astral which I suspect are both wrong. nostr:npub13sx6fp3pxq5rl70x0kyfmunyzaa9pzt5utltjm0p8xqyafndv95q3saapa nostr:npub1v0lxxxxutpvrelsksy8cdhgfux9l6a42hsj2qzquu2zk7vc9qnkszrqj49 nostr:npub1g53mukxnjkcmr94fhryzkqutdz2ukq4ks0gvy5af25rgmwsl4ngq43drvk nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z ",
        )

    @Test
    fun generatePow() {
        benchmarkRule.measureRepeated {
            PoWMiner.run(baseTemplate, KeyPair().pubKey.toHexKey(), 5)
        }
    }

    @Test
    fun setPoWCalculationHex() {
        benchmarkRule.measureRepeated {
            assertEquals(26, PoWRankEvaluator.calculatePowRankOf("00000026c91e9fc75fdb95b367776e2594b931cebda6d5ca3622501006669c9e"))
        }
    }

    @Test
    fun setPoWCalculationBytes() {
        val bytes = "00000026c91e9fc75fdb95b367776e2594b931cebda6d5ca3622501006669c9e".hexToByteArray()
        benchmarkRule.measureRepeated {
            assertEquals(26, PoWRankEvaluator.calculatePowRankOf(bytes))
        }
    }
}
