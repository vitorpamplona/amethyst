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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.platformTempDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TorService] over an in-memory [ArtiBridge] and a real temporary data directory, so the
 * lifecycle Android and iOS share is pinned without a native Arti: the Connecting -> Bootstrapping
 * -> Active promotion, init failures that stay Connecting for the watchdog, the SOCKS port retry,
 * the poisoned-guard wipe, the persisted-bootstrap seed and the runtime AllGuardsDown detector.
 *
 * [TorManagerTest] covers the policy above it against a fake [TorBackend].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TorServiceTest {
    private val dir: Path = platformTempDirectory / "TorServiceTest-${Random.nextLong().toULong()}"
    private val fs = platformFileSystem

    @AfterTest
    fun cleanUp() {
        fs.deleteRecursivelyQuietly(dir)
    }

    private fun TestScope.service(engine: FakeArti): TorService =
        TorService(
            engine = engine,
            dataDir = dir,
            fileSystem = fs,
            scope = backgroundScope,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            nowMs = { testScheduler.currentTime },
        )

    private fun writeGuards(json: String) {
        val file = dir / "state" / "state" / "guards.json"
        fs.createDirectories(file.parent!!)
        fs.write(file) { writeUtf8(json) }
    }

    @Test
    fun startIsRoutableAtOnceAndActiveOnceTheDirectoryLands() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)

            service.start()
            assertEquals(TorServiceStatus.Bootstrapping(17392), service.status.value)
            assertEquals(listOf(dir.toString()), engine.initializedWith)
            assertFalse(service.bootstrapInFlight.value)

            engine.progress = 420
            advanceTimeBy(600)
            runCurrent()
            assertEquals(420, service.bootstrapProgress.value)
            assertEquals(TorServiceStatus.Bootstrapping(17392), service.status.value)

            engine.bootstrapped = 1
            advanceTimeBy(600)
            runCurrent()
            assertEquals(TorServiceStatus.Active(17392), service.status.value)
        }

    @Test
    fun aFailedInitStaysConnectingAndTheNextStartRetries() =
        runTest {
            val engine = FakeArti(initResult = -1)
            val service = service(engine)

            service.start()
            assertEquals(TorServiceStatus.Connecting, service.status.value)
            assertFalse(service.bootstrapInFlight.value)
            assertEquals(0, engine.socksStarts.size)

            engine.initResult = 0
            service.start()
            assertEquals(2, engine.initializedWith.size)
            assertEquals(TorServiceStatus.Bootstrapping(17392), service.status.value)
        }

    @Test
    fun aTakenPortMovesToTheNext() =
        runTest {
            val engine = FakeArti(busyPorts = setOf(17392, 17393))
            val service = service(engine)

            service.start()
            assertEquals(listOf(17392, 17393, 17394), engine.socksStarts)
            assertEquals(TorServiceStatus.Bootstrapping(17394), service.status.value)
        }

    @Test
    fun noFreePortStaysConnectingForTheWatchdog() =
        runTest {
            val engine = FakeArti(busyPorts = (17392 until 17392 + 10).toSet())
            val service = service(engine)

            service.start()
            assertEquals(TorServiceStatus.Connecting, service.status.value)
        }

    @Test
    fun aSecondStartWhileBoundReassertsBootstrappingWithoutReinitializing() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)

            service.start()
            service.start()
            assertEquals(1, engine.initializedWith.size)
            assertEquals(1, engine.socksStarts.size)
            assertEquals(TorServiceStatus.Bootstrapping(17392), service.status.value)
        }

    @Test
    fun stopReleasesThePortAndKeepsTheClient() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)

            service.start()
            service.stop()
            assertEquals(TorServiceStatus.Off, service.status.value)
            assertEquals(1, engine.socksStops)
            assertEquals(0, engine.destroys)
            assertEquals(-1, service.bootstrapProgress.value)

            service.start()
            assertEquals(1, engine.initializedWith.size)
        }

    @Test
    fun resetDropsTheClientAndKeepsTheData() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)
            writeGuards(CONFIRMED_GUARDS)

            service.start()
            service.reset()
            assertEquals(TorServiceStatus.Off, service.status.value)
            assertEquals(1, engine.destroys)
            assertTrue(fs.exists(dir / "state" / "state" / "guards.json"))

            service.start()
            assertEquals(2, engine.initializedWith.size)
        }

    @Test
    fun resetWithCleanStateWipesTheDataDir() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)
            writeGuards(CONFIRMED_GUARDS)

            service.start()
            service.resetWithCleanState()
            assertEquals(TorServiceStatus.Off, service.status.value)
            assertEquals(1, engine.destroys)
            assertFalse(fs.exists(dir))
        }

    @Test
    fun aPoisonedGuardSampleIsWipedBeforeInitializing() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)
            writeGuards(ALL_DISABLED_GUARDS)

            service.start()
            assertFalse(fs.exists(dir / "state" / "state" / "guards.json"))
            assertEquals(1, engine.initializedWith.size)
        }

    @Test
    fun aHealthySampleSurvivesStart() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)
            writeGuards(CONFIRMED_GUARDS)

            service.start()
            assertTrue(fs.exists(dir / "state" / "state" / "guards.json"))
        }

    @Test
    fun aConfirmedGuardOnDiskProvesAPriorBootstrap() =
        runTest {
            val service = service(FakeArti())
            assertFalse(service.hasBootstrappedBefore())

            writeGuards(CONFIRMED_GUARDS)
            assertTrue(service.hasBootstrappedBefore())
        }

    @Test
    fun persistentAllGuardsDownRaisesTheSignal() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)
            var signals = 0
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                service.guardsDownSignal.collect { signals++ }
            }

            service.start()
            val log = engine.log!!
            repeat(39) { log("WARN tor_guardmgr: AllGuardsDown { n_accepted: 0, n_rejected: 60 }") }
            log("INFO arti: something else")
            runCurrent()
            assertEquals(0, signals)

            log("WARN tor_guardmgr: AllGuardsDown { n_accepted: 0, n_rejected: 60 }")
            runCurrent()
            assertEquals(1, signals)
        }

    @Test
    fun scatteredAllGuardsDownDoesNotRaiseTheSignal() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)
            var signals = 0
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                service.guardsDownSignal.collect { signals++ }
            }

            service.start()
            val log = engine.log!!
            repeat(3) {
                repeat(30) { log("AllGuardsDown") }
                // Past the 60s window: the count starts over.
                advanceTimeBy(61_000)
            }
            runCurrent()
            assertEquals(0, signals)
        }

    @Test
    fun aClientThatDisappearsLeavesTheStatusForTheWatchdog() =
        runTest {
            val engine = FakeArti()
            val service = service(engine)

            service.start()
            engine.bootstrapped = -1
            advanceTimeBy(600)
            runCurrent()
            assertEquals(TorServiceStatus.Bootstrapping(17392), service.status.value)

            // The watcher stopped: a client coming back is not promoted by a dead poller.
            engine.bootstrapped = 1
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(TorServiceStatus.Bootstrapping(17392), service.status.value)
        }

    companion object {
        private val CONFIRMED_GUARDS =
            """
            { "default": { "guards": [
                { "confirmed_at": "2026-06-17T01:36:40Z", "disabled": null, "unlisted_since": null },
                { "confirmed_at": null, "disabled": null, "unlisted_since": null }
            ] } }
            """.trimIndent()

        private val ALL_DISABLED_GUARDS =
            """
            { "default": { "guards": [
                { "confirmed_at": null, "disabled": { "type": "TooManyIndeterminateFailures" } },
                { "confirmed_at": null, "disabled": { "type": "TooManyIndeterminateFailures" } }
            ] } }
            """.trimIndent()
    }
}

/** In-memory [ArtiBridge]: records every call and answers what the test sets. */
private class FakeArti(
    var initResult: Int = 0,
    private val busyPorts: Set<Int> = emptySet(),
) : ArtiBridge {
    var log: ((String) -> Unit)? = null
    val initializedWith = mutableListOf<String>()
    val socksStarts = mutableListOf<Int>()
    var socksStops = 0
    var destroys = 0
    var bootstrapped = 0
    var progress = 0

    override fun setLogCallback(callback: (String) -> Unit) {
        log = callback
    }

    override fun initialize(dataDir: String): Int {
        initializedWith += dataDir
        return initResult
    }

    override fun isBootstrapped(): Int = bootstrapped

    override fun bootstrapProgressPermille(): Int = progress

    override fun startSocksProxy(port: Int): Int {
        socksStarts += port
        return if (port in busyPorts) -3 else 0
    }

    override fun stopSocksProxy(): Int {
        socksStops++
        return 0
    }

    override fun destroy(): Int {
        destroys++
        return 0
    }
}
