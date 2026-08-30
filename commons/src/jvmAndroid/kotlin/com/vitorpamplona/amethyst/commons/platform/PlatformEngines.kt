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
package com.vitorpamplona.amethyst.commons.platform

import com.vitorpamplona.quartz.utils.Log
import java.util.ServiceLoader

/**
 * Finds and runs this build's [PlatformEngineInstaller].
 *
 * `ServiceLoader` because it is the one mechanism both runtimes already have —
 * Android supports it, and it needs no reflection by name, no initialisation
 * order, and no dependency from the startup path onto any implementation.
 *
 * A build with none installs nothing, which every seam already handles by
 * falling back to whatever it does without an engine.
 */
object PlatformEngines {
    private val installers: List<PlatformEngineInstaller> by lazy {
        runCatching { ServiceLoader.load(PlatformEngineInstaller::class.java).toList() }.getOrDefault(emptyList())
    }

    fun install(context: Any?) {
        if (installers.isEmpty()) {
            Log.w("PlatformEngines") { "no platform engines on the classpath; every seam falls back to its no-engine behaviour" }
            return
        }
        each("install") { it.install(context) }
    }

    /** Runs after startup settles; see [PlatformEngineInstaller.warmUp]. */
    fun warmUp(context: Any?) = each("warmUp") { it.warmUp(context) }

    private fun each(
        phase: String,
        action: (PlatformEngineInstaller) -> Unit,
    ) = installers.forEach { installer ->
        runCatching { action(installer) }
            .onFailure { Log.w("PlatformEngines", "${installer::class.java.name} failed to $phase", it) }
    }
}
