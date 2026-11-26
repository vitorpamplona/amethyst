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

import com.vitorpamplona.androiddevice.adb.DeviceCrash
import com.vitorpamplona.androiddevice.adb.DeviceKey
import com.vitorpamplona.androiddevice.adb.ScreenSize
import com.vitorpamplona.androiddevice.device.DeviceScreen
import com.vitorpamplona.androiddevice.ui.UiTree
import java.io.File

/** A device's log as the agent reads it: what it shows, and the lines. */
class DeviceLog(
    val head: String,
    val text: String,
)

/**
 * One device as the panel and the agents drive it, whatever it is: an Android emulator or phone
 * ([DeviceConnection], adb and the emulator's gRPC), or an iOS simulator ([SimulatorConnection],
 * simctl and idb). Apps are named by their Android package or iOS bundle id.
 *
 * Every x/y here is in the device's own pixels, turned as its screen is now; [scale] takes them
 * to the agent's (the pixels of [agentShot]).
 */
interface DeviceDriver {
    val serial: String
    val platform: DevicePlatform

    /** The screen's size as last shown (screenshot or UI tree), for where an agent's touch lands. */
    val lastShownSize: ScreenSize?

    /** From the device's pixels to the agent's ([AgentShot] pixels). */
    suspend fun scale(): Double

    /** The screen in the device's pixels, turned as it is now. */
    suspend fun screenSize(): ScreenSize

    /** Quarter turns from portrait. */
    suspend fun rotation(): Int

    suspend fun rotate(quarterTurns: Int)

    /** The live view. */
    suspend fun screen(): DeviceScreen

    suspend fun tap(
        x: Int,
        y: Int,
    )

    suspend fun longPress(
        x: Int,
        y: Int,
        holdMs: Int,
    )

    suspend fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int,
    )

    suspend fun press(
        key: DeviceKey,
        longPress: Boolean = false,
    )

    suspend fun type(text: String) = screen().type(text)

    /** A picture of the screen at the agent's scale, as PNG. */
    suspend fun agentShot(): AgentShot

    /** The screen at full size, as PNG: for the user to attach to a message. */
    suspend fun screenshotPng(): ByteArray

    suspend fun uiTree(): UiTree

    /** Opens [url] (in [app], when given); returns what the device said. */
    suspend fun openUrl(
        url: String,
        app: String?,
    ): String

    /** Installs an APK or an `.app`; returns what the device said. */
    suspend fun install(file: File): String

    suspend fun launch(app: String)

    suspend fun stopApp(app: String)

    /** Erases [app]'s data; returns what the device said. */
    suspend fun clearData(app: String): String

    /** Whether [app] runs now; null when the device didn't say. */
    suspend fun isRunning(app: String): Boolean?

    /**
     * The device's latest log: [lines] of [app]'s process when given and running, at [level]
     * (V, D, I, W, E, F) and above; with [crashesOnly], the crashes alone.
     */
    suspend fun log(
        lines: Int,
        app: String?,
        level: Char,
        crashesOnly: Boolean,
    ): DeviceLog

    /** The crashes the device has a record of, oldest first: each has an [DeviceCrash.id] that stays. */
    suspend fun crashes(): List<DeviceCrash>
}
