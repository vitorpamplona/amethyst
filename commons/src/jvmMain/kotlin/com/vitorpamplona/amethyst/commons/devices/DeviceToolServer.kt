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

import com.vitorpamplona.agents.HostTool
import com.vitorpamplona.agents.HostToolResult
import com.vitorpamplona.agents.HostToolServer
import com.vitorpamplona.agents.ImageInput
import com.vitorpamplona.amethyst.commons.devices.android.AndroidAppRunner
import com.vitorpamplona.amethyst.commons.devices.ios.IosBackend
import com.vitorpamplona.devicecore.input.DeviceKey
import com.vitorpamplona.devicecore.ui.Bounds
import com.vitorpamplona.devicecore.ui.UiNode
import com.vitorpamplona.devicecore.ui.UiTree
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * The devices beside a coding chat, as tools for its agent: Android emulators and phones, and iOS
 * simulators on a Mac. See the screen (a screenshot, or the UI tree as text), touch, type, press
 * keys, start an emulator or a simulator, build and run the chat's app, read the device's log. The
 * device panel beside the chat shows the same screen, and where the agent touches.
 *
 * Every coordinate is in the pixels of the screenshots this server sends (see [DeviceDriver]).
 * Emulators and simulators are the agent's to use; a phone only once the user allows it in the
 * panel, since its screen may show their own messages.
 */
class DeviceToolServer(
    private val devices: Devices,
    /** Each platform's way to build and run the chat's app. */
    private val runners: List<AppRunner>,
    /**
     * Who the chat works for: its workstream, or the chat outside one. Its device pick, and the
     * emulator copies it starts, are kept under this key, so a workstream's chats share a device.
     */
    private val owner: () -> String?,
    /** What to call [owner] in the device picker: the workstream's title. */
    private val ownerLabel: () -> String? = { null },
    /** The chat's folder: where `run_app` finds the build and `install_app` the file. */
    private val folder: File,
) : HostToolServer {
    override val name = NAME
    override val tools = TOOLS

    /** The last UI tree of each device, for taps by element number. */
    private val trees = ConcurrentHashMap<String, UiTree>()

    /** Where this chat is in each device's crash list: it hears of each crash once. */
    private val crashCursors = ConcurrentHashMap<String, Long>()

    /** The app this chat opened on each device, watched for dying without a crash. */
    private val openedApp = ConcurrentHashMap<String, String>()

    override fun alwaysAsks(tool: String): Boolean = false

    override fun describe(
        tool: String,
        arguments: JsonObject,
    ): String? =
        when (tool) {
            RUN_APP -> "Build ${arguments.str("app") ?: arguments.str("module") ?: "the app"}${(arguments.str("variant") ?: arguments.str("scheme"))?.let { " ($it)" } ?: ""} and install it on ${deviceLabel(arguments)}"
            INSTALL_APP -> "Install ${arguments.str("path")} on ${deviceLabel(arguments)}"
            CLEAR_DATA -> "Erase all data of ${arguments.str("package")} on ${deviceLabel(arguments)}"
            STOP_DEVICE -> "Shut down ${deviceLabel(arguments)}"
            else -> null
        }

    private fun deviceLabel(arguments: JsonObject): String = arguments.str("device") ?: devices.selected(owner())?.let { "${it.name} (${it.serial})" } ?: "the device"

    override suspend fun call(
        tool: String,
        arguments: JsonObject,
    ): HostToolResult =
        try {
            when (tool) {
                LIST -> list()
                START_DEVICE -> startDevice(arguments)
                else -> {
                    val device = resolve(arguments.str("device"))
                    if (!device.agentAllowed) {
                        HostToolResult(
                            "${device.name} is the user's own phone: ask them to allow agents on it in the device panel beside this chat (\"Let agents use this phone\"), then try again.",
                            isError = true,
                        )
                    } else {
                        onDevice(tool, device, arguments)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            HostToolResult(e.message ?: "The device didn't answer", isError = true)
        } catch (e: IllegalArgumentException) {
            HostToolResult(e.message ?: "Bad arguments", isError = true)
        }

    private suspend fun onDevice(
        tool: String,
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val serial = device.serial
        if (!device.isOnline && tool != STOP_DEVICE) throw IOException("${device.name} is ${device.status.name.lowercase().replace('_', ' ')}, not ready")
        val chat = owner()
        if (chat != null) devices.select(chat, serial)
        if (tool in WITHOUT_CRASH_CHECK) return act(tool, device, args)
        // The first touch of a device only marks where its crash log starts. After that, one read
        // per call, after it acts: a crash that lands after a result went back is in the next one's.
        if (!crashCursors.containsKey(serial)) newCrashes(serial)
        val result =
            try {
                act(tool, device, args)
            } catch (e: IOException) {
                HostToolResult(e.message ?: "The device didn't answer", isError = true)
            }
        // The crash log and the app's process, asked at once: two adb calls, one wait.
        val (crashes, gone) =
            coroutineScope {
                val crashes = async { newCrashes(serial) }
                val alive = async { appAlive(tool, serial) }
                crashes.await().let { it to appGone(serial, it, alive.await()) }
            }
        val notes =
            listOfNotNull(
                crashes.takeIf { it.isNotEmpty() }?.let { "SINCE YOUR LAST DEVICE ACTION, " + crashText(it) },
                gone,
            )
        return if (notes.isEmpty()) result else result.copy(text = notes.joinToString("\n\n") + "\n\n" + result.text)
    }

    /** The crashes on [serial] this chat hasn't heard of. The first look only marks where it starts. */
    private suspend fun newCrashes(serial: String): List<SeenCrash> {
        val since = crashCursors.getOrPut(serial) { devices.crashCursor() }
        try {
            devices.pollCrashes(serial)
        } catch (e: IOException) {
            Log.w(TAG, "Could not read the crash log of $serial", e)
            return emptyList()
        }
        val fresh = devices.crashesSince(serial, since)
        fresh.maxOfOrNull { it.seq }?.let { crashCursors[serial] = it }
        return fresh
    }

    private fun crashText(crashes: List<SeenCrash>): String =
        (if (crashes.size == 1) "AN APP CRASHED:\n" else "${crashes.size} APP CRASHES:\n") +
            crashes.joinToString("\n") { it.crash.summary() }

    /**
     * Whether the app this chat opened still runs, or null when there's nothing to watch: no app
     * opened, a tool that stops it on purpose, or adb didn't answer.
     */
    private suspend fun appAlive(
        tool: String,
        serial: String,
    ): Boolean? {
        val pkg = openedApp[serial] ?: return null
        if (tool in STOPS_APP) return null
        return try {
            devices.connection(serial).isRunning(pkg)
        } catch (_: IOException) {
            null
        }
    }

    /** The app this chat opened, gone with no crash in the log (killed, or it exited): said once. */
    private fun appGone(
        serial: String,
        crashes: List<SeenCrash>,
        alive: Boolean?,
    ): String? {
        val pkg = openedApp[serial] ?: return null
        if (crashes.any { it.crash.packageName == pkg }) {
            openedApp.remove(serial)
            return null
        }
        if (alive != false) return null
        openedApp.remove(serial)
        return "$pkg IS NO LONGER RUNNING, with nothing in the crash log (it was killed, or it exited). $LOG with package shows its last lines."
    }

    private suspend fun act(
        tool: String,
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val serial = device.serial
        return when (tool) {
            SELECT -> HostToolResult("Working on ${device.name} ($serial) from now on; the panel shows it.")
            STOP_DEVICE -> {
                devices.stop(serial)
                HostToolResult("${device.name} is shutting down.")
            }
            SCREENSHOT -> screenshot(device, "Screenshot of ${device.name} ($serial)")
            UI_TREE -> uiTree(device)
            TAP -> tap(device, args)
            SWIPE -> swipe(device, args)
            TYPE -> type(device, args)
            KEY -> key(device, args)
            ROTATE -> rotate(device, args)
            WAIT_FOR -> waitFor(device, args)
            OPEN_URL -> {
                val url = args.str("url") ?: throw IllegalArgumentException("url is required")
                val out = devices.connection(serial).openUrl(url, args.str("package"))
                devices.report(DeviceActivity(serial, DeviceActivity.Kind.APP, label = url))
                after(device, args, if (out.contains("Error", ignoreCase = true)) out else "Opened $url")
            }
            INSTALL_APP -> {
                val file = args.str("path")?.let { p -> File(p).takeIf { it.isAbsolute } ?: File(folder, p) } ?: throw IllegalArgumentException("path is required")
                val out =
                    if (device.isIos) {
                        if (!file.exists()) throw IOException("No app at ${file.path}")
                        devices.connection(serial).install(file)
                    } else {
                        if (!file.isFile) throw IOException("No APK at ${file.path}")
                        val pkg = runners.filterIsInstance<AndroidAppRunner>().firstOrNull()?.packageName(file)
                        "Installed ${file.name}${pkg?.let { " ($it)" }.orEmpty()}: " + devices.connection(serial).install(file)
                    }
                // Installing over a running app ends its process.
                openedApp.remove(serial)
                HostToolResult(out)
            }
            LAUNCH_APP -> {
                val pkg = args.str("package") ?: throw IllegalArgumentException("package is required")
                devices.connection(serial).launch(pkg)
                openedApp[serial] = pkg
                devices.report(DeviceActivity(serial, DeviceActivity.Kind.APP, label = pkg))
                after(device, args, "Opened $pkg", defaultShot = true)
            }
            STOP_APP -> {
                val pkg = args.str("package") ?: throw IllegalArgumentException("package is required")
                devices.connection(serial).stopApp(pkg)
                openedApp.remove(serial, pkg)
                HostToolResult("Stopped $pkg")
            }
            CLEAR_DATA -> {
                val pkg = args.str("package") ?: throw IllegalArgumentException("package is required")
                openedApp.remove(serial, pkg)
                HostToolResult(devices.connection(serial).clearData(pkg))
            }
            RESET -> {
                // Without asking only on this workstream's own copy: anything else may hold the user's
                // unsaved state (the AVD itself, set up from the Devices screen; another workstream's copy).
                if (device.owner == null || device.owner != owner()) {
                    throw IOException("$RESET only resets this workstream's own copy. ${device.name} isn't one: ask the user to reset it, or $START_DEVICE for a copy of your own.")
                }
                val fresh = devices.reset(serial)
                openedApp.remove(serial)
                val now = devices.device(fresh) ?: device
                after(now, args, "${device.name} is back to the state it started from" + if (fresh != serial) " (it is $fresh now)" else "", defaultShot = true)
            }
            RUN_APP -> runApp(device, args)
            LOG -> log(device, args)
            else -> HostToolResult("No tool named $tool", isError = true)
        }
    }

    private suspend fun list(): HostToolResult {
        devices.refresh()
        val state = devices.state.value
        if (!state.anyPlatform) {
            return HostToolResult("Neither an Android SDK (set ANDROID_HOME) nor Xcode (a Mac) is installed on this computer, so there are no devices to drive.", isError = true)
        }
        val selected = devices.selected(owner())?.serial
        val text =
            buildString {
                if (state.devices.isEmpty()) {
                    append("No devices are connected or running.")
                } else {
                    append("Devices:")
                    state.devices.forEach { d ->
                        append("\n- ").append(d.serial)
                        if (d.name != d.serial) append(": ").append(d.name)
                        val what =
                            when (d.kind) {
                                DeviceKind.EMULATOR -> "Android emulator"
                                DeviceKind.SIMULATOR -> "iOS simulator"
                                DeviceKind.PHYSICAL -> "Android phone"
                            }
                        append(" (").append(what)
                        d.osVersion?.let { append(", ").append(it) }
                        append(", ").append(
                            d.status.name
                                .lowercase()
                                .replace('_', ' '),
                        )
                        if (!d.agentAllowed) append(", the user hasn't allowed agents on it")
                        when (d.owner) {
                            null -> Unit
                            owner() -> append(", this workstream's own copy")
                            else -> append(", another workstream's copy").append(d.ownerLabel?.let { ": $it" }.orEmpty())
                        }
                        append(')')
                        if (d.serial == selected) append(" ← this chat's device")
                    }
                }
                // What could start: every platform's, as start_device names them.
                if (state.startable.isNotEmpty()) {
                    append("\nVirtual devices $START_DEVICE can start (a copy of its own for this workstream):")
                    state.startable.forEach { v ->
                        append("\n- ")
                            .append(v.name)
                            .append(" (")
                            .append(if (v.platform == DevicePlatform.IOS) "iOS simulator" else "Android emulator")
                            .append(", ")
                            .append(v.detail)
                            .append(", name: ")
                            .append(v.id)
                            .append(')')
                    }
                }
                val android = state.android
                when {
                    !android.adbFound -> append("\nadb isn't installed, so no Android devices" + (android.sdkDir?.let { " (the Android SDK at ${it.path} has no platform-tools)" } ?: "") + ".")
                    !android.emulatorFound -> append("\nThe Android emulator isn't installed.")
                    state.startable.none { it.platform == DevicePlatform.ANDROID } -> append("\nNo Android virtual devices (AVDs) exist yet; one can be made with the SDK's avdmanager or Android Studio's Device Manager.")
                }
                android.error?.let { append("\nadb said: ").append(it) }
                val ios = state.ios
                if (ios.xcodeFound) {
                    if (state.startable.none { it.platform == DevicePlatform.IOS } && state.devices.none { it.isIos }) {
                        append("\nNo iOS simulators exist yet; Xcode's Devices and Simulators window makes them.")
                    }
                    if (!ios.idbFound) append("\n").append(IosBackend.IDB_MISSING)
                    ios.error?.let { append("\nsimctl said: ").append(it) }
                }
            }
        return HostToolResult(text)
    }

    /**
     * Starts this workstream's own copy of a virtual device, of either platform: by [name] (an AVD
     * or simulator name, or its id), else the newest of the platform the chat's folder builds for.
     */
    private suspend fun startDevice(args: JsonObject): HostToolResult {
        devices.refresh()
        val state = devices.state.value
        val wantedPlatform =
            when (args.str("platform")?.lowercase()) {
                null -> null
                "android" -> DevicePlatform.ANDROID
                "ios" -> DevicePlatform.IOS
                else -> throw IllegalArgumentException("platform is android or ios")
            }
        val candidates = state.startable.filter { wantedPlatform == null || it.platform == wantedPlatform }
        val wanted = args.str("name")
        val target =
            when {
                wanted != null ->
                    candidates.firstOrNull { it.id.equals(wanted, ignoreCase = true) }
                        ?: candidates.filter { it.name.equals(wanted, ignoreCase = true) }.maxByOrNull { it.rank }
                        ?: candidates.filter { it.name.contains(wanted, ignoreCase = true) }.maxByOrNull { it.rank }
                        // Running already: a copy of it is reused, or the platform says why it can't be made.
                        ?: state.devices
                            .firstOrNull { it.isVirtual && (it.name.equals(wanted, ignoreCase = true) || it.source.equals(wanted, ignoreCase = true)) }
                            ?.source
                            ?.let { source -> StartableDevice(source, state.devices.first { it.source == source }.platform, wanted, "", "") }
                        ?: throw IOException("Nothing named $wanted can be started. There are: ${candidates.joinToString { "${it.name} (${it.id})" }.ifEmpty { "none" }}")
                candidates.isEmpty() -> throw IOException("No virtual device can be started: make an AVD (Android Studio's Device Manager) or a simulator (Xcode's Devices and Simulators window)")
                else -> defaultStart(candidates)
            }
        val mine = owner()
        val serial =
            devices.start(
                target.id,
                StartOptions(coldBoot = args.bool("cold_boot") == true, wipeData = args.bool("wipe_data") == true),
                owner = mine,
                ownerLabel = ownerLabel(),
            )
        mine?.let { devices.select(it, serial) }
        val running = devices.device(serial)
        return HostToolResult(
            "${running?.name ?: target.name}${running?.osVersion?.let { " ($it)" }.orEmpty()} is ready as $serial and shows in the device panel." +
                if (mine != null) {
                    " It is this chat's own copy: it started from the state the device last saved, other workstreams don't touch it, and its changes go when it stops ($RESET goes back to that start without restarting it)."
                } else {
                    ""
                },
        )
    }

    /**
     * Without a name: the platform the chat's folder builds for (an Xcode project and no Android
     * app means iOS), then its newest phone (a tablet only when nothing else is there).
     */
    private suspend fun defaultStart(candidates: List<StartableDevice>): StartableDevice {
        val builds = runners.associate { r -> r.platform to (r.targets(folder).isNotEmpty()) }
        val platform =
            when {
                builds[DevicePlatform.IOS] == true && builds[DevicePlatform.ANDROID] != true -> DevicePlatform.IOS
                builds[DevicePlatform.ANDROID] == true -> DevicePlatform.ANDROID
                else -> null
            }
        val pool = candidates.filter { platform == null || it.platform == platform }.ifEmpty { candidates }
        return pool.filter { !it.isTablet }.ifEmpty { pool }.maxBy { it.rank }
    }

    /** [arg] (a serial or a name) or, without one, the chat's device: the one it picked, else the only one online. */
    private suspend fun resolve(arg: String?): DeviceInfo {
        // An agent acts in bursts (tap, look, tap): one adb listing serves the burst.
        devices.refreshIfStale()
        val all = devices.state.value.devices
        if (arg != null) {
            return all.firstOrNull { it.serial == arg }
                ?: all.firstOrNull { it.name.equals(arg, ignoreCase = true) || it.source.equals(arg, ignoreCase = true) }
                ?: all.firstOrNull { it.name.contains(arg, ignoreCase = true) }
                ?: throw IOException("No device $arg. ${if (all.isEmpty()) "None is running; $START_DEVICE starts one." else "There are: " + all.joinToString { "${it.serial} (${it.name})" }}")
        }
        devices.selected(owner())?.let { return it }
        val online = all.filter { it.isOnline }
        throw IOException(
            when {
                online.isEmpty() -> "No device is running for this chat. $START_DEVICE starts its own copy of a virtual device (an Android emulator or an iOS simulator); $LIST shows them."
                else -> "Several devices are online; pass device (one of ${online.joinToString { it.serial }}), or select_device."
            },
        )
    }

    private suspend fun screenshot(
        device: DeviceInfo,
        caption: String,
    ): HostToolResult {
        val shot = devices.connection(device.serial).agentShot()
        devices.report(DeviceActivity(device.serial, DeviceActivity.Kind.LOOK))
        return HostToolResult(
            "$caption, ${shot.width}x${shot.height}. Coordinates for tap and swipe are in this image's pixels.",
            images = listOf(ImageInput("${device.serial}.png", "image/png", Base64.getEncoder().encodeToString(shot.png))),
        )
    }

    /** What an action says, and with `screenshot: true` (or [defaultShot]) a picture once the screen settles. */
    private suspend fun after(
        device: DeviceInfo,
        args: JsonObject,
        text: String,
        defaultShot: Boolean = false,
    ): HostToolResult {
        if (args.bool("screenshot") ?: defaultShot) {
            delay((args.int("settle_ms") ?: SETTLE_MS).coerceIn(0, MAX_SETTLE_MS).toLong())
            return screenshot(device, "$text. The screen now")
        }
        return HostToolResult(text)
    }

    private suspend fun uiTree(device: DeviceInfo): HostToolResult {
        val connection = devices.connection(device.serial)
        val tree = connection.uiTree()
        trees[device.serial] = tree
        devices.report(DeviceActivity(device.serial, DeviceActivity.Kind.LOOK))
        return HostToolResult(
            "The screen of ${device.name}, as its views describe themselves (#number kind \"text\" desc id [left,top][right,bottom] in screenshot pixels, flags). " +
                "tap takes element=<number>, or text.\n" + tree.describe(connection.scale()),
        )
    }

    /** Where a tap goes, in the device's own pixels: an element of the last tree, a text to find, or x/y. */
    private suspend fun target(
        device: DeviceInfo,
        args: JsonObject,
        connection: DeviceDriver,
    ): Pair<Pair<Int, Int>, String> {
        args.int("element")?.let { id ->
            val node = (trees[device.serial] ?: connection.uiTree().also { trees[device.serial] = it }).node(id) ?: throw IllegalArgumentException("No element #$id in the last ui_tree: call ui_tree again")
            return (node.bounds.centerX to node.bounds.centerY) to describe(node)
        }
        args.str("text")?.let { text ->
            val tree = connection.uiTree().also { trees[device.serial] = it }
            val node = tree.find(text) ?: throw IOException("Nothing on screen says \"$text\". ui_tree shows what is there.")
            return (node.bounds.centerX to node.bounds.centerY) to describe(node)
        }
        val x = args.int("x") ?: throw IllegalArgumentException("Give element, text, or x and y")
        val y = args.int("y") ?: throw IllegalArgumentException("Give element, text, or x and y")
        val scale = connection.scale()
        return ((x / scale).roundToInt() to (y / scale).roundToInt()) to "($x, $y)"
    }

    private fun describe(node: UiNode) = "#${node.id} ${node.shortClass}" + (node.text.takeIf { it.isNotBlank() } ?: node.contentDescription.takeIf { it.isNotBlank() })?.let { " \"$it\"" }.orEmpty()

    private fun fraction(
        connection: DeviceDriver,
        x: Int,
        y: Int,
    ): Pair<Float?, Float?> {
        val shown = connection.lastShownSize ?: return null to null
        return (x.toFloat() / shown.width).coerceIn(0f, 1f) to (y.toFloat() / shown.height).coerceIn(0f, 1f)
    }

    private suspend fun tap(
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val connection = devices.connection(device.serial)
        val (point, what) = target(device, args, connection)
        val (x, y) = point
        val long = args.bool("long_press") == true
        if (long) connection.longPress(x, y, LONG_PRESS_MS) else connection.tap(x, y)
        val (fx, fy) = fraction(connection, x, y)
        devices.report(DeviceActivity(device.serial, if (long) DeviceActivity.Kind.LONG_PRESS else DeviceActivity.Kind.TAP, fx, fy, label = what))
        return after(device, args, "${if (long) "Long-pressed" else "Tapped"} $what")
    }

    private suspend fun swipe(
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val connection = devices.connection(device.serial)
        val scale = connection.scale()
        val duration = (args.int("duration_ms") ?: SWIPE_MS).coerceIn(50, 5_000)
        val direction = args.str("direction")?.lowercase()
        val (from, to) =
            if (direction != null) {
                // Inside an element, or across the middle of the screen, away from the edges the
                // system takes as back and home gestures.
                val area =
                    args.int("element")?.let { id -> trees[device.serial]?.node(id)?.bounds ?: throw IllegalArgumentException("No element #$id in the last ui_tree") }
                        ?: (connection.lastShownSize ?: connection.screenSize()).let { s -> Bounds(0, 0, s.width, s.height) }
                val w = area.right - area.left
                val h = area.bottom - area.top
                val cx = area.centerX
                val cy = area.centerY
                when (direction) {
                    "up" -> (cx to area.top + (h * 0.75).roundToInt()) to (cx to area.top + (h * 0.25).roundToInt())
                    "down" -> (cx to area.top + (h * 0.25).roundToInt()) to (cx to area.top + (h * 0.75).roundToInt())
                    "left" -> (area.left + (w * 0.8).roundToInt() to cy) to (area.left + (w * 0.2).roundToInt() to cy)
                    "right" -> (area.left + (w * 0.2).roundToInt() to cy) to (area.left + (w * 0.8).roundToInt() to cy)
                    else -> throw IllegalArgumentException("direction is up, down, left or right")
                }
            } else {
                fun coord(name: String) = ((args.int(name) ?: throw IllegalArgumentException("Give direction, or x1, y1, x2 and y2")) / scale).roundToInt()
                (coord("x1") to coord("y1")) to (coord("x2") to coord("y2"))
            }
        connection.swipe(from.first, from.second, to.first, to.second, duration)
        val (fx, fy) = fraction(connection, from.first, from.second)
        val (tx, ty) = fraction(connection, to.first, to.second)
        devices.report(DeviceActivity(device.serial, DeviceActivity.Kind.SWIPE, fx, fy, tx, ty, label = direction ?: "swipe"))
        return after(device, args, "Swiped ${direction ?: "from (${args.int("x1")}, ${args.int("y1")}) to (${args.int("x2")}, ${args.int("y2")})"}")
    }

    private suspend fun type(
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val text = args.str("text") ?: throw IllegalArgumentException("text is required")
        val connection = devices.connection(device.serial)
        if (args.int("element") != null || args.str("into") != null) {
            val focus = if (args.int("element") != null) args else JsonObject(mapOf("text" to args["into"]!!))
            val (point, _) = target(device, focus, connection)
            connection.tap(point.first, point.second)
            delay(FOCUS_MS)
        }
        connection.type(text)
        if (args.bool("submit") == true) connection.press(DeviceKey.ENTER)
        devices.report(DeviceActivity(device.serial, DeviceActivity.Kind.TYPE, label = text.take(40)))
        return after(device, args, "Typed ${text.length} characters${if (args.bool("submit") == true) " and pressed Enter" else ""}")
    }

    private suspend fun key(
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val name = args.str("key") ?: throw IllegalArgumentException("key is required")
        val key = DeviceKey.named(name) ?: throw IllegalArgumentException("No key $name. Keys: ${DeviceKey.entries.joinToString { it.name.lowercase() }}")
        val connection = devices.connection(device.serial)
        connection.press(key, longPress = args.bool("long_press") == true)
        devices.report(DeviceActivity(device.serial, DeviceActivity.Kind.KEY, label = key.name.lowercase()))
        return after(device, args, "Pressed ${key.name.lowercase()}")
    }

    private suspend fun rotate(
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val turns =
            when (args.str("orientation")?.lowercase()?.replace('-', '_')) {
                "portrait" -> 0
                "landscape" -> 1
                "reverse_portrait", "upside_down" -> 2
                "reverse_landscape" -> 3
                else -> throw IllegalArgumentException("orientation is portrait, landscape, reverse_portrait or reverse_landscape")
            }
        devices.connection(device.serial).rotate(turns)
        return after(device, args, "Turned to ${args.str("orientation")}", defaultShot = false)
    }

    private suspend fun waitFor(
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val text = args.str("text") ?: throw IllegalArgumentException("text is required")
        val gone = args.bool("gone") == true
        val timeout = (args.int("timeout_ms") ?: 10_000).coerceIn(500, 60_000)
        val connection = devices.connection(device.serial)
        val started = System.currentTimeMillis()
        var lastError: String? = null
        while (System.currentTimeMillis() - started < timeout) {
            try {
                val tree = connection.uiTree()
                trees[device.serial] = tree
                if (tree.contains(text) != gone) {
                    val waited = System.currentTimeMillis() - started
                    return after(device, args, "\"$text\" ${if (gone) "is gone" else "is on screen"} after ${waited}ms")
                }
            } catch (e: IOException) {
                // Mid-animation, uiautomator can't read the screen: try again.
                lastError = e.message
            }
            delay(WAIT_POLL_MS)
        }
        return HostToolResult("\"$text\" ${if (gone) "is still on screen" else "didn't appear"} after ${timeout}ms${lastError?.let { " ($it)" }.orEmpty()}", isError = true)
    }

    private suspend fun runApp(
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val runner = runners.firstOrNull { it.platform == device.platform } ?: throw IOException("This computer can't build apps for ${device.name}")
        val targets = runner.targets(folder)
        if (targets.isEmpty()) {
            throw IOException(
                if (device.isIos) {
                    "No Xcode project in ${folder.path}: build the app with xcodebuild and use $INSTALL_APP"
                } else {
                    "No Android app module in ${folder.path}: build it with Gradle and use $INSTALL_APP"
                },
            )
        }
        // app names the module (`:amethyst`) or project; variant the Android variant or Xcode scheme.
        val app = args.str("app") ?: args.str("module")
        val variant = args.str("variant") ?: args.str("scheme")
        val target =
            app?.let { a -> targets.firstOrNull { it.label == a || it.label == a.removePrefix(":") || (it is AppTarget.Android && it.target.module == a) } }
                ?: variant?.let { v -> targets.firstOrNull { v in it.variants } }
                ?: app?.let { throw IOException("No app $it. There are: ${targets.joinToString { t -> t.label }}") }
                ?: targets.first()
        val chosen = variant?.takeIf { it in target.variants } ?: variant?.let { throw IOException("${target.label} has no $it. It has: ${target.variants.joinToString()}") } ?: target.variants.first()
        val id = runner.run(target, chosen, device.serial)
        openedApp[device.serial] = id
        return after(device, args, "Built ${target.label} $chosen, installed and opened $id on ${device.name}", defaultShot = true)
    }

    private suspend fun log(
        device: DeviceInfo,
        args: JsonObject,
    ): HostToolResult {
        val lines = (args.int("lines") ?: 200).coerceIn(10, 2_000)
        val level =
            args
                .str("level")
                ?.firstOrNull()
                ?.uppercaseChar()
                ?.takeIf { it in "VDIWEF" } ?: 'V'
        val read = devices.connection(device.serial).log(lines, args.str("package"), level, args.bool("crashes_only") == true)
        val log = read.text.let { if (it.length > MAX_LOG_CHARS) "…" + it.takeLast(MAX_LOG_CHARS) else it }
        return HostToolResult("${read.head} of ${device.name}, last $lines lines:\n" + log.ifBlank { "(empty)" })
    }

    companion object {
        const val NAME = "device"

        const val LIST = "list_devices"
        const val START_DEVICE = "start_device"
        const val STOP_DEVICE = "stop_device"
        const val SELECT = "select_device"
        const val SCREENSHOT = "screenshot"
        const val UI_TREE = "ui_tree"
        const val TAP = "tap"
        const val SWIPE = "swipe"
        const val TYPE = "type_text"
        const val KEY = "press_key"
        const val ROTATE = "rotate"
        const val WAIT_FOR = "wait_for"
        const val OPEN_URL = "open_url"
        const val INSTALL_APP = "install_app"
        const val LAUNCH_APP = "launch_app"
        const val STOP_APP = "stop_app"
        const val CLEAR_DATA = "clear_app_data"
        const val RUN_APP = "run_app"
        const val LOG = "device_log"
        const val RESET = "reset_device"

        /** The tools a crash check would only slow down: they don't touch the app. */
        private val WITHOUT_CRASH_CHECK = setOf(SELECT, STOP_DEVICE, LOG)

        /** The tools that end the app on purpose: its process going is no news. */
        private val STOPS_APP = setOf(STOP_APP, CLEAR_DATA, INSTALL_APP, RESET, RUN_APP)

        private const val TAG = "DeviceToolServer"

        private const val SETTLE_MS = 800
        private const val MAX_SETTLE_MS = 10_000
        private const val LONG_PRESS_MS = 800
        private const val SWIPE_MS = 300
        private const val FOCUS_MS = 300L
        private const val WAIT_POLL_MS = 700L
        private const val MAX_LOG_CHARS = 24_000

        /** What runs without asking: looking, touching, typing on the device the user gave the chat. Building, installing, erasing and shutting down ask. */
        val PRE_APPROVED = listOf(LIST, START_DEVICE, SELECT, SCREENSHOT, UI_TREE, TAP, SWIPE, TYPE, KEY, ROTATE, WAIT_FOR, OPEN_URL, LAUNCH_APP, STOP_APP, LOG, RESET)

        /** How the brief tells a coding agent about the device. */
        const val BRIEF =
            "A device panel sits beside this chat. The $NAME tools ($LIST, $START_DEVICE, $RUN_APP, $SCREENSHOT, $UI_TREE, $TAP, " +
                "$TYPE, $SWIPE, $KEY, $WAIT_FOR, $LOG) drive the Android emulator or phone, or the iOS simulator, it shows, and the user watches what you do there. " +
                "To check a change: $RUN_APP (Gradle for Android, xcodebuild for iOS; or build yourself and $INSTALL_APP), then look with $UI_TREE or $SCREENSHOT, and act. " +
                "Every device tool's result starts with any crash or ANR since your last one, so a clean result means no app crashed. " +
                "An emulator or simulator you start is this workstream's own copy; $RESET puts it back to where it started."

        private fun schema(json: String) = Json.parseToJsonElement(json).jsonObject

        private const val DEVICE = """"device":{"type":"string","description":"Serial or name; omit for this chat's device"}"""
        private const val SHOT = """"screenshot":{"type":"boolean","description":"Return a screenshot once the screen settles"},"settle_ms":{"type":"integer","description":"How long to wait before that screenshot (default 800)"}"""

        val TOOLS =
            listOf(
                HostTool(
                    LIST,
                    "Lists the devices: running Android emulators, connected phones and booted iOS simulators, then the virtual devices $START_DEVICE can start (Android AVDs, iOS simulators).",
                    schema("""{"type":"object","properties":{}}"""),
                ),
                HostTool(
                    START_DEVICE,
                    "Starts this workstream's own copy of a virtual device in the device panel and waits until it is ready (up to a few minutes): an Android emulator (AVD), which boots from the state " +
                        "the AVD last saved, or an iOS simulator (macOS with Xcode), booted without a Simulator.app window from what that simulator holds. The copy is this workstream's alone and " +
                        "its changes go when it stops; one already running for this workstream is reused. Without name, the newest phone of the platform this chat's folder builds for.",
                    schema(
                        """{"type":"object","properties":{"name":{"type":"string","description":"A name or id from list_devices"},"platform":{"type":"string","enum":["android","ios"]},"cold_boot":{"type":"boolean","description":"Android: without the saved state"},"wipe_data":{"type":"boolean","description":"Android: from factory state"}}}""",
                    ),
                ),
                HostTool(
                    RESET,
                    "Puts this workstream's own emulator or simulator copy back to where it started: an emulator to the state its AVD last saved, a simulator to a fresh copy of the one it copies (logged-in accounts, installed apps and data as they were then). " +
                        "Only works on the copy $START_DEVICE gave this workstream. Returns a screenshot.",
                    schema("""{"type":"object","properties":{$DEVICE,$SHOT}}"""),
                ),
                HostTool(STOP_DEVICE, "Shuts an emulator or a simulator down; a workstream's copy goes with its changes.", schema("""{"type":"object","properties":{$DEVICE}}""")),
                HostTool(SELECT, "Makes a device this chat's device: the panel shows it, and the other tools use it when no device is given.", schema("""{"type":"object","properties":{$DEVICE},"required":["device"]}""")),
                HostTool(
                    SCREENSHOT,
                    "A picture of the device's screen. Every x/y in the device tools is in this picture's pixels.",
                    schema("""{"type":"object","properties":{$DEVICE}}"""),
                ),
                HostTool(
                    UI_TREE,
                    "The screen as text: each view that shows text, has a description or can be tapped, scrolled or typed in, numbered, with its bounds in screenshot pixels. " +
                        "Cheaper and more exact than a screenshot for finding what to tap; Compose screens show their text and content descriptions, iOS screens their accessibility labels, values and identifiers.",
                    schema("""{"type":"object","properties":{$DEVICE}}"""),
                ),
                HostTool(
                    TAP,
                    "Taps the screen: an element number from the last ui_tree, a text it shows (or its description or id), or x/y in screenshot pixels.",
                    schema(
                        """{"type":"object","properties":{"element":{"type":"integer"},"text":{"type":"string"},"x":{"type":"integer"},"y":{"type":"integer"},"long_press":{"type":"boolean"},$DEVICE,$SHOT}}""",
                    ),
                ),
                HostTool(
                    SWIPE,
                    "Swipes: a direction the finger moves (up scrolls down to show what is below), over an element or the screen's middle; or from x1/y1 to x2/y2 in screenshot pixels.",
                    schema(
                        """{"type":"object","properties":{"direction":{"type":"string","enum":["up","down","left","right"]},"element":{"type":"integer"},"x1":{"type":"integer"},"y1":{"type":"integer"},"x2":{"type":"integer"},"y2":{"type":"integer"},"duration_ms":{"type":"integer"},$DEVICE,$SHOT}}""",
                    ),
                ),
                HostTool(
                    TYPE,
                    "Types text into the focused field, or first taps the field given by element or into (a text it shows). Any text on an emulator or a simulator; ASCII only on a phone.",
                    schema(
                        """{"type":"object","properties":{"text":{"type":"string"},"element":{"type":"integer"},"into":{"type":"string"},"submit":{"type":"boolean","description":"Press Enter after"},$DEVICE,$SHOT},"required":["text"]}""",
                    ),
                ),
                HostTool(
                    KEY,
                    "Presses a key: back, home, recents, power, volume_up, volume_down, enter, backspace, delete, tab, escape, up, down, left, right, page_up, page_down, paste, wake. " +
                        "On iOS, back is a swipe in from the left edge, recents is two presses of home, and power is the side button.",
                    schema("""{"type":"object","properties":{"key":{"type":"string"},"long_press":{"type":"boolean"},$DEVICE,$SHOT},"required":["key"]}"""),
                ),
                HostTool(
                    ROTATE,
                    "Turns the device. An emulator or a simulator turns as its own rotate button turns it (apps locked to portrait stay so); a phone gets the rotation locked.",
                    schema("""{"type":"object","properties":{"orientation":{"type":"string","enum":["portrait","landscape","reverse_portrait","reverse_landscape"]},$DEVICE,$SHOT},"required":["orientation"]}"""),
                ),
                HostTool(
                    WAIT_FOR,
                    "Waits until a text shows on screen (or, with gone, until it no longer does), up to timeout_ms (default 10000, at most 60000).",
                    schema("""{"type":"object","properties":{"text":{"type":"string"},"gone":{"type":"boolean"},"timeout_ms":{"type":"integer"},$DEVICE,$SHOT},"required":["text"]}"""),
                ),
                HostTool(
                    OPEN_URL,
                    "Opens a URL or deep link on the device (https:, nostr:, an app's own scheme), optionally in one Android package.",
                    schema("""{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},$DEVICE,$SHOT},"required":["url"]}"""),
                ),
                HostTool(
                    INSTALL_APP,
                    "Installs (or updates) an app: an APK on Android, a simulator build's .app on iOS. A relative path is from this chat's folder.",
                    schema("""{"type":"object","properties":{"path":{"type":"string"},$DEVICE},"required":["path"]}"""),
                ),
                HostTool(LAUNCH_APP, "Opens an installed app by its package name (Android) or bundle id (iOS) and returns a screenshot.", schema("""{"type":"object","properties":{"package":{"type":"string","description":"Package name or bundle id"},$DEVICE,$SHOT},"required":["package"]}""")),
                HostTool(STOP_APP, "Force-stops an app.", schema("""{"type":"object","properties":{"package":{"type":"string","description":"Package name or bundle id"},$DEVICE},"required":["package"]}""")),
                HostTool(
                    CLEAR_DATA,
                    "Erases an app's data on the device, as a fresh install would have it (on iOS it is reinstalled from its own bundle; keychain items stay).",
                    schema("""{"type":"object","properties":{"package":{"type":"string","description":"Package name or bundle id"},$DEVICE},"required":["package"]}"""),
                ),
                HostTool(
                    RUN_APP,
                    "Builds this chat's app for the device, installs it and opens it, like Android Studio's or Xcode's Run; returns a screenshot. " +
                        "Android: a debug variant, with the build's Gradle wrapper. iOS simulator: a scheme's Debug configuration of an Xcode project or workspace in the folder " +
                        "(an iosApp/ of a Kotlin Multiplatform build too), with xcodebuild. Without app/variant, the first app and its first variant or scheme. Takes minutes on a first build.",
                    schema(
                        """{"type":"object","properties":{"app":{"type":"string","description":"Android: the Gradle module, e.g. :amethyst; iOS: the Xcode project"},"variant":{"type":"string","description":"Android: e.g. playDebug; iOS: the scheme"},$DEVICE,$SHOT}}""",
                    ),
                ),
                HostTool(
                    LOG,
                    "Reads the device log (Android's logcat; an iOS simulator's unified log of the last minutes): the last lines, of one app's process when package (or bundle id) is given, at level and above (V, D, I, W, E, F); " +
                        "crashes_only reads just the stack traces of apps that crashed (on iOS, the latest crash reports).",
                    schema(
                        """{"type":"object","properties":{"package":{"type":"string"},"lines":{"type":"integer"},"level":{"type":"string"},"crashes_only":{"type":"boolean"},$DEVICE}}""",
                    ),
                ),
            )

        private fun JsonObject.str(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.roundToInt() }

        private fun JsonObject.bool(name: String): Boolean? = this[name]?.jsonPrimitive?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }
    }
}
