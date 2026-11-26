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

/** One platform's look at its devices: what runs, what can start, and what it has to work with. */
class BackendSnapshot(
    val devices: List<DeviceInfo>,
    val startable: List<StartableDevice>,
    val setup: PlatformSetup,
    /** Failures of starts this backend made, by [StartableDevice.id]. */
    val startFailures: Map<String, String> = emptyMap(),
)

/**
 * One platform's devices, as [Devices] drives them: Android's (adb, AVDs, the emulator) or iOS's
 * (simctl, idb). Everything that differs between platforms lives behind this; selection, the
 * activity overlay, crashes and agents' leave are [Devices]'.
 *
 * Each backend keeps its own locks and caches, so one platform's slow tool (a `simctl list` on a
 * Mac with many runtimes, an adb server restarting) never holds up the other.
 */
interface DeviceBackend {
    val platform: DevicePlatform

    /** Whether [serial] is this platform's, also before a look lists it (a copy just made). */
    fun claims(serial: String): Boolean

    /** Looks again. Throws only [java.io.IOException] for a look that failed outright. */
    suspend fun refresh(): BackendSnapshot

    /**
     * Starts [id] (a [StartableDevice.id]) and returns its serial once it is ready. With an
     * [owner], it is that owner's own copy; one already running for it is reused.
     */
    suspend fun start(
        id: String,
        options: StartOptions,
        owner: String?,
        ownerLabel: String?,
    ): String

    /** Shuts [serial] down; a copy goes with its changes. */
    suspend fun stop(serial: String)

    /** Back to where a copy started; returns the serial it has after (a fresh copy may have a new one). */
    suspend fun reset(serial: String): String

    suspend fun connection(serial: String): DeviceDriver

    /** Stops [owner]'s copies: its workstream was removed or its clones freed. */
    fun releaseOwner(owner: String)

    /** The app is quitting: stop what this app started. */
    fun shutdown()
}
