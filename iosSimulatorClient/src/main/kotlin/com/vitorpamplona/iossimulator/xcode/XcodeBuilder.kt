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
package com.vitorpamplona.iossimulator.xcode

import com.vitorpamplona.androiddevice.CommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * An iOS app an Xcode project or workspace builds, and the schemes it can be built with: what the
 * device panel's Run button and the agents' `run_app` build for a simulator.
 */
data class XcodeAppTarget(
    /** The folder holding [container]: where the build runs. */
    val root: File,
    /** The `.xcworkspace` (CocoaPods, several projects) or `.xcodeproj`. */
    val container: File,
    /** Shared schemes first, by name; the project's own name when none is shared. */
    val schemes: List<String>,
) {
    val isWorkspace: Boolean get() = container.name.endsWith(".xcworkspace")
    val label: String get() = container.name.substringBeforeLast('.')
}

/** An app a build wrote, and the bundle id simctl launches it by. */
data class BuiltApp(
    val app: File,
    val bundleId: String,
)

object XcodeAppTargets {
    private val SKIP = setOf("Pods", "build", "DerivedData", "node_modules", ".git", ".gradle", "Carthage")

    /**
     * The Xcode apps in [dir]: its own project, a Kotlin Multiplatform build's `iosApp/`, and those
     * of each repository one or two folders down (a workspace of several). A project inside a
     * workspace that lists it is the workspace's.
     */
    fun find(dir: File): List<XcodeAppTarget> {
        val folders = (sequenceOf(dir) + children(dir) + children(dir).flatMap(::children)).distinct()
        val found =
            folders
                .mapNotNull { folder ->
                    val entries = folder.listFiles().orEmpty()
                    val workspace = entries.firstOrNull { it.isDirectory && it.name.endsWith(".xcworkspace") }
                    val project = entries.firstOrNull { it.isDirectory && it.name.endsWith(".xcodeproj") }
                    val container = workspace ?: project ?: return@mapNotNull null
                    XcodeAppTarget(folder, container, schemes(container, project))
                }.toList()
        return found.sortedBy { it.root.path.length }
    }

    private fun children(dir: File): Sequence<File> =
        dir
            .listFiles { f -> f.isDirectory && f.name !in SKIP && !f.name.startsWith(".") && !f.name.endsWith(".xcodeproj") && !f.name.endsWith(".xcworkspace") }
            .orEmpty()
            .sortedBy { it.name }
            .asSequence()

    /** The shared schemes (the `.xcscheme` files in `xcshareddata/xcschemes`), else the project's name, as Xcode makes one. */
    fun schemes(
        container: File,
        project: File?,
    ): List<String> {
        val shared =
            listOfNotNull(container, project)
                .flatMap { File(it, "xcshareddata/xcschemes").listFiles { f -> f.name.endsWith(".xcscheme") }.orEmpty().toList() }
                .map { it.name.removeSuffix(".xcscheme") }
                .distinct()
                .sorted()
        return shared.ifEmpty { listOf((project ?: container).name.substringBeforeLast('.')) }
    }
}

/**
 * Builds a scheme for one simulator with `xcodebuild`, into a derived-data folder of the project's
 * own (`build/amethyst-ios`), and finds the app it made.
 */
class XcodeBuilder(
    private val xcrun: File,
    private val runner: CommandRunner,
) {
    suspend fun build(
        target: XcodeAppTarget,
        scheme: String,
        udid: String,
        log: File,
        timeoutMs: Long = 30 * 60_000,
    ): BuiltApp {
        val derived = File(target.root, "build/amethyst-ios")
        log.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("Could not create $it") }
        val command =
            listOf(
                xcrun.path,
                "xcodebuild",
                if (target.isWorkspace) "-workspace" else "-project",
                target.container.path,
                "-scheme",
                scheme,
                "-configuration",
                "Debug",
                "-destination",
                "platform=iOS Simulator,id=$udid",
                "-derivedDataPath",
                derived.path,
                "build",
            )
        val result = runner.run(command, timeoutMs = timeoutMs, directory = target.root, log = log)
        if (result.exitCode == CommandRunner.TIMED_OUT) throw IOException("xcodebuild didn't finish in ${timeoutMs / 60_000} minutes")
        if (!result.ok) throw IOException("xcodebuild $scheme failed: ${errors(log)}")
        val app = builtApp(File(derived, "Build/Products"), scheme) ?: throw IOException("xcodebuild finished, but no .app is in ${derived.path}/Build/Products")
        val bundleId = bundleId(app) ?: throw IOException("${app.name} has no CFBundleIdentifier in its Info.plist")
        return BuiltApp(app, bundleId)
    }

    /** The app a Debug simulator build wrote: the scheme's, else the newest. */
    fun builtApp(
        products: File,
        scheme: String,
    ): File? {
        val apps =
            products
                .listFiles { f -> f.isDirectory && f.name.endsWith("-iphonesimulator") }
                .orEmpty()
                .flatMap { dir -> dir.listFiles { f -> f.isDirectory && f.name.endsWith(".app") }.orEmpty().toList() }
        return apps.firstOrNull { it.name == "$scheme.app" } ?: apps.maxByOrNull { it.lastModified() }
    }

    /** The app's bundle id: `plutil` reads a binary plist, as Xcode writes it; else the XML one. */
    suspend fun bundleId(app: File): String? {
        val plist = File(app, "Info.plist")
        if (!plist.isFile) return null
        val plutil = File("/usr/bin/plutil")
        if (plutil.canExecute()) {
            val result = runner.run(listOf(plutil.path, "-extract", "CFBundleIdentifier", "raw", "-o", "-", plist.path))
            result.text
                .trim()
                .takeIf { result.ok && it.isNotEmpty() }
                ?.let { return it }
        }
        return withContext(Dispatchers.IO) { xmlBundleId(plist.readText()) }
    }

    companion object {
        private val XML_ID = Regex("""<key>CFBundleIdentifier</key>\s*<string>([^<]+)</string>""")

        fun xmlBundleId(plist: String): String? =
            XML_ID
                .find(plist)
                ?.groupValues
                ?.get(1)
                ?.trim()

        /** xcodebuild's `error:` lines, else the log's end: what to tell the user a build failed with. */
        fun errors(log: File): String {
            val lines =
                try {
                    log.readLines()
                } catch (_: IOException) {
                    return "see ${log.path}"
                }
            val errors =
                lines
                    .map { it.trim() }
                    .filter { it.contains(" error: ") || it.startsWith("error:") }
                    .distinct()
                    .take(6)
            return (errors.ifEmpty { lines.map { it.trim() }.filter { it.isNotEmpty() }.takeLast(6) }).joinToString(" / ")
        }
    }
}
