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

import com.vitorpamplona.androiddevice.CommandRunner
import com.vitorpamplona.androiddevice.ProcessCommandRunner
import com.vitorpamplona.iossimulator.XcodeTools
import com.vitorpamplona.iossimulator.xcode.XcodeAppTarget
import com.vitorpamplona.iossimulator.xcode.XcodeAppTargets
import com.vitorpamplona.iossimulator.xcode.XcodeBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Builds an Xcode scheme for a simulator, installs the app and opens it: the device panel's Run
 * button and the agents' `run_app` on an iOS simulator, as [AndroidAppRunner] is on Android. One
 * run at a time per project folder; [states] says how each is going, in the same terms.
 */
class IosAppRunner(
    private val devices: AndroidDevices,
    private val tools: XcodeTools = XcodeTools(),
    private val runner: CommandRunner = ProcessCommandRunner,
) {
    private val _states = MutableStateFlow<Map<String, AppRunState>>(emptyMap())

    /** By the project's folder (its path). */
    val states: StateFlow<Map<String, AppRunState>> = _states.asStateFlow()

    private fun set(
        root: File,
        state: AppRunState,
    ) = _states.update { it + (root.path to state) }

    /** The Xcode apps in [folder]; none without Xcode. */
    suspend fun targets(folder: File): List<XcodeAppTarget> = if (tools.available) withContext(Dispatchers.IO) { XcodeAppTargets.find(folder) } else emptyList()

    /** Builds [scheme] of [target] for the simulator [udid], installs it there and opens it; returns its bundle id. */
    suspend fun run(
        target: XcodeAppTarget,
        scheme: String,
        udid: String,
    ): String {
        val xcrun = tools.xcrun ?: throw IOException("Xcode isn't installed")
        val root = target.root
        if (_states.value[root.path]?.isRunning == true) throw IOException("${target.label} is already being built")
        val log = File(root, "build/amethyst-ios-run.log")
        try {
            set(root, AppRunState.Building(target.label, scheme))
            val built = XcodeBuilder(xcrun, runner).build(target, scheme, udid, log)
            set(root, AppRunState.Installing(built.bundleId))
            val connection = devices.connection(udid)
            connection.install(built.app)
            connection.launch(built.bundleId)
            set(root, AppRunState.Done(built.bundleId, udid))
            devices.report(DeviceActivity(udid, DeviceActivity.Kind.APP, label = built.bundleId))
            return built.bundleId
        } catch (e: IOException) {
            set(root, AppRunState.Failed(e.message ?: "The run failed", log.takeIf { it.isFile }))
            throw e
        } catch (e: CancellationException) {
            set(root, AppRunState.Idle)
            throw e
        } catch (e: RuntimeException) {
            set(root, AppRunState.Failed(e.message ?: e.javaClass.simpleName, log.takeIf { it.isFile }))
            throw IOException(e.message ?: "The run failed", e)
        }
    }
}
