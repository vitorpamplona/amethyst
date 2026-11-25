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
import com.vitorpamplona.amethyst.commons.claudecode.AndroidSdk
import com.vitorpamplona.androiddevice.AndroidSdkTools
import com.vitorpamplona.androiddevice.CommandRunner
import com.vitorpamplona.androiddevice.ProcessCommandRunner
import com.vitorpamplona.androiddevice.adb.Adb
import com.vitorpamplona.androiddevice.adb.AdbDevice
import com.vitorpamplona.androiddevice.adb.DeviceCrash
import com.vitorpamplona.androiddevice.avd.AvdInfo
import com.vitorpamplona.androiddevice.avd.Avds
import com.vitorpamplona.androiddevice.emulator.EmulatorController
import com.vitorpamplona.androiddevice.emulator.EmulatorDiscovery
import com.vitorpamplona.androiddevice.emulator.EmulatorJwtAuth
import com.vitorpamplona.androiddevice.emulator.EmulatorLauncher
import com.vitorpamplona.androiddevice.emulator.LaunchOptions
import com.vitorpamplona.androiddevice.emulator.LaunchedEmulator
import com.vitorpamplona.androiddevice.emulator.RunningEmulator
import com.vitorpamplona.androiddevice.grpc.GrpcChannel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class DeviceKind { EMULATOR, PHYSICAL }

enum class DeviceStatus {
    /** An emulator this app is starting, not yet seen by adb. */
    STARTING,

    /** adb sees it, Android hasn't finished booting. */
    BOOTING,
    ONLINE,
    OFFLINE,

    /** A phone that hasn't accepted this computer's adb key yet: the user taps Allow on it. */
    UNAUTHORIZED,

    /** adb can't open the USB device (Linux udev rules). */
    NO_PERMISSION,
}

/** One device the panel lists. */
@Immutable
data class DeviceInfo(
    val serial: String,
    val name: String,
    val kind: DeviceKind,
    val status: DeviceStatus,
    val avdName: String? = null,
    /** Whether the emulator's own stream feeds the live view (fast); else adb screenshots. */
    val liveStream: Boolean = false,
    val startedHere: Boolean = false,
    /** Whether agents may look at and drive it: every emulator; a phone once the user allows it. */
    val agentAllowed: Boolean = false,
    /**
     * The workstream (or chat) whose own copy this emulator is ([AndroidDevices.start] with an
     * owner): a read-only instance that drops its changes when it stops. Null for a shared device.
     */
    val owner: String? = null,
    /** What to call [owner] in the picker: the workstream's title. */
    val ownerLabel: String? = null,
) {
    val isOnline: Boolean get() = status == DeviceStatus.ONLINE
}

@Immutable
data class DevicesState(
    val sdkDir: File? = null,
    val adbFound: Boolean = false,
    val emulatorFound: Boolean = false,
    val devices: List<DeviceInfo> = emptyList(),
    val avds: List<AvdInfo> = emptyList(),
    /** Whether the first look has finished: before it, "no devices" isn't known yet. */
    val loaded: Boolean = false,
    /** Why the last look failed, as adb said it. */
    val error: String? = null,
    /** AVDs this app is starting, and why the last start of each failed. */
    val startFailures: Map<String, String> = emptyMap(),
)

/** What an agent just did on a device, for the panel to show where it touched. */
@Immutable
data class DeviceActivity(
    val serial: String,
    val kind: Kind,
    /** Where, as a fraction of the screen as shown (0..1); null for what has no place (a key). */
    val x: Float? = null,
    val y: Float? = null,
    val toX: Float? = null,
    val toY: Float? = null,
    val label: String = "",
    val at: Long = System.currentTimeMillis(),
) {
    enum class Kind { TAP, LONG_PRESS, SWIPE, KEY, TYPE, LOOK, APP }
}

/** A crash or ANR found in a device's log after the app started watching it; [seq] orders them across devices. */
@Immutable
data class SeenCrash(
    val seq: Long,
    val serial: String,
    val crash: DeviceCrash,
    /** When the app found it, in epoch milliseconds. */
    val at: Long = System.currentTimeMillis(),
)

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
 * The Android devices on this computer, for the device panel and the agents' device tools: the
 * phones and emulators adb sees, the AVDs that can be started, and the emulators this app started
 * (stopped again when it quits). One per process ([shared]); it looks for devices only while a
 * screen [watch]es, or when a tool asks.
 */
class AndroidDevices(
    private val sdkHome: () -> File? = { AndroidSdk.home() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val runner: CommandRunner = ProcessCommandRunner,
    private val discoveryDirs: () -> List<File> = { EmulatorDiscovery.directories() },
    private val avdHome: () -> File = { Avds.home() },
    /** Where an emulator this app starts writes what it prints. */
    private val logDir: File = File(System.getProperty("java.io.tmpdir"), "amethyst-emulators"),
    private val pollMs: Long = 2_000,
) {
    private val _state = MutableStateFlow(DevicesState())
    val state: StateFlow<DevicesState> = _state.asStateFlow()

    private val _activity = MutableSharedFlow<DeviceActivity>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** What agents do on the devices, as it happens. */
    val activity: SharedFlow<DeviceActivity> = _activity.asSharedFlow()

    private val _selection = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The device each chat (by key) works on, as the user or its agent picked it. */
    val selection: StateFlow<Map<String, String>> = _selection.asStateFlow()

    /** Phones the user let agents use; emulators need no leave. */
    private val allowed = MutableStateFlow<Set<String>>(emptySet())

    private val launched = ConcurrentHashMap<String, LaunchedEmulator>()

    /** Starts in progress, by AVD and owner: two workstreams may each start a copy of one AVD. */
    private val starting = ConcurrentHashMap<String, Starting>()

    /** The emulators that are a workstream's own copy: serial to (owner, label). */
    private val owners = ConcurrentHashMap<String, Pair<String, String?>>()
    private val booted = ConcurrentHashMap.newKeySet<String>()
    private val connections = ConcurrentHashMap<String, DeviceConnection>()
    private val refreshLock = Mutex()
    private val watchers = AtomicInteger(0)

    @Volatile
    private var poller: Job? = null

    @Volatile
    private var tools: AndroidSdkTools? = null

    @Volatile
    private var cachedAvds: Pair<List<AvdInfo>, Long>? = null

    @Volatile
    private var refreshedAt = 0L

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

    /**
     * Keeps the list current while the caller shows it; call what it returns when it no longer
     * does. Looks right away, then every [pollMs].
     */
    fun watch(): () -> Unit {
        if (watchers.incrementAndGet() == 1) {
            poller =
                scope.launch {
                    while (true) {
                        refresh()
                        delay(pollMs)
                    }
                }
        }
        var released = false
        return {
            if (!released) {
                released = true
                if (watchers.decrementAndGet() == 0) {
                    poller?.cancel()
                    poller = null
                }
            }
        }
    }

    /** Looks again: adb's list, the running emulators' discovery files, the AVD folder. */
    suspend fun refresh() {
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
                // Booted once is booted until it goes: the property is asked only while it boots.
                adbDevices.filter { it.isOnline && it.isEmulator && it.serial !in booted }.forEach { d ->
                    if (adb?.bootCompleted(d.serial) == true) booted += d.serial
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
                // or crash bookkeeping for the next emulator that gets it.
                owners.keys.removeIf { it !in seen && !launched.containsKey(it) }
                crashesSeen.keys.retainAll(seen)
                refreshedAt = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        sdkDir = sdk.sdkDir,
                        adbFound = adb != null,
                        emulatorFound = sdk.emulator != null,
                        devices = merge(adbDevices, emulators, avds),
                        avds = avds,
                        loaded = true,
                        error = error,
                    )
                }
            }
        }
    }

    private fun merge(
        adbDevices: List<AdbDevice>,
        emulators: List<RunningEmulator>,
        avds: List<AvdInfo>,
    ): List<DeviceInfo> {
        val byPort = emulators.associateBy { it.consolePort }
        val allowedNow = allowed.value
        val listed =
            adbDevices.map { d ->
                val emulator = d.consolePort?.let { byPort[it] }
                val avd = emulator?.avdName?.let { name -> avds.firstOrNull { it.name == name } }
                val kind = if (d.isEmulator) DeviceKind.EMULATOR else DeviceKind.PHYSICAL
                DeviceInfo(
                    serial = d.serial,
                    // An emulator nobody's discovery file names: its serial says more than adb's
                    // "sdk_gphone64_x86_64" model.
                    name = avd?.displayName ?: emulator?.avdDisplayName ?: emulator?.avdName?.replace('_', ' ') ?: (if (d.isEmulator) d.serial else d.model) ?: d.serial,
                    kind = kind,
                    status =
                        when {
                            d.state == "device" && (kind == DeviceKind.PHYSICAL || d.serial in booted) -> DeviceStatus.ONLINE
                            d.state == "device" -> DeviceStatus.BOOTING
                            d.state == "unauthorized" || d.state == "authorizing" -> DeviceStatus.UNAUTHORIZED
                            d.state.startsWith("no permissions") -> DeviceStatus.NO_PERMISSION
                            else -> DeviceStatus.OFFLINE
                        },
                    avdName = emulator?.avdName,
                    liveStream = emulator != null && (emulator.grpcReachable || launched.containsKey(d.serial)),
                    startedHere = launched.containsKey(d.serial),
                    agentAllowed = kind == DeviceKind.EMULATOR || d.serial in allowedNow,
                    owner = owners[d.serial]?.first,
                    ownerLabel = owners[d.serial]?.second,
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
                        kind = DeviceKind.EMULATOR,
                        status = DeviceStatus.STARTING,
                        avdName = s.avd,
                        startedHere = true,
                        agentAllowed = true,
                        owner = s.owner,
                        ownerLabel = s.ownerLabel,
                    )
                }
        return listed + pending
    }

    /** [refresh], unless the last one is younger than [maxAgeMs]: a burst of tool calls asks adb once. */
    suspend fun refreshIfStale(maxAgeMs: Long = 1_000) {
        if (System.currentTimeMillis() - refreshedAt > maxAgeMs) refresh()
    }

    fun device(serial: String): DeviceInfo? = _state.value.devices.firstOrNull { it.serial == serial }

    /**
     * Starts [avd] and returns its serial once Android has booted. Throws [IOException] when the
     * SDK has no emulator, or it exits or doesn't boot in [bootTimeoutMs].
     *
     * With an [owner] (a workstream, or a chat outside one), it is that owner's own copy: a
     * read-only instance that boots from the AVD's saved state and drops its changes when it stops,
     * so workstreams working at once don't share a device. One already running for [owner] is
     * reused. Without one, it is the AVD itself, shared, saving its state when it shuts down.
     */
    suspend fun start(
        avd: String,
        options: LaunchOptions = LaunchOptions(),
        bootTimeoutMs: Long = 5 * 60_000,
        owner: String? = null,
        ownerLabel: String? = null,
    ): String {
        val emulator = tools().emulator ?: throw IOException("The Android SDK's emulator isn't installed")
        refresh()
        _state.value.devices
            .firstOrNull { it.avdName == avd && it.owner == owner && it.status != DeviceStatus.OFFLINE && it.status != DeviceStatus.STARTING }
            ?.let { running -> return if (running.status == DeviceStatus.ONLINE) running.serial else waitForBoot(running.serial, bootTimeoutMs) }
        val key = owner?.let { "$avd@$it" } ?: avd
        val entry = Starting(avd, owner, ownerLabel)
        if (starting.putIfAbsent(key, entry) != null) throw IOException("$avd is already starting")
        _state.update { it.copy(startFailures = it.startFailures - avd) }
        try {
            refresh()
            if (!logDir.isDirectory && !logDir.mkdirs()) Log.w(TAG) { "Could not create $logDir" }
            val launchOptions = if (owner != null) options.copy(readOnly = true) else options
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
            refresh()
            return waitForBoot(serial, bootTimeoutMs)
        } catch (e: IOException) {
            _state.update { it.copy(startFailures = it.startFailures + (avd to (e.message ?: avd))) }
            throw e
        } finally {
            starting.remove(key)
            refresh()
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
        val name =
            _state.value.avds
                .firstOrNull { it.name == avd }
                ?.displayName ?: avd
        return IOException(
            if (owner != null) {
                "$name is running with its saved state (from the Devices screen or Android Studio). Shut it down to let workstreams run their own copies; what it saves becomes their starting point."
            } else {
                "Workstreams are running their own copies of $name. Stop them first, or use one of them."
            },
            e,
        )
    }

    /** [start] in the background, for a button; a failure shows in [DevicesState.startFailures]. */
    fun startInBackground(
        avd: String,
        options: LaunchOptions = LaunchOptions(),
        owner: String? = null,
        ownerLabel: String? = null,
        onBooted: (String) -> Unit = {},
    ) {
        scope.launch {
            try {
                onBooted(start(avd, options, owner = owner, ownerLabel = ownerLabel))
            } catch (e: IOException) {
                Log.w(TAG, "Could not start $avd", e)
            }
        }
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
                refresh()
                return serial
            }
            val launch = launched[serial]
            if (launch != null && !launch.process.isAlive) throw IOException("The emulator stopped while it booted")
            delay(BOOT_POLL_MS)
        }
        throw IOException("$serial didn't finish booting in ${timeoutMs / 1000}s")
    }

    /** Shuts an emulator down (saving its quick-boot state), or disconnects nothing for a phone. */
    suspend fun stop(serial: String) {
        val device = device(serial)
        if (device?.kind == DeviceKind.PHYSICAL) throw IOException("A phone can't be stopped from here")
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
        refresh()
    }

    /** Stops the copies [owner] started: its workstream was removed or its clones freed. */
    fun releaseOwner(owner: String) {
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
    suspend fun reset(serial: String) {
        // One Android Studio started answers this app with a signed token whose issuer may not load
        // snapshots (PERMISSION_DENIED), so it isn't asked.
        if (!launched.containsKey(serial)) throw IOException("Only an emulator started from here can be reset: restart it to drop its changes")
        val controller = connection(serial).emulator ?: throw IOException("Its control port didn't answer: restart it to drop its changes")
        controller.loadSnapshot()
        // The loaded state drops adb's connection to the guest for a moment ("device offline"):
        // back only once adb reaches it again, so the next command doesn't fail.
        val adb = adb() ?: return
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
    }

    /**
     * Shuts [serial] down and starts its AVD again (a cold boot with [options]), for the same owner:
     * one after the other, waiting for the old one to leave adb's list so the start doesn't find it.
     */
    fun restartInBackground(
        serial: String,
        options: LaunchOptions,
        onBooted: (String) -> Unit = {},
    ) {
        val device = device(serial) ?: return
        val avd = device.avdName ?: return
        scope.launch {
            try {
                stop(serial)
                val until = System.currentTimeMillis() + STOP_WAIT_S * 1000
                while (device(serial) != null && System.currentTimeMillis() < until) {
                    delay(BOOT_POLL_MS / 4)
                    refresh()
                }
                onBooted(start(avd, options, owner = device.owner, ownerLabel = device.ownerLabel))
            } catch (e: IOException) {
                Log.w(TAG, "Could not restart $serial", e)
            }
        }
    }

    fun stopInBackground(serial: String) {
        scope.launch {
            try {
                stop(serial)
            } catch (e: IOException) {
                Log.w(TAG, "Could not stop $serial", e)
            }
        }
    }

    /**
     * How to drive [serial], with the emulator's gRPC endpoint when it opens (checked once with a
     * status call; an emulator that refuses it is driven through adb).
     */
    suspend fun connection(serial: String): DeviceConnection {
        connections[serial]?.let { cached ->
            val retryAt = adbOnlyUntil[serial]
            if (cached.emulator != null || retryAt == null || System.currentTimeMillis() < retryAt) return cached
        }
        val adb = adb() ?: throw IOException("adb isn't installed: install the Android SDK's platform-tools")
        val controller = emulatorController(serial)
        val connection = DeviceConnection(serial, adb, controller)
        // An emulator whose control port should answer but didn't (still coming up, or Android
        // Studio's not yet taking this app's key) is asked again after a pause, instead of being
        // stuck on adb screenshots for its whole life.
        if (controller == null && (launched.containsKey(serial) || device(serial)?.liveStream == true)) {
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

    fun select(
        chatKey: String,
        serial: String,
    ) = _selection.update { it + (chatKey to serial) }

    /**
     * The device [chatKey] works on: the one picked for it if still there, else its own copy, else
     * the only one online that isn't another workstream's copy.
     */
    fun selected(chatKey: String?): DeviceInfo? {
        val devices = _state.value.devices
        chatKey?.let { key -> _selection.value[key]?.let { serial -> devices.firstOrNull { it.serial == serial }?.let { return it } } }
        chatKey?.let { key -> devices.firstOrNull { it.owner == key && it.status != DeviceStatus.OFFLINE }?.let { return it } }
        return devices.filter { it.isOnline && (it.owner == null || it.owner == chatKey) }.singleOrNull()
    }

    private val crashLock = Mutex()
    private val crashesSeen = ConcurrentHashMap<String, MutableSet<String>>()
    private val crashSeq = AtomicLong(0)
    private val _crashes = MutableStateFlow<List<SeenCrash>>(emptyList())

    /**
     * The crashes and ANRs found since the app first looked at each device's log, oldest first.
     * Each reader keeps its own place ([crashCursor]): the panel and an agent both hear of each one.
     */
    val crashes: StateFlow<List<SeenCrash>> = _crashes.asStateFlow()

    /** Where the crash list ends now: a reader that starts here hears only of later crashes. */
    fun crashCursor(): Long = crashSeq.get()

    /**
     * Reads [serial]'s crash log and adds what is new to [crashes]. The first read of a device
     * only notes what is there: those crashes happened before anyone was watching.
     */
    suspend fun pollCrashes(serial: String): List<SeenCrash> =
        crashLock.withLock {
            val found = (adb() ?: return@withLock emptyList()).crashes(serial)
            val known = crashesSeen[serial]
            if (known == null) {
                crashesSeen[serial] = found.mapTo(HashSet()) { it.id }
                return@withLock emptyList()
            }
            val fresh = found.filter { known.add(it.id) }.map { SeenCrash(crashSeq.incrementAndGet(), serial, it) }
            if (fresh.isNotEmpty()) _crashes.update { (it + fresh).takeLast(MAX_CRASHES) }
            fresh
        }

    /** The crashes of [serial] after [cursor]. */
    fun crashesSince(
        serial: String,
        cursor: Long,
    ): List<SeenCrash> = _crashes.value.filter { it.serial == serial && it.seq > cursor }

    fun allowAgent(
        serial: String,
        allow: Boolean,
    ) {
        allowed.update { if (allow) it + serial else it - serial }
        _state.update { s -> s.copy(devices = s.devices.map { d -> if (d.serial == serial && d.kind == DeviceKind.PHYSICAL) d.copy(agentAllowed = allow) else d }) }
    }

    fun report(activity: DeviceActivity) {
        _activity.tryEmit(activity)
    }

    /** Stops the emulators this app started. */
    fun shutdown() {
        launched.values.forEach { it.process.destroy() }
        launched.clear()
    }

    companion object {
        private const val TAG = "AndroidDevices"
        private const val BOOT_POLL_MS = 2_000L
        private const val RESET_RECONNECT_MS = 30_000L
        private const val RESET_POLL_MS = 300L
        private const val GRPC_RETRY_MS = 5_000L
        private const val STOP_WAIT_S = 20L
        private const val MAX_CRASHES = 50
        private const val AVD_TTL_MS = 10_000L

        val shared: AndroidDevices by lazy {
            AndroidDevices().also { devices ->
                Runtime.getRuntime().addShutdownHook(Thread({ devices.shutdown() }, "android-devices-shutdown"))
            }
        }

        /** Builds and runs the apps of the chats' folders on [shared]'s devices. */
        val apps: AndroidAppRunner by lazy { AndroidAppRunner(shared) }
    }
}
