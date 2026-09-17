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
package com.vitorpamplona.amethyst.service.resourceusage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The two counters that exist because CPU time is not energy: with the screen
 * off, what costs battery is how often something wakes the SoC and the radio,
 * not how many milliseconds it then computes for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WakeCountersTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun accountant(scope: CoroutineScope) =
        ResourceUsageAccountant(
            ResourceUsageStore(File(temp.root, "u.json")),
            scope,
            epochDay = { 0L },
        )

    // ------------------------------------------------------------------
    // DeviceSleepSampler
    // ------------------------------------------------------------------

    @Test
    fun splitsWallTimeIntoAwakeAndAsleep() =
        runTest {
            val accountant = accountant(backgroundScope)
            var elapsed = 0L
            var uptime = 0L
            val sampler = DeviceSleepSampler(accountant, { false }, { elapsed }, { uptime })
            sampler.register()

            // An hour of wall time in which the device ran for six minutes.
            elapsed = 3_600_000
            uptime = 360_000
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(360_000L, counters[UsageKeys.deviceAwakeMs(UsageKeys.BG)])
            assertEquals(3_240_000L, counters[UsageKeys.deviceSleepMs(UsageKeys.BG)])
        }

    /**
     * The shape that says "something is holding this device awake": wall time and
     * uptime advance together, so nothing ever suspended.
     */
    @Test
    fun aDeviceThatNeverSleepsRecordsNoSleep() =
        runTest {
            val accountant = accountant(backgroundScope)
            var elapsed = 0L
            var uptime = 0L
            val sampler = DeviceSleepSampler(accountant, { false }, { elapsed }, { uptime })
            sampler.register()

            elapsed = 600_000
            uptime = 600_000
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(600_000L, counters[UsageKeys.deviceAwakeMs(UsageKeys.BG)])
            assertNull(counters[UsageKeys.deviceSleepMs(UsageKeys.BG)])
        }

    /** Priming must not book the whole time since boot as the first interval. */
    @Test
    fun theFirstSampleOnlyTakesABaseline() =
        runTest {
            val accountant = accountant(backgroundScope)
            DeviceSleepSampler(accountant, { false }, { 86_400_000L }, { 3_600_000L }).register()

            assertTrue(accountant.allDaysIncludingLive()[0L].orEmpty().isEmpty())
        }

    /** Foreground and background intervals must not be mixed — only the closed half is the finding. */
    @Test
    fun attributesEachIntervalToItsOwnVisibility() =
        runTest {
            val accountant = accountant(backgroundScope)
            var elapsed = 0L
            var uptime = 0L
            var foreground = true
            val sampler = DeviceSleepSampler(accountant, { foreground }, { elapsed }, { uptime })
            sampler.register()

            // Ten minutes in the app: the screen was on, so nothing slept.
            elapsed = 600_000
            uptime = 600_000
            sampler.sample()

            // Then an hour closed, mostly suspended.
            foreground = false
            elapsed = 4_200_000
            uptime = 900_000
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(600_000L, counters[UsageKeys.deviceAwakeMs(UsageKeys.FG)])
            assertNull(counters[UsageKeys.deviceSleepMs(UsageKeys.FG)])
            assertEquals(300_000L, counters[UsageKeys.deviceAwakeMs(UsageKeys.BG)])
            assertEquals(3_300_000L, counters[UsageKeys.deviceSleepMs(UsageKeys.BG)])
        }

    /** Uptime cannot outrun wall time; a bogus reading must not become sleep the device never had. */
    @Test
    fun clampsAnImpossibleUptimeReading() =
        runTest {
            val accountant = accountant(backgroundScope)
            var elapsed = 0L
            var uptime = 0L
            val sampler = DeviceSleepSampler(accountant, { false }, { elapsed }, { uptime })
            sampler.register()

            elapsed = 1_000
            uptime = 9_999
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(1_000L, counters[UsageKeys.deviceAwakeMs(UsageKeys.BG)])
            assertNull(counters[UsageKeys.deviceSleepMs(UsageKeys.BG)])
        }

    // ------------------------------------------------------------------
    // RelayWakeEstimator
    // ------------------------------------------------------------------

    private fun wakeKeyBg() = UsageKeys.relayWakes(mobile = false, foreground = false)

    @Test
    fun countsAWakeAfterSilenceAndNotDuringChatter() =
        runTest {
            val accountant = accountant(backgroundScope)
            var now = 0L
            val estimator = RelayWakeEstimator(accountant, { false }, { false }, { now })

            estimator.onInboundFrame() // first frame is always a wake
            now = 1_000
            estimator.onInboundFrame() // same burst
            now = 5_000
            estimator.onInboundFrame() // still the same burst
            now = 60_000
            estimator.onInboundFrame() // a minute of silence: a new wake

            assertEquals(2L, accountant.allDaysIncludingLive()[0L].orEmpty()[wakeKeyBg()])
        }

    /**
     * The whole point of sharing one timestamp across relays: the device wakes
     * once however many sockets had something to say. Per-relay gaps would
     * multiply one wake-up by the pool size — and the pool is ~190 relays.
     */
    @Test
    fun simultaneousFramesFromManyRelaysAreOneWake() =
        runTest {
            val accountant = accountant(backgroundScope)
            var now = 0L
            val estimator = RelayWakeEstimator(accountant, { false }, { false }, { now })

            estimator.onInboundFrame()
            now = 30_000
            // 190 relays all answer within the same few milliseconds.
            repeat(190) { estimator.onInboundFrame() }

            assertEquals(2L, accountant.allDaysIncludingLive()[0L].orEmpty()[wakeKeyBg()])
        }

    /** A relay that server-pings every 30-70s wakes us on every ping — that is the finding. */
    @Test
    fun aPingCadenceLongerThanTheGapWakesEveryTime() =
        runTest {
            val accountant = accountant(backgroundScope)
            var now = 0L
            val estimator = RelayWakeEstimator(accountant, { false }, { false }, { now })

            repeat(10) {
                estimator.onInboundFrame()
                now += 45_000
            }

            assertEquals(10L, accountant.allDaysIncludingLive()[0L].orEmpty()[wakeKeyBg()])
        }

    // ------------------------------------------------------------------
    // WakeWorkTracker — "and then what", the half that decides a wake's cost
    // ------------------------------------------------------------------

    private fun tracker(
        accountant: ResourceUsageAccountant,
        now: () -> Long,
    ) = WakeWorkTracker(accountant, { false }, { false }, now)

    /** Activity inside one window extends it; it is booked once, when it settles. */
    @Test
    fun oneBurstIsOneWindowSpanningItsFirstToLastActivity() =
        runTest {
            val accountant = accountant(backgroundScope)
            var now = 0L
            val t = tracker(accountant) { now }

            t.onActivity() // window opens at 0
            now = 200
            t.onActivity()
            now = 1_500
            t.onActivity() // still the same burst: last activity at 1500

            // Nothing is booked while the window is open.
            assertEquals(0L, t.closedWindows)

            now = 20_000
            t.closeIfSettled()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(1L, counters[UsageKeys.wakeWorkWindows(UsageKeys.BG)])
            assertEquals(1_500L, counters[UsageKeys.wakeWorkMs(UsageKeys.BG)])
        }

    /**
     * The number the whole counter exists for: the same wake count can mean very
     * different things, and only the busy time tells them apart.
     */
    @Test
    fun twoBurstsAreTwoWindowsWithTheirOwnSpans() =
        runTest {
            val accountant = accountant(backgroundScope)
            var now = 0L
            val t = tracker(accountant) { now }

            t.onActivity()
            now = 100
            t.onActivity() // window A: 100ms

            now = 60_000
            t.onActivity() // silence exceeded -> A closes, B opens
            now = 63_000
            t.onActivity() // window B: 3000ms
            now = 80_000
            t.closeIfSettled()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(2L, counters[UsageKeys.wakeWorkWindows(UsageKeys.BG)])
            assertEquals(3_100L, counters[UsageKeys.wakeWorkMs(UsageKeys.BG)])
            // And the shape, which the total alone would hide.
            assertEquals(1L, counters[UsageKeys.wakeWorkSpan(100, mobile = false, foreground = false)])
            assertEquals(1L, counters[UsageKeys.wakeWorkSpan(3_000, mobile = false, foreground = false)])
        }

    /**
     * A flush landing mid-burst must not cut one busy period in two — that would
     * halve every span and double the window count.
     */
    @Test
    fun aFlushDuringAnActiveBurstDoesNotCloseIt() =
        runTest {
            val accountant = accountant(backgroundScope)
            var now = 0L
            val t = tracker(accountant) { now }

            t.onActivity()
            now = 1_000
            t.onActivity()

            now = 2_000
            t.closeIfSettled() // only 1s of silence: still busy

            assertEquals(0L, t.closedWindows)
            assertTrue(accountant.allDaysIncludingLive()[0L].orEmpty().isEmpty())
        }

    /**
     * Without pre-flush closing, a device that slept right after a burst would
     * never book that burst — the next activity might be hours away.
     */
    @Test
    fun aSettledWindowIsBookedByTheFlushHookNotByTheNextActivity() =
        runTest {
            val accountant = accountant(backgroundScope)
            var now = 0L
            val t = tracker(accountant) { now }

            t.onActivity()
            now = 500
            t.onActivity()

            now = 30_000
            t.closeIfSettled()
            assertEquals(1L, t.closedWindows)

            // Idempotent: a second flush with no new activity books nothing more.
            now = 40_000
            t.closeIfSettled()
            assertEquals(1L, t.closedWindows)
            assertEquals(1L, accountant.allDaysIncludingLive()[0L].orEmpty()[UsageKeys.wakeWorkWindows(UsageKeys.BG)])
        }

    /** A wake that settles instantly is still a wake — it just has a zero-length span. */
    @Test
    fun aSingleFrameWakeIsStillCountedAsAWindow() =
        runTest {
            val accountant = accountant(backgroundScope)
            var now = 0L
            val t = tracker(accountant) { now }

            t.onActivity()
            now = 30_000
            t.closeIfSettled()

            val counters = accountant.allDaysIncludingLive()[0L].orEmpty()
            assertEquals(1L, counters[UsageKeys.wakeWorkWindows(UsageKeys.BG)])
            assertNull(counters[UsageKeys.wakeWorkMs(UsageKeys.BG)])
            assertEquals(1L, counters[UsageKeys.wakeWorkSpan(0, mobile = false, foreground = false)])
        }

    /**
     * Histogram bucket names come from the bounds, so two histograms with
     * different prefixes can share one — `relay.life` and `wakework.span` both
     * produce `lt5s`. Reading a bucket by segment match therefore reports relay
     * session lifetimes as busy-window lengths, which is how this was found:
     * the repo's own `newKeysDoNotDisturbSummary` guard went red.
     */
    @Test
    fun spanBucketsDoNotAbsorbTheRelayLifeHistogram() =
        runTest {
            val counters =
                mapOf(
                    // A relay session that lived 3s — a `relay.life.lt5s.*` key.
                    UsageKeys.relayLife(3_000, mobile = false, foreground = false) to 7L,
                    // One genuine busy window of 3s — a `wakework.span.lt5s.*` key.
                    UsageKeys.wakeWorkSpan(3_000, mobile = false, foreground = false) to 1L,
                )

            val summary = UsageSummary.from(counters)

            assertEquals(mapOf("lt5s" to 1L), summary.wakeWorkSpans)
        }

    /** Relay wakes must not silently join the HTTP-defined "radio bursts" figure. */
    @Test
    fun relayWakesStayOutOfTheHttpBurstTotal() =
        runTest {
            val accountant = accountant(backgroundScope)
            val estimator = RelayWakeEstimator(accountant, { false }, { false }, { 0L })
            estimator.onInboundFrame()

            val summary = UsageSummary.from(accountant.allDaysIncludingLive()[0L].orEmpty())
            assertEquals(1L, summary.relayWakes)
            assertEquals(0L, summary.radioBursts)
        }
}
