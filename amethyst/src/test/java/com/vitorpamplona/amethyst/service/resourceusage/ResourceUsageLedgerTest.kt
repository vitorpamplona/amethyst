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

import com.vitorpamplona.amethyst.service.playback.playerPool.MediaPlayTimeTracker
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import kotlinx.coroutines.CoroutineScope
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
    fun countersKeepAccumulatingAfterADrain() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 200L })

            accountant.add("x", 5)
            accountant.flush()
            accountant.add("x", 7)
            accountant.flush()

            assertEquals(12L, store.allDays()[200]?.get("x"))
        }

    @Test
    fun hookAddsAreDrainedInPlaceAndNeverRearmTheFlushLoop() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 300L })
            var hookRuns = 0
            accountant.addPreFlushHook {
                hookRuns++
                accountant.add("hook.counter", 1)
            }

            accountant.add("x", 1)
            testScheduler.advanceTimeBy(31_000)
            testScheduler.runCurrent()

            assertEquals("the one debounced flush ran its hooks once", 1, hookRuns)
            assertEquals(1L, store.allDays()[300]?.get("hook.counter"))

            // If hook adds re-armed the debounce, more flushes would fire and
            // the hook counter would keep growing without any real activity.
            testScheduler.advanceTimeBy(300_000)
            testScheduler.runCurrent()
            assertEquals("no self-perpetuating flush loop", 1, hookRuns)
            assertEquals(1L, store.allDays()[300]?.get("hook.counter"))
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

class RadioBurstEstimatorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun countsBurstsOnlyAfterRadioIdleGaps() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })
            var now = 100_000L
            val estimator =
                RadioBurstEstimator(
                    accountant = accountant,
                    isMobile = { true },
                    isForeground = { false },
                    nowMs = { now },
                )

            estimator.onHttpActivity() // first activity = one burst
            now += 2_000
            estimator.onHttpActivity() // 2s later: same burst
            now += RadioBurstEstimator.BURST_GAP_MS + 1
            estimator.onHttpActivity() // >10s of silence: new burst
            now += 500
            estimator.onHttpActivity() // same burst

            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(2L, counters[UsageKeys.radioBursts(mobile = true, foreground = false)])
        }
}

class LoopbackExclusionTest {
    @Test
    fun loopbackHostsAreRecognizedAndRealHostsAreNot() {
        assertTrue(UsageCountingInterceptor.isLoopback("127.0.0.1"))
        assertTrue(UsageCountingInterceptor.isLoopback("127.4.5.6"))
        assertTrue(UsageCountingInterceptor.isLoopback("localhost"))
        assertTrue(UsageCountingInterceptor.isLoopback("::1"))
        assertFalse(UsageCountingInterceptor.isLoopback("relay.example.com"))
        assertFalse(UsageCountingInterceptor.isLoopback("192.168.1.10"))
        assertFalse(UsageCountingInterceptor.isLoopback("128.0.0.1"))
    }
}

class MediaPlayTimeTrackerTest {
    @Test
    fun accumulatesOnlyWhilePlaying() {
        var now = 0L
        var played = 0L
        val tracker = MediaPlayTimeTracker(onPlayed = { played += it }, nowMs = { now })

        tracker.onIsPlayingChanged(true)
        now = 30_000L
        tracker.onIsPlayingChanged(false)

        now = 100_000L // paused time must not count
        tracker.onIsPlayingChanged(true)
        now = 105_000L
        tracker.onIsPlayingChanged(false)
        tracker.onIsPlayingChanged(false) // duplicate stop is a no-op

        assertEquals(35_000L, played)
    }
}

class SessionTimeIntegratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun accountsActiveTimeAndCountsActivations() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            var clock = 0L
            val session = SessionTimeIntegrator(accountant, "s.ms", "s.starts", nowMs = { clock })

            session.setActive(true)
            clock += 5_000
            session.setActive(false)
            clock += 60_000 // idle time must not count
            session.setActive(true)
            clock += 1_000
            session.setActive(false)

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(6_000L, today["s.ms"])
            assertEquals(2L, today["s.starts"])
        }

    @Test
    fun repeatedActivationsDoNotDoubleCountStarts() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            var clock = 0L
            val session = SessionTimeIntegrator(accountant, "s.ms", "s.starts", nowMs = { clock })

            session.setActive(true)
            clock += 1_000
            session.setActive(true)
            clock += 1_000
            session.setActive(false)

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(2_000L, today["s.ms"])
            assertEquals(1L, today["s.starts"])
        }

    @Test
    fun openSegmentIsAccountedOnFlushWithoutClosing() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            var clock = 0L
            val session = SessionTimeIntegrator(accountant, "s.ms", nowMs = { clock })
            session.registerFlushHook()

            session.setActive(true)
            clock += 120_000
            val mid = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals("multi-hour stable sessions account without a transition", 120_000L, mid["s.ms"])

            clock += 30_000
            session.setActive(false)
            val end = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(150_000L, end["s.ms"])
        }
}

class BatteryDrainSamplerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun harness(scope: CoroutineScope): Triple<ResourceUsageAccountant, BatteryDrainSampler, Controls> {
        val store = ResourceUsageStore(File(temp.root, "u.json"))
        val accountant = ResourceUsageAccountant(store, scope, epochDay = { 100L })
        val controls = Controls()
        val sampler =
            BatteryDrainSampler(
                accountant = accountant,
                capacityPct = { controls.pct },
                isCharging = { controls.charging },
                isForeground = { controls.foreground },
            )
        return Triple(accountant, sampler, controls)
    }

    private class Controls {
        var pct: Int? = 90
        var charging = false
        var foreground = true
    }

    @Test
    fun firstSampleOnlyEstablishesTheBaseline() =
        runTest {
            val (accountant, sampler, _) = harness(backgroundScope)
            sampler.sample()
            assertNull(accountant.allDaysIncludingLive()[100]?.get(UsageKeys.BATTERY_DRAIN_FG))
        }

    @Test
    fun dischargeDropsAreAccountedByVisibility() =
        runTest {
            val (accountant, sampler, controls) = harness(backgroundScope)
            sampler.sample() // baseline 90, discharging
            controls.pct = 87
            sampler.sample() // -3 while foreground
            controls.foreground = false
            controls.pct = 85
            sampler.sample() // -2 while background

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(3L, today[UsageKeys.BATTERY_DRAIN_FG])
            assertEquals(2L, today[UsageKeys.BATTERY_DRAIN_BG])
        }

    @Test
    fun intervalsTouchingAChargerAreSkipped() =
        runTest {
            val (accountant, sampler, controls) = harness(backgroundScope)
            sampler.sample() // baseline 90, discharging
            controls.charging = true
            controls.pct = 80 // weird drop while charging: ignore
            sampler.sample()
            controls.charging = false
            controls.pct = 78 // first discharging interval after charging: baseline only
            sampler.sample()
            controls.pct = 77
            sampler.sample() // clean discharging interval: -1

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(1L, today[UsageKeys.BATTERY_DRAIN_FG])
        }

    @Test
    fun aLevelIncreaseResetsTheBaselineWithoutAccounting() =
        runTest {
            val (accountant, sampler, controls) = harness(backgroundScope)
            sampler.sample() // baseline 90
            controls.pct = 95 // e.g. charged while the process was frozen
            sampler.sample()
            controls.pct = 94
            sampler.sample() // -1

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(1L, today[UsageKeys.BATTERY_DRAIN_FG])
        }
}

class MeteringNostrSignerTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** Pure-Kotlin fake so the test never touches secp256k1 JNI. */
    private class FakeSigner : NostrSigner("aa".repeat(32)) {
        override fun isWriteable() = true

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = throw UnsupportedOperationException("fake")

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = "enc04"

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = "dec04"

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = "enc44"

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = "dec44"

        override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent = throw UnsupportedOperationException("fake")

        override suspend fun deriveKey(nonce: HexKey): HexKey = "bb".repeat(32)

        override suspend fun signPsbt(psbtHex: String): String = psbtHex

        override fun hasForegroundSupport() = true
    }

    @Test
    fun countsSignsAndCryptoOpsWithoutChangingResults() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            val metered = MeteringNostrSigner(FakeSigner(), accountant)

            assertEquals("dec44", metered.nip44Decrypt("x", "aa".repeat(32)))
            assertEquals("dec04", metered.nip04Decrypt("x", "aa".repeat(32)))
            assertEquals("enc44", metered.nip44Encrypt("x", "aa".repeat(32)))
            runCatching { metered.sign<Event>(0L, 1, arrayOf(), "hello") }

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(2L, today[UsageKeys.DECRYPT_COUNT])
            assertEquals(1L, today[UsageKeys.ENCRYPT_COUNT])
            assertEquals(1L, today[UsageKeys.signs(UsageKeys.SIGNER_LOCAL)])
            assertNull("non-local signers must not pollute crypto CPU time", today[UsageKeys.DECRYPT_US])
        }

    @Test
    fun innermostSignerUnwrapsTheMeteringDecorator() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            val raw = FakeSigner()
            val metered = MeteringNostrSigner(raw, accountant)
            assertEquals(raw, metered.innermostSigner())
        }
}

class ScreenTimeIntegratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun routeNamesLoseTheirArgumentsBeforeAnythingIsRecorded() {
        assertEquals("Profile", ScreenTimeIntegrator.screenNameOf("com.vitorpamplona.amethyst.ui.navigation.routes.Route.Profile/{userId}"))
        assertEquals("Hashtag", ScreenTimeIntegrator.screenNameOf("routes.Route.Hashtag/{tag}?extra={extra}"))
        assertEquals("Home", ScreenTimeIntegrator.screenNameOf("routes.Route.Home"))
        assertNull(ScreenTimeIntegrator.screenNameOf(null))
        assertNull(ScreenTimeIntegrator.screenNameOf(""))
    }

    @Test
    fun accountsScreenTimeOnlyWhileForeground() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            var clock = 0L
            val isForeground = MutableStateFlow(true)
            val integrator = ScreenTimeIntegrator(accountant, nowMs = { clock })
            integrator.start(backgroundScope, isForeground)
            testScheduler.runCurrent()

            integrator.onScreen("Home")
            testScheduler.runCurrent()
            clock += 5_000
            integrator.onScreen("Video")
            testScheduler.runCurrent()
            clock += 3_000
            isForeground.value = false // backgrounded on Video: segment closes
            testScheduler.runCurrent()
            clock += 60_000 // background time must not count
            isForeground.value = true
            testScheduler.runCurrent()
            clock += 2_000
            integrator.onScreen(null)
            testScheduler.runCurrent()

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(5_000L, today[UsageKeys.screenMs("Home")])
            assertEquals(5_000L, today[UsageKeys.screenMs("Video")])
        }
}

class UsageSummaryMapsTest {
    @Test
    fun screenTimeAndCellularMapsAreExtractedFromCounters() {
        val s =
            UsageSummary.from(
                mapOf(
                    UsageKeys.screenMs("Home") to 10_000L,
                    UsageKeys.screenMs("Video") to 5_000L,
                    UsageKeys.net(UsageKeys.ROLE_IMAGE, mobile = true, foreground = true, received = true) to 100L,
                    UsageKeys.net(UsageKeys.ROLE_IMAGE, mobile = false, foreground = true, received = true) to 900L,
                ),
            )
        assertEquals(10_000L, s.screenTimeMs["Home"])
        assertEquals(5_000L, s.screenTimeMs["Video"])
        assertEquals(100L, s.mobileBytesPerSubsystem[UsageKeys.ROLE_IMAGE])
        assertEquals(1_000L, s.bytesPerSubsystem[UsageKeys.ROLE_IMAGE])
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
