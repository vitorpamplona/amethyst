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
package com.vitorpamplona.quartz.nip01Core.hints

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test

class HintIndexerTest {
    companion object {
        val keys =
            mutableListOf<HexKey>()
                .apply {
                    repeat(200_000) {
                        add(RandomInstance.bytes(32).toHexKey())
                    }
                }.toSet()

        val eventIds =
            mutableListOf<HexKey>().apply {
                repeat(200_000) {
                    add(RandomInstance.bytes(32).toHexKey())
                }
            }

        val addresses =
            keys.take(30_000).map {
                Address.assemble(
                    RandomInstance.int(65_000),
                    it,
                    RandomInstance.randomChars(10),
                )
            }

        val relays by
            lazy {
                TestResourceLoader()
                    .loadString("relayDB.txt")
                    .split('\n')
                    .mapNotNull {
                        val relay = RelayUrlNormalizer.normalizeOrNull(it)
                        if (relay == null || relay.isLocalHost()) {
                            null
                        } else {
                            relay
                        }
                    }
            }

        val indexer by lazy {
            val result = HintIndexer()

            println("${keys.size} ${relays.size}")

            // Simulates 5 outbox relays for each key
            keys.forEach { key ->
                repeat(5) {
                    result.addKey(key, relays.random())
                }
            }

            println("${keys.size}")

            // Simulates each event being in 5 + 0..10 relays.
            eventIds.forEach { id ->
                repeat(5 + RandomInstance.int(10)) {
                    result.addEvent(id, relays.random())
                }
            }

            // Simulates each address being in 5 + 0..10 relays.
            addresses.forEach { address ->
                repeat(5 + RandomInstance.int(10)) {
                    result.addAddress(address, relays.random())
                }
            }

            result
        }
    }

    val testSize = 1000
    val testProb = 0.02f

    fun assert99PercentSuccess(success: () -> Boolean) {
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

    fun assert100PercentSuccess(success: () -> Boolean) {
        var failureCounter = 0
        repeat(testSize) {
            if (!success()) {
                failureCounter++
            }
        }

        assertTrue(
            "Failure rate: $failureCounter of 1000 elements => ${(failureCounter / testSize.toFloat()) * 100}%",
            failureCounter == 0,
        )
    }

    @Test
    fun runProbExistingKeys() =
        assert100PercentSuccess {
            indexer.hintsForKey(keys.random()).isNotEmpty()
        }

    @Test
    fun runProbNewKeys() =
        assert99PercentSuccess {
            // 99% of the queries return less than 2 relays.
            indexer.hintsForKey(RandomInstance.bytes(32)).size < 3
        }

    @Test
    fun runProbExistingEventIds() =
        assert100PercentSuccess {
            indexer.hintsForEvent(eventIds.random()).isNotEmpty()
        }

    @Test
    fun runProbNewEventIds() =
        assert99PercentSuccess {
            indexer.hintsForEvent(RandomInstance.bytes(32)).size < 3
        }

    @Test
    fun runProbExistingAddresses() =
        assert100PercentSuccess {
            indexer.hintsForAddress(addresses.random()).isNotEmpty()
        }

    @Test
    fun runProbNewAddresses() =
        assert99PercentSuccess {
            val newAddress =
                Address.assemble(
                    RandomInstance.int(65000),
                    RandomInstance.bytes(32).toHexKey(),
                    RandomInstance.randomChars(10),
                )

            indexer.hintsForAddress(newAddress).size < 3
        }
}
