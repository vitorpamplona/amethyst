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
package com.vitorpamplona.amethyst.commons.service.image

import coil3.annotation.ExperimentalCoilApi
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import coil3.network.ConcurrentRequestStrategy
import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * One download per image, owned by the image loader instead of by the request that asked for it.
 *
 * Coil's `DeDupeConcurrentRequestStrategy` runs a download inside the first request's coroutine, so
 * cancelling that request kills the download, and a second request for the same image that was
 * waiting on it starts over from zero. On a feed that is the usual case: posts arriving at the top
 * push a card down, its request is cancelled, and the card at its new place asks for the same image
 * at about the same moment (19 of 20 such re-requests within 0.15 s, measured on the desktop feeds).
 *
 * Here the download runs in [scope] and every request for the key waits on it; a cancelled request
 * only stops waiting. When the last one leaves, the download is cut, unless it is small: one whose
 * response says it is at most [finishBelowBytes] finishes into the disk cache, since the image is
 * usually just a few items down. A response that has not said its size yet gets [sizeWaitMs] to say
 * it, and at most [maxOrphans] downloads that nobody waits for run at once. The size comes from
 * [reportingDownloadSize], which the network fetchers wrap their client in.
 *
 * As with Coil's strategy, the first request back takes the download's own result and the others
 * run their own fetch, which reads the copy left in the disk cache. A failure is the outcome of the
 * request whose fetch it was: any other runs its own, since it may differ (a signed BUD-01 retry
 * that joined an anonymous download of the same blob must not inherit its 401).
 */
@OptIn(ExperimentalCoilApi::class)
class SharedDownloadStrategy(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val finishBelowBytes: Long = FINISH_BELOW_BYTES,
    private val sizeWaitMs: Long = SIZE_WAIT_MS,
    private val maxOrphans: Int = MAX_ORPHANS,
) : ConcurrentRequestStrategy {
    private class Download {
        lateinit var job: Deferred<FetchResult>
        val size = DownloadSize()

        // All below are guarded by [lock].
        var waiters = 0
        var claimed = false
        var result: FetchResult? = null
        var orphan = false
        var done = false
    }

    private val lock = Any()
    private val downloads = HashMap<String, Download>()
    private var orphans = 0

    override suspend fun apply(
        key: String,
        block: suspend () -> FetchResult,
    ): FetchResult {
        while (true) {
            val (download, started) = join(key, block)
            val result =
                try {
                    download.job.await()
                } catch (e: CancellationException) {
                    leave(key, download)
                    // This request was cancelled: it only stops waiting.
                    currentCoroutineContext().ensureActive()
                    // Its fetch threw a cancellation of its own: the outcome of the request that
                    // started it. A request that joined runs its own.
                    if (started) throw e
                    continue
                } catch (e: Throwable) {
                    synchronized(lock) { download.waiters-- }
                    if (started) throw e
                    continue
                }
            return if (claim(download)) result else block()
        }
    }

    /** Waits on the download running for [key], starting one when there is none; true when it started it. */
    private fun join(
        key: String,
        block: suspend () -> FetchResult,
    ): Pair<Download, Boolean> =
        synchronized(lock) {
            val running = downloads[key]
            if (running != null) {
                running.waiters++
                if (running.orphan) {
                    running.orphan = false
                    orphans--
                }
                return running to false
            }
            val download = Download()
            download.waiters = 1
            downloads[key] = download
            // Dispatched, not run here: the lock is never held across the download.
            download.job =
                scope.async(download.size) {
                    try {
                        block().also { synchronized(lock) { download.result = it } }
                    } finally {
                        synchronized(lock) {
                            // Marked here, under the lock, not read off the job: the job is still
                            // active while this runs, and a decision taken now must see it is over.
                            download.done = true
                            if (downloads[key] === download) downloads.remove(key)
                            if (download.orphan) {
                                download.orphan = false
                                orphans--
                            }
                        }
                        closeIfAbandoned(download)
                    }
                }
            download to true
        }

    /** Whether this request, back from waiting, is the first: it takes the download's own outcome. */
    private fun claim(download: Download): Boolean =
        synchronized(lock) {
            download.waiters--
            !download.claimed.also { download.claimed = true }
        }

    /** A cancelled request stops waiting; the download goes on only while someone else waits for it, or it is small. */
    private fun leave(
        key: String,
        download: Download,
    ) {
        val orphaned =
            synchronized(lock) {
                download.waiters--
                download.waiters == 0 && !download.done
            }
        if (orphaned) decide(key, download) else closeIfAbandoned(download)
    }

    /** Finishes [download], which nobody waits for, if its response is small; cuts it otherwise. */
    private fun decide(
        key: String,
        download: Download,
    ) {
        scope.launch {
            // The size comes with the response headers: give them a moment to arrive.
            val bytes = withTimeoutOrNull(sizeWaitMs) { download.size.bytes.first { it != null } }
            val cut =
                synchronized(lock) {
                    // Wanted again meanwhile, or already over.
                    if (download.waiters > 0 || download.done) return@launch
                    // An empty first response is followed by Coil's second request without cache
                    // headers, whose size is not known yet: 0 says nothing.
                    if (bytes != null && bytes in 1..finishBelowBytes && (download.orphan || orphans < maxOrphans)) {
                        if (!download.orphan) {
                            download.orphan = true
                            orphans++
                        }
                        false
                    } else {
                        if (downloads[key] === download) downloads.remove(key)
                        true
                    }
                }
            if (cut) download.job.cancel()
        }
    }

    /** Releases the result of a download that finished after every request for it left. */
    private fun closeIfAbandoned(download: Download) {
        val abandoned =
            synchronized(lock) {
                val result = download.result
                if (result != null && download.waiters == 0 && !download.claimed) {
                    download.claimed = true
                    result
                } else {
                    null
                }
            }
        (abandoned as? SourceFetchResult)?.source?.close()
    }

    companion object {
        /** A download nobody waits for anymore finishes when its response is at most this big. */
        const val FINISH_BELOW_BYTES = 512L * 1024

        /** How long a download nobody waits for may run before its response says how big it is. */
        const val SIZE_WAIT_MS = 1_000L

        /** At most this many downloads nobody waits for finish at once. */
        const val MAX_ORPHANS = 6
    }
}

/**
 * The size of the response a [SharedDownloadStrategy] download is fetching, set by
 * [reportingDownloadSize] when its headers arrive: -1 when it does not say, null until then.
 */
class DownloadSize : AbstractCoroutineContextElement(Key) {
    val bytes = MutableStateFlow<Long?>(null)

    companion object Key : CoroutineContext.Key<DownloadSize>
}

/** This client, telling the [SharedDownloadStrategy] download it runs in how big each response is. */
fun NetworkClient.reportingDownloadSize(): NetworkClient = SizeReportingNetworkClient(this)

private class SizeReportingNetworkClient(
    private val delegate: NetworkClient,
) : NetworkClient {
    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ): T {
        val size = currentCoroutineContext()[DownloadSize]
        return delegate.executeRequest(request) { response ->
            size?.bytes?.value = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            block(response)
        }
    }
}
