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
package com.vitorpamplona.amethyst.desktop.service.bench

import coil3.EventListener
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale

/**
 * Where the images a scroll pass asked for came from, and how long each took: the image loader's
 * [listener] counts them while the benchmark runs. Frame timing can't see a cache miss, because the
 * image loads and decodes off the UI thread: a miss shows as an image that appears late, which
 * these numbers catch.
 */
object ImageLoadStats {
    /** One pass's image loads. [memory] are memory-cache hits, drawn without loading anything. */
    class Counts(
        val memory: Int,
        val disk: Int,
        val network: Int,
        val failed: Int,
        // Milliseconds from request to result of every load that did not come from memory.
        val loadMs: DoubleArray,
    ) {
        val total: Int get() = memory + disk + network + failed

        val memoryPercent: Double get() = if (total == 0) 0.0 else 100.0 * memory / total

        companion object {
            val NONE = Counts(0, 0, 0, 0, DoubleArray(0))
        }
    }

    // By identity: requests compare by value, and two equal ones in flight at once (two cards
    // showing the same avatar) would share one start time.
    private val started: MutableMap<ImageRequest, Long> = Collections.synchronizedMap(IdentityHashMap())

    // Guarded by this.
    private var memory = 0
    private var disk = 0
    private var network = 0
    private var failed = 0
    private var loadNanos = ArrayList<Long>()

    val listener =
        object : EventListener() {
            override fun onStart(request: ImageRequest) {
                started[request] = System.nanoTime()
            }

            override fun onSuccess(
                request: ImageRequest,
                result: SuccessResult,
            ) {
                val took = started.remove(request)?.let { System.nanoTime() - it }
                synchronized(this@ImageLoadStats) {
                    log
                        .append(
                            listOf(
                                pass,
                                result.dataSource.name,
                                took?.let { "%.1f".format(Locale.ROOT, it / 1e6) } ?: "",
                                "${result.image.width}x${result.image.height}",
                                result.image.size.toString(),
                                result.isSampled.toString(),
                                result.memoryCacheKey
                                    ?.extras
                                    ?.entries
                                    ?.joinToString(",") { "${it.key}=${it.value}" }
                                    .orEmpty(),
                                result.memoryCacheKey?.key ?: request.data.toString(),
                                // Who asked: the size it asked for, and its listener (the feed prefetch has one).
                                request.sizeResolver.toString(),
                                request.listener?.javaClass?.simpleName ?: "",
                            ).joinToString("\t"),
                        ).append('\n')
                    when (result.dataSource) {
                        DataSource.MEMORY_CACHE -> memory++
                        DataSource.DISK -> disk++
                        DataSource.NETWORK -> network++
                        // Already in memory as bytes (a data: URI, a blurhash): no load to speak of.
                        DataSource.MEMORY -> memory++
                    }
                    if (result.dataSource != DataSource.MEMORY_CACHE && result.dataSource != DataSource.MEMORY && took != null) loadNanos.add(took)
                }
            }

            override fun onError(
                request: ImageRequest,
                result: ErrorResult,
            ) {
                started.remove(request)
                synchronized(this@ImageLoadStats) { failed++ }
            }

            override fun onCancel(request: ImageRequest) {
                started.remove(request)
            }
        }

    // Every image the passes loaded, one TSV line each (see [log]).
    private val log = StringBuilder()
    private var pass = ""

    /** Every load of the run: pass, source, ms, decoded size, bytes, sampled, key extras, key. */
    @Synchronized
    fun log(): String = "pass\tsource\tms\tdecoded\tbytes\tsampled\textras\tkey\tsize\tlistener\n" + log

    /** Starts counting the pass [name]. */
    @Synchronized
    fun reset(name: String = "") {
        pass = name
        memory = 0
        disk = 0
        network = 0
        failed = 0
        loadNanos = ArrayList()
    }

    /** What was counted since [reset]. */
    @Synchronized
    fun snapshot(): Counts = Counts(memory, disk, network, failed, DoubleArray(loadNanos.size) { loadNanos[it] / 1e6 })
}
