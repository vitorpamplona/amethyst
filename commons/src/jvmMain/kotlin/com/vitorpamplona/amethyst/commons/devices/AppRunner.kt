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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.devices.android.AndroidAppTarget
import com.vitorpamplona.iossimulator.xcode.XcodeAppTarget
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** How running the app on a device is going, on either platform. */
@Immutable
sealed class AppRunState {
    data object Idle : AppRunState()

    data class Building(
        /** The app module (`amethyst`) or Xcode project being built. */
        val label: String,
        /** The Android variant (`playDebug`) or Xcode scheme. */
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

/** An app in a chat's folder that a platform can build and run: a Gradle app module, or an Xcode project. */
@Immutable
sealed class AppTarget {
    abstract val platform: DevicePlatform

    /** The build's folder: where it runs, and what its [AppRunState] is kept under. */
    abstract val root: File
    abstract val label: String

    /** Android's debug variants (`playDebug`), or Xcode's schemes. */
    abstract val variants: List<String>

    data class Android(
        val target: AndroidAppTarget,
    ) : AppTarget() {
        override val platform get() = DevicePlatform.ANDROID
        override val root get() = target.root
        override val label get() = target.label
        override val variants get() = target.variants
    }

    data class Ios(
        val target: XcodeAppTarget,
    ) : AppTarget() {
        override val platform get() = DevicePlatform.IOS
        override val root get() = target.root
        override val label get() = target.label
        override val variants get() = target.schemes
    }
}

/**
 * Builds one platform's apps and runs them on a device, like the IDE's Run: the panel's Run button
 * and the agents' `run_app`. One run at a time per build; [states] says how each is going.
 */
interface AppRunner {
    val platform: DevicePlatform

    /** By the build's folder ([AppTarget.root] path). */
    val states: StateFlow<Map<String, AppRunState>>

    /** This platform's apps in [folder]. */
    suspend fun targets(folder: File): List<AppTarget>

    /** Builds [variant] of [target], installs it on [serial] and opens it; returns its package name or bundle id. */
    suspend fun run(
        target: AppTarget,
        variant: String,
        serial: String,
    ): String
}
