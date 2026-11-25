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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.androiddevice.adb.DeviceKey
import com.vitorpamplona.androiddevice.emulator.LaunchOptions
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The device panel beside a coding chat ([chatKey], working in [folder]), or the full Devices
 * screen without one: which device it shows, starting and stopping emulators, the Run button, the
 * hardware keys and logcat. The live screen itself is the UI's ([connection] gives it the device).
 */
class DevicePanelViewModel(
    private val devices: AndroidDevices,
    private val apps: AndroidAppRunner,
    /** The chat whose device this shows; null for the Devices screen, which keeps its own pick. */
    val chatKey: String?,
    private val folder: File?,
) : ViewModel() {
    private val release = devices.watch()

    val state: StateFlow<DevicesState> = devices.state

    /** The key the device pick is kept under: the chat's, or the Devices screen's own. */
    private val pickKey = chatKey ?: SCREEN_PICK

    /**
     * The device shown: the one picked here, else the only one online, else the first online
     * (emulators first). Whatever it falls back to is then kept as the pick, so a phone plugged in
     * later doesn't swap the screen out from under the user.
     */
    val selected: StateFlow<DeviceInfo?> =
        combine(devices.state, devices.selection) { state, _ ->
            devices.selected(pickKey)
                ?: state.devices
                    .filter { it.isOnline }
                    .sortedBy { it.kind != DeviceKind.EMULATOR }
                    .firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, devices.selected(pickKey))

    /** What agents do on the device shown, as it happens. */
    val activity: Flow<DeviceActivity> = devices.activity.filter { it.serial == selected.value?.serial }

    private val _targets = MutableStateFlow<List<AndroidAppTarget>>(emptyList())

    /** The Android apps in the chat's folder, for the Run button. */
    val targets: StateFlow<List<AndroidAppTarget>> = _targets.asStateFlow()

    val runState: StateFlow<AppRunState> =
        combine(apps.states, _targets) { states, targets -> targets.firstNotNullOfOrNull { states[it.root.path] } ?: AppRunState.Idle }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppRunState.Idle)

    private val _error = MutableStateFlow<String?>(null)

    /** The last thing that failed here (a key, the screenshot), as the device said it. */
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        folder?.let { dir -> viewModelScope.launch(Dispatchers.IO) { _targets.value = AndroidAppTargets.find(dir) } }
        viewModelScope.launch {
            selected.collect { device ->
                if (device != null && device.status != DeviceStatus.STARTING && devices.selection.value[pickKey] == null) devices.select(pickKey, device.serial)
            }
        }
    }

    fun select(serial: String) = devices.select(pickKey, serial)

    fun start(
        avd: String,
        coldBoot: Boolean = false,
    ) = devices.startInBackground(avd, LaunchOptions(coldBoot = coldBoot)) { serial -> select(serial) }

    fun stop(serial: String) = devices.stopInBackground(serial)

    fun allowAgent(
        serial: String,
        allow: Boolean,
    ) = devices.allowAgent(serial, allow)

    suspend fun connection(serial: String): DeviceConnection = devices.connection(serial)

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
        act { devices.connection(serial).screen().key(key) }
    }

    /** A quarter turn to the left, as the emulator's own rotate button turns it. */
    fun rotate() {
        val serial = selected.value?.serial ?: return
        act {
            val adb = devices.connection(serial).adb
            adb.rotate(serial, adb.rotation(serial) + 1)
        }
    }

    /** Builds, installs and opens [variant] of [target] on the device shown. */
    fun run(
        target: AndroidAppTarget,
        variant: String,
    ) {
        val serial = selected.value?.serial ?: return
        viewModelScope.launch {
            try {
                apps.run(target, variant, serial)
            } catch (e: IOException) {
                Log.w(TAG, "Run failed", e)
            }
        }
    }

    /** A full-size screenshot as a PNG file, to attach to the chat's next message. */
    suspend fun screenshotFile(): File? {
        val serial = selected.value?.serial ?: return null
        return try {
            val png = devices.connection(serial).adb.screencapPng(serial)
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
                            val adb = devices.connection(serial).adb
                            val pkg = (runState.value as? AppRunState.Done)?.packageName ?: (runState.value as? AppRunState.Installing)?.packageName
                            val pid = if (appOnly && pkg != null) adb.pidOf(serial, pkg) else null
                            _logcat.value = adb.logcat(serial, LOGCAT_LINES, pid, if (pid == null) 'W' else 'V').lines().filter { it.isNotBlank() && !it.startsWith("---------") }
                        } catch (e: IOException) {
                            _logcat.value = listOf(e.message ?: "logcat failed")
                        }
                    }
                    delay(LOGCAT_POLL_MS)
                }
            }
    }

    /** Whether the device shown can be stopped from here: an emulator. */
    val canStop: StateFlow<Boolean> =
        selected
            .map { it?.kind == DeviceKind.EMULATOR }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    override fun onCleared() {
        logcatJob?.cancel()
        release()
    }

    companion object {
        private const val TAG = "DevicePanel"
        private const val SCREEN_PICK = "devices-screen"
        private const val LOGCAT_LINES = 300
        private const val LOGCAT_POLL_MS = 2_000L
    }
}
