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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.claudecode.AndroidSdk
import com.vitorpamplona.androiddevice.CommandRunner
import com.vitorpamplona.androiddevice.ProcessCommandRunner
import com.vitorpamplona.androiddevice.emulator.EmulatorLauncher
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException

/** An Android app module in a Gradle build, and the debug variants it can be run as. */
@Immutable
data class AndroidAppTarget(
    /** The folder with `gradlew`. */
    val root: File,
    /** Its Gradle path, `:amethyst`. */
    val module: String,
    val moduleDir: File,
    /** `playDebug`, `fdroidDebug`; or just `debug` without flavors. */
    val variants: List<String>,
) {
    val label: String get() = if (module.count { it == ':' } <= 1) module.removePrefix(":") else module
}

/** An APK a build wrote, and its application id when the build said it. */
data class BuiltApk(
    val file: File,
    val applicationId: String?,
)

/** How running the app on a device is going. */
@Immutable
sealed class AppRunState {
    data object Idle : AppRunState()

    data class Building(
        val target: AndroidAppTarget,
        val variant: String,
    ) : AppRunState()

    data class Installing(
        val packageName: String,
    ) : AppRunState()

    data class Done(
        val packageName: String,
        val serial: String,
        val at: Long = System.currentTimeMillis(),
    ) : AppRunState()

    data class Failed(
        val message: String,
        val log: File?,
    ) : AppRunState()

    val isRunning: Boolean get() = this is Building || this is Installing
}

/** The Android apps in a folder: the folder's own Gradle build, or each repository's in a workspace of several. */
object AndroidAppTargets {
    fun find(dir: File): List<AndroidAppTarget> {
        val roots = if (isGradleRoot(dir)) listOf(dir) else dir.listFiles { f -> f.isDirectory && isGradleRoot(f) }?.sortedBy { it.name }.orEmpty()
        return roots.flatMap(::inBuild)
    }

    private fun isGradleRoot(dir: File) = File(dir, "gradlew").isFile || File(dir, "gradlew.bat").isFile

    private fun buildFile(dir: File): File? = listOf("build.gradle.kts", "build.gradle").map { File(dir, it) }.firstOrNull { it.isFile }

    private fun inBuild(root: File): List<AndroidAppTarget> {
        val settings = listOf("settings.gradle.kts", "settings.gradle").map { File(root, it) }.firstOrNull { it.isFile }
        val modules = settings?.let { modules(read(it)) }.orEmpty().ifEmpty { root.listFiles { f -> f.isDirectory }?.map { ":" + it.name }.orEmpty() }
        return modules.mapNotNull { module ->
            val moduleDir = File(root, module.removePrefix(":").replace(':', '/'))
            val build = buildFile(moduleDir)?.let(::read) ?: return@mapNotNull null
            if (!isApp(build)) return@mapNotNull null
            AndroidAppTarget(root, module, moduleDir, variants(build))
        }
    }

    private fun read(file: File): String =
        try {
            file.readText()
        } catch (_: IOException) {
            ""
        }

    /** The project paths `include(":a", ":b")` / `include ':a'` name. */
    fun modules(settings: String): List<String> =
        Regex("""include\s*\(?([^)\n]*)\)?""")
            .findAll(settings.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n"))
            .flatMap { m -> Regex("""["']([:\w.-]+)["']""").findAll(m.groupValues[1]).map { it.groupValues[1] } }
            .map { if (it.startsWith(":")) it else ":$it" }
            .distinct()
            .toList()

    /** Whether a build file applies the Android application plugin, by id or by catalog alias. */
    fun isApp(build: String): Boolean =
        build.contains("com.android.application") ||
            Regex("""alias\(libs\.plugins\.(android[.]?[aA]pplication|androidApp)\)""").containsMatchIn(build)

    /**
     * The debug variants: one per product flavor (`create("play")`, `register("play")`, or Groovy's
     * `play {`) of a single dimension, else just `debug`. Several dimensions combine in ways only
     * Gradle knows: those builds get `debug`, which Gradle then names ambiguous, and the agent runs it.
     */
    fun variants(build: String): List<String> {
        val start = build.indexOf("productFlavors")
        if (start < 0) return listOf("debug")
        val open = build.indexOf('{', start).takeIf { it >= 0 } ?: return listOf("debug")
        var depth = 0
        var end = open
        while (end < build.length) {
            when (build[end]) {
                '{' -> depth++
                '}' -> if (--depth == 0) break
            }
            end++
        }
        val block = build.substring(open + 1, end)
        val kotlin = Regex("""(?:create|register|maybeCreate)\(\s*"(\w+)"""").findAll(block).map { it.groupValues[1] }.toList()
        // Groovy: a name opening a block at the top of productFlavors.
        val groovy =
            if (kotlin.isNotEmpty()) {
                emptyList()
            } else {
                var d = 0
                val names = mutableListOf<String>()
                Regex("""(\w+)\s*\{|\{|}""").findAll(block).forEach { m ->
                    when {
                        m.value == "}" -> d--
                        m.value == "{" -> d++
                        else -> {
                            if (d == 0) names += m.groupValues[1]
                            d++
                        }
                    }
                }
                names
            }
        val flavors = (kotlin + groovy).distinct()
        val dimensions =
            Regex("""flavorDimensions\s*(\+=|=|\()?\s*(?:listOf\()?([^\n)]*)""")
                .find(build)
                ?.groupValues
                ?.get(2)
                ?.count { it == '"' || it == '\'' }
                ?.div(2) ?: 1
        return if (flavors.isEmpty() || dimensions > 1) listOf("debug") else flavors.map { it + "Debug" }
    }
}

/**
 * Builds a debug variant of an app module with the build's own Gradle wrapper, installs it on a
 * device and opens it: the device panel's Run button and the agents' `run_app` tool. One run at a
 * time per build; [states] says how each is going.
 */
class AndroidAppRunner(
    private val devices: AndroidDevices,
    private val runner: CommandRunner = ProcessCommandRunner,
) {
    private val _states = MutableStateFlow<Map<String, AppRunState>>(emptyMap())

    /** By the build's folder (its path). */
    val states: StateFlow<Map<String, AppRunState>> = _states.asStateFlow()

    private fun set(
        root: File,
        state: AppRunState,
    ) = _states.update { it + (root.path to state) }

    /** Builds, installs and opens [variant] of [target] on [serial]; returns the package name. */
    suspend fun run(
        target: AndroidAppTarget,
        variant: String,
        serial: String,
    ): String {
        val root = target.root
        if (_states.value[root.path]?.isRunning == true) throw IOException("${target.label} is already being built")
        val log = File(root, "build/amethyst-device-run.log")
        try {
            set(root, AppRunState.Building(target, variant))
            val connection = devices.connection(serial)
            gradle(target, variant, serial, log)
            val built = apkFor(target, variant, abis(connection.adb.getprop(serial, "ro.product.cpu.abilist")))
            val packageName = built.applicationId ?: packageName(built.file)
            val apk = built.file
            set(root, AppRunState.Installing(packageName))
            connection.adb.install(serial, apk)
            connection.adb.launch(serial, packageName)
            set(root, AppRunState.Done(packageName, serial))
            devices.report(DeviceActivity(serial, DeviceActivity.Kind.APP, label = packageName))
            return packageName
        } catch (e: IOException) {
            set(root, AppRunState.Failed(e.message ?: "The run failed", log.takeIf { it.isFile }))
            throw e
        } catch (e: CancellationException) {
            // The screen or the agent's turn went away: the build stopped with it (see CommandRunner).
            set(root, AppRunState.Idle)
            throw e
        } catch (e: RuntimeException) {
            // Unreadable build output, a bad manifest: still a failure, never "already being built" forever.
            set(root, AppRunState.Failed(e.message ?: e.javaClass.simpleName, log.takeIf { it.isFile }))
            throw IOException(e.message ?: "The run failed", e)
        }
    }

    private suspend fun gradle(
        target: AndroidAppTarget,
        variant: String,
        serial: String,
        log: File,
    ) {
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
        val gradlew = File(target.root, if (windows) "gradlew.bat" else "gradlew").absolutePath
        val task = "${target.module}:assemble${variant.replaceFirstChar { it.uppercase() }}"
        log.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("Could not create $it") }
        val result =
            runner.run(
                listOf(gradlew, task),
                timeoutMs = BUILD_TIMEOUT_MS,
                environment = AndroidSdk.environment() + ("ANDROID_SERIAL" to serial),
                directory = target.root,
                log = log,
            )
        if (result.exitCode == CommandRunner.TIMED_OUT) throw IOException("$task didn't finish in ${BUILD_TIMEOUT_MS / 60_000} minutes")
        if (!result.ok) throw IOException("$task failed: ${EmulatorLauncher.tail(log, 8)}")
    }

    companion object {
        private const val TAG = "AndroidAppRunner"
        private const val BUILD_TIMEOUT_MS = 30 * 60_000L
        private val json = Json { ignoreUnknownKeys = true }

        fun abis(abilist: String): List<String> = abilist.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        /**
         * The APK the build wrote for [variant], from its `output-metadata.json`: the one for the
         * device's first ABI it has, else the universal one, else the first; with the application
         * id the same file names.
         */
        fun apkFor(
            target: AndroidAppTarget,
            variant: String,
            deviceAbis: List<String>,
        ): BuiltApk {
            val buildType = Regex("(Debug|Release)$").find(variant)?.value?.lowercase() ?: "debug"
            val flavor = variant.removeSuffix(buildType.replaceFirstChar { it.uppercase() }).takeIf { it.isNotEmpty() && it != buildType }
            val dir = File(target.moduleDir, "build/outputs/apk/" + listOfNotNull(flavor, buildType).joinToString("/"))
            val metadata = readMetadata(File(dir, "output-metadata.json"))
            val outputs =
                metadata
                    ?.get("elements")
                    ?.jsonArray
                    ?.map { it.jsonObject }
                    .orEmpty()

            fun abi(element: JsonObject): String? =
                element["filters"]
                    ?.jsonArray
                    ?.firstOrNull { it.jsonObject["filterType"]?.jsonPrimitive?.content == "ABI" }
                    ?.jsonObject
                    ?.get("value")
                    ?.jsonPrimitive
                    ?.content

            fun file(element: JsonObject) =
                element["outputFile"]
                    ?.jsonPrimitive
                    ?.content
                    ?.let { File(dir, it) }
                    ?.takeIf { it.isFile }
            val chosen =
                deviceAbis.firstNotNullOfOrNull { wanted -> outputs.firstOrNull { abi(it) == wanted }?.let(::file) }
                    ?: outputs.firstOrNull { abi(it) == null }?.let(::file)
                    ?: outputs.firstNotNullOfOrNull(::file)
                    ?: dir.listFiles { f -> f.name.endsWith(".apk") }?.maxByOrNull { it.lastModified() }
            val file = chosen ?: throw IOException("The build wrote no APK in ${dir.path}")
            return BuiltApk(file, metadata?.get("applicationId")?.jsonPrimitive?.content)
        }

        private fun readMetadata(file: File): JsonObject? =
            try {
                if (file.isFile) json.parseToJsonElement(file.readText()).jsonObject else null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unreadable $file", e)
                null
            } catch (e: IOException) {
                Log.w(TAG, "Unreadable $file", e)
                null
            }
    }

    /**
     * [apk]'s package name: from the build's `output-metadata.json` beside it, else from the SDK's
     * `aapt2`. (apksig isn't shipped in the desktop app.)
     */
    suspend fun packageName(apk: File): String {
        readMetadata(File(apk.parentFile, "output-metadata.json"))
            ?.get("applicationId")
            ?.jsonPrimitive
            ?.content
            ?.let { return it }
        val aapt2 = devices.sdkTools().aapt2 ?: throw IOException("Can't read ${apk.name}'s package name: no build-tools (aapt2) in the Android SDK")
        val result = runner.run(listOf(aapt2.absolutePath, "dump", "packagename", apk.absolutePath), 30_000)
        return result.text
            .trim()
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.takeIf { result.ok } ?: throw IOException("aapt2 couldn't read ${apk.name}: ${result.stderr.trim()}")
    }
}
