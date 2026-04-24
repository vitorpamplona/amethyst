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

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Cross-process write serialisation for the file-backed event store.
 *
 * Acquires an exclusive `flock(2)` on `.lock` while the body runs, so a
 * second `amy` invocation against the same directory blocks until the
 * first releases. Re-entrant for the calling thread — nested
 * `withWriteLock` calls (e.g. transaction body that calls insert) reuse
 * the existing lock instead of self-deadlocking.
 *
 * Readers do not take this lock. The store's atomic-rename writes mean
 * readers see either the pre- or post-mutation state, never a torn
 * file. Stale `idx/` entries that point at a just-unlinked canonical
 * are tolerated by the query loop's `NoSuchFileException` handling.
 */
internal class FsLockManager(
    root: Path,
) : AutoCloseable {
    private val lockPath: Path = root.resolve(LOCK_FILE)
    private val mu = Any()

    /** Per-thread re-entry depth. Allows nested `withWriteLock` calls. */
    private val depth = ThreadLocal.withInitial { 0 }

    /** Active channel + lock when depth > 0 (any thread). Held by whoever owns the lock. */
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    init {
        try {
            Files.createFile(lockPath)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            // existing lock file from a previous run — fine
        }
    }

    fun <T> withWriteLock(body: () -> T): T {
        // Re-entrant on the same thread: just run.
        if (depth.get() > 0) {
            depth.set(depth.get() + 1)
            try {
                return body()
            } finally {
                depth.set(depth.get() - 1)
            }
        }

        // Cross-thread + cross-process serialisation.
        synchronized(mu) {
            val ch = FileChannel.open(lockPath, StandardOpenOption.READ, StandardOpenOption.WRITE)
            val l = ch.lock() // exclusive, blocking
            channel = ch
            lock = l
            depth.set(1)
            try {
                return body()
            } finally {
                depth.set(0)
                try {
                    l.release()
                } catch (_: Throwable) {
                    // ignore
                }
                try {
                    ch.close()
                } catch (_: Throwable) {
                    // ignore
                }
                channel = null
                lock = null
            }
        }
    }

    override fun close() {
        synchronized(mu) {
            try {
                lock?.release()
            } catch (_: Throwable) {
                // ignore
            }
            try {
                channel?.close()
            } catch (_: Throwable) {
                // ignore
            }
            channel = null
            lock = null
        }
    }

    companion object {
        const val LOCK_FILE = ".lock"
    }
}
