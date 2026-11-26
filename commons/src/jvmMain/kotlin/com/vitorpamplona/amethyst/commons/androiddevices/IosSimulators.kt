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
package com.vitorpamplona.amethyst.commons.androiddevices

import androidx.compose.runtime.Immutable
import com.vitorpamplona.androiddevice.CommandRunner
import com.vitorpamplona.androiddevice.ProcessCommandRunner
import com.vitorpamplona.iossimulator.XcodeTools
import com.vitorpamplona.iossimulator.idb.IdbCompanion
import com.vitorpamplona.iossimulator.simctl.SimDevice
import com.vitorpamplona.iossimulator.simctl.Simctl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** What the iOS side of the device list knows. */
@Immutable
data class SimulatorsState(
    /** Whether simulators can be listed here: a Mac with Xcode (or `AMETHYST_XCRUN`). */
    val xcodeFound: Boolean = false,
    /** Whether idb's companion is installed: the live screen, touches and the UI tree need it. */
    val idbFound: Boolean = false,
    /** Every iPhone and iPad simulator, booted or not. */
    val simulators: List<SimDevice> = emptyList(),
    /** Why the last look failed, as simctl said it. */
    val error: String? = null,
)

/**
 * The iOS simulators on this Mac, for the device list ([AndroidDevices] merges them in): the ones
 * that exist, booting them headless (no Simulator.app window), each workstream's own copy, and one
 * idb companion per booted simulator this app drives.
 *
 * A workstream's copy is a `simctl clone` of the simulator the user picked, named so it is
 * recognised as this app's: it starts with what that simulator holds (apps, logins, settings), is
 * the workstream's alone, and is deleted when it stops. Like the emulator, a simulator can't be
 * copied while it runs: the user sets up the starting state on the simulator itself (from the
 * Devices screen), shuts it down, and every copy starts from there.
 */
class IosSimulators(
    private val tools: XcodeTools = XcodeTools(),
    private val runner: CommandRunner = ProcessCommandRunner,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** Where each companion writes what it prints. */
    private val logDir: File = File(System.getProperty("java.io.tmpdir"), "amethyst-simulators"),
    /** Added to simctl's and the companions' environment: a stand-in's settings, in tests. */
    private val environment: Map<String, String> = emptyMap(),
) {
    /** A copy this app made: the simulator it copies, and the workstream it is for. */
    class Copy(
        val source: String,
        val owner: String,
        val ownerLabel: String?,
    )

    private val simctl: Simctl? get() = tools.xcrun?.let { Simctl(it, runner, environment) }

    private val copies = ConcurrentHashMap<String, Copy>()
    private val bootedHere = ConcurrentHashMap.newKeySet<String>()

    /** Boots in progress, by the udid being booted (or the source while its copy is being made). */
    private val booting = ConcurrentHashMap<String, Pair<String?, String?>>()
    private val companions = ConcurrentHashMap<String, IdbCompanion>()
    private val connections = ConcurrentHashMap<String, SimulatorConnection>()
    private val connectLocks = ConcurrentHashMap<String, Mutex>()
    private val startFailures = ConcurrentHashMap<String, String>()

    @Volatile
    private var swept = false

    val available: Boolean get() = tools.available
    val idbFound: Boolean get() = tools.idbCompanion != null

    fun copyOf(udid: String): Copy? = copies[udid]

    fun isBooting(udid: String): Boolean = booting.containsKey(udid)

    fun startedHere(udid: String): Boolean = udid in bootedHere || copies.containsKey(udid)

    fun failures(): Map<String, String> = startFailures.toMap()

    /** Every iPhone and iPad simulator, from simctl; an empty list (no error) without Xcode. */
    suspend fun list(): SimulatorsState {
        val simctl = simctl ?: return SimulatorsState()
        return try {
            val all = simctl.list().devices.filter { it.isIos && it.isAvailable }
            val udids = all.map { it.udid }.toSet()
            // A simulator deleted elsewhere takes its bookkeeping with it.
            copies.keys.retainAll(udids)
            bootedHere.retainAll(udids)
            connections.keys.removeIf { it !in udids || all.first { d -> d.udid == it }.isShutdown }
            companions.entries.removeIf { (udid, c) ->
                val gone = udid !in udids || !c.isAlive || all.first { d -> d.udid == udid }.isShutdown
                if (gone) c.close()
                gone
            }
            if (!swept) sweepLeftovers(all)
            SimulatorsState(xcodeFound = true, idbFound = idbFound, simulators = all)
        } catch (e: IOException) {
            SimulatorsState(xcodeFound = true, idbFound = idbFound, error = e.message)
        }
    }

    /** Copies an earlier run of the app left (it quit without deleting them), once they are shut down. */
    private fun sweepLeftovers(all: List<SimDevice>) {
        swept = true
        val leftovers = all.filter { it.name.endsWith(COPY_MARK) && it.isShutdown && !copies.containsKey(it.udid) }
        if (leftovers.isEmpty()) return
        scope.launch {
            leftovers.forEach { d ->
                try {
                    simctl?.delete(d.udid)
                } catch (e: IOException) {
                    Log.w(TAG, "Could not delete the leftover copy ${d.name}", e)
                }
            }
        }
    }

    /**
     * Boots [udid] headless and waits until it is ready; returns the udid that runs. With an
     * [owner], it is that owner's own copy (one already running for it is reused): [udid] must
     * then be shut down, since a running simulator can't be copied.
     */
    suspend fun boot(
        udid: String,
        owner: String?,
        ownerLabel: String?,
        bootTimeoutMs: Long = 5 * 60_000,
    ): String {
        val simctl = simctl ?: throw IOException("Xcode isn't installed: simulators need it")
        val all = simctl.list().devices
        val source = all.firstOrNull { it.udid == udid } ?: throw IOException("No simulator $udid")
        if (owner != null) {
            copies.entries.firstOrNull { (_, c) -> c.source == udid && c.owner == owner }?.let { (copy, _) ->
                val state = all.firstOrNull { it.udid == copy }
                if (state?.isBooted == true && !booting.containsKey(copy)) return copy
                if (state != null) return bootAndWait(simctl, copy, owner, ownerLabel, bootTimeoutMs)
            }
        }
        val key = udid
        if (booting.putIfAbsent(key, owner to ownerLabel) != null) throw IOException("${source.name} is already starting")
        startFailures.remove(udid)
        try {
            if (owner == null) {
                bootedHere += udid
                return bootAndWait(simctl, udid, null, null, bootTimeoutMs)
            }
            if (!source.isShutdown) {
                throw IOException(
                    "${source.name} is running (from the Devices screen, Xcode or Simulator.app). Shut it down to let workstreams make their own copies of it; what it holds then becomes their starting point.",
                )
            }
            val copy = simctl.clone(udid, copyName(source.name, ownerLabel))
            copies[copy] = Copy(udid, owner, ownerLabel)
            return bootAndWait(simctl, copy, owner, ownerLabel, bootTimeoutMs)
        } catch (e: IOException) {
            startFailures[udid] = e.message ?: "It didn't boot"
            throw e
        } finally {
            booting.remove(key)
        }
    }

    private suspend fun bootAndWait(
        simctl: Simctl,
        udid: String,
        owner: String?,
        ownerLabel: String?,
        timeoutMs: Long,
    ): String {
        booting[udid] = owner to ownerLabel
        try {
            simctl.bootAndWait(udid, timeoutMs)
            return udid
        } finally {
            booting.remove(udid)
        }
    }

    /** Shuts [udid] down; a copy is deleted with its changes. */
    suspend fun stop(udid: String) {
        val simctl = simctl ?: return
        companions.remove(udid)?.close()
        connections.remove(udid)
        simctl.shutdown(udid)
        bootedHere.remove(udid)
        if (copies.remove(udid) != null) simctl.delete(udid)
    }

    /**
     * Puts a copy back to where it started: a fresh copy of the simulator it came from, in its
     * place. Returns the new copy's udid (simctl gives every copy its own).
     */
    suspend fun reset(udid: String): String {
        val copy = copies[udid] ?: throw IOException("Only a workstream's own copy can be reset: shut this simulator down and erase it in Xcode's Devices window to wipe it")
        stop(udid)
        return boot(copy.source, copy.owner, copy.ownerLabel)
    }

    /** Stops [owner]'s copies: its workstream was removed or its clones freed. */
    fun releaseOwner(owner: String) {
        val mine = copies.filterValues { it.owner == owner }.keys
        if (mine.isEmpty()) return
        scope.launch {
            mine.forEach { udid ->
                try {
                    stop(udid)
                } catch (e: IOException) {
                    Log.w(TAG, "Could not stop the copy $udid", e)
                }
            }
        }
    }

    /**
     * How to drive [udid]: its own idb companion, started on first use and kept while the
     * simulator runs. Throws [IOException] without idb, or when the companion doesn't come up.
     */
    suspend fun connection(udid: String): SimulatorConnection {
        connections[udid]?.let { cached -> if (companions[udid]?.isAlive == true) return cached }
        return connectLocks.computeIfAbsent(udid) { Mutex() }.withLock {
            connections[udid]?.let { cached -> if (companions[udid]?.isAlive == true) return@withLock cached }
            val simctl = simctl ?: throw IOException("Xcode isn't installed: simulators need it")
            val binary = tools.idbCompanion ?: throw IOException(IDB_MISSING)
            companions.remove(udid)?.close()
            if (!logDir.isDirectory && !logDir.mkdirs()) Log.w(TAG) { "Could not create $logDir" }
            val companion = IdbCompanion.start(binary, udid, File(logDir, "idb-$udid.log"), environment = environment)
            try {
                val target = companion.client.describe()
                val connection = SimulatorConnection(udid, companion.client, simctl, target)
                companions[udid] = companion
                connections[udid] = connection
                connection
            } catch (e: CancellationException) {
                companion.close()
                throw e
            } catch (e: IOException) {
                companion.close()
                throw IOException("idb_companion started but didn't answer: ${e.message}", e)
            }
        }
    }

    /**
     * When the app quits: the companions go, the copies are shut down and deleted (they hold
     * nothing to keep), and the simulators booted from here are shut down, as emulators are.
     */
    fun shutdown() {
        companions.values.forEach { runCatching { it.close() } }
        companions.clear()
        val simctl = simctl ?: return
        val going = copies.keys + bootedHere
        if (going.isEmpty()) return
        runBlocking {
            withTimeoutOrNull(SHUTDOWN_WAIT_MS) {
                going.forEach { udid ->
                    runCatching { simctl.shutdown(udid) }
                    if (copies.containsKey(udid)) runCatching { simctl.delete(udid) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "IosSimulators"
        private const val SHUTDOWN_WAIT_MS = 20_000L

        /** What names a copy as this app's: a later run deletes one an earlier run left. */
        const val COPY_MARK = " (Amethyst copy)"

        const val IDB_MISSING = "idb's companion isn't installed: the live screen and touches need it. Install it with Homebrew: brew tap meta/fb && brew trust meta/fb && brew install meta/fb/idb"

        fun copyName(
            source: String,
            ownerLabel: String?,
        ): String = source + (ownerLabel?.takeIf { it.isNotBlank() }?.let { " – ${it.take(40)}" } ?: "") + COPY_MARK
    }
}
