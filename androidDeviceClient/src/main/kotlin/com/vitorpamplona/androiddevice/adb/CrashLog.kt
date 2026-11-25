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
package com.vitorpamplona.androiddevice.adb

/** An app that crashed or stopped answering, as the device's log tells it. */
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
    enum class Kind { JAVA, NATIVE, ANR }

    /** The crash in a few lines: what, where, and the top of the trace. */
    fun summary(maxLines: Int = 14): String {
        val what =
            when (kind) {
                Kind.JAVA -> "crashed"
                Kind.NATIVE -> "crashed in native code"
                Kind.ANR -> "stopped responding (ANR)"
            }
        val head = "${packageName ?: "pid $pid"} $what at $time: $headline"
        val trace = details.take(maxLines).joinToString("\n") { "  $it" }
        val more = if (details.size > maxLines) "\n  … ${details.size - maxLines} more lines (logcat crashes_only)" else ""
        return if (trace.isEmpty()) head else "$head\n$trace$more"
    }
}

/**
 * Finds crashes in `logcat -v threadtime` output of the `crash` and `system` buffers: Java
 * exceptions (`AndroidRuntime: FATAL EXCEPTION`), native crashes (`libc: Fatal signal`, then
 * debuggerd's `DEBUG` lines) and ANRs (`ActivityManager: ANR in`).
 */
object CrashLog {
    private val PROCESS = Regex("""^Process: ([^,\s]+)""")
    private val CMDLINE = Regex("""Cmdline: (\S+)""")
    private val NATIVE_PID = Regex("""pid \d+ \(([^)]+)\)""")
    private val ANR_PID = Regex("""^PID: (\d+)""")
    private val LINE = Regex("""^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+(.*?)\s*: (.*)$""")

    private class Line(
        val time: String,
        val pid: Int,
        val tag: String,
        val message: String,
    )

    private fun lineOf(text: String): Line? = LINE.matchEntire(text.trimEnd())?.groupValues?.let { g -> Line(g[1], g[2].toInt(), g[5].trim(), g[6]) }

    fun parse(log: String): List<DeviceCrash> {
        val lines = log.lineSequence().mapNotNull(::lineOf).toList()
        val crashes = ArrayList<DeviceCrash>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.tag == "AndroidRuntime" && line.message.startsWith("FATAL EXCEPTION") -> {
                    val body = lines.drop(i + 1).takeWhile { it.tag == "AndroidRuntime" && it.pid == line.pid && !it.message.startsWith("FATAL EXCEPTION") }
                    val pkg = body.firstNotNullOfOrNull { PROCESS.find(it.message)?.groupValues?.get(1) }
                    val trace = body.map { it.message.trimEnd() }.filter { !it.startsWith("Process: ") && it.isNotBlank() }
                    crashes += DeviceCrash(id(line, DeviceCrash.Kind.JAVA), DeviceCrash.Kind.JAVA, line.time, line.pid, pkg, trace.firstOrNull()?.trim() ?: line.message, trace.drop(1).map { it.trim() })
                    i += body.size + 1
                }
                line.tag == "libc" && line.message.contains("Fatal signal") -> {
                    val body = lines.drop(i + 1).takeWhile { it.tag == "DEBUG" || it.tag == "crash_dump64" || it.tag == "crash_dump32" }
                    val pkg =
                        body.firstNotNullOfOrNull { CMDLINE.find(it.message)?.groupValues?.get(1) }
                            ?: NATIVE_PID.find(line.message)?.groupValues?.get(1)
                    val frames = body.map { it.message.trim() }.filter { it.startsWith("#") || it.startsWith("Abort message") || it.startsWith("signal ") }
                    crashes += DeviceCrash(id(line, DeviceCrash.Kind.NATIVE), DeviceCrash.Kind.NATIVE, line.time, line.pid, pkg, line.message.substringBefore(", fault addr").trim(), frames)
                    i += body.size + 1
                }
                line.tag == "ActivityManager" && line.message.startsWith("ANR in ") -> {
                    val body = lines.drop(i + 1).takeWhile { it.tag == "ActivityManager" && it.pid == line.pid && !it.message.startsWith("ANR in ") }.take(12)
                    val pkg =
                        line.message
                            .removePrefix("ANR in ")
                            .substringBefore(' ')
                            .trim()
                    val reason =
                        body
                            .firstOrNull { it.message.startsWith("Reason:") }
                            ?.message
                            ?.removePrefix("Reason:")
                            ?.trim()
                    val anrPid =
                        body.firstNotNullOfOrNull {
                            ANR_PID
                                .find(it.message)
                                ?.groupValues
                                ?.get(1)
                                ?.toIntOrNull()
                        } ?: line.pid
                    crashes += DeviceCrash(id(line, DeviceCrash.Kind.ANR), DeviceCrash.Kind.ANR, line.time, anrPid, pkg, reason ?: line.message, body.map { it.message.trim() }.filter { !it.startsWith("Reason:") })
                    i += body.size + 1
                }
                else -> i++
            }
        }
        return crashes
    }

    private fun id(
        line: Line,
        kind: DeviceCrash.Kind,
    ) = "${line.time}/${line.pid}/$kind"
}
