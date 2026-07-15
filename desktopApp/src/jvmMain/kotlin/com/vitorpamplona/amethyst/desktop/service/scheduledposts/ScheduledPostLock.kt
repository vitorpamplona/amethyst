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
package com.vitorpamplona.amethyst.desktop.service.scheduledposts

import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross-process single-writer lock for the scheduled-post drain.
 *
 * Two things can drain the shared `scheduled_posts.json` at the same time: the
 * in-app 45s timer (while the desktop app runs) and the headless
 * `--publish-scheduled` process launched by the OS scheduler. If both drained at
 * once they could double-publish a claimed row (or race on the file rename).
 *
 * [withDrainLock] serializes them with an OS-level advisory lock on
 * `~/.amethyst/scheduled/scheduled.lock` via [FileChannel.tryLock]. The lock is
 * non-blocking: if another process already holds it, [withDrainLock] returns
 * `null` and the caller skips this cycle (the other drainer will handle the due
 * rows). An in-process [AtomicBoolean] guard layers on top so two threads inside
 * the same JVM never both try to acquire the file lock (a JVM cannot hold two
 * overlapping locks on the same channel, and the file-lock is per-JVM anyway).
 */
object ScheduledPostLock {
    private const val TAG = "ScheduledPostLock"
    private const val LOCK_FILE_NAME = "scheduled.lock"

    // In-process guard: a single JVM must not attempt two overlapping file locks.
    private val inProcessBusy = AtomicBoolean(false)

    private fun lockFile(): File {
        val dir = File(System.getProperty("user.home"), ".amethyst/scheduled")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, LOCK_FILE_NAME)
    }

    /**
     * Runs [block] while holding the exclusive drain lock. Returns the block's
     * result, or `null` if the lock could not be acquired (someone else is
     * draining) — in which case [block] is never invoked.
     */
    fun <T> withDrainLock(block: () -> T): T? {
        // In-process fast path: bail if another thread in this JVM already holds it.
        if (!inProcessBusy.compareAndSet(false, true)) {
            Log.d(TAG) { "Drain skipped: another thread in this process holds the lock" }
            return null
        }

        var raf: RandomAccessFile? = null
        var lock: FileLock? = null
        try {
            raf = RandomAccessFile(lockFile(), "rw")
            lock =
                try {
                    raf.channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }

            if (lock == null) {
                Log.d(TAG) { "Drain skipped: lock held by another process" }
                return null
            }

            return block()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire scheduled-post drain lock", e)
            return null
        } finally {
            try {
                lock?.release()
            } catch (e: Exception) {
                Log.w(TAG) { "Failed to release drain lock: ${e.message}" }
            }
            try {
                raf?.close()
            } catch (e: Exception) {
                Log.w(TAG) { "Failed to close lock file: ${e.message}" }
            }
            inProcessBusy.set(false)
        }
    }
}
