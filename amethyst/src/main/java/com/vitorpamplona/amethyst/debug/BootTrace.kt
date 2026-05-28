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
package com.vitorpamplona.amethyst.debug

import androidx.tracing.Trace
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thin wrapper around [androidx.tracing.Trace] for boot-time markers picked up
 * by `:macrobenchmark`'s [androidx.benchmark.macro.TraceSectionMetric]. All
 * markers use the `Boot:` prefix so they're easy to grep, extract, and remove.
 *
 * Both forms are cheap when tracing isn't recording — `beginSection` is a
 * JNI call that returns early.
 */
object BootTrace {
    private val cookieGen = AtomicInteger(0)

    inline fun <T> section(
        name: String,
        block: () -> T,
    ): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Emits an instant marker — a zero-duration async section. Shows up as a
     * single tick in Perfetto and as a measurable section in
     * [androidx.benchmark.macro.TraceSectionMetric].
     */
    fun mark(name: String) {
        val cookie = cookieGen.incrementAndGet()
        Trace.beginAsyncSection(name, cookie)
        Trace.endAsyncSection(name, cookie)
    }
}
