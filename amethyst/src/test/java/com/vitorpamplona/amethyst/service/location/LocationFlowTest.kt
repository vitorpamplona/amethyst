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
package com.vitorpamplona.amethyst.service.location

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationFlowTest {
    /**
     * A LocationManager that reports [providers] and refuses [denied] with a
     * SecurityException, mimicking the pre-API-31 fine-location requirement.
     */
    private fun manager(
        providers: List<String>,
        denied: Set<String> = emptySet(),
    ): LocationManager {
        val lm = mockk<LocationManager>(relaxed = true)
        every { lm.allProviders } returns providers
        every { lm.getLastKnownLocation(any()) } returns null
        every {
            lm.requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), any())
        } answers {
            val provider = firstArg<String>()
            if (provider in denied) throw SecurityException("denied: $provider")
        }
        return lm
    }

    @Test
    fun firesNeitherEdgeWhenNoProviderExists() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val flow = LocationFlow(manager(providers = emptyList()), sdkInt = 37).get(60_000L, 500f) { edges.add(it) }

            val failure = runCatching { flow.collect { } }.exceptionOrNull()

            assertTrue("expected SecurityException, got $failure", failure is SecurityException)
            assertEquals(emptyList<Boolean>(), edges)
        }

    @Test
    fun firesNeitherEdgeWhenEveryRungIsDenied() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val lm = manager(providers = listOf("fused", "network"), denied = setOf("fused", "network"))
            val flow = LocationFlow(lm, sdkInt = 37).get(60_000L, 500f) { edges.add(it) }

            val failure = runCatching { flow.collect { } }.exceptionOrNull()

            assertTrue("expected SecurityException, got $failure", failure is SecurityException)
            assertEquals(emptyList<Boolean>(), edges)
        }

    @Test
    fun fallsThroughToTheNextRungWhenOneIsDenied() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val lm = manager(providers = listOf("fused", "network"), denied = setOf("fused"))
            val job = launch { LocationFlow(lm, sdkInt = 37).get(60_000L, 500f) { edges.add(it) }.collect { } }

            runCurrent()

            assertEquals(listOf(true), edges)
            verify { lm.requestLocationUpdates("network", 60_000L, 500f, any<LocationListener>(), any()) }

            job.cancelAndJoin()
        }

    @Test
    fun pairsTheListeningEdgesAroundASuccessfulRegistration() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val lm = manager(providers = listOf("network"))
            val job = launch { LocationFlow(lm, sdkInt = 30).get(60_000L, 500f) { edges.add(it) }.collect { } }

            runCurrent()
            assertEquals(listOf(true), edges)

            job.cancelAndJoin()

            assertEquals(listOf(true, false), edges)
            verify { lm.removeUpdates(any<LocationListener>()) }
        }

    @Test
    fun releasesTheRegistrationWhenCancelledDuringTheSeed() =
        runTest {
            // `send` in the seed suspends, so a collector cancelling while the
            // getLastKnownLocation sweep is in flight unwinds the producer
            // there. Cleanup must still run, or the refcount sticks at >= 1
            // forever and the OS registration leaks.
            val edges = mutableListOf<Boolean>()
            val lm = mockk<LocationManager>(relaxed = true)
            every { lm.allProviders } returns listOf("network")

            lateinit var job: Job
            every { lm.getLastKnownLocation(any()) } answers {
                // Cancel from inside the sweep, so the subsequent send() throws.
                job.cancel()
                mockk<Location> { every { time } returns 1L }
            }

            job = launch { LocationFlow(lm, sdkInt = 30).get(60_000L, 500f) { edges.add(it) }.collect { } }
            runCurrent()
            job.join()

            assertEquals("the acquire must be released even on cancellation", listOf(true, false), edges)
            verify { lm.removeUpdates(any<LocationListener>()) }
        }

    @Test
    fun registersOnExactlyOneProvider() =
        runTest {
            val lm = manager(providers = listOf("fused", "network", "gps", "passive"))
            val job = launch { LocationFlow(lm, sdkInt = 37).get(60_000L, 500f).collect { } }

            runCurrent()

            verify(exactly = 1) {
                lm.requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), any())
            }

            job.cancelAndJoin()
        }
}
