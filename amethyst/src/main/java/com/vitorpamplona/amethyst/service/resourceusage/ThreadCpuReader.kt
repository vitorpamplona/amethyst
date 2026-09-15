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
package com.vitorpamplona.amethyst.service.resourceusage

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Reads per-thread CPU time for the current process. Split behind an interface
 * so [ThreadCpuSampler] can be unit-tested on the JVM without a `/proc`.
 *
 * Not thread-safe: implementations may reuse buffers. The sampler holds a lock
 * around every call, which is the only caller.
 */
interface ThreadCpuReader {
    /** Live thread ids, or an empty array when the task directory is unreadable. */
    fun listTids(): IntArray

    /** Kernel `comm` for a tid, or null if it exited (or cannot be read). */
    fun readName(tid: Int): String?

    /**
     * Cumulative user+system CPU for a tid in **clock ticks**, or -1 if it
     * exited between the listing and the read.
     *
     * Ticks rather than milliseconds so [ThreadCpuSampler] can subtract two
     * samples exactly: converting each sample to ms first would truncate every
     * reading, and 650 truncated readings sampled twice a minute drift.
     */
    fun readCpuTicks(tid: Int): Long

    /** Clock ticks per second — the divisor for [readCpuTicks] (100 on every Android kernel seen so far). */
    val ticksPerSecond: Long
}

/**
 * The real reader: `/proc/self/task/<tid>/{comm,stat}`. A process can always
 * read its own task directory — no root, no permission, and it works on a
 * release build, which is the only build where these measurements mean
 * anything (see `WorkerThreadPriorityGovernor`, which sweeps the same
 * directory for the same reason).
 *
 * Every read is failure-tolerant: a thread can exit between the listing and
 * the read, and that race is the normal case in a process that spawns hundreds
 * of short-lived socket threads. Failures return null/-1 and the sampler skips
 * the thread; nothing throws.
 */
class ProcThreadCpuReader(
    private val taskDir: File = File("/proc/self/task"),
    override val ticksPerSecond: Long = defaultTicksPerSecond(),
) : ThreadCpuReader {
    // Reused across every read in a sweep. ~650 threads x 2 files each would
    // otherwise be ~1300 byte[] allocations per flush on an already GC-pressured
    // heap — the same reasoning that made the governor use list() over listFiles().
    private val buffer = ByteArray(BUFFER_SIZE)

    override fun listTids(): IntArray {
        val names = taskDir.list() ?: return IntArray(0)
        val tids = IntArray(names.size)
        var count = 0
        for (name in names) {
            val tid = name.toIntOrNull() ?: continue
            tids[count++] = tid
        }
        return if (count == tids.size) tids else tids.copyOf(count)
    }

    override fun readName(tid: Int): String? {
        val length = readInto(File(taskDir, "$tid/comm"))
        if (length <= 0) return null
        // The kernel terminates `comm` with a newline; trimEnd also covers the
        // (unobserved) case of trailing padding.
        return String(buffer, 0, length, Charsets.UTF_8).trimEnd()
    }

    override fun readCpuTicks(tid: Int): Long {
        val length = readInto(File(taskDir, "$tid/stat"))
        if (length <= 0) return -1
        return parseUtimePlusStime(buffer, length)
    }

    /** Reads up to [BUFFER_SIZE] bytes into [buffer]; returns the length, or -1 on any failure. */
    private fun readInto(file: File): Int =
        try {
            FileInputStream(file).use { input ->
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read <= 0) break
                    total += read
                }
                total
            }
        } catch (e: IOException) {
            // Thread exited between listing and read, or /proc is restricted.
            -1
        }

    companion object {
        /**
         * A `stat` line is ~300 bytes and the two fields we want sit in the
         * first ~80, so a short read still parses. 512 leaves room without
         * making the reused buffer a page.
         */
        private const val BUFFER_SIZE = 512

        /** Field 14 (`utime`), counting from 1 — the 12th token after the `comm` field. */
        private const val UTIME_TOKEN_AFTER_COMM = 12

        fun defaultTicksPerSecond(): Long =
            try {
                Os.sysconf(OsConstants._SC_CLK_TCK).takeIf { it > 0 } ?: 100L
            } catch (e: Throwable) {
                // sysconf is a syscall wrapper that has no documented failure mode here,
                // but a bad value would silently scale every CPU figure, so fall back.
                100L
            }

        /**
         * Sums `utime` (field 14) and `stime` (field 15) out of a `/proc/<tid>/stat`
         * line, in clock ticks. Returns -1 if the line is malformed.
         *
         * **Parses from the LAST `)`, not by splitting on spaces.** Field 2 is the
         * thread's `comm` wrapped in parentheses and the kernel does not escape it,
         * so a thread named `Jit thread pool` — or, in this app, `OkHttp some.host`
         * — puts spaces inside a field and shifts every naive index. No field after
         * `comm` contains a `)`, so the last one is unambiguously its close.
         */
        fun parseUtimePlusStime(
            bytes: ByteArray,
            length: Int,
        ): Long {
            var cursor = -1
            for (i in length - 1 downTo 0) {
                if (bytes[i] == ')'.code.toByte()) {
                    cursor = i + 1
                    break
                }
            }
            if (cursor < 0) return -1

            // The first token after `comm` is field 3 (state), so field 14 is the
            // 12th and field 15 the 13th.
            var utime = -1L
            var token = 0
            while (cursor < length) {
                while (cursor < length && bytes[cursor] == ' '.code.toByte()) cursor++
                if (cursor >= length) break
                var value = 0L
                var digits = 0
                while (cursor < length) {
                    val digit = bytes[cursor] - '0'.code.toByte()
                    if (digit < 0 || digit > 9) break
                    value = value * 10 + digit
                    digits++
                    cursor++
                }
                // Skip the rest of a non-numeric token (the single-letter state field).
                while (cursor < length && bytes[cursor] != ' '.code.toByte()) cursor++
                token++
                if (token == UTIME_TOKEN_AFTER_COMM) {
                    if (digits == 0) return -1
                    utime = value
                } else if (token == UTIME_TOKEN_AFTER_COMM + 1) {
                    if (digits == 0) return -1
                    return utime + value
                }
            }
            return -1
        }
    }
}
