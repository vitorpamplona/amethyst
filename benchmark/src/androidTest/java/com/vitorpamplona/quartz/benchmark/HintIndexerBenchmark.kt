/**
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
package com.vitorpamplona.quartz.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.RandomInstance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.Charset

@RunWith(AndroidJUnit4::class)
class HintIndexerBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    companion object {
        val keys =
            mutableListOf<HexKey>().apply {
                for (seed in 0..1_000_000) {
                    add(RandomInstance.bytes(32).toHexKey())
                }
            }

        val relays =
            javaClass.classLoader
                ?.getResourceAsStream("relayDB.txt")!!
                .readBytes()
                .toString(Charset.forName("utf-8"))
                .split("\n")
                .mapNotNull(RelayUrlNormalizer::normalizeOrNull)
    }

    @Test
    fun relayUriHashcode() {
        benchmarkRule.measureRepeated {
            "wss://relay.bitcoin.social".hashCode()
        }
    }

    @Test
    fun getRelayHints() {
        val indexer = HintIndexer()

        keys.forEach { key ->
            (0..5).map {
                indexer.addKey(key, relays.random())
            }
        }

        val key = keys.random()

        benchmarkRule.measureRepeated {
            indexer.hintsForKey(key)
        }
    }

    @Test
    fun buildIndexer() {
        benchmarkRule.measureRepeated {
            val indexer = HintIndexer()
            keys.forEach { key ->
                (0..5).map {
                    indexer.addKey(key, relays.random())
                }
            }
        }
    }
}
