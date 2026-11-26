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
package com.vitorpamplona.amethyst.commons.devices

import com.vitorpamplona.amethyst.commons.claudecode.AndroidSdk
import com.vitorpamplona.amethyst.commons.devices.android.AndroidAppRunner
import com.vitorpamplona.amethyst.commons.devices.android.AndroidBackend
import com.vitorpamplona.amethyst.commons.devices.ios.IosAppRunner
import com.vitorpamplona.amethyst.commons.devices.ios.IosBackend
import com.vitorpamplona.iossimulator.XcodeTools
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Every device on this computer, of every platform, for the device panel and the agents' device
 * tools: one list, whatever runs them. Each platform is a [DeviceBackend] (Android's adb and
 * emulator, iOS's simctl and idb); what is the same for all of them lives here: which device each
 * chat works on, what agents do on them, the crashes found, and the user's leave for a phone.
 *
 * One per process ([shared]). It looks for devices only while a screen [watch]es, or when a tool
 * asks; each look asks every platform at once, and a platform that fails or hangs keeps showing
 * what it last showed, with the error, instead of holding up or emptying the others.
 */
class Devices(
    private val backends: List<DeviceBackend>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val pollMs: Long = 2_000,
    /** How long one platform's look may take before the others go on without it. */
    private val refreshTimeoutMs: Long = 20_000,
) {
    private val _state = MutableStateFlow(DevicesState())
    val state: StateFlow<DevicesState> = _state.asStateFlow()

    private val _activity = MutableSharedFlow<DeviceActivity>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** What agents do on the devices, as it happens. */
    val activity: SharedFlow<DeviceActivity> = _activity.asSharedFlow()

    private val _selection = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The device each chat (by key) works on, as the user or its agent picked it. */
    val selection: StateFlow<Map<String, String>> = _selection.asStateFlow()

    /** Phones the user let agents use; virtual devices need no leave. */
    private val allowed = MutableStateFlow<Set<String>>(emptySet())

    /** Each platform's last good look. */
    private val snapshots = ConcurrentHashMap<DevicePlatform, BackendSnapshot>()
    private val mergeLock = Mutex()
    private val watchers = AtomicInteger(0)

    @Volatile
    private var poller: Job? = null

    @Volatile
    private var refreshedAt = 0L

    fun backend(platform: DevicePlatform): DeviceBackend? = backends.firstOrNull { it.platform == platform }

    /** The backend [serial] belongs to: the one that lists it, else the first that claims it. */
    private fun backendOf(serial: String): DeviceBackend =
        device(serial)?.let { backend(it.platform) }
            ?: backends.firstOrNull { it.claims(serial) }
            ?: throw IOException("No platform here drives $serial")

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

    /** Looks again, on every platform at once. Never throws: a platform's failure shows in its setup's error. */
    suspend fun refresh() {
        coroutineScope {
            backends
                .map { backend -> async { backend.platform to look(backend) } }
                .awaitAll()
                .forEach { (platform, snapshot) -> snapshot?.let { snapshots[platform] = it } }
        }
        merge()
    }

    /** One platform's look, or its last good one marked with what went wrong (null before any). */
    private suspend fun look(backend: DeviceBackend): BackendSnapshot? =
        try {
            withTimeout(refreshTimeoutMs) { backend.refresh() }
        } catch (e: CancellationException) {
            // A timeout is a CancellationException too: only a cancel from outside goes up.
            if (e is TimeoutCancellationException) stale(backend, "didn't answer in " + if (refreshTimeoutMs < 1_000) "${refreshTimeoutMs}ms" else "${refreshTimeoutMs / 1_000}s") else throw e
        } catch (e: IOException) {
            stale(backend, e.message ?: "failed")
        } catch (e: RuntimeException) {
            // An unexpected answer from a tool must not take the other platform's list with it.
            Log.w(TAG, "The ${backend.platform} devices couldn't be listed", e)
            stale(backend, e.message ?: e.javaClass.simpleName)
        }

    private fun stale(
        backend: DeviceBackend,
        why: String,
    ): BackendSnapshot? {
        val last = snapshots[backend.platform] ?: return null
        return BackendSnapshot(last.devices, last.startable, last.setup.withError(why), last.startFailures)
    }

    private fun PlatformSetup.withError(why: String): PlatformSetup =
        when (this) {
            is AndroidSetup -> copy(error = why)
            is IosSetup -> copy(error = why)
        }

    private suspend fun merge() {
        mergeLock.withLock {
            val all = backends.mapNotNull { snapshots[it.platform] }
            val allowedNow = allowed.value
            val devices = all.flatMap { it.devices }.map { d -> if (d.kind == DeviceKind.PHYSICAL) d.copy(agentAllowed = d.serial in allowedNow) else d }
            val seen = devices.map { it.serial }.toSet()
            crashesSeen.keys.retainAll(seen)
            refreshedAt = System.currentTimeMillis()
            _state.value =
                DevicesState(
                    devices = devices,
                    startable = all.flatMap { s -> s.startable.sortedByDescending { it.rank } },
                    android = all.firstNotNullOfOrNull { it.setup as? AndroidSetup } ?: AndroidSetup(),
                    ios = all.firstNotNullOfOrNull { it.setup as? IosSetup } ?: IosSetup(),
                    loaded = true,
                    startFailures = all.fold(emptyMap()) { acc, s -> acc + s.startFailures },
                )
        }
    }

    /** [refresh], unless the last one is younger than [maxAgeMs]: a burst of tool calls asks once. */
    suspend fun refreshIfStale(maxAgeMs: Long = 1_000) {
        if (System.currentTimeMillis() - refreshedAt > maxAgeMs) refresh()
    }

    fun device(serial: String): DeviceInfo? = _state.value.devices.firstOrNull { it.serial == serial }

    /**
     * Starts the virtual device [id] (a [StartableDevice.id]: an AVD's name, a simulator's udid)
     * and returns its serial once it is ready. With an [owner] (a workstream, or a chat outside
     * one), it is that owner's own copy; one already running for it is reused. Without one, it is
     * the device itself, shared, keeping what changes on it.
     */
    suspend fun start(
        id: String,
        options: StartOptions = StartOptions(),
        owner: String? = null,
        ownerLabel: String? = null,
    ): String {
        if (_state.value.startable(id) == null) refresh()
        // Startable, or running already (a copy is reused; a simulator that runs says why it can't be copied).
        val platform =
            _state.value.startable(id)?.platform
                ?: _state.value.devices
                    .firstOrNull { it.source == id }
                    ?.platform
                ?: throw IOException("Nothing named $id can be started")
        val backend = backend(platform) ?: throw IOException("No $platform devices here")
        // The start shows in the list (starting, booting) as soon as the platform lists it.
        val showing = scope.launch { delay(START_SHOW_MS).also { refresh() } }
        try {
            return backend.start(id, options, owner, ownerLabel)
        } finally {
            showing.cancel()
            refresh()
        }
    }

    /** [start] in the background, for a button; a failure shows in [DevicesState.startFailures]. */
    fun startInBackground(
        id: String,
        options: StartOptions = StartOptions(),
        owner: String? = null,
        ownerLabel: String? = null,
        onBooted: (String) -> Unit = {},
    ) {
        scope.launch {
            try {
                onBooted(start(id, options, owner = owner, ownerLabel = ownerLabel))
            } catch (e: IOException) {
                Log.w(TAG, "Could not start $id", e)
            }
        }
    }

    /** Shuts a virtual device down; a copy goes with its changes. */
    suspend fun stop(serial: String) {
        if (device(serial)?.kind == DeviceKind.PHYSICAL) throw IOException("A phone can't be stopped from here")
        try {
            backendOf(serial).stop(serial)
        } finally {
            refresh()
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
     * Puts a copy back to where it started; returns the serial it has now (a simulator's fresh
     * copy has a new one, and whoever had the old one has this one).
     */
    suspend fun reset(serial: String): String {
        val fresh = backendOf(serial).reset(serial)
        if (fresh != serial) _selection.update { picks -> picks.mapValues { (_, s) -> if (s == serial) fresh else s } }
        refresh()
        return fresh
    }

    /**
     * Shuts [serial] down and starts what it runs again with [options] (a cold boot), for the same
     * owner: one after the other, waiting for the old one to leave the list so the start doesn't find it.
     */
    fun restartInBackground(
        serial: String,
        options: StartOptions,
        onBooted: (String) -> Unit = {},
    ) {
        val device = device(serial) ?: return
        val source = device.source ?: return
        scope.launch {
            try {
                stop(serial)
                val until = System.currentTimeMillis() + RESTART_WAIT_MS
                while (device(serial) != null && System.currentTimeMillis() < until) {
                    delay(RESTART_POLL_MS)
                    refresh()
                }
                onBooted(start(source, options, owner = device.owner, ownerLabel = device.ownerLabel))
            } catch (e: IOException) {
                Log.w(TAG, "Could not restart $serial", e)
            }
        }
    }

    /** Stops the copies [owner] started, on every platform: its workstream was removed or its clones freed. */
    fun releaseOwner(owner: String) = backends.forEach { it.releaseOwner(owner) }

    /** How to drive [serial]. */
    suspend fun connection(serial: String): DeviceDriver = backendOf(serial).connection(serial)

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
     * Reads [serial]'s crashes and adds what is new to [crashes]. The first read of a device only
     * notes what is there: those crashes happened before anyone was watching.
     */
    suspend fun pollCrashes(serial: String): List<SeenCrash> =
        crashLock.withLock {
            val found = connection(serial).crashes()
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

    /** Stops what this app started, on every platform. */
    fun shutdown() = backends.forEach { runCatching { it.shutdown() } }

    companion object {
        private const val TAG = "Devices"
        private const val MAX_CRASHES = 50
        private const val START_SHOW_MS = 300L
        private const val RESTART_WAIT_MS = 20_000L
        private const val RESTART_POLL_MS = 500L

        val android: AndroidBackend by lazy { AndroidBackend() }
        val ios: IosBackend by lazy { IosBackend() }

        val shared: Devices by lazy {
            Devices(listOf(android, ios)).also { devices ->
                Runtime.getRuntime().addShutdownHook(Thread({ devices.shutdown() }, "devices-shutdown"))
            }
        }

        /** Builds and runs the chats' apps: Gradle's on Android devices, Xcode's on simulators. */
        val runners: List<AppRunner> by lazy {
            listOf(AndroidAppRunner(android, shared::report), IosAppRunner(ios, shared::report))
        }

        /**
         * Whether this computer can run devices at all: an Android SDK, or Xcode's simulators (a
         * Mac). The device panel and the agents' device tools are offered only then.
         */
        val supported: Boolean by lazy { AndroidSdk.home() != null || XcodeTools().available }
    }
}
