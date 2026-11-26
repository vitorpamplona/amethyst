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
package com.vitorpamplona.iossimulator.simctl

import com.vitorpamplona.devicecore.CommandResult
import com.vitorpamplona.devicecore.CommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException

/** One simulator `simctl list` names. */
data class SimDevice(
    val udid: String,
    val name: String,
    /** "Booted", "Shutdown", "Booting", "Shutting Down", "Creating". */
    val state: String,
    /** `com.apple.CoreSimulator.SimRuntime.iOS-26-0`. */
    val runtimeId: String,
    /** "iOS 26.0", or the identifier's tail when the runtime isn't installed any more. */
    val runtimeName: String,
    /** `com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro`. */
    val deviceType: String,
    /** False when its runtime or device type is gone (Xcode was updated without it). */
    val isAvailable: Boolean,
    val dataPath: String? = null,
) {
    val isBooted: Boolean get() = state == "Booted"
    val isShutdown: Boolean get() = state == "Shutdown"

    /** An iPhone or iPad (iOS runtime), not a watch, TV or Vision Pro. */
    val isIos: Boolean get() = runtimeId.contains(".SimRuntime.iOS-")

    val isIpad: Boolean get() = deviceType.contains("iPad", ignoreCase = true) || name.startsWith("iPad")

    /** "26.0" from the runtime, for sorting newest first. */
    val osVersion: String get() = runtimeName.substringAfterLast(' ')
}

/** An installed simulator runtime. */
data class SimRuntime(
    val identifier: String,
    val name: String,
    val version: String,
    val isAvailable: Boolean,
)

/** What `simctl list -j` says. */
data class SimctlList(
    val devices: List<SimDevice>,
    val runtimes: List<SimRuntime>,
)

/**
 * Apple's `xcrun simctl`: the simulators that exist, and their lives. Every call is one run of the
 * program; failures are [IOException]s carrying what simctl said.
 */
class Simctl(
    private val xcrun: File,
    private val runner: CommandRunner,
    /** Added to simctl's environment (a stand-in's settings). */
    private val environment: Map<String, String> = emptyMap(),
) {
    private suspend fun simctl(
        vararg args: String,
        timeoutMs: Long = 30_000,
        allow: (CommandResult) -> Boolean = { false },
    ): CommandResult {
        val result = runner.run(listOf(xcrun.path, "simctl") + args, timeoutMs, environment)
        if (result.exitCode == CommandRunner.TIMED_OUT) throw IOException("simctl ${args.first()} didn't finish in ${timeoutMs / 1000}s")
        if (!result.ok && !allow(result)) throw IOException("simctl ${args.first()}: ${said(result)}")
        return result
    }

    private fun said(result: CommandResult) =
        result.stderr
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { result.text.trim() }
            .ifEmpty { "exit code ${result.exitCode}" }

    suspend fun list(): SimctlList {
        // Everything at once: a second kind after the first would be read as a search term.
        val out = simctl("list", "-j").text
        return withContext(Dispatchers.Default) { parseList(out) }
    }

    /** Boots [udid] (headless: no Simulator.app window) and waits until it is ready, or [timeoutMs]. */
    suspend fun bootAndWait(
        udid: String,
        timeoutMs: Long = 5 * 60_000,
    ) {
        // `bootstatus -b` boots it when it isn't, then waits for its services: the screen is up after.
        simctl("bootstatus", udid, "-b", timeoutMs = timeoutMs)
    }

    suspend fun shutdown(udid: String) {
        simctl("shutdown", udid, timeoutMs = 60_000, allow = { it.stderr.contains("current state: Shutdown") })
    }

    /** A new simulator like [udid] (its apps, data and settings), named [name]; returns its udid. [udid] must be shut down. */
    suspend fun clone(
        udid: String,
        name: String,
    ): String =
        simctl("clone", udid, name, timeoutMs = 5 * 60_000).text.trim().lines().last().trim().also {
            if (!UDID.matches(it)) throw IOException("simctl clone said \"$it\", not a udid")
        }

    suspend fun delete(udid: String) {
        simctl("delete", udid, timeoutMs = 60_000)
    }

    /** Back to factory state: no apps, no data. [udid] must be shut down. */
    suspend fun erase(udid: String) {
        simctl("erase", udid, timeoutMs = 2 * 60_000)
    }

    suspend fun install(
        udid: String,
        app: File,
    ) {
        simctl("install", udid, app.path, timeoutMs = 5 * 60_000)
    }

    suspend fun uninstall(
        udid: String,
        bundleId: String,
    ) {
        simctl("uninstall", udid, bundleId, timeoutMs = 60_000)
    }

    /** Opens [bundleId], ending a run that is already going; returns its pid. */
    suspend fun launch(
        udid: String,
        bundleId: String,
    ): Int? {
        val out = simctl("launch", "--terminate-running-process", udid, bundleId, timeoutMs = 60_000).text
        return out.substringAfterLast(':').trim().toIntOrNull()
    }

    suspend fun terminate(
        udid: String,
        bundleId: String,
    ) {
        // Nothing to end is no failure: it isn't running, which is what was asked.
        simctl("terminate", udid, bundleId, allow = { it.stderr.contains("found nothing to terminate", ignoreCase = true) || it.stderr.contains("not running", ignoreCase = true) })
    }

    suspend fun openUrl(
        udid: String,
        url: String,
    ) {
        simctl("openurl", udid, url)
    }

    /** The screen as a PNG, full size: for when the companion isn't there. */
    suspend fun screenshot(udid: String): ByteArray {
        val file = withContext(Dispatchers.IO) { File.createTempFile("simctl-shot-", ".png") }
        try {
            simctl("io", udid, "screenshot", "--type=png", file.path)
            return withContext(Dispatchers.IO) { file.readBytes() }
        } finally {
            if (!file.delete() && file.exists()) file.deleteOnExit()
        }
    }

    /** Whether [bundleId] is installed: its bundle's folder, or null. */
    suspend fun appPath(
        udid: String,
        bundleId: String,
    ): String? {
        val result = simctl("get_app_container", udid, bundleId, "app", allow = { true })
        return result.text.trim().takeIf { result.ok && it.isNotEmpty() }
    }

    /** The pid [bundleId] runs as, or null when it doesn't: launchd's list inside the simulator. */
    suspend fun pidOf(
        udid: String,
        bundleId: String,
    ): Int? = runningApps(simctl("spawn", udid, "launchctl", "list").text)[bundleId]

    /**
     * The simulator's log of the last [minutes], as `log show` prints it compactly: the entries
     * [predicate] picks (`log`'s predicate syntax: `processID == 4821`), at [level] ("default",
     * "info" or "debug") and above.
     */
    suspend fun log(
        udid: String,
        minutes: Int,
        predicate: String? = null,
        level: String = "default",
    ): String {
        val args =
            buildList {
                addAll(listOf("spawn", udid, "log", "show", "--style", "compact", "--last", "${minutes.coerceAtLeast(1)}m"))
                when (level) {
                    "debug" -> add("--debug")
                    "info" -> add("--info")
                }
                if (predicate != null) addAll(listOf("--predicate", predicate))
            }
        return simctl(*args.toTypedArray(), timeoutMs = 60_000).text
    }

    companion object {
        private val UDID = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")
        private val LAUNCHD_APP = Regex("""^(\d+|-)\s+\S+\s+UIKitApplication:([^\[\s]+)""")

        private val json = Json { ignoreUnknownKeys = true }

        fun parseList(text: String): SimctlList {
            val root = json.parseToJsonElement(text) as? JsonObject ?: throw IOException("simctl list isn't JSON")
            val runtimes =
                (root["runtimes"] as? JsonArray).orEmpty().mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    SimRuntime(o.str("identifier") ?: return@mapNotNull null, o.str("name").orEmpty(), o.str("version").orEmpty(), o.bool("isAvailable") ?: true)
                }
            val names = runtimes.associate { it.identifier to it.name }
            val devices =
                (root["devices"] as? JsonObject).orEmpty().flatMap { (runtime, list) ->
                    (list as? JsonArray).orEmpty().mapNotNull { e ->
                        val o = e as? JsonObject ?: return@mapNotNull null
                        SimDevice(
                            udid = o.str("udid") ?: return@mapNotNull null,
                            name = o.str("name").orEmpty(),
                            state = o.str("state").orEmpty(),
                            runtimeId = runtime,
                            runtimeName = names[runtime] ?: runtimeLabel(runtime),
                            deviceType = o.str("deviceTypeIdentifier").orEmpty(),
                            isAvailable = o.bool("isAvailable") ?: true,
                            dataPath = o.str("dataPath"),
                        )
                    }
                }
            return SimctlList(devices, runtimes)
        }

        /** `com.apple.CoreSimulator.SimRuntime.iOS-26-0` → "iOS 26.0". */
        fun runtimeLabel(identifier: String): String {
            val tail = identifier.substringAfterLast('.')
            val platform = tail.substringBefore('-')
            val version = tail.substringAfter('-', "").replace('-', '.')
            return if (version.isEmpty()) tail else "$platform $version"
        }

        /** Bundle id → pid of the apps `launchctl list` shows running ("-" is loaded, not running). */
        fun runningApps(launchctl: String): Map<String, Int> =
            launchctl
                .lineSequence()
                .mapNotNull { LAUNCHD_APP.find(it.trim()) }
                .mapNotNull { m -> m.groupValues[1].toIntOrNull()?.let { m.groupValues[2] to it } }
                .toMap()

        private fun JsonObject.str(name: String): String? = (this[name] as? JsonElement)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

        private fun JsonObject.bool(name: String): Boolean? = this[name]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
    }
}
