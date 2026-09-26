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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reserve/derive split.
 *
 * Reserving is the durability boundary — it suspends and must persist a NUT-13
 * counter before any secret derived from it reaches a mint. Deriving is pure.
 * Keeping them apart is what lets [RandomSecretFactory] stay free of storage
 * and lets the mint layer see where the write happens.
 */
class SecretFactoryTest {
    private val seed = ByteArray(64) { it.toByte() }

    // Keyset ids are hex by NUT-02, and NUT-13 derivation decodes them, so a
    // placeholder like "keyset1" is rejected before any secret is produced.
    private val keysetA = "009a1f293253e41e"
    private val keysetB = "00ad268c4d1f5826"

    @Test
    fun randomFactoryReservesNothing() =
        runTest {
            val reservation = RandomSecretFactory.reserve(keysetA, 3)

            assertNull(reservation.firstCounter, "random secrets keep no durable state")
            assertEquals(keysetA, reservation.keysetId)
        }

    @Test
    fun randomFactoryDerivesDistinctSecrets() =
        runTest {
            val secrets = RandomSecretFactory.nextSecrets(keysetA, 4)

            assertEquals(4, secrets.size)
            assertEquals(4, secrets.map { it.secretHex }.toSet().size, "random secrets must not repeat")
        }

    @Test
    fun deterministicFactoryReservesTheWholeBatchOnce() =
        runTest {
            val calls = mutableListOf<Pair<String, Int>>()
            val factory =
                DeterministicSecretFactory(
                    seedProvider = { seed },
                    reserveCounters = { keysetId, count ->
                        calls.add(keysetId to count)
                        10L
                    },
                )

            val reservation = factory.reserve(keysetA, 5)

            assertEquals(listOf(keysetA to 5), calls, "one reservation for the batch, not one per secret")
            assertEquals(10L, reservation.firstCounter)
        }

    /**
     * With no seed the factory falls back to random, so reserving would burn
     * counters for secrets that are never derived from them.
     */
    @Test
    fun noSeedMeansNoCountersBurned() =
        runTest {
            var reserved = false
            val factory =
                DeterministicSecretFactory(
                    seedProvider = { null },
                    reserveCounters = { _, _ ->
                        reserved = true
                        0L
                    },
                )

            val reservation = factory.reserve(keysetA, 3)

            assertTrue(!reserved, "the counter store must not be touched")
            assertNull(reservation.firstCounter)
        }

    /** Derivation is pure: the same reservation yields the same secrets. */
    @Test
    fun deriveIsDeterministicForAReservation() =
        runTest {
            val factory = DeterministicSecretFactory(seedProvider = { seed }, reserveCounters = { _, _ -> 7L })
            val reservation = SecretReservation(keysetA, 7L)

            assertEquals(factory.derive(reservation, 3), factory.derive(reservation, 3))
        }

    /** Consecutive counters must give different secrets, or a reused index would be harmless — it is not. */
    @Test
    fun eachCounterInABatchDerivesADifferentSecret() =
        runTest {
            val factory = DeterministicSecretFactory(seedProvider = { seed }, reserveCounters = { _, _ -> 0L })

            val secrets = factory.derive(SecretReservation(keysetA, 0L), 4)

            assertEquals(4, secrets.map { it.secretHex }.toSet().size)
        }

    /** NUT-13 derivation is keyset-aware: the same counter on another keyset is a different secret. */
    @Test
    fun theSameCounterOnAnotherKeysetDerivesADifferentSecret() =
        runTest {
            val factory = DeterministicSecretFactory(seedProvider = { seed }, reserveCounters = { _, _ -> 0L })

            val onA = factory.derive(SecretReservation(keysetA, 0L), 1).first()
            val onB = factory.derive(SecretReservation(keysetB, 0L), 1).first()

            assertNotEquals(onA.secretHex, onB.secretHex)
        }

    /** A reservation carrying no counter derives random secrets, whatever the factory. */
    @Test
    fun aCounterlessReservationFallsBackToRandom() =
        runTest {
            val factory = DeterministicSecretFactory(seedProvider = { seed }, reserveCounters = { _, _ -> 0L })

            val first = factory.derive(SecretReservation(keysetA, null), 2)
            val second = factory.derive(SecretReservation(keysetA, null), 2)

            assertNotEquals(first.map { it.secretHex }, second.map { it.secretHex }, "random, so not reproducible")
        }
}
