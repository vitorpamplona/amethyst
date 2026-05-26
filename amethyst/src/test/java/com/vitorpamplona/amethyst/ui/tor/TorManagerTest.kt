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
package com.vitorpamplona.amethyst.ui.tor

import com.vitorpamplona.amethyst.commons.tor.TorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TorManager]'s self-heal logic. Drives the manager with in-memory
 * [TorBackend] + [TorPreferencesPort] fakes and a virtual clock so the 45s watchdog
 * delay and 5-min cooldown can be exercised in milliseconds.
 *
 * Companion integration test in `amethyst/src/androidTest/.../tor/TorBootstrapInstrumentedTest.kt`
 * covers the real-Arti bootstrap path on-device (currently @Ignore'd; see file for enable steps).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TorManagerTest {
    // ------------------------------------------------------------------
    // construction + persisted state
    // ------------------------------------------------------------------

    @Test
    fun `init loads persisted bypass approval`() =
        runTest(UnconfinedTestDispatcher()) {
            val recent = 1_000_000_000_000L
            val prefs = FakeTorPreferences(initialApprovalMs = recent)
            val manager = buildManager(prefs = prefs, clock = { recent + 1_000L })

            advanceUntilIdle()

            assertTrue(manager.rememberedApprovalActive())
        }

    @Test
    fun `init does not flag approval when none persisted`() =
        runTest(UnconfinedTestDispatcher()) {
            val manager = buildManager()
            advanceUntilIdle()

            assertFalse(manager.rememberedApprovalActive())
        }

    @Test
    fun `rememberedApprovalActive is false once outside the 1h window`() =
        runTest(UnconfinedTestDispatcher()) {
            val now = 1_000_000_000_000L
            val tooOld = now - TorManager.APPROVAL_REMEMBER_MS - 1L
            val prefs = FakeTorPreferences(initialApprovalMs = tooOld)
            val manager = buildManager(prefs = prefs, clock = { now })

            advanceUntilIdle()

            assertFalse(manager.rememberedApprovalActive())
        }

    // ------------------------------------------------------------------
    // torType change clears the bypass loop
    // ------------------------------------------------------------------

    @Test
    fun `torType change clears in-memory bypass and persisted approval`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialApprovalMs = 999L)
            val manager = buildManager(prefs = prefs)
            advanceUntilIdle()

            manager.sessionBypass.value = true

            prefs.setTorType(TorType.OFF)
            advanceUntilIdle()

            assertFalse(manager.sessionBypass.value)
            assertEquals(0L, prefs.lastBypassApprovalMs)
        }

    @Test
    fun `approveBypassForOneHour sets sessionBypass and persists timestamp`() =
        runTest(UnconfinedTestDispatcher()) {
            val now = 1_000_000_000_000L
            val prefs = FakeTorPreferences()
            val manager = buildManager(prefs = prefs, clock = { now })
            advanceUntilIdle()

            manager.approveBypassForOneHour()
            advanceUntilIdle()

            assertTrue(manager.sessionBypass.value)
            assertEquals(now, prefs.lastBypassApprovalMs)
            assertTrue(manager.rememberedApprovalActive())
        }

    // ------------------------------------------------------------------
    // onNetworkChange — drops client + clears bypass + primes cooldown
    // ------------------------------------------------------------------

    @Test
    fun `onNetworkChange clears bypass and persisted approval and resets backend`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialApprovalMs = 12345L)
            val backend = FakeTorBackend()
            val manager = buildManager(prefs = prefs, backend = backend)
            advanceUntilIdle()
            manager.sessionBypass.value = true

            manager.onNetworkChange()
            advanceUntilIdle()

            assertFalse(manager.sessionBypass.value)
            assertEquals(0L, prefs.lastBypassApprovalMs)
            assertTrue("onNetworkChange should reset backend at least once", backend.resetCount >= 1)
        }

    @Test
    fun `onNetworkChange primes cooldown so the watchdog does not double-reset`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // Constant clock — the only way self-heal would fire is if onNetworkChange
            // failed to prime lastSelfHealAtMs.
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()
            val resetCountBefore = backend.resetCount

            manager.onNetworkChange()
            advanceUntilIdle()

            // Status is back at Connecting after the network-change reset cycle.
            // Advance past the 45s watchdog; cooldown must suppress a second reset.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            // Exactly one extra reset from onNetworkChange itself, none from the watchdog.
            assertEquals(resetCountBefore + 1, backend.resetCount)
            assertEquals(0, backend.resetWithCleanStateCount)
        }

    // ------------------------------------------------------------------
    // stuck-Connecting watchdog
    // ------------------------------------------------------------------

    @Test
    fun `watchdog uses gentle reset before first Active`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // Big constant clock so (now - lastSelfHealAtMs=0) is well past cooldown.
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()

            assertEquals(TorServiceStatus.Connecting, manager.status.value)
            assertEquals(0, backend.resetCount)

            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            assertEquals("gentle reset only — no state wipe before first Active", 1, backend.resetCount)
            assertEquals(0, backend.resetWithCleanStateCount)
        }

    @Test
    fun `watchdog uses full reset after first Active`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()

            // Drive backend to Active so hasEverBootstrapped flips.
            backend.setActive(9050)
            advanceUntilIdle()
            assertTrue(manager.status.value is TorServiceStatus.Active)

            // Back to Connecting — watchdog timer (re-)starts.
            backend.setConnecting()
            advanceUntilIdle()

            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            assertEquals(0, backend.resetCount)
            assertEquals("after Active, watchdog wipes state too", 1, backend.resetWithCleanStateCount)
        }

    @Test
    fun `watchdog cancels its delay when status leaves Connecting`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()

            // Halfway through the watchdog delay, the bootstrap succeeds.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS / 2)
            backend.setActive(9050)
            advanceUntilIdle()

            // Past the original deadline — must NOT fire.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS)
            runCurrent()

            assertEquals(0, backend.resetCount)
            assertEquals(0, backend.resetWithCleanStateCount)
        }

    @Test
    fun `watchdog cooldown blocks a second fire within the window`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            var clockNow = 1_000_000_000_000L
            val manager = buildManager(backend = backend, clock = { clockNow })
            advanceUntilIdle()

            // First fire.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()
            assertEquals(1, backend.resetCount)

            // Status returns to Connecting via the reset → re-start cycle. Advance another
            // 45s of virtual time — clock has barely moved, so cooldown must block.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()
            assertEquals("cooldown should suppress the second fire", 1, backend.resetCount)
        }

    @Test
    fun `watchdog can fire again once the cooldown elapses`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            var clockNow = 1_000_000_000_000L
            val manager = buildManager(backend = backend, clock = { clockNow })
            advanceUntilIdle()

            // First fire.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()
            assertEquals(1, backend.resetCount)

            // Move wall-clock past the cooldown window.
            clockNow += TorManager.SELF_HEAL_COOLDOWN_MS + 1_000L
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            assertEquals("after cooldown elapses, watchdog fires again", 2, backend.resetCount)
        }

    // ------------------------------------------------------------------
    // top-level status routing
    // ------------------------------------------------------------------

    @Test
    fun `status emits Off when torType is OFF`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialTorType = TorType.OFF)
            val backend = FakeTorBackend()
            val manager = buildManager(prefs = prefs, backend = backend)
            advanceUntilIdle()

            assertEquals(TorServiceStatus.Off, manager.status.value)
            assertEquals(0, backend.startCount)
        }

    @Test
    fun `status emits Active(port) for EXTERNAL with valid port`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialTorType = TorType.EXTERNAL, initialPort = 9150)
            val manager = buildManager(prefs = prefs)
            advanceUntilIdle()

            val status = manager.status.value
            assertTrue(status is TorServiceStatus.Active)
            assertEquals(9150, (status as TorServiceStatus.Active).port)
        }

    @Test
    fun `status emits Off for EXTERNAL when port is invalid`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialTorType = TorType.EXTERNAL, initialPort = 0)
            val manager = buildManager(prefs = prefs)
            advanceUntilIdle()

            assertEquals(TorServiceStatus.Off, manager.status.value)
        }

    @Test
    fun `status follows backend status under INTERNAL`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend)
            advanceUntilIdle()

            assertEquals(TorServiceStatus.Connecting, manager.status.value)

            backend.setActive(17392)
            advanceUntilIdle()
            assertEquals(TorServiceStatus.Active(17392), manager.status.value)
        }

    @Test
    fun `activePortOrNull mirrors the Active port`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend)
            // activePortOrNull is WhileSubscribed — give it a subscriber for the test.
            val portJob = backgroundScope.launch { manager.activePortOrNull.collect {} }
            advanceUntilIdle()

            backend.setActive(17392)
            advanceUntilIdle()

            assertEquals(17392, manager.activePortOrNull.value)
            portJob.cancel()
        }

    @Test
    fun `sessionBypass forces Off even with torType INTERNAL`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend)
            advanceUntilIdle()
            backend.setActive(17392)
            advanceUntilIdle()
            assertNotEquals(TorServiceStatus.Off, manager.status.value)

            manager.sessionBypass.value = true
            advanceUntilIdle()

            assertEquals(TorServiceStatus.Off, manager.status.value)
            assertTrue(backend.stopCount >= 1)
        }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun TestScope.buildManager(
        prefs: FakeTorPreferences = FakeTorPreferences(),
        backend: FakeTorBackend = FakeTorBackend(),
        clock: () -> Long = { 1_000_000_000_000L },
    ): TorManager =
        TorManager(
            torPrefs = prefs,
            service = backend,
            scope = backgroundScope,
            // Unconfined so `MutableStateFlow.value = …` propagates through `flowOn`
            // synchronously — otherwise advanceUntilIdle never settles the cross-dispatcher
            // channel and `manager.status.value` is observed as the stateIn initial (Off).
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            nowMs = clock,
        )
}

/** In-memory [TorBackend] driven by tests. */
private class FakeTorBackend : TorBackend {
    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    override val status: StateFlow<TorServiceStatus> = _status.asStateFlow()

    var startCount = 0
        private set
    var stopCount = 0
        private set
    var resetCount = 0
        private set
    var resetWithCleanStateCount = 0
        private set

    override suspend fun start() {
        startCount++
        _status.value = TorServiceStatus.Connecting
    }

    override suspend fun stop() {
        stopCount++
        _status.value = TorServiceStatus.Off
    }

    override suspend fun reset() {
        resetCount++
        _status.value = TorServiceStatus.Off
    }

    override suspend fun resetWithCleanState() {
        resetWithCleanStateCount++
        _status.value = TorServiceStatus.Off
    }

    fun setActive(port: Int) {
        _status.value = TorServiceStatus.Active(port)
    }

    fun setConnecting() {
        _status.value = TorServiceStatus.Connecting
    }
}

/** In-memory [TorPreferencesPort] driven by tests. */
private class FakeTorPreferences(
    initialTorType: TorType = TorType.INTERNAL,
    initialPort: Int = 9050,
    initialApprovalMs: Long = 0L,
) : TorPreferencesPort {
    private val _torType = MutableStateFlow(initialTorType)
    private val _externalSocksPort = MutableStateFlow(initialPort)

    override val torType: StateFlow<TorType> = _torType.asStateFlow()
    override val externalSocksPort: StateFlow<Int> = _externalSocksPort.asStateFlow()

    var lastBypassApprovalMs: Long = initialApprovalMs

    override suspend fun loadLastBypassApprovalMs(): Long = lastBypassApprovalMs

    override suspend fun saveLastBypassApprovalMs(value: Long) {
        lastBypassApprovalMs = value
    }

    fun setTorType(value: TorType) {
        _torType.value = value
    }
}
