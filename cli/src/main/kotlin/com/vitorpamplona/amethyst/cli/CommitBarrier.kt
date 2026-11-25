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
package com.vitorpamplona.amethyst.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * `AMY_TEST_COMMIT_BARRIER=<dir>`, for interop tests only: a run that arms it ([Context.commitBarrier])
 * stages its group Commit as usual, then holds it before the publish: it writes `<dir>/ready-<pid>`
 * and waits for `<dir>/go`. A harness that starts two devices this way and creates `go` once both
 * are ready gets two Commits built on one epoch, each without having seen the other: a fork, every
 * time, instead of whenever two process start-ups happen to overlap.
 *
 * Only the verbs that make one Commit arm it (`marmot devices rename`), after their sync, so the
 * Commit held is the one the test races and nothing the sync published waits.
 */
object CommitBarrier {
    const val ENV = "AMY_TEST_COMMIT_BARRIER"

    private const val POLL_MS = 100L
    private const val MAX_WAIT_MS = 180_000L

    /** The barrier directory this run was given, or null outside such a test. */
    fun fromEnv(): File? = System.getenv(ENV)?.takeIf { it.isNotBlank() }?.let(::File)

    /** Says this run is ready at [dir] and waits for the harness's go; publishes anyway after three minutes. */
    suspend fun await(dir: File) =
        withContext(Dispatchers.IO) {
            if (!dir.isDirectory && !dir.mkdirs()) System.err.println("warning: $ENV: could not create $dir")
            val ready = File(dir, "ready-${ProcessHandle.current().pid()}")
            if (!ready.createNewFile() && !ready.exists()) System.err.println("warning: $ENV: could not write $ready")
            System.err.println("$ENV: Commit staged, holding it until ${File(dir, "go")} exists")
            val go = File(dir, "go")
            var waited = 0L
            while (!go.exists() && waited < MAX_WAIT_MS) {
                delay(POLL_MS)
                waited += POLL_MS
            }
            if (!go.exists()) System.err.println("warning: $ENV: no go after ${MAX_WAIT_MS / 1000}s, publishing anyway")
        }
}
