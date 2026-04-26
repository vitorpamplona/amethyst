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
package com.vitorpamplona.quartz.nip01Core.store.fs

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock

/**
 * Cross-process write serialisation for the file-backed event store.
 *
 * Two layers of mutual exclusion:
 *
 *  - [inProcessLock] — a `ReentrantLock` serialising threads within this
 *    JVM. Reentrant on the same thread, so nested
 *    `withWriteLock` calls (e.g. a transaction body that calls insert)
 *    reuse the lock instead of self-deadlocking.
 *
 *  - [FileChannel.lock] on `.lock` — a POSIX/Windows advisory file lock
 *    serialising this JVM against other JVMs pointed at the same store.
 *    Acquired once on first entry, released once on last exit (tracked
 *    via the reentrant hold count).
 *
 * Readers do not take either lock. The store's atomic-rename writes
 * mean readers see either the pre- or post-mutation state, never a
 * torn file. Stale `idx/` entries that point at a just-unlinked
 * canonical are tolerated by the query loop's `NoSuchFileException`
 * handling.
 */
internal class FsLockManager(
    root: Path,
) : AutoCloseable {
    private val lockPath: Path = root.resolve(LOCK_FILE)
    private val inProcessLock = ReentrantLock()

    /** Held only while this JVM owns the file lock (depth ≥ 1). Guarded by [inProcessLock]. */
    private var channel: FileChannel? = null
    private var fileLock: FileLock? = null

    init {
        try {
            Files.createFile(lockPath)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            // existing lock file from a previous run — fine
        }
    }

    fun acquireWriteLock() {
        inProcessLock.lock()
        try {
            // Only the outermost re-entry actually touches the file lock.
            if (inProcessLock.holdCount == 1) {
                val ch = FileChannel.open(lockPath, StandardOpenOption.READ, StandardOpenOption.WRITE)
                val l =
                    try {
                        ch.lock() // exclusive, blocking
                    } catch (t: Throwable) {
                        ch.close()
                        throw t
                    }
                channel = ch
                fileLock = l
            }
        } catch (t: Throwable) {
            inProcessLock.unlock()
            throw t
        }
    }

    fun releaseWriteLock() {
        try {
            if (inProcessLock.holdCount == 1) {
                releaseFileLock()
            }
        } finally {
            inProcessLock.unlock()
        }
    }

    /**
     * Inline so callers may invoke `suspend` functions inside the lock
     * body — needed by [FsEventStore.delete], which calls the suspend
     * `query` to enumerate ids before deleting them.
     */
    inline fun <T> withWriteLock(body: () -> T): T {
        acquireWriteLock()
        try {
            return body()
        } finally {
            releaseWriteLock()
        }
    }

    override fun close() {
        inProcessLock.lock()
        try {
            releaseFileLock()
        } finally {
            inProcessLock.unlock()
        }
    }

    private fun releaseFileLock() {
        try {
            fileLock?.release()
        } catch (_: IOException) {
            // best-effort during unwind
        }
        try {
            channel?.close()
        } catch (_: IOException) {
            // best-effort during unwind
        }
        fileLock = null
        channel = null
    }

    companion object {
        const val LOCK_FILE = ".lock"
    }
}
