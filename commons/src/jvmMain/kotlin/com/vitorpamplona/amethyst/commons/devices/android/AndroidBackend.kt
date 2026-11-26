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
package com.vitorpamplona.amethyst.commons.devices.android

import com.vitorpamplona.amethyst.commons.claudecode.AndroidSdk
import com.vitorpamplona.amethyst.commons.devices.AndroidSetup
import com.vitorpamplona.amethyst.commons.devices.BackendSnapshot
import com.vitorpamplona.amethyst.commons.devices.DeviceBackend
import com.vitorpamplona.amethyst.commons.devices.DeviceInfo
import com.vitorpamplona.amethyst.commons.devices.DeviceKind
import com.vitorpamplona.amethyst.commons.devices.DevicePlatform
import com.vitorpamplona.amethyst.commons.devices.DeviceStatus
import com.vitorpamplona.amethyst.commons.devices.StartOptions
import com.vitorpamplona.amethyst.commons.devices.StartableDevice
import com.vitorpamplona.androiddevice.AndroidSdkTools
import com.vitorpamplona.androiddevice.adb.Adb
import com.vitorpamplona.androiddevice.adb.AdbDevice
import com.vitorpamplona.androiddevice.avd.AvdInfo
import com.vitorpamplona.androiddevice.avd.Avds
import com.vitorpamplona.androiddevice.emulator.EmulatorController
import com.vitorpamplona.androiddevice.emulator.EmulatorDiscovery
import com.vitorpamplona.androiddevice.emulator.EmulatorJwtAuth
import com.vitorpamplona.androiddevice.emulator.EmulatorLauncher
import com.vitorpamplona.androiddevice.emulator.LaunchOptions
import com.vitorpamplona.androiddevice.emulator.LaunchedEmulator
import com.vitorpamplona.androiddevice.emulator.RunningEmulator
import com.vitorpamplona.devicecore.CommandRunner
import com.vitorpamplona.devicecore.ProcessCommandRunner
import com.vitorpamplona.devicecore.grpc.GrpcChannel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** An emulator this app is starting, before adb lists it. */
private class Starting(
    val avd: String,
    val owner: String?,
    val ownerLabel: String?,
) {
    @Volatile
    var serial: String? = null
}

/**
 * The Android devices on this computer: the phones and emulators adb sees, the AVDs that can be
 * started, and the emulators this app started (stopped again when it quits). A workstream's copy
 * of an AVD is a read-only instance that boots from the AVD's saved state and drops its changes.
 */
class AndroidBackend(
    private val sdkHome: () -> File? = { AndroidSdk.home() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val runner: CommandRunner = ProcessCommandRunner,
    private val discoveryDirs: () -> List<File> = { EmulatorDiscovery.directories() },
    private val avdHome: () -> File = { Avds.home() },
    /** Where an emulator this app starts writes what it prints. */
    private val logDir: File = File(System.getProperty("java.io.tmpdir"), "amethyst-emulators"),
    private val bootTimeoutMs: Long = 5 * 60_000,
) : DeviceBackend {
    override val platform = DevicePlatform.ANDROID

    private val launched = ConcurrentHashMap<String, LaunchedEmulator>()

    /** Starts in progress, by AVD and owner: two workstreams may each start a copy of one AVD. */
    private val starting = ConcurrentHashMap<String, Starting>()

    /** The emulators that are a workstream's own copy: serial to (owner, label). */
    private val owners = ConcurrentHashMap<String, Pair<String, String?>>()
    private val booted = ConcurrentHashMap.newKeySet<String>()
    private val connections = ConcurrentHashMap<String, AndroidConnection>()
    private val failures = ConcurrentHashMap<String, String>()
    private val refreshLock = Mutex()

    @Volatile
    private var tools: AndroidSdkTools? = null

    @Volatile
    private var cachedAvds: Pair<List<AvdInfo>, Long>? = null

    /** The last look, for a start to find what already runs without asking adb again. */
    @Volatile
    private var last: List<DeviceInfo> = emptyList()

    /**
     * The SDK's programs, looked up again while one is missing: the panel tells the user to install
     * platform-tools or the emulator, and should see them once they have.
     */
    private fun tools(): AndroidSdkTools {
        tools?.let { if (it.adb != null && it.emulator != null) return it }
        return AndroidSdkTools(sdkHome()).also { tools = it }
    }

    /** The SDK's programs: `adb`, the emulator, `aapt2`. */
    fun sdkTools(): AndroidSdkTools = tools()

    fun adb(): Adb? = tools().adb?.let { Adb(it, runner) }

    /** An adb serial is never a udid (8-4-4-4-12 hex): those are simulators'. */
    override fun claims(serial: String): Boolean = !UDID.matches(serial)

    /** Looks again: adb's list, the running emulators' discovery files, the AVD folder. */
    override suspend fun refresh(): BackendSnapshot =
        refreshLock.withLock {
            withContext(Dispatchers.IO) {
                val sdk = tools()
                val adb = sdk.adb?.let { Adb(it, runner) }
                // The AVD folder changes when the user makes one: no need to read every .ini every poll.
                val avds =
                    cachedAvds?.takeIf { System.currentTimeMillis() - it.second < AVD_TTL_MS }?.first
                        ?: Avds.list(avdHome()).also { cachedAvds = it to System.currentTimeMillis() }
                val emulators = EmulatorDiscovery.running(discoveryDirs())
                var error: String? = null
                val adbDevices =
                    if (adb == null) {
                        emptyList()
                    } else {
                        try {
                            adb.devices()
                        } catch (e: IOException) {
                            error = e.message
                            emptyList()
                        }
                    }
                // Booted once is booted until it goes: the property is asked only while it boots,
                // of every booting emulator at once (each is an adb round trip).
                val booting = adbDevices.filter { it.isOnline && it.isEmulator && it.serial !in booted }
                if (adb != null && booting.isNotEmpty()) {
                    coroutineScope {
                        booting
                            .map { d -> async { d.serial.takeIf { runCatching { adb.bootCompleted(it) }.getOrDefault(false) } } }
                            .awaitAll()
                            .filterNotNull()
                            .forEach { booted += it }
                    }
                }
                val seen = adbDevices.map { it.serial }.toSet()
                booted.retainAll(seen)
                connections.keys.retainAll(seen)
                adbOnlyUntil.keys.retainAll(seen)
                // Keys given to emulators that have gone: their folders went with them.
                jwtAuths.entries.removeIf { (dir, auth) -> !File(dir).isDirectory.also { if (!it) auth.close() } }
                // An emulator that went away on its own takes its process with it.
                launched.entries.removeIf { (_, l) -> !l.process.isAlive }
                // Ports are reused: a serial gone from adb (and not one starting here) leaves no owner
                // for the next emulator that gets it.
                owners.keys.removeIf { it !in seen && !launched.containsKey(it) }
                val devices = merge(adbDevices, emulators, avds)
                last = devices
                BackendSnapshot(
                    devices = devices,
                    startable = avds.map { it.startable() },
                    setup = AndroidSetup(sdk.sdkDir, adb != null, sdk.emulator != null, error),
                    startFailures = failures.toMap(),
                )
            }
        }

    private fun AvdInfo.startable() =
        StartableDevice(
            id = name,
            platform = DevicePlatform.ANDROID,
            name = displayName,
            detail = listOfNotNull(apiLevel?.let { "API $it" }, abi).joinToString(" · "),
            rank = "%04d".format(apiLevel ?: 0),
            isTablet = displayName.contains("tablet", ignoreCase = true),
        )

    private fun merge(
        adbDevices: List<AdbDevice>,
        emulators: List<RunningEmulator>,
        avds: List<AvdInfo>,
    ): List<DeviceInfo> {
        val byPort = emulators.associateBy { it.consolePort }
        val listed =
            adbDevices.map { d ->
                val emulator = d.consolePort?.let { byPort[it] }
                val avd = emulator?.avdName?.let { name -> avds.firstOrNull { it.name == name } }
                val kind = if (d.isEmulator) DeviceKind.EMULATOR else DeviceKind.PHYSICAL
                val ours = launched.containsKey(d.serial)
                DeviceInfo(
                    serial = d.serial,
                    // An emulator nobody's discovery file names: its serial says more than adb's
                    // "sdk_gphone64_x86_64" model.
                    name = avd?.displayName ?: emulator?.avdDisplayName ?: emulator?.avdName?.replace('_', ' ') ?: (if (d.isEmulator) d.serial else d.model) ?: d.serial,
                    platform = DevicePlatform.ANDROID,
                    kind = kind,
                    status =
                        when {
                            d.state == "device" && (kind == DeviceKind.PHYSICAL || d.serial in booted) -> DeviceStatus.ONLINE
                            d.state == "device" -> DeviceStatus.BOOTING
                            d.state == "unauthorized" || d.state == "authorizing" -> DeviceStatus.UNAUTHORIZED
                            d.state.startsWith("no permissions") -> DeviceStatus.NO_PERMISSION
                            else -> DeviceStatus.OFFLINE
                        },
                    source = emulator?.avdName,
                    osVersion = avd?.apiLevel?.let { "API $it" },
                    liveStream = emulator != null && (emulator.grpcReachable || ours),
                    startedHere = ours,
                    // Leave for phones is the user's: Devices sets it.
                    agentAllowed = kind == DeviceKind.EMULATOR,
                    owner = owners[d.serial]?.first,
                    ownerLabel = owners[d.serial]?.second,
                    // Over the emulator's gRPC endpoint, which lets only a token holder load a
                    // snapshot: an emulator this app started, not one Android Studio did.
                    canReset = ours,
                    canColdBoot = emulator?.avdName != null,
                )
            }
        // Starting, not yet in adb's list.
        val pending =
            starting.values
                .filter { s -> listed.none { it.serial == s.serial } }
                .map { s ->
                    DeviceInfo(
                        serial = s.serial ?: "avd:${s.avd}@${s.owner.orEmpty()}",
                        name = avds.firstOrNull { it.name == s.avd }?.displayName ?: s.avd.replace('_', ' '),
                        platform = DevicePlatform.ANDROID,
                        kind = DeviceKind.EMULATOR,
                        status = DeviceStatus.STARTING,
                        source = s.avd,
                        startedHere = true,
                        agentAllowed = true,
                        owner = s.owner,
                        ownerLabel = s.ownerLabel,
                    )
                }
        return listed + pending
    }

    /**
     * Starts the AVD [id] and returns its serial once Android has booted. Throws [IOException] when
     * the SDK has no emulator, or it exits or doesn't boot in time.
     *
     * With an [owner] (a workstream, or a chat outside one), it is that owner's own copy: a
     * read-only instance that boots from the AVD's saved state and drops its changes when it stops,
     * so workstreams working at once don't share a device. One already running for [owner] is
     * reused. Without one, it is the AVD itself, shared, saving its state when it shuts down.
     */
    override suspend fun start(
        id: String,
        options: StartOptions,
        owner: String?,
        ownerLabel: String?,
    ): String {
        val avd = id
        val emulator = tools().emulator ?: throw IOException("The Android SDK's emulator isn't installed")
        refresh()
        last
            .firstOrNull { it.source == avd && it.owner == owner && it.status != DeviceStatus.OFFLINE && it.status != DeviceStatus.STARTING }
            ?.let { running -> return if (running.status == DeviceStatus.ONLINE) running.serial else waitForBoot(running.serial, bootTimeoutMs) }
        val key = owner?.let { "$avd@$it" } ?: avd
        val entry = Starting(avd, owner, ownerLabel)
        if (starting.putIfAbsent(key, entry) != null) throw IOException("$avd is already starting")
        failures.remove(avd)
        try {
            if (!logDir.isDirectory && !logDir.mkdirs()) Log.w(TAG) { "Could not create $logDir" }
            val launchOptions = LaunchOptions(coldBoot = options.coldBoot, wipeData = options.wipeData, readOnly = owner != null)
            val log = File(logDir, "${key.replace(Regex("[^A-Za-z0-9_.@-]"), "_")}.log")
            val launch =
                try {
                    EmulatorLauncher(emulator, runner, discoveryDirs).launch(avd, launchOptions, log)
                } catch (e: IOException) {
                    throw explainInstanceLock(e, avd, owner)
                }
            val serial = launch.info.serial
            launched[serial] = launch
            if (owner != null) owners[serial] = owner to ownerLabel
            entry.serial = serial
            return waitForBoot(serial, bootTimeoutMs)
        } catch (e: IOException) {
            failures[avd] = e.message ?: avd
            throw e
        } finally {
            starting.remove(key)
        }
    }

    /**
     * The emulator runs several instances of one AVD only when every one is read-only: a
     * workstream's copy can't start while the AVD runs with its saved state, nor the other way.
     */
    private fun explainInstanceLock(
        e: IOException,
        avd: String,
        owner: String?,
    ): IOException {
        if (e.message?.contains("Another emulator instance is running", ignoreCase = true) != true) return e
        val name = cachedAvds?.first?.firstOrNull { it.name == avd }?.displayName ?: avd
        return IOException(
            if (owner != null) {
                "$name is running with its saved state (from the Devices screen or Android Studio). Shut it down to let workstreams run their own copies; what it saves becomes their starting point."
            } else {
                "Workstreams are running their own copies of $name. Stop them first, or use one of them."
            },
            e,
        )
    }

    suspend fun waitForBoot(
        serial: String,
        timeoutMs: Long,
    ): String {
        val adb = adb() ?: throw IOException("adb isn't installed")
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < timeoutMs) {
            if (adb.bootCompleted(serial)) {
                booted += serial
                return serial
            }
            val launch = launched[serial]
            if (launch != null && !launch.process.isAlive) throw IOException("The emulator stopped while it booted")
            delay(BOOT_POLL_MS)
        }
        throw IOException("$serial didn't finish booting in ${timeoutMs / 1000}s")
    }

    /** Shuts an emulator down (saving its quick-boot state, unless it is a copy). */
    override suspend fun stop(serial: String) {
        if (last.firstOrNull { it.serial == serial }?.kind == DeviceKind.PHYSICAL) throw IOException("A phone can't be stopped from here")
        val launch = launched.remove(serial)
        try {
            adb()?.emuKill(serial)
        } catch (e: IOException) {
            Log.w(TAG, "adb emu kill $serial failed", e)
        }
        if (launch != null) {
            withContext(Dispatchers.IO) {
                if (!launch.process.waitFor(STOP_WAIT_S, TimeUnit.SECONDS)) launch.process.destroy()
            }
        }
        connections.remove(serial)
        owners.remove(serial)
    }

    override fun releaseOwner(owner: String) {
        val mine = owners.filterValues { it.first == owner }.keys
        if (mine.isEmpty()) return
        scope.launch {
            mine.forEach { serial ->
                try {
                    stop(serial)
                } catch (e: IOException) {
                    Log.w(TAG, "Could not stop $serial", e)
                }
            }
        }
    }

    /**
     * Puts an emulator back to the state the AVD last saved (its quick-boot snapshot): a
     * workstream's copy to where it started. Needs the emulator's gRPC endpoint (one this app started).
     */
    override suspend fun reset(serial: String): String {
        // One Android Studio started answers this app with a signed token whose issuer may not load
        // snapshots (PERMISSION_DENIED), so it isn't asked.
        if (!launched.containsKey(serial)) throw IOException("Only an emulator started from here can be reset: restart it to drop its changes")
        val controller = androidConnection(serial).emulator ?: throw IOException("Its control port didn't answer: restart it to drop its changes")
        controller.loadSnapshot()
        // The loaded state drops adb's connection to the guest for a moment ("device offline"):
        // back only once adb reaches it again, so the next command doesn't fail.
        val adb = adb() ?: return serial
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < RESET_RECONNECT_MS) {
            // adb itself may stumble while the guest reconnects: the reset is done, so keep looking.
            val back =
                try {
                    adb.devices().any { it.serial == serial && it.isOnline } && adb.bootCompleted(serial)
                } catch (e: IOException) {
                    Log.d(TAG, "adb stumbled while an emulator reconnected", e)
                    false
                }
            if (back) break
            delay(RESET_POLL_MS)
        }
        booted += serial
        return serial
    }

    override suspend fun connection(serial: String): AndroidConnection = androidConnection(serial)

    /**
     * How to drive [serial], with the emulator's gRPC endpoint when it opens (checked once with a
     * status call; an emulator that refuses it is driven through adb).
     */
    suspend fun androidConnection(serial: String): AndroidConnection {
        connections[serial]?.let { cached ->
            val retryAt = adbOnlyUntil[serial]
            if (cached.emulator != null || retryAt == null || System.currentTimeMillis() < retryAt) return cached
        }
        val adb = adb() ?: throw IOException("adb isn't installed: install the Android SDK's platform-tools")
        val controller = emulatorController(serial)
        val connection = AndroidConnection(serial, adb, controller)
        // An emulator whose control port should answer but didn't (still coming up, or Android
        // Studio's not yet taking this app's key) is asked again after a pause, instead of being
        // stuck on adb screenshots for its whole life.
        if (controller == null && (launched.containsKey(serial) || last.firstOrNull { it.serial == serial }?.liveStream == true)) {
            adbOnlyUntil[serial] = System.currentTimeMillis() + GRPC_RETRY_MS
        } else {
            adbOnlyUntil.remove(serial)
        }
        connections[serial] = connection
        return connection
    }

    /** Emulators driven through adb for now, though their control port should open: when to try it again. */
    private val adbOnlyUntil = ConcurrentHashMap<String, Long>()

    private suspend fun emulatorController(serial: String): EmulatorController? {
        if (!serial.startsWith("emulator-")) return null
        val port = serial.removePrefix("emulator-").toIntOrNull() ?: return null
        val info = withContext(Dispatchers.IO) { EmulatorDiscovery.running(discoveryDirs()) }.firstOrNull { it.consolePort == port } ?: return null
        val grpcPort = info.grpcPort ?: return null
        val ours = launched.containsKey(serial)
        if (!ours && !info.grpcReachable) return null
        val token = info.grpcToken ?: if (ours) consoleToken() else null
        val jwt = if (token == null && info.usesJwt) info.jwksDir?.let { jwtAuth(it, info.activeJwks) } else null
        val controller = EmulatorController(GrpcChannel("127.0.0.1", grpcPort, token, authorization = jwt?.let { auth -> auth::authorization }))
        return try {
            // Puts the key in the emulator's folder and waits for it to take it, off the caller's thread.
            jwt?.let { withContext(Dispatchers.IO) { it.register() } }
            controller.status()
            controller
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "The emulator $serial refused its gRPC endpoint; using adb", e)
            null
        }
    }

    /** One key per emulator key folder (Android Studio's emulators), taken back out when the app quits. */
    private val jwtAuths = ConcurrentHashMap<String, EmulatorJwtAuth>()

    private fun jwtAuth(
        jwksDir: String,
        activeJwks: String?,
    ): EmulatorJwtAuth =
        jwtAuths.computeIfAbsent(jwksDir) {
            if (jwtAuths.isEmpty()) Runtime.getRuntime().addShutdownHook(Thread { jwtAuths.values.forEach { it.close() } })
            EmulatorJwtAuth(Path.of(jwksDir), activeJwks?.let { Path.of(it) })
        }

    /** The emulator console's token, which `-grpc-use-token` also takes. */
    private fun consoleToken(): String? =
        try {
            File(System.getProperty("user.home"), ".emulator_console_auth_token")
                .takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (_: IOException) {
            null
        }

    /** Stops the emulators this app started. */
    override fun shutdown() {
        launched.values.forEach { it.process.destroy() }
        launched.clear()
    }

    companion object {
        private const val TAG = "AndroidBackend"
        private const val BOOT_POLL_MS = 2_000L
        private const val RESET_RECONNECT_MS = 30_000L
        private const val RESET_POLL_MS = 300L
        private const val GRPC_RETRY_MS = 5_000L
        private const val STOP_WAIT_S = 20L
        private const val AVD_TTL_MS = 10_000L

        private val UDID = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")
    }
}
