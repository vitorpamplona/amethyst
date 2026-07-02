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
package com.vitorpamplona.quartz.utils

/**
 * Destination for every diagnostic line Quartz emits past the [Log.minLevel] gate.
 *
 * Quartz owns *what* to log; the consumer of the library owns *where* it goes.
 * Replace [Log.sink] to route Quartz's diagnostics into your own logging stack
 * (Timber, SLF4J/Logback, Crashlytics, a file, a test buffer, or nowhere at all):
 *
 * ```kotlin
 * Log.sink = LogSink { level, tag, message, throwable ->
 *     Timber.tag(tag).log(level.toAndroidPriority(), throwable, message)
 * }
 * ```
 *
 * The default is [PlatformLogSink], which reproduces the historical per-platform
 * behavior (android.util.Log on Android, System.err on JVM, NSLog on Apple,
 * println on Linux), so nothing changes unless a consumer opts in.
 *
 * The [message] has already passed the [Log.minLevel] filter and any lazy
 * `() -> String` builder has already been evaluated, so a sink never needs to
 * re-check the level for allocation reasons.
 */
fun interface LogSink {
    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    )
}

/**
 * Default [LogSink] that forwards to the platform's native logger through
 * [PlatformLog]. Used unless a consumer installs their own [Log.sink].
 */
object PlatformLogSink : LogSink {
    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        when (level) {
            LogLevel.DEBUG -> PlatformLog.d(tag, message, throwable)
            LogLevel.INFO -> PlatformLog.i(tag, message, throwable)
            LogLevel.WARN -> PlatformLog.w(tag, message, throwable)
            LogLevel.ERROR -> PlatformLog.e(tag, message, throwable)
        }
    }
}
