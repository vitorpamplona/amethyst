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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResourceUsageStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File(temp.root, ResourceUsageStore.FILE_NAME)
    }

    @Test
    fun mergesAndReloadsFromDisk() =
        runTest {
            val store = ResourceUsageStore(file)
            store.mergeInto(100, mapOf("a" to 5L, "b" to 2L))
            store.mergeInto(100, mapOf("a" to 3L))
            store.mergeInto(101, mapOf("a" to 1L))

            val reloaded = ResourceUsageStore(file).allDays()
            assertEquals(8L, reloaded[100]?.get("a"))
            assertEquals(2L, reloaded[100]?.get("b"))
            assertEquals(1L, reloaded[101]?.get("a"))
        }

    @Test
    fun prunesBucketsOlderThanKeepDays() =
        runTest {
            val store = ResourceUsageStore(file, keepDays = 7)
            store.mergeInto(100, mapOf("a" to 1L))
            store.mergeInto(110, mapOf("a" to 1L))

            val days = store.allDays()
            assertNull("day 100 is older than 110-7 and must be pruned", days[100])
            assertEquals(1L, days[110]?.get("a"))
        }

    @Test
    fun alertStateRoundTrips() =
        runTest {
            val store = ResourceUsageStore(file)
            assertEquals(0L, store.lastAlertAtSec())
            assertFalse(store.alertsOptOut())

            store.markAlertPrompted(12345L)
            store.setAlertsOptOut(true)

            val reloaded = ResourceUsageStore(file)
            assertEquals(12345L, reloaded.lastAlertAtSec())
            assertTrue(reloaded.alertsOptOut())
        }
}

class ResourceUsageAccountantTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun accumulatesAndFlushesIntoTheRightDay() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            var day = 200L
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { day })

            accountant.add("x", 5)
            accountant.add("x", 5)
            accountant.flush()
            day = 201L
            accountant.add("x", 7)
            accountant.flush()

            val days = store.allDays()
            assertEquals(10L, days[200]?.get("x"))
            assertEquals(7L, days[201]?.get("x"))
        }

    @Test
    fun liveCountersAreVisibleBeforeFlush() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 300L })

            accountant.add("x", 42)
            val days = accountant.allDaysIncludingLive()
            assertEquals(42L, days[300]?.get("x"))
            // and not yet on disk
            assertNull(store.allDays()[300])
        }

    @Test
    fun preFlushHooksRunOnFlushAndRead() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 300L })
            var hookRuns = 0
            accountant.addPreFlushHook { hookRuns++ }

            accountant.flush()
            accountant.allDaysIncludingLive()
            assertEquals(2, hookRuns)
        }
}

class RelayConnectionTimeIntegratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun integratesConnectionTimeAcrossStateChanges() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })

            var now = 0L
            val count = MutableStateFlow(0)
            val mobile = MutableStateFlow<Boolean?>(false)
            val fg = MutableStateFlow(true)

            val integrator =
                RelayConnectionTimeIntegrator(
                    connectedCount = count,
                    isMobile = mobile,
                    isForeground = fg,
                    accountant = accountant,
                    nowMs = { now },
                )
            val job = integrator.start(backgroundScope)
            testScheduler.runCurrent()

            // 5 relays connected on wifi foreground for 10s
            count.value = 5
            testScheduler.runCurrent()
            now = 10_000L

            // switch to cellular background: closes the wifi segment
            mobile.value = true
            fg.value = false
            testScheduler.runCurrent()

            // 5 relays on cellular background for 20s, then all disconnect
            now = 30_000L
            count.value = 0
            testScheduler.runCurrent()

            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(5 * 10_000L, counters[UsageKeys.relayConnMs(mobile = false, foreground = true)])
            assertEquals(5 * 20_000L, counters[UsageKeys.relayConnMs(mobile = true, foreground = false)])
            job.cancel()
        }

    @Test
    fun closeOpenSegmentAccountsLongStableSessions() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })

            var now = 0L
            val count = MutableStateFlow(3)
            val integrator =
                RelayConnectionTimeIntegrator(
                    connectedCount = count,
                    isMobile = MutableStateFlow(true),
                    isForeground = MutableStateFlow(false),
                    accountant = accountant,
                    nowMs = { now },
                )
            val job = integrator.start(backgroundScope)
            testScheduler.runCurrent()

            // hours pass with no state change at all (always-on background)
            now = 2 * 60 * 60 * 1000L
            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(
                "reading the ledger must account the still-open segment",
                3 * 2 * 60 * 60 * 1000L,
                counters[UsageKeys.relayConnMs(mobile = true, foreground = false)],
            )
            job.cancel()
        }
}

class ProcessCpuSamplerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun accountsCpuDeltasAcrossSamples() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })
            var cpu = 1_000L
            val sampler = ProcessCpuSampler(accountant) { cpu }

            cpu = 1_500L
            sampler.sample()
            cpu = 1_800L
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(800L, counters[UsageKeys.CPU_MS])
        }
}

class ForegroundTimeIntegratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun accountsOnlyForegroundTime() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })
            var now = 0L
            val fg = MutableStateFlow(false)
            val integrator = ForegroundTimeIntegrator(fg, accountant) { now }
            val job = integrator.start(backgroundScope)
            testScheduler.runCurrent()

            // 60s in background — must not count
            now = 60_000L
            fg.value = true
            testScheduler.runCurrent()

            // 30s in foreground — counts
            now = 90_000L
            fg.value = false
            testScheduler.runCurrent()

            // 60s more in background, then read: still 30s
            now = 150_000L
            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(30_000L, counters[UsageKeys.APP_FG_MS])
            job.cancel()
        }

    @Test
    fun openForegroundSegmentIsAccountedOnRead() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })
            var now = 0L
            val fg = MutableStateFlow(true)
            val integrator = ForegroundTimeIntegrator(fg, accountant) { now }
            val job = integrator.start(backgroundScope)
            testScheduler.runCurrent()

            now = 45_000L
            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(45_000L, counters[UsageKeys.APP_FG_MS])
            job.cancel()
        }
}

class ResourceUsageAlertsTest {
    private fun day(vararg counters: Pair<String, Long>) = mapOf(*counters)

    @Test
    fun quietUsageDoesNotAlert() {
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.net(UsageKeys.ROLE_IMAGE, mobile = true, foreground = false, received = true) to 1024L * 1024L,
                        UsageKeys.relayConnMs(mobile = true, foreground = false) to 60L * 60L * 1000L,
                    ),
            )
        assertNull(ResourceUsageAlerts.evaluate(days, today = 10L))
    }

    @Test
    fun backgroundMobileDataCrossingThresholdAlerts() {
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.net(UsageKeys.ROLE_VIDEO, mobile = true, foreground = false, received = true) to
                            ResourceUsageAlerts.BG_MOBILE_BYTES_PER_DAY + 1,
                    ),
            )
        val alert = ResourceUsageAlerts.evaluate(days, today = 10L)
        assertEquals(ResourceUsageAlerts.Reason.BACKGROUND_MOBILE_DATA, alert?.reason)
        assertEquals(9L, alert?.day)
    }

    @Test
    fun foregroundMobileDataDoesNotTripTheBackgroundThreshold() {
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.net(UsageKeys.ROLE_VIDEO, mobile = true, foreground = true, received = true) to
                            ResourceUsageAlerts.BG_MOBILE_BYTES_PER_DAY * 10,
                    ),
            )
        assertNull(ResourceUsageAlerts.evaluate(days, today = 10L))
    }

    @Test
    fun connectionTimeCounterDoesNotLeakIntoByteThreshold() {
        // relay.connms keys carry mobile+bg dims but are milliseconds, not
        // bytes: they must never count toward the data threshold.
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.relayConnMs(mobile = true, foreground = false) to
                            ResourceUsageAlerts.BG_MOBILE_BYTES_PER_DAY * 100,
                    ),
            )
        val alert = ResourceUsageAlerts.evaluate(days, today = 10L)
        assertEquals(ResourceUsageAlerts.Reason.BACKGROUND_MOBILE_CONNECTION_TIME, alert?.reason)
    }

    @Test
    fun todayIsCheckedWhenYesterdayIsQuiet() {
        val days =
            mapOf(
                10L to day(UsageKeys.APP_STARTS to ResourceUsageAlerts.APP_STARTS_PER_DAY + 1),
            )
        val alert = ResourceUsageAlerts.evaluate(days, today = 10L)
        assertEquals(ResourceUsageAlerts.Reason.PROCESS_CHURN, alert?.reason)
    }

    @Test
    fun reconnectChurnAlerts() {
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.relayConnects(mobile = true, foreground = false) to 3_000L,
                        UsageKeys.relayConnects(mobile = false, foreground = true) to
                            ResourceUsageAlerts.RELAY_CONNECTS_PER_DAY - 2_000L,
                    ),
            )
        val alert = ResourceUsageAlerts.evaluate(days, today = 10L)
        assertEquals(ResourceUsageAlerts.Reason.RECONNECT_CHURN, alert?.reason)
    }

    @Test
    fun promptRateLimiting() {
        val now = 1_000_000L
        val week = ResourceUsageAlerts.MIN_DAYS_BETWEEN_PROMPTS * 24 * 60 * 60
        assertTrue(ResourceUsageAlerts.shouldPrompt(lastAlertAtSec = 0, optOut = false, nowSec = now))
        assertFalse(ResourceUsageAlerts.shouldPrompt(lastAlertAtSec = now - week + 10, optOut = false, nowSec = now))
        assertTrue(ResourceUsageAlerts.shouldPrompt(lastAlertAtSec = now - week - 10, optOut = false, nowSec = now))
        assertFalse(ResourceUsageAlerts.shouldPrompt(lastAlertAtSec = 0, optOut = true, nowSec = now))
    }
}
