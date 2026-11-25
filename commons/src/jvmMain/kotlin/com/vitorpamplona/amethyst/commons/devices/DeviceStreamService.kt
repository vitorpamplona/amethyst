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

import com.vitorpamplona.emulator.AndroidSdk
import com.vitorpamplona.emulator.EmulatorController
import com.vitorpamplona.emulator.EmulatorDiscovery
import com.vitorpamplona.emulator.EmulatorFrame
import com.vitorpamplona.emulator.EmulatorLaunchOptions
import com.vitorpamplona.emulator.EmulatorLauncher
import com.vitorpamplona.emulator.ImageEncoding
import com.vitorpamplona.emulator.KeyboardEvent
import com.vitorpamplona.emulator.RunningEmulator
import com.vitorpamplona.emulator.SkinRotation
import com.vitorpamplona.emulator.VmRunState
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes

/**
 * The Android emulators on this computer, for the dock's devices panel: the SDK and its virtual
 * devices, the emulators running now (found through the emulator's own discovery files), starting
 * one without a window of its own, and one [DeviceSession] per running emulator that streams its
 * screen and takes input. Sessions live for the process, not for the panel, so moving the panel
 * or popping it out keeps the stream going.
 *
 * Desktop only: Android can't run the emulator.
 */
class DeviceStreamService(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val discovery = EmulatorDiscovery()

    /** The SDK on this computer; null when none is installed where the SDK's own tools look. */
    val sdk: AndroidSdk? by lazy { AndroidSdk.locate() }

    private val _emulators = MutableStateFlow<List<RunningEmulator>>(emptyList())

    /** The emulators running now, refreshed while someone [watch]es. */
    val emulators: StateFlow<List<RunningEmulator>> = _emulators.asStateFlow()

    private val _avds = MutableStateFlow<List<String>>(emptyList())

    /** The SDK's virtual devices, by name. */
    val avds: StateFlow<List<String>> = _avds.asStateFlow()

    private val _starting = MutableStateFlow<Set<String>>(emptySet())

    /** The virtual devices being started now. */
    val starting: StateFlow<Set<String>> = _starting.asStateFlow()

    private val _startErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Why the last start of a virtual device failed, by name. */
    val startErrors: StateFlow<Map<String, String>> = _startErrors.asStateFlow()

    private val sessions = mutableMapOf<Long, DeviceSession>()
    private var watchers = 0
    private var watchJob: Job? = null

    /**
     * Keeps [emulators] current while the panel shows (a poll every couple of seconds: the
     * discovery files are the emulator's only announcement). Call the returned function to stop.
     */
    @Synchronized
    fun watch(): () -> Unit {
        watchers++
        if (watchJob == null) {
            watchJob =
                scope.launch {
                    refreshAvds()
                    while (isActive) {
                        refresh()
                        delay(POLL_MS)
                    }
                }
        }
        var released = false
        return {
            synchronized(this) {
                if (!released) {
                    released = true
                    watchers--
                    if (watchers == 0) {
                        watchJob?.cancel()
                        watchJob = null
                    }
                }
            }
        }
    }

    /** Looks for emulators and virtual devices again. */
    fun rescan() {
        scope.launch {
            refreshAvds()
            refresh()
        }
    }

    private fun refresh() {
        val running =
            try {
                discovery.running()
            } catch (e: IOException) {
                Log.w(TAG, "Could not read the running emulators", e)
                return
            }
        _emulators.value = running
        synchronized(sessions) {
            val alive = running.map { it.pid }.toSet()
            sessions.keys.filter { it !in alive }.forEach { pid -> sessions.remove(pid)?.close() }
        }
    }

    private suspend fun refreshAvds() {
        val emulatorBinary = sdk?.emulatorBinary
        _avds.value =
            if (emulatorBinary != null) {
                try {
                    EmulatorLauncher(emulatorBinary, sdk?.root).listAvds()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not list the virtual devices", e)
                    AndroidSdk.listAvdFiles().map { it.name }
                }
            } else {
                AndroidSdk.listAvdFiles().map { it.name }
            }
    }

    /**
     * Starts virtual device [avd] with no window of its own and its gRPC service on, then waits for
     * it to announce itself (it is in [emulators] once it does).
     */
    fun start(avd: String) {
        val binary = sdk?.emulatorBinary ?: return
        if (avd in _starting.value) return
        _starting.update { it + avd }
        _startErrors.update { it - avd }
        scope.launch {
            try {
                val launcher = EmulatorLauncher(binary, sdk?.root)
                val process = launcher.start(avd, EmulatorLaunchOptions(grpcPort = EmulatorLauncher.freeLocalPort()))
                val running = EmulatorLauncher.awaitRunning(process.toHandle(), avd, discovery, timeout = 5.minutes)
                if (running == null) {
                    val message = if (process.isAlive) "it did not come up in time" else "it exited with code ${process.exitValue()}"
                    _startErrors.update { it + (avd to message) }
                }
                refresh()
            } catch (e: IOException) {
                Log.w(TAG, "Could not start $avd", e)
                _startErrors.update { it + (avd to (e.message ?: e.javaClass.simpleName)) }
            } finally {
                _starting.update { it - avd }
            }
        }
    }

    /** Shuts [emulator] down, as its power menu would, or ends its process when it doesn't answer. */
    fun stop(emulator: RunningEmulator) {
        scope.launch {
            try {
                session(emulator).controller.setVmState(VmRunState.SHUTDOWN)
            } catch (e: IOException) {
                Log.w(TAG, "The emulator did not take the shutdown; ending its process", e)
                ProcessHandle.of(emulator.pid).ifPresent { it.destroy() }
            }
            delay(SHUTDOWN_SETTLE_MS)
            refresh()
        }
    }

    /** The session of [emulator], shared by everyone who shows it. */
    fun session(emulator: RunningEmulator): DeviceSession =
        synchronized(sessions) {
            sessions.getOrPut(emulator.pid) { DeviceSession(emulator, EmulatorController(emulator, http), scope) }
        }

    companion object {
        private const val TAG = "DeviceStreamService"
        private const val POLL_MS = 2_000L
        private const val SHUTDOWN_SETTLE_MS = 1_500L

        /** The one service of this process. */
        val shared: DeviceStreamService by lazy { DeviceStreamService() }
    }
}

/**
 * One running emulator as the panel sees it: its screen as a stream of PNG frames at the size the
 * panel asks for ([requestSize]), its display size in device pixels (to place touches), and input.
 * The stream runs while at least one view holds it ([acquire]); asked for a new size it waits for
 * the size to rest (a splitter being dragged asks every frame) and restarts at the new size.
 */
class DeviceSession internal constructor(
    val emulator: RunningEmulator,
    internal val controller: EmulatorController,
    private val scope: CoroutineScope,
) {
    private val _frame = MutableStateFlow<EmulatorFrame?>(null)

    /** The newest frame: a PNG ([EmulatorFrame.encoding]) the size the panel asked for. */
    val frame: StateFlow<EmulatorFrame?> = _frame.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    /** Why the stream stopped, while it is retrying; null while it streams. */
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _displaySize = MutableStateFlow<Pair<Int, Int>?>(null)

    /** The device's display in its own pixels, where touches land; null until the emulator says. */
    val displaySize: StateFlow<Pair<Int, Int>?> = _displaySize.asStateFlow()

    private val requested = MutableStateFlow<Pair<Int, Int>?>(null)
    private var holders = 0
    private var job: Job? = null
    private var rotation = SkinRotation.PORTRAIT

    /** Streams while held; call the returned function to let go. */
    @Synchronized
    fun acquire(): () -> Unit {
        holders++
        if (job == null) job = scope.launch { run() }
        var released = false
        return {
            synchronized(this) {
                if (!released) {
                    released = true
                    holders--
                    if (holders == 0) {
                        job?.cancel()
                        job = null
                    }
                }
            }
        }
    }

    /** The panel's size for the screen, in pixels. */
    fun requestSize(
        width: Int,
        height: Int,
    ) {
        if (width > 0 && height > 0) requested.value = width to height
    }

    @OptIn(FlowPreview::class)
    private suspend fun run() {
        loadDisplaySize()
        requested
            .filterNotNull()
            .debounce(RESIZE_SETTLE_MS)
            .distinctUntilChanged()
            .collectLatest { (width, height) ->
                while (true) {
                    try {
                        controller.screenshots(ImageEncoding.PNG, width, height).collect { frame ->
                            _error.value = null
                            _frame.value = frame
                            rotation = frame.rotation
                        }
                        // The stream ended (the emulator closed it): ask again.
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        _error.value = e.message ?: e.javaClass.simpleName
                        Log.w("DeviceSession", "The screen stream of ${emulator.name} stopped", e)
                    }
                    delay(RETRY_MS)
                    if (_displaySize.value == null) loadDisplaySize()
                }
            }
    }

    private suspend fun loadDisplaySize() {
        try {
            val status = controller.getStatus()
            val w = status.displayWidth
            val h = status.displayHeight
            if (w != null && h != null && w > 0 && h > 0) _displaySize.value = w to h
        } catch (e: IOException) {
            _error.value = e.message ?: e.javaClass.simpleName
        }
    }

    /** A mouse event at ([x], [y]) in device pixels; [pressed] holds the primary button (a touch). */
    fun mouse(
        x: Int,
        y: Int,
        pressed: Boolean,
    ) = send { controller.sendMouse(x, y, if (pressed) 1 else 0) }

    /** Scrolls by [dx], [dy] wheel notches. */
    fun wheel(
        dx: Int,
        dy: Int,
    ) = send { controller.sendWheel(dx, dy) }

    /** Presses and releases a W3C key name (`GoBack`, `GoHome`, `AppSwitch`, `Enter`...). */
    fun key(name: String) = send { controller.pressKey(name) }

    /** A key going down or up, for keys held while typing (Shift) and for repeats. */
    fun keyEvent(event: KeyboardEvent) = send { controller.sendKey(event) }

    /** Types [text] as the keyboard would. */
    fun type(text: String) = send { controller.typeText(text) }

    /** Turns the device a quarter turn. */
    fun rotate() {
        val next =
            when (rotation) {
                SkinRotation.PORTRAIT -> SkinRotation.LANDSCAPE
                SkinRotation.LANDSCAPE -> SkinRotation.REVERSE_PORTRAIT
                SkinRotation.REVERSE_PORTRAIT -> SkinRotation.REVERSE_LANDSCAPE
                SkinRotation.REVERSE_LANDSCAPE -> SkinRotation.PORTRAIT
            }
        send { controller.rotateTo(next) }
    }

    private fun send(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: IOException) {
                Log.w("DeviceSession", "Input to ${emulator.name} failed", e)
            }
        }
    }

    internal fun close() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val RESIZE_SETTLE_MS = 200L
        const val RETRY_MS = 2_000L
    }
}
