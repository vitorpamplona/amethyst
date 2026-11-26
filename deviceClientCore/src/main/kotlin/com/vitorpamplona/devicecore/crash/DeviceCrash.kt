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
package com.vitorpamplona.devicecore.crash

/** An app that crashed or stopped answering, as the device's log (or crash report) tells it. */
data class DeviceCrash(
    /** Its time and process: the same crash read twice has the same id. */
    val id: String,
    val kind: Kind,
    /** `MM-DD HH:MM:SS.mmm`, as logcat prints it. */
    val time: String,
    val pid: Int,
    val packageName: String?,
    /** The exception (`java.lang.IllegalStateException: boom`), the signal, or the ANR's reason. */
    val headline: String,
    /** The trace: the exception's frames and causes, a native backtrace, or the ANR's lines. */
    val details: List<String>,
) {
    /** [IOS] is an iOS app's crash report (an `.ips`), whatever stopped it: a signal, an exception, a Swift trap. */
    enum class Kind { JAVA, NATIVE, ANR, IOS }

    /** The crash in a few lines: what, where, and the top of the trace. */
    fun summary(maxLines: Int = 14): String {
        val what =
            when (kind) {
                Kind.JAVA -> "crashed"
                Kind.NATIVE -> "crashed in native code"
                Kind.ANR -> "stopped responding (ANR)"
                Kind.IOS -> "crashed"
            }
        val head = "${packageName ?: "pid $pid"} $what at $time: $headline"
        val trace = details.take(maxLines).joinToString("\n") { "  $it" }
        val more = if (details.size > maxLines) "\n  … ${details.size - maxLines} more lines (logcat crashes_only)" else ""
        return if (trace.isEmpty()) head else "$head\n$trace$more"
    }
}
