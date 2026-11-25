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
import com.vitorpamplona.androiddevice.avd.AvdInfo
import com.vitorpamplona.androiddevice.avd.Avds
import com.vitorpamplona.androiddevice.emulator.EmulatorController
import com.vitorpamplona.androiddevice.emulator.EmulatorDiscovery
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    /** AVD name to the serial it got, once its discovery file said; null while it starts. */
    private val starting = ConcurrentHashMap<String, String>()
    private val startingAvds = ConcurrentHashMap.newKeySet<String>()
    private val booted = ConcurrentHashMap.newKeySet<String>()
    private val connections = ConcurrentHashMap<String, DeviceConnection>()
    private val refreshLock = Mutex()
    private val watchers = AtomicInteger(0)

    @Volatile
    private var poller: Job? = null

    @Volatile
    private var tools: AndroidSdkTools? = null

    private fun tools(): AndroidSdkTools = tools ?: AndroidSdkTools(sdkHome()).also { tools = it }

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
                val avds = Avds.list(avdHome())
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
                // An emulator that went away on its own takes its process with it.
                launched.entries.removeIf { (_, l) -> !l.process.isAlive }
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
                    name = avd?.displayName ?: emulator?.avdName?.replace('_', ' ') ?: d.model ?: d.serial,
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
                )
            }
        // Starting, not yet in adb's list.
        val pending =
            startingAvds
                .filter { avd -> listed.none { it.avdName == avd } }
                .map { avd ->
                    DeviceInfo(
                        serial = starting[avd] ?: "avd:$avd",
                        name = avds.firstOrNull { it.name == avd }?.displayName ?: avd.replace('_', ' '),
                        kind = DeviceKind.EMULATOR,
                        status = DeviceStatus.STARTING,
                        avdName = avd,
                        startedHere = true,
                        agentAllowed = true,
                    )
                }
        return listed + pending
    }

    fun device(serial: String): DeviceInfo? = _state.value.devices.firstOrNull { it.serial == serial }

    /**
     * Starts [avd] and returns its serial once Android has booted. Throws [IOException] when the
     * SDK has no emulator, or it exits or doesn't boot in [bootTimeoutMs].
     */
    suspend fun start(
        avd: String,
        options: LaunchOptions = LaunchOptions(),
        bootTimeoutMs: Long = 5 * 60_000,
    ): String {
        val emulator = tools().emulator ?: throw IOException("The Android SDK's emulator isn't installed")
        _state.value.devices
            .firstOrNull { it.avdName == avd && it.status != DeviceStatus.OFFLINE }
            ?.let { running ->
                if (running.status == DeviceStatus.ONLINE) return running.serial
                if (running.status != DeviceStatus.STARTING) return waitForBoot(running.serial, bootTimeoutMs)
            }
        if (!startingAvds.add(avd)) throw IOException("$avd is already starting")
        _state.update { it.copy(startFailures = it.startFailures - avd) }
        try {
            refresh()
            if (!logDir.isDirectory && !logDir.mkdirs()) Log.w(TAG) { "Could not create $logDir" }
            val launch = EmulatorLauncher(emulator, runner, discoveryDirs).launch(avd, options, File(logDir, "$avd.log"))
            val serial = launch.info.serial
            launched[serial] = launch
            starting[avd] = serial
            refresh()
            return waitForBoot(serial, bootTimeoutMs)
        } catch (e: IOException) {
            _state.update { it.copy(startFailures = it.startFailures + (avd to (e.message ?: avd))) }
            throw e
        } finally {
            startingAvds.remove(avd)
            starting.remove(avd)
            refresh()
        }
    }

    /** [start] in the background, for a button; a failure shows in [DevicesState.startFailures]. */
    fun startInBackground(
        avd: String,
        options: LaunchOptions = LaunchOptions(),
        onBooted: (String) -> Unit = {},
    ) {
        scope.launch {
            try {
                onBooted(start(avd, options))
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
        refresh()
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
        connections[serial]?.let { return it }
        val adb = adb() ?: throw IOException("adb isn't installed: install the Android SDK's platform-tools")
        val controller = emulatorController(serial)
        return DeviceConnection(serial, adb, controller).also { connections[serial] = it }
    }

    private suspend fun emulatorController(serial: String): EmulatorController? {
        if (!serial.startsWith("emulator-")) return null
        val port = serial.removePrefix("emulator-").toIntOrNull() ?: return null
        val info = withContext(Dispatchers.IO) { EmulatorDiscovery.running(discoveryDirs()) }.firstOrNull { it.consolePort == port } ?: return null
        val grpcPort = info.grpcPort ?: return null
        val ours = launched.containsKey(serial)
        if (!ours && !info.grpcReachable) return null
        val token = info.grpcToken ?: if (ours) consoleToken() else null
        val controller = EmulatorController(GrpcChannel("127.0.0.1", grpcPort, token))
        return try {
            controller.status()
            controller
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "The emulator $serial refused its gRPC endpoint; using adb", e)
            null
        }
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

    /** The device [chatKey] works on: the one picked for it if still there, else the only one online. */
    fun selected(chatKey: String?): DeviceInfo? {
        val devices = _state.value.devices
        chatKey?.let { key -> _selection.value[key]?.let { serial -> devices.firstOrNull { it.serial == serial }?.let { return it } } }
        return devices.filter { it.isOnline }.singleOrNull()
    }

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
        private const val STOP_WAIT_S = 20L

        val shared: AndroidDevices by lazy {
            AndroidDevices().also { devices ->
                Runtime.getRuntime().addShutdownHook(Thread({ devices.shutdown() }, "android-devices-shutdown"))
            }
        }

        /** Builds and runs the apps of the chats' folders on [shared]'s devices. */
        val apps: AndroidAppRunner by lazy { AndroidAppRunner(shared) }
    }
}
