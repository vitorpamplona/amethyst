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
package com.vitorpamplona.androiddevice.adb

import com.vitorpamplona.androiddevice.CommandResult
import com.vitorpamplona.androiddevice.CommandRunner
import com.vitorpamplona.androiddevice.ProcessCommandRunner
import java.io.File
import java.io.IOException

/** A device `adb devices -l` lists. [state] is adb's word: `device` (ready), `offline`, `unauthorized`… */
data class AdbDevice(
    val serial: String,
    val state: String,
    val model: String?,
    val product: String?,
) {
    val isOnline: Boolean get() = state == "device"
    val isEmulator: Boolean get() = serial.startsWith("emulator-")

    /** The console port of an emulator (`emulator-5554` → 5554). */
    val consolePort: Int? get() = if (isEmulator) serial.removePrefix("emulator-").toIntOrNull() else null
}

/** A key every Android device has, by its `KeyEvent` code (adb) and its W3C name (the emulator's gRPC). */
enum class DeviceKey(
    val keyCode: Int,
    val w3c: String,
) {
    BACK(4, "GoBack"),
    HOME(3, "GoHome"),
    RECENTS(187, "AppSwitch"),
    POWER(26, "Power"),
    VOLUME_UP(24, "AudioVolumeUp"),
    VOLUME_DOWN(25, "AudioVolumeDown"),
    ENTER(66, "Enter"),
    BACKSPACE(67, "Backspace"),
    DELETE(112, "Delete"),
    TAB(61, "Tab"),
    ESCAPE(111, "Escape"),
    UP(19, "ArrowUp"),
    DOWN(20, "ArrowDown"),
    LEFT(21, "ArrowLeft"),
    RIGHT(22, "ArrowRight"),
    MOVE_HOME(122, "Home"),
    MOVE_END(123, "End"),
    PAGE_UP(92, "PageUp"),
    PAGE_DOWN(93, "PageDown"),
    PASTE(279, "Paste"),
    WAKE(224, "WakeUp"),
    ;

    companion object {
        /** By its name, case- and separator-insensitive ("volume_up", "Volume Up", "back"). */
        fun named(name: String): DeviceKey? {
            val n =
                name
                    .trim()
                    .uppercase()
                    .replace(' ', '_')
                    .replace('-', '_')
            return entries.firstOrNull { it.name == n }
                ?: when (n) {
                    "APP_SWITCH", "OVERVIEW" -> RECENTS
                    "DEL" -> BACKSPACE
                    "RETURN" -> ENTER
                    "ESC" -> ESCAPE
                    "WAKEUP" -> WAKE
                    else -> null
                }
        }
    }
}

/** A size in pixels. */
data class ScreenSize(
    val width: Int,
    val height: Int,
) {
    fun swapped() = ScreenSize(height, width)
}

/** A screenshot as RGBA pixels, row by row from the top. */
class RawScreen(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
)

/** What `wm size` says, both in the natural orientation. */
data class WmSize(
    val physical: ScreenSize,
    val logical: ScreenSize,
)

/**
 * The SDK's `adb`, run once per call: the device list, a shell on a device, installs, input,
 * screenshots, logcat and the UI tree (uiautomator). Works with every device, emulators and phones
 * alike; the emulator's gRPC endpoint ([com.vitorpamplona.androiddevice.emulator.EmulatorController])
 * is only the fast path for its screen and input.
 */
class Adb(
    val executable: File,
    private val runner: CommandRunner = ProcessCommandRunner,
) {
    private suspend fun adb(
        args: List<String>,
        timeoutMs: Long = 30_000,
    ): CommandResult = runner.run(listOf(executable.absolutePath) + args, timeoutMs)

    private suspend fun checked(
        args: List<String>,
        timeoutMs: Long = 30_000,
    ): CommandResult {
        val result = adb(args, timeoutMs)
        if (result.exitCode == CommandRunner.TIMED_OUT) throw IOException("adb ${args.joinToString(" ")} timed out")
        if (!result.ok) {
            throw IOException(
                result.stderr
                    .trim()
                    .ifEmpty { result.text.trim() }
                    .ifEmpty { "adb exited with ${result.exitCode}" },
            )
        }
        return result
    }

    suspend fun devices(): List<AdbDevice> = parseDevices(checked(listOf("devices", "-l"), 10_000).text)

    /**
     * Runs [command] in the device's shell and returns what it printed. Arguments are quoted for
     * the device's `sh`, so spaces and symbols arrive as given.
     */
    suspend fun shell(
        serial: String,
        vararg command: String,
        timeoutMs: Long = 30_000,
    ): String = checked(listOf("-s", serial, "shell", command.joinToString(" ") { quote(it) }), timeoutMs).text

    /** Like [shell], but the raw bytes of what it printed (a PNG), with no newline translation. */
    suspend fun execOut(
        serial: String,
        vararg command: String,
        timeoutMs: Long = 30_000,
    ): ByteArray = checked(listOf("-s", serial, "exec-out", command.joinToString(" ") { quote(it) }), timeoutMs).stdout

    /**
     * The screen as raw RGBA pixels (`screencap` without `-p`): no PNG encode on the device, which
     * is most of a PNG screenshot's time. Ten megabytes for a 1080x2400 screen, so it pays where
     * the transfer is cheap (an emulator, over the local socket), not over USB.
     */
    suspend fun screencapRaw(serial: String): RawScreen = parseRawScreencap(execOut(serial, "screencap", timeoutMs = 15_000))

    suspend fun screencapPng(serial: String): ByteArray {
        val png = execOut(serial, "screencap", "-p", timeoutMs = 15_000)
        if (png.size < 8 || png[1] != 'P'.code.toByte() || png[2] != 'N'.code.toByte()) throw IOException("The device sent no picture of its screen")
        return png
    }

    /**
     * The panel's size in its natural orientation: [WmSize.physical] is what the emulator's touches
     * are measured in; [WmSize.logical] (an override `wm size` set, else the same) is what apps,
     * `input tap` and uiautomator use.
     */
    suspend fun screenSize(serial: String): WmSize = parseWmSize(shell(serial, "wm", "size", timeoutMs = 10_000)) ?: throw IOException("The device didn't say its screen size")

    suspend fun tap(
        serial: String,
        x: Int,
        y: Int,
    ) {
        shell(serial, "input", "tap", x.toString(), y.toString())
    }

    suspend fun swipe(
        serial: String,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int,
    ) {
        shell(serial, "input", "swipe", x1.toString(), y1.toString(), x2.toString(), y2.toString(), durationMs.toString())
    }

    suspend fun key(
        serial: String,
        key: DeviceKey,
        longPress: Boolean = false,
    ) {
        if (longPress) shell(serial, "input", "keyevent", "--longpress", key.keyCode.toString()) else shell(serial, "input", "keyevent", key.keyCode.toString())
    }

    /**
     * Types [text] into the focused field. `input text` takes no spaces (they go as `%s`) and only
     * ASCII; anything else throws, so the caller can paste it instead.
     */
    suspend fun type(
        serial: String,
        text: String,
    ) {
        if (text.any { it.code !in 0x20..0x7e && it != '\n' }) throw IOException("adb can only type ASCII text")
        // A newline is Enter: `input text` would drop it.
        text.split('\n').forEachIndexed { i, line ->
            if (i > 0) key(serial, DeviceKey.ENTER)
            if (line.isNotEmpty()) shell(serial, "input", "text", line.replace(" ", "%s"))
        }
    }

    suspend fun install(
        serial: String,
        apk: File,
    ): String = checked(listOf("-s", serial, "install", "-r", "-t", apk.absolutePath), INSTALL_TIMEOUT_MS).text.trim()

    /** Opens [packageName]'s launcher activity, as tapping its icon would. */
    suspend fun launch(
        serial: String,
        packageName: String,
    ) {
        val out = shell(serial, "monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1")
        if (out.contains("No activities found", ignoreCase = true) || out.contains("monkey aborted", ignoreCase = true)) {
            throw IOException("$packageName has no launcher activity on the device, or isn't installed")
        }
    }

    suspend fun forceStop(
        serial: String,
        packageName: String,
    ) {
        shell(serial, "am", "force-stop", packageName)
    }

    suspend fun clearData(
        serial: String,
        packageName: String,
    ): String = shell(serial, "pm", "clear", packageName).trim()

    /** Opens [url] with whatever handles it (a browser, or an app's deep link, `nostr:` included). */
    suspend fun openUrl(
        serial: String,
        url: String,
        packageName: String? = null,
    ): String =
        if (packageName != null) {
            shell(serial, "am", "start", "-a", "android.intent.action.VIEW", "-d", url, packageName)
        } else {
            shell(serial, "am", "start", "-a", "android.intent.action.VIEW", "-d", url)
        }.trim()

    suspend fun pidOf(
        serial: String,
        packageName: String,
    ): Int? {
        val name = quote(packageName)
        // Android before 7 has no pidof ("not found"): its ps lists every process, the name last.
        val out = adb(listOf("-s", serial, "shell", "pidof $name 2>/dev/null || ps | grep -F $name"), 10_000).text
        return parsePid(out, packageName)
    }

    /**
     * The last [lines] of the log: only [pid]'s when given, only [minLevel] and above (V, D, I,
     * W, E, F). [buffer] `crash` holds just the stack traces of apps that crashed.
     */
    suspend fun logcat(
        serial: String,
        lines: Int = 200,
        pid: Int? = null,
        minLevel: Char = 'V',
        buffer: String? = null,
    ): String {
        val args =
            buildList {
                addAll(listOf("-s", serial, "logcat", "-d", "-v", "threadtime", "-t", lines.toString()))
                if (buffer != null) addAll(listOf("-b", buffer))
                if (pid != null) add("--pid=$pid")
                if (minLevel != 'V') add("*:$minLevel")
            }
        return checked(args, 20_000).text
    }

    /**
     * The crashes and ANRs the device's log still holds, oldest first: the `crash` buffer (Java
     * and native crashes) and the system buffer's `ActivityManager` errors (ANRs), read in one
     * shell call, since a busy system buffer would push the crash lines out of one merged tail.
     */
    suspend fun crashes(serial: String): List<DeviceCrash> {
        val script = "logcat -d -v threadtime -b crash -t $CRASH_LINES; logcat -d -v threadtime -b system -t $ANR_LINES ActivityManager:E '*:S'"
        return CrashLog.parse(checked(listOf("-s", serial, "shell", script), 20_000).text)
    }

    /** Starts `adb logcat` streaming for [serial]; the caller reads and stops the process. */
    fun logcatStream(
        serial: String,
        pid: Int? = null,
    ): Process =
        runner.start(
            buildList {
                addAll(listOf(executable.absolutePath, "-s", serial, "logcat", "-v", "threadtime", "-T", "200"))
                if (pid != null) add("--pid=$pid")
            },
        )

    /** The window hierarchy, as uiautomator writes it (XML). Fails while the screen animates, as uiautomator does. */
    suspend fun uiDump(serial: String): String {
        val direct = adb(listOf("-s", serial, "exec-out", "uiautomator dump /dev/tty"), 30_000).stdout.toString(Charsets.UTF_8)
        hierarchyIn(direct)?.let { return it }
        // Some images can't write to /dev/tty: through a file instead.
        val path = "/data/local/tmp/amethyst_ui.xml"
        val dumped = shell(serial, "uiautomator", "dump", path)
        val xml = execOut(serial, "cat", path).toString(Charsets.UTF_8)
        return hierarchyIn(xml) ?: throw IOException(dumped.trim().ifEmpty { direct.trim() }.ifEmpty { "uiautomator gave no hierarchy" })
    }

    suspend fun getprop(
        serial: String,
        name: String,
    ): String = shell(serial, "getprop", name, timeoutMs = 10_000).trim()

    suspend fun bootCompleted(serial: String): Boolean =
        try {
            getprop(serial, "sys.boot_completed") == "1"
        } catch (_: IOException) {
            false
        }

    /** Turns the screen to [quarterTurns] (0 portrait … 3), with auto-rotate off so it stays. */
    suspend fun rotate(
        serial: String,
        quarterTurns: Int,
    ) {
        shell(serial, "settings", "put", "system", "accelerometer_rotation", "0")
        shell(serial, "settings", "put", "system", "user_rotation", (quarterTurns and 3).toString())
    }

    /**
     * How Android has turned its screen now, in quarter turns: what uiautomator's bounds, `screencap`
     * and `input` use. It can differ from how the device is held (an app locked to landscape on a
     * portrait emulator). Null when the device doesn't say.
     */
    suspend fun displayRotation(serial: String): Int? = parseDisplayRotation(shell(serial, "dumpsys", "input"))

    /** Auto-rotate on: Android turns its screen as the device's sensor says (an emulator's rotate). */
    suspend fun followSensor(serial: String) {
        shell(serial, "settings", "put", "system", "accelerometer_rotation", "1")
    }

    suspend fun rotation(serial: String): Int = shell(serial, "settings", "get", "system", "user_rotation").trim().toIntOrNull()?.and(3) ?: 0

    /** Asks an emulator to quit, through its console. */
    suspend fun emuKill(serial: String) {
        adb(listOf("-s", serial, "emu", "kill"), 15_000)
    }

    companion object {
        private const val INSTALL_TIMEOUT_MS = 5 * 60_000L
        private val WHITESPACE = Regex("\\s+")
        private const val CRASH_LINES = 400
        private const val ANR_LINES = 200

        /** The screen's rotation from `dumpsys input`: the touchscreen's `SurfaceOrientation`, else the default viewport's. */
        fun parseDisplayRotation(dump: String): Int? =
            (SURFACE_ORIENTATION.find(dump) ?: VIEWPORT_ORIENTATION.find(dump))
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.and(3)

        private val SURFACE_ORIENTATION = Regex("SurfaceOrientation:\\s*(\\d)")
        private val VIEWPORT_ORIENTATION = Regex("(?:mDefaultViewport|Viewport INTERNAL)[^\\n]*?orientation=(\\d)")

        /** A pid from `pidof`'s answer (pids alone), or the `ps` line whose name is [packageName]. */
        fun parsePid(
            out: String,
            packageName: String,
        ): Int? =
            out.lineSequence().map { it.trim().split(WHITESPACE) }.firstNotNullOfOrNull { cols ->
                when {
                    cols.size == 1 || cols.all { c -> c.all(Char::isDigit) } -> cols.first().toIntOrNull()
                    cols.last() == packageName -> cols.getOrNull(1)?.toIntOrNull()
                    else -> null
                }
            }

        fun parseDevices(out: String): List<AdbDevice> =
            out
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("List of devices") && !it.startsWith("*") && !it.startsWith("adb ") }
                .mapNotNull { line ->
                    val parts = line.split(WHITESPACE)
                    if (parts.size < 2) return@mapNotNull null
                    // "no permissions (…)" is two words and more; everything after the serial up to the
                    // first key:value is the state.
                    val rest = parts.drop(1)
                    val state = rest.takeWhile { !it.contains(':') }.joinToString(" ")
                    val props = rest.filter { it.contains(':') }.associate { it.substringBefore(':') to it.substringAfter(':') }
                    AdbDevice(parts[0], state, props["model"]?.replace('_', ' '), props["product"])
                }.toList()

        /**
         * `screencap`'s raw output: width, height and pixel format as little-endian ints, a fourth
         * int (the color space) on newer Androids, then the pixels. The header's length is what's
         * left once the pixels are counted, so both layouts read the same.
         */
        fun parseRawScreencap(bytes: ByteArray): RawScreen {
            if (bytes.size < 12) throw IOException("The device sent no picture of its screen")

            fun int(at: Int) = (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8) or ((bytes[at + 2].toInt() and 0xff) shl 16) or ((bytes[at + 3].toInt() and 0xff) shl 24)
            val width = int(0)
            val height = int(4)
            val format = int(8)
            val pixels = width.toLong() * height * 4
            val header = bytes.size - pixels
            // Format 1 is RGBA_8888, what every emulator image writes.
            if (width <= 0 || height <= 0 || format != 1 || header !in 12L..16L) throw IOException("Unexpected raw screenshot (${width}x$height, format $format)")
            return RawScreen(width, height, bytes.copyOfRange(header.toInt(), bytes.size))
        }

        fun parseWmSize(out: String): WmSize? {
            fun sizeAfter(label: String) = Regex("$label size:\\s*(\\d+)x(\\d+)").find(out)?.let { ScreenSize(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            val physical = sizeAfter("Physical") ?: return null
            return WmSize(physical, sizeAfter("Override") ?: physical)
        }

        /** The `<hierarchy>` element in what uiautomator printed, or null. */
        fun hierarchyIn(out: String): String? {
            val start = out.indexOf("<?xml").takeIf { it >= 0 } ?: out.indexOf("<hierarchy").takeIf { it >= 0 } ?: return null
            val end = out.lastIndexOf("</hierarchy>").takeIf { it >= 0 } ?: return null
            return out.substring(start, end + "</hierarchy>".length)
        }

        /** [arg] quoted for the device's `sh`: plain words stay as they are. */
        fun quote(arg: String): String = if (arg.isNotEmpty() && arg.all { it.isLetterOrDigit() || it in SAFE }) arg else "'" + arg.replace("'", "'\\''") + "'"

        private const val SAFE = "-_./:=,%@+"
    }
}
