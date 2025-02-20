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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexerTest.Companion.indexer
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.usedMemoryMb
import junit.framework.TestCase.assertTrue
import org.junit.Test

class HintIndexerTest {
    companion object {
        val VALID_CHARS: List<Char> = ('0'..'9') + ('a'..'z') + ('A'..'Z')

        private fun randomChars(size: Int): String = CharArray(size) { VALID_CHARS.random() }.concatToString()

        val keys =
            mutableListOf<HexKey>().apply {
                for (seed in 0..1_000_000) {
                    add(RandomInstance.bytes(32).toHexKey())
                }
            }

        val eventIds =
            mutableListOf<HexKey>().apply {
                for (seed in 0..1_000_000) {
                    add(RandomInstance.bytes(32).toHexKey())
                }
            }

        val addresses =
            keys.take(100_000).map {
                Address.assemble(
                    RandomInstance.int(65_000),
                    it,
                    randomChars(10),
                )
            }

        val relays =
            this::class.java
                .getResourceAsStream("relayDB.txt")
                ?.readAllBytes()
                .toString()
                .split("\n")

        val indexer by lazy {
            System.gc()
            Thread.sleep(1000)

            val startingMemory = Runtime.getRuntime().usedMemoryMb()
            val result = HintIndexer()
            val endingMemory = Runtime.getRuntime().usedMemoryMb()

            println("Filter using ${endingMemory - startingMemory}MB")

            // Simulates 5 outbox relays for each key
            keys.forEach { key ->
                (0..5).map {
                    result.addKey(key, relays.random())
                }
            }

            // Simulates each event being in 8 + 0..10 relays.
            eventIds.forEach { id ->
                repeat(8 + RandomInstance.int(10)) {
                    result.addEvent(id, relays.random())
                }
            }

            // Simulates each address being in 8 + 0..10 relays.
            addresses.forEach { address ->
                repeat(8 + RandomInstance.int(10)) {
                    result.addAddress(address, relays.random())
                }
            }

            result
        }
    }

    val testSize = 1000
    val testProb = 0.015f

    fun assert99PercentSucess(success: () -> Boolean) {
        var failureCounter = 0
        repeat(testSize) {
            if (!success()) {
                failureCounter++
            }
        }

        assertTrue(
            "Failure rate: $failureCounter of 1000 elements => ${(failureCounter / testSize.toFloat()) * 100}%",
            failureCounter / testSize.toFloat() < testProb,
        )
    }

    @Test
    fun runProbExistingKeys() =
        assert99PercentSucess {
            indexer.getKey(keys.random()).isNotEmpty()
        }

    @Test
    fun runProbNewKeys() =
        assert99PercentSucess {
            indexer.getKey(RandomInstance.bytes(32)).isEmpty()
        }

    @Test
    fun runProbExistingEventIds() =
        assert99PercentSucess {
            indexer.getEvent(eventIds.random()).isNotEmpty()
        }

    @Test
    fun runProbNewEventIds() =
        assert99PercentSucess {
            indexer.getEvent(RandomInstance.bytes(32)).isEmpty()
        }

    @Test
    fun runProbExistingAddresses() =
        assert99PercentSucess {
            indexer.getAddress(addresses.random()).isNotEmpty()
        }

    @Test
    fun runProbNewAddresses() =
        assert99PercentSucess {
            val newAddress =
                Address.assemble(
                    RandomInstance.int(65000),
                    RandomInstance.bytes(32).toHexKey(),
                    randomChars(10),
                )

            indexer.getAddress(newAddress).isEmpty()
        }
}
