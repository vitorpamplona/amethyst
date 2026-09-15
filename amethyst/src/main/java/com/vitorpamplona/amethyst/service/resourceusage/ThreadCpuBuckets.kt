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

/**
 * Maps a Linux thread's kernel `comm` onto one of a small, **compile-time
 * fixed** set of subsystem buckets, so [ThreadCpuSampler] can attribute CPU
 * time without ever keying a counter on a runtime string.
 *
 * That last part is not stylistic. OkHttp renames its threads after the host
 * they are currently serving (`OkHttp relay.example.com`), so a thread name is
 * potentially a **relay hostname** — exactly what the ledger promises never to
 * record (see [UsageKeys]). Classifying to a fixed bucket here is what keeps
 * that promise: the name is read, matched, and dropped; only the bucket
 * survives.
 *
 * Bucket names also avoid every segment [UsageKeys] reserves for
 * [UsageKeys.sumMatching] — `image`/`video`/`other` are HTTP roles, so the
 * image-decode and playback buckets are named [MEDIA] and folded into
 * [DISPATCH] rather than borrowing a role name. `ThreadCpuBucketsTest` pins this.
 *
 * Matching is by `comm` **prefix** because the kernel caps `comm` at 15
 * characters, so real names arrive truncated (`ExoPlayer:Playb`,
 * `tokio-runtime-w`). Each thread is classified once per tid for its whole
 * life, so the cost is a string compare per thread, not per sample.
 */
object ThreadCpuBuckets {
    /** The UI thread: recomposition, layout, input, and anything mistakenly posted here. */
    const val MAIN = "main"

    /** RenderThread and friends — they draw the frames everything else is competing for. */
    const val RENDER = "render"

    /** OkHttp/Okio: relay websockets, HTTP, TLS, and the connection pool. */
    const val NET = "net"

    /** kotlinx.coroutines' shared scheduler — Dispatchers.Default AND Dispatchers.IO. */
    const val DISPATCH = "dispatch"

    /** Plain JVM executors and bare threads that are not the coroutine scheduler. */
    const val POOL = "pool"

    /** ART's garbage collector and reference daemons. */
    const val GC = "gc"

    /** ART's JIT and runtime workers — high early in a cold start, ~0 once warm. */
    const val JIT = "jit"

    /** Arti's tokio runtime: circuit crypto plus directory/guard keep-alives. */
    const val TOR = "tor"

    /** Codecs, players and audio I/O: ExoPlayer, MediaCodec, AudioTrack/Record, Opus. */
    const val MEDIA = "media"

    /** Binder threads — incoming IPC the system framework is blocked on. */
    const val BINDER = "binder"

    /** Anything unmatched. A large [MISC] means this table needs a new row, not that the CPU is unexplained. */
    const val MISC = "misc"

    /**
     * CPU burned by threads that exited between two samples, recovered as the
     * difference between the process total and the sum of the live threads
     * (see [ThreadCpuSampler]). Not a thread name — a residual — but it is a
     * bucket in the emitted table, and a large one is itself the finding:
     * short-lived thread churn.
     */
    const val GONE = "gone"

    /** Every bucket a sample can emit, including the [GONE] residual. */
    val ALL = listOf(MAIN, RENDER, NET, DISPATCH, POOL, GC, JIT, TOR, MEDIA, BINDER, MISC, GONE)

    /**
     * Prefix table, scanned in order. Earlier rows win, so put the specific
     * before the general (`Jit thread pool` must be matched before `pool`).
     */
    private val PREFIXES =
        listOf(
            // --- ART internals. Matched first: several start with words that
            // would otherwise fall into POOL or MISC.
            "Jit thread pool" to JIT,
            "Runtime worker" to JIT,
            "HeapTaskDaemon" to GC,
            "ReferenceQueueD" to GC,
            "FinalizerDaemon" to GC,
            "FinalizerWatchd" to GC,
            // --- Drawing.
            "RenderThread" to RENDER,
            "hwuiTask" to RENDER,
            "GPU completion" to RENDER,
            // --- Networking. "OkHttp <host>" lands here, which is the point:
            // the hostname never escapes this function.
            "OkHttp" to NET,
            "Okio" to NET,
            "ConnectionPool" to NET,
            // --- Coroutines. Dispatchers.IO shares this pool, so Coil's image
            // decodes and every withContext(IO) land in DISPATCH, not MEDIA.
            "DefaultDispatch" to DISPATCH,
            "kotlinx.coroutin" to DISPATCH,
            // --- Tor (Arti runs on tokio).
            "tokio-runtime" to TOR,
            "arti" to TOR,
            // --- Media.
            "ExoPlayer" to MEDIA,
            "MediaCodec" to MEDIA,
            "codec_looper" to MEDIA,
            "AudioTrack" to MEDIA,
            "AudioRecord" to MEDIA,
            "Opus" to MEDIA,
            // --- IPC.
            "binder:" to BINDER,
            "Binder:" to BINDER,
            "HwBinder:" to BINDER,
            // --- Generic executors, last because the names are the vaguest.
            "pool-" to POOL,
            "Thread-" to POOL,
            "AsyncTask" to POOL,
            "queued-work" to POOL,
        )

    /** The bucket for a kernel `comm`, or [MISC] when no prefix matches. */
    fun classify(comm: String): String {
        for ((prefix, bucket) in PREFIXES) {
            if (comm.startsWith(prefix)) return bucket
        }
        return MISC
    }
}
