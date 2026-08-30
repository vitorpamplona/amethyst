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

/**
 * Puts this platform's implementations behind the shared seams.
 *
 * The seams (`VideoTranscoder`, `HlsTranscoder`, …) let shared code *use*
 * whatever does the work here. This is the other half: how the right one gets
 * installed without the shared startup path naming it.
 *
 * That distinction is what makes a seam worth anything. `AppModules` runs on
 * both platforms, so a line there saying `VideoTranscoder.installed =
 * LightCompressorTranscoder(...)` puts an Android-only class straight back into
 * shared code — the engine would be swappable everywhere except at the one
 * place that chooses it.
 *
 * So the choice is made by whoever is on the classpath. Each platform ships one
 * implementation of this and declares it in `META-INF/services`; startup asks
 * for whatever is there.
 *
 * [context] is the platform's application context where it has one (Android),
 * and null where it does not.
 */
interface PlatformEngineInstaller {
    fun install(context: Any?)

    /**
     * Optional work to do once the app is up and idle — forcing an expensive
     * lazy off the main thread, typically. Called well after [install], and
     * only if the platform has something to warm.
     */
    fun warmUp(context: Any?) {}
}
