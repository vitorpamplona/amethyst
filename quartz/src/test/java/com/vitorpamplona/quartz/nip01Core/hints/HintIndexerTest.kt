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
package com.vitorpamplona.quartz.nip01Core.hints

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.utils.RandomInstance
import junit.framework.TestCase.assertTrue
import org.junit.Test

class HintIndexerTest {
    val key1 = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b".hexToByteArray()
    val key2 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c".hexToByteArray()
    val key3 = "560c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c".hexToByteArray()

    val keys =
        mutableListOf<HexKey>().apply {
            for (seed in 0..1_000_000) {
                add(RandomInstance.bytes(32).toHexKey())
            }
        }

    val relays =
        this.javaClass
            .getResourceAsStream("relayDB.txt")
            ?.readAllBytes()
            .toString()
            .split("\n")

    val keyHints =
        keys
            .map { key ->
                (0..5).map { PubKeyHint(key, relays.random()) }
            }.flatten()

    val key1Relays = (0..5).map { relays.random() }
    val key2Relays = (0..4).map { relays.random() }
    val key3Relays = (0..3).map { relays.random() }

    @Test
    fun runExistingKeys() {
        val indexer = HintIndexer()
        keyHints.forEach {
            indexer.index(it.id(), it.relay!!)
        }

        var failureCounter = 0
        repeat(1000) {
            val key = keys.random()
            if (indexer.get(key.hexToByteArray()).isEmpty()) {
                failureCounter++
            }
        }

        assertTrue("Failures $failureCounter ${failureCounter / 1_000.0}", failureCounter / 1_000.0f < 0.015)
    }

    @Test
    fun runProb() {
        val indexer = HintIndexer()
        keyHints.forEach {
            indexer.index(it.id(), it.relay!!)
        }

        var failureCounter = 0
        repeat(1_000) {
            if (indexer.get(RandomInstance.bytes(32)).isNotEmpty()) {
                failureCounter++
            }
        }
        assertTrue("Failures $failureCounter ${failureCounter / 1_000.0}", failureCounter / 1_000.0f < 0.015)
    }
}
