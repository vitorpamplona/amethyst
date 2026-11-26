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
package com.vitorpamplona.amethyst.commons.devices.ios

import com.vitorpamplona.amethyst.commons.devices.AgentImages
import com.vitorpamplona.amethyst.commons.devices.AgentShot
import com.vitorpamplona.amethyst.commons.devices.DeviceDriver
import com.vitorpamplona.amethyst.commons.devices.DeviceLog
import com.vitorpamplona.amethyst.commons.devices.DevicePlatform
import com.vitorpamplona.devicecore.crash.DeviceCrash
import com.vitorpamplona.devicecore.input.DeviceKey
import com.vitorpamplona.devicecore.screen.DeviceScreen
import com.vitorpamplona.devicecore.screen.PngSize
import com.vitorpamplona.devicecore.screen.ScreenSize
import com.vitorpamplona.devicecore.ui.UiTree
import com.vitorpamplona.iossimulator.ax.AxTree
import com.vitorpamplona.iossimulator.crash.IosCrashReport
import com.vitorpamplona.iossimulator.device.HidKeys
import com.vitorpamplona.iossimulator.device.SimulatorScreen
import com.vitorpamplona.iossimulator.idb.HidStep
import com.vitorpamplona.iossimulator.idb.IdbClient
import com.vitorpamplona.iossimulator.idb.IdbScreen
import com.vitorpamplona.iossimulator.idb.IdbTarget
import com.vitorpamplona.iossimulator.simctl.Simctl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * One booted iOS simulator as the panel and the agents drive it: idb's companion for the screen,
 * touches, keys, text, rotation, the accessibility tree and crash reports; simctl for apps, URLs
 * and the log.
 *
 * As on Android, every x/y here is in the screen's pixels as it is turned now; idb takes points,
 * so they are divided by the screen's density on the way out.
 */
class SimulatorConnection(
    override val serial: String,
    val idb: IdbClient,
    private val simctl: Simctl,
    target: IdbTarget,
) : DeviceDriver {
    override val platform = DevicePlatform.IOS

    /** idb's companion streams the screen. */
    override val liveScreen = true

    private val natural: IdbScreen = target.screen ?: throw IOException("idb_companion didn't say the simulator's screen size")
    private val density = natural.density.takeIf { it > 0 } ?: (natural.widthPixels.toDouble() / natural.widthPoints.coerceAtLeast(1))
    private val typesText = target.typesText

    @Volatile
    override var lastShownSize: ScreenSize? = null
        private set

    @Volatile
    private var cachedScreen: SimulatorScreen? = null

    @Volatile
    private var turned: Pair<Int, Long>? = null

    override suspend fun rotation(): Int {
        turned?.let { (value, at) -> if (System.currentTimeMillis() - at < ROTATION_TTL_MS) return value }
        val now =
            try {
                idb.orientation() ?: 0
            } catch (e: IOException) {
                Log.d(TAG, "The companion didn't say how ${natural.widthPoints}x${natural.heightPoints} is turned", e)
                0
            }
        turned = now to System.currentTimeMillis()
        return now
    }

    override suspend fun rotate(quarterTurns: Int) {
        idb.setOrientation(quarterTurns)
        turned = null
    }

    override suspend fun screenSize(): ScreenSize = ScreenSize(natural.widthPixels, natural.heightPixels).let { if (rotation() % 2 == 1) it.swapped() else it }

    override suspend fun scale(): Double = AgentImages.agentScale(screenSize())

    override suspend fun screen(): DeviceScreen = cachedScreen ?: SimulatorScreen(idb, natural, typesText, rotation = ::rotation).also { cachedScreen = it }

    private fun points(px: Int): Double = px / density

    override suspend fun tap(
        x: Int,
        y: Int,
    ) = idb.hid(listOf(HidStep.Touch(points(x), points(y), true), HidStep.Delay(TAP_HOLD_S), HidStep.Touch(points(x), points(y), false)))

    override suspend fun longPress(
        x: Int,
        y: Int,
        holdMs: Int,
    ) = idb.hid(listOf(HidStep.Touch(points(x), points(y), true), HidStep.Delay(holdMs / 1000.0), HidStep.Touch(points(x), points(y), false)))

    override suspend fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int,
    ) {
        val steps = (durationMs / SWIPE_STEP_MS).coerceIn(2, MAX_SWIPE_STEPS)
        val pause = durationMs / 1000.0 / steps
        val path = (0..steps).map { i -> points(x1 + (x2 - x1) * i / steps) to points(y1 + (y2 - y1) * i / steps) }
        idb.hid(
            buildList {
                path.forEachIndexed { i, (x, y) ->
                    if (i > 0) add(HidStep.Delay(pause))
                    add(HidStep.Touch(x, y, true))
                }
                add(HidStep.Touch(path.last().first, path.last().second, false))
            },
        )
    }

    override suspend fun press(
        key: DeviceKey,
        longPress: Boolean,
    ) {
        // Back is a swipe on iOS: the live view's screen knows how to make it.
        if (key == DeviceKey.BACK) return screen().key(key)
        val steps = HidKeys.steps(key) ?: throw IOException("An iPhone has no ${key.name.lowercase()} key")
        if (!longPress) return idb.hid(steps)
        // Held: everything down, a second, everything up (Siri on Home, the power menu on Lock).
        val downs = steps.filter { (it is HidStep.Button && it.down) || (it is HidStep.Key && it.down) }
        val ups = steps.filter { (it is HidStep.Button && !it.down) || (it is HidStep.Key && !it.down) }
        idb.hid(downs + HidStep.Delay(LONG_PRESS_S) + ups)
    }

    override suspend fun agentShot(): AgentShot {
        val png = idb.screenshot()
        PngSize.of(png)?.let { lastShownSize = it }
        val scale = scale()
        return withContext(Dispatchers.Default) { AgentImages.scaledPng(png, scale) }
    }

    override suspend fun screenshotPng(): ByteArray = idb.screenshot()

    override suspend fun uiTree(): UiTree {
        val json = idb.accessibilityTree()
        val tree = withContext(Dispatchers.Default) { AxTree.parse(json, density) }
        lastShownSize = screenSize()
        return tree
    }

    override suspend fun openUrl(
        url: String,
        app: String?,
    ): String {
        // simctl hands the URL to whatever claims it; an app can't be named.
        simctl.openUrl(serial, url)
        return "Opened $url"
    }

    override suspend fun install(file: File): String {
        if (!file.name.endsWith(".app") && !file.name.endsWith(".ipa")) throw IOException("${file.name} isn't an iOS app (.app or .ipa): a simulator installs those")
        simctl.install(serial, file)
        return "Installed ${file.name}"
    }

    override suspend fun launch(app: String) {
        simctl.launch(serial, app)
    }

    override suspend fun stopApp(app: String) = simctl.terminate(serial, app)

    /**
     * iOS has no "clear data": the app is reinstalled from its own bundle, which drops its
     * container (documents, defaults, caches). Its keychain items stay, as they do on a phone.
     */
    override suspend fun clearData(app: String): String {
        val bundle = simctl.appPath(serial, app)?.let(::File)?.takeIf { it.isDirectory } ?: throw IOException("$app isn't installed")
        val copy = withContext(Dispatchers.IO) { Files.createTempDirectory("amethyst-app-").toFile() }
        try {
            val kept = File(copy, bundle.name)
            withContext(Dispatchers.IO) { if (!bundle.copyRecursively(kept, overwrite = true)) throw IOException("Could not copy ${bundle.name}") }
            simctl.terminate(serial, app)
            simctl.uninstall(serial, app)
            simctl.install(serial, kept)
            return "Reinstalled $app with no data (its keychain items stay)"
        } finally {
            if (!copy.deleteRecursively()) Log.w(TAG) { "Could not delete $copy" }
        }
    }

    override suspend fun isRunning(app: String): Boolean = simctl.pidOf(serial, app) != null

    override suspend fun log(
        lines: Int,
        app: String?,
        level: Char,
        crashesOnly: Boolean,
    ): DeviceLog {
        if (crashesOnly) {
            val recent = crashes().takeLast(CRASHES_SHOWN)
            return DeviceLog("Crash reports", recent.joinToString("\n\n") { it.summary(maxLines = 40) })
        }
        val pid = app?.let { simctl.pidOf(serial, it) }
        val severity = if (level.uppercaseChar() in "WEF") "(messageType == error OR messageType == fault)" else null
        val predicate = listOfNotNull(pid?.let { "processID == $it" }, severity).joinToString(" AND ").ifEmpty { null }
        val detail =
            when (level.uppercaseChar()) {
                'V', 'D' -> "debug"
                'I' -> "info"
                else -> "default"
            }
        val text = simctl.log(serial, LOG_MINUTES, predicate, detail)
        val tail =
            text
                .lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("Timestamp") }
                .toList()
                .takeLast(lines)
                .joinToString("\n")
        val head =
            when {
                app != null && pid == null -> "$app isn't running; the whole log of the last $LOG_MINUTES minutes"
                app != null -> "Log of $app (pid $pid)"
                else -> "Log of the last $LOG_MINUTES minutes"
            }
        return DeviceLog(head, tail)
    }

    /** Reports already read, by name: a report never changes, and reading one is a round trip. */
    private val reports = LinkedHashMap<String, DeviceCrash>()

    @Volatile
    private var primed = false

    /**
     * Only reports from about when this connection started are asked for: older ones can't be news
     * (the first look marks what is there), and a long-used simulator may have hundreds.
     */
    private val watchingSince = System.currentTimeMillis() / 1000 - CRASH_SLACK_S

    /**
     * The crash reports idb finds for this simulator. The first call only notes them (their
     * details aren't read: those crashes happened before anyone watched); later ones read each new
     * report once.
     */
    override suspend fun crashes(): List<DeviceCrash> {
        val listed = idb.crashes(watchingSince).sortedBy { it.timestamp }
        synchronized(reports) { if (listed.all { it.name in reports }) return reports.values.toList() }
        for (info in listed) {
            if (synchronized(reports) { info.name in reports }) continue
            val crash =
                if (!primed) {
                    DeviceCrash(info.name, DeviceCrash.Kind.IOS, "", info.pid.toInt(), info.bundleId.ifEmpty { null }, info.processName, emptyList())
                } else {
                    try {
                        IosCrashReport.parse(info, idb.crashShow(info.name))
                    } catch (e: IOException) {
                        Log.w(TAG, "Could not read the crash report ${info.name}", e)
                        IosCrashReport.parse(info, "")
                    }
                }
            synchronized(reports) { reports[info.name] = crash }
        }
        primed = true
        return synchronized(reports) { listed.mapNotNull { reports[it.name] } }
    }

    companion object {
        private const val TAG = "SimulatorConnection"
        private const val ROTATION_TTL_MS = 1_500L
        private const val TAP_HOLD_S = 0.04
        private const val LONG_PRESS_S = 1.0
        private const val SWIPE_STEP_MS = 16
        private const val MAX_SWIPE_STEPS = 60
        private const val LOG_MINUTES = 2
        private const val CRASHES_SHOWN = 5
        private const val CRASH_SLACK_S = 60L
    }
}
