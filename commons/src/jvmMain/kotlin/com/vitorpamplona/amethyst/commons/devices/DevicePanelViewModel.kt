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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.devicecore.input.DeviceKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The device panel beside a coding chat ([chatKey], working in [folder]), or the full Devices
 * screen without one: which device it shows, starting and stopping emulators and simulators, the
 * Run button, the hardware keys and the device log. The live screen itself is the UI's
 * ([connection] gives it the device).
 */
class DevicePanelViewModel(
    private val devices: Devices,
    /** Each platform's Run: Gradle's, Xcode's. */
    private val runners: List<AppRunner>,
    /** The chat whose device this shows; null for the Devices screen, which keeps its own pick. */
    val chatKey: String?,
    private val folder: File?,
    /**
     * Whose device it is ([com.vitorpamplona.amethyst.commons.claudecode.ClaudeCodeService.deviceOwner]):
     * the chat's workstream, else the chat. An emulator started here is its own copy. Null for the
     * Devices screen, which starts the AVD itself, saving its state.
     */
    private val owner: String? = chatKey,
    private val ownerLabel: String? = null,
) : ViewModel() {
    /**
     * How many panels show this ViewModel now ([shown]). It outlives the panel (it lives as long as
     * the chat's screen), so the polling runs only while one shows it: a closed panel asks adb nothing.
     */
    private var showing = 0
    private var releaseWatch: (() -> Unit)? = null
    private var crashJob: Job? = null

    val state: StateFlow<DevicesState> = devices.state

    /** The key the device pick is kept under: the chat's workstream, or the Devices screen's own. */
    private val pickKey = owner ?: SCREEN_PICK

    /**
     * The device shown: the one picked here, else the only one online, else the first online
     * (emulators first). Whatever it falls back to is then kept as the pick, so a phone plugged in
     * later doesn't swap the screen out from under the user.
     */
    val selected: StateFlow<DeviceInfo?> =
        combine(devices.state, devices.selection) { state, _ ->
            devices.selected(pickKey)
                ?: state.devices
                    // Another workstream's copy is never a chat's by default; the Devices screen shows any.
                    .filter { it.isOnline && (owner == null || it.owner == null) }
                    .sortedBy { it.kind == DeviceKind.PHYSICAL }
                    .firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, devices.selected(pickKey))

    /** What agents do on the device shown, as it happens. */
    val activity: Flow<DeviceActivity> = devices.activity.filter { it.serial == selected.value?.serial }

    private val _targets = MutableStateFlow<List<AppTarget>>(emptyList())

    /** The apps in the chat's folder, of every platform, for the Run button: the device shown picks its platform's. */
    val targets: StateFlow<List<AppTarget>> = _targets.asStateFlow()

    private val runStates: Flow<Map<String, AppRunState>> =
        if (runners.isEmpty()) MutableStateFlow(emptyMap()) else combine(runners.map { it.states }) { all -> all.fold(emptyMap<String, AppRunState>()) { acc, m -> acc + m } }

    /** How the Run of the device shown is going: its platform's app's. */
    val runState: StateFlow<AppRunState> =
        combine(runStates, _targets, selected) { states, targets, device ->
            targets.filter { it.platform == device?.platform }.firstNotNullOfOrNull { states[it.root.path] } ?: AppRunState.Idle
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppRunState.Idle)

    private val _error = MutableStateFlow<String?>(null)

    /** The last thing that failed here (a key, the screenshot), as the device said it. */
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        folder?.let { dir ->
            // Each platform looks on its own: Gradle's settings and Xcode's projects, read at once.
            runners.forEach { runner ->
                viewModelScope.launch(Dispatchers.IO) {
                    val found = runner.targets(dir)
                    _targets.update { (it.filter { t -> t.platform != runner.platform } + found).sortedBy { t -> t.platform } }
                }
            }
        }
        // An error belongs to the device it came from: gone with it, or when another is shown.
        viewModelScope.launch {
            selected
                .map { it?.serial }
                .distinctUntilChanged()
                .drop(1)
                .collect { _error.value = null }
        }
        viewModelScope.launch {
            selected.collect { device ->
                if (device != null && device.status != DeviceStatus.STARTING && devices.selection.value[pickKey] == null) devices.select(pickKey, device.serial)
            }
        }
    }

    /**
     * The panel is on screen: keep the device list current and watch the shown device's crash log
     * (crashes the user causes by hand show too, not only the ones an agent's tools find). Call
     * what it returns when the panel goes.
     */
    fun shown(): () -> Unit {
        if (showing++ == 0) {
            releaseWatch = devices.watch()
            crashJob =
                viewModelScope.launch {
                    while (true) {
                        selected.value?.takeIf { it.isOnline }?.let { device ->
                            try {
                                devices.pollCrashes(device.serial)
                            } catch (e: IOException) {
                                Log.w(TAG, "Could not read the crash log of ${device.serial}", e)
                            }
                        }
                        delay(CRASH_POLL_MS)
                    }
                }
        }
        var released = false
        return {
            if (!released) {
                released = true
                if (--showing == 0) stopPolling()
            }
        }
    }

    private fun stopPolling() {
        releaseWatch?.invoke()
        releaseWatch = null
        crashJob?.cancel()
        crashJob = null
        showLogcat(false)
    }

    fun select(serial: String) = devices.select(pickKey, serial)

    /** Shuts the device shown down and starts it again without its saved state, as the same owner's device. */
    fun coldBoot() {
        val serial = selected.value?.serial ?: return
        devices.restartInBackground(serial, StartOptions(coldBoot = true)) { select(it) }
    }

    /** Starts a virtual device ([StartableDevice.id]): this chat's own copy, or on the Devices screen the device itself. */
    fun start(id: String) = devices.startInBackground(id, StartOptions(), owner = owner, ownerLabel = ownerLabel) { serial -> select(serial) }

    /** Whether a start from here makes this chat's own copy (a chat) or starts the AVD itself (the Devices screen). */
    val startsCopies: Boolean get() = owner != null

    /** Back to where a copy started: the state its AVD last saved, or a fresh copy of its simulator. */
    fun reset() {
        val serial = selected.value?.serial ?: return
        act {
            val fresh = devices.reset(serial)
            if (fresh != serial) select(fresh)
        }
    }

    /**
     * Where this panel is in the crash list: it shows the ones after it. Starts at now, so a crash
     * from before the panel opened isn't news.
     */
    private val crashFloor = MutableStateFlow(devices.crashCursor())

    /** The latest crash on the device shown since the panel opened or the user dismissed the last. */
    val crash: StateFlow<SeenCrash?> =
        combine(devices.crashes, selected, crashFloor) { crashes, device, floor ->
            crashes.lastOrNull { it.serial == device?.serial && it.seq > floor }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun dismissCrash() {
        crash.value?.let { crashFloor.value = it.seq }
    }

    fun stop(serial: String) = devices.stopInBackground(serial)

    fun allowAgent(
        serial: String,
        allow: Boolean,
    ) = devices.allowAgent(serial, allow)

    suspend fun connection(serial: String): DeviceDriver = devices.connection(serial)

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                _error.value = null
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Device action failed", e)
                _error.value = e.message
            }
        }
    }

    fun press(key: DeviceKey) {
        val serial = selected.value?.serial ?: return
        act { devices.connection(serial).press(key) }
    }

    /** A quarter turn to the left, as the emulator's (or the simulator's) own rotate button turns it. */
    fun rotate() {
        val serial = selected.value?.serial ?: return
        act {
            val connection = devices.connection(serial)
            connection.rotate(connection.rotation() + 1)
        }
    }

    /** Builds, installs and opens [variant] of [target] on the device shown, with its platform's runner. */
    fun run(
        target: AppTarget,
        variant: String,
    ) {
        val serial = selected.value?.serial ?: return
        val runner = runners.firstOrNull { it.platform == target.platform } ?: return
        viewModelScope.launch {
            try {
                runner.run(target, variant, serial)
            } catch (e: IOException) {
                Log.w(TAG, "Run failed", e)
            }
        }
    }

    /** A full-size screenshot as a PNG file, to attach to the chat's next message. */
    suspend fun screenshotFile(): File? {
        val serial = selected.value?.serial ?: return null
        return try {
            val png = devices.connection(serial).screenshotPng()
            withContext(Dispatchers.IO) {
                File.createTempFile("device-", ".png").also {
                    it.deleteOnExit()
                    it.writeBytes(png)
                }
            }
        } catch (e: IOException) {
            _error.value = e.message
            null
        }
    }

    private val _logcat = MutableStateFlow<List<String>>(emptyList())

    /** The device's latest log lines while [showLogcat] is on: the app's own, when it runs. */
    val logcat: StateFlow<List<String>> = _logcat.asStateFlow()

    private var logcatJob: Job? = null

    fun showLogcat(
        show: Boolean,
        appOnly: Boolean = true,
    ) {
        logcatJob?.cancel()
        logcatJob = null
        if (!show) return
        logcatJob =
            viewModelScope.launch {
                while (true) {
                    val serial = selected.value?.serial
                    if (serial != null && selected.value?.isOnline == true) {
                        try {
                            val connection = devices.connection(serial)
                            val pkg = (runState.value as? AppRunState.Done)?.packageName ?: (runState.value as? AppRunState.Installing)?.packageName
                            val app = pkg?.takeIf { appOnly && connection.isRunning(it) == true }
                            _logcat.value =
                                connection
                                    .log(LOGCAT_LINES, app, if (app == null) 'W' else 'V', crashesOnly = false)
                                    .text
                                    .lines()
                                    .filter { it.isNotBlank() && !it.startsWith("---------") }
                        } catch (e: IOException) {
                            _logcat.value = listOf(e.message ?: "logcat failed")
                        }
                    }
                    // An iOS simulator's log is read with `log show`, which takes a second or two.
                    delay(if (selected.value?.isIos == true) IOS_LOG_POLL_MS else LOGCAT_POLL_MS)
                }
            }
    }

    /** Whether the device shown can be stopped from here: an emulator or a simulator. */
    val canStop: StateFlow<Boolean> =
        selected
            .map { it?.isVirtual == true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    override fun onCleared() {
        stopPolling()
    }

    companion object {
        private const val TAG = "DevicePanel"
        private const val SCREEN_PICK = "devices-screen"
        private const val LOGCAT_LINES = 300
        private const val LOGCAT_POLL_MS = 2_000L
        private const val IOS_LOG_POLL_MS = 4_000L
        private const val CRASH_POLL_MS = 3_000L
    }
}
