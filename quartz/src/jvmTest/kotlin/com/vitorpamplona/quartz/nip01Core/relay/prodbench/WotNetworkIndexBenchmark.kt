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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.ParallelEventVerifier
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.followerCount
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.hops
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.rank
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test

/** Unsigned (hi, lo) order: the byte order of the hex pubkey. */
private fun compare(
    aHi: Long,
    aLo: Long,
    bHi: Long,
    bLo: Long,
): Int {
    val c = aHi.toULong().compareTo(bHi.toULong())
    return if (c != 0) c else aLo.toULong().compareTo(bLo.toULong())
}

/**
 * Phase 0 of `commons/plans/2026-10-07-wot-network-index.md`: how long does it take to go from
 * an observer's kind 10040 to a saved, loadable network index?
 *
 * Reads the observer's 10040, takes its `30382:rank` provider, then measures each stage on the
 * provider's full card set:
 *
 *  1. download only (NostrClient + fetchAllPages, events held in memory),
 *  2. Schnorr verify of every card (parallel, all cores),
 *  3. build the index (128-bit pubkey prefixes, sorted; rank / hops / followers side arrays),
 *  4. save the index file + the (event id, created_at) file used by negentropy,
 *  5. load + decode the index file, and a lookup micro-benchmark,
 *
 * then runs the pipeline the app would actually use, end to end in one pass: download →
 * [ParallelEventVerifier] → append to arrays → sort → save, without holding events.
 *
 * Opens sockets to a live relay, so it is opt-in:
 * `./gradlew :quartz:jvmTest --tests "*WotNetworkIndexBenchmark*" -PprodRelayBench=1`.
 * Override the observer with `-DwotObserver=<hex>`.
 */
class WotNetworkIndexBenchmark {
    companion object {
        const val DEFAULT_OBSERVER = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
        val LIST_RELAYS = listOf("wss://scores.brainstorm.world", "wss://purplepag.es", "wss://relay.damus.io")
        const val IDLE_MS = 60_000L
        const val MIN_SCORE = 5
    }

    /** Growable column store for verified cards; the shape the index file is built from. */
    private class Columns(
        capacity: Int,
    ) {
        var size = 0
        var hi = LongArray(capacity)
        var lo = LongArray(capacity)
        var rank = ByteArray(capacity)
        var hops = ByteArray(capacity)
        var followers = IntArray(capacity)
        var createdAt = LongArray(capacity)
        var ids = arrayOfNulls<String>(capacity)

        fun add(event: Event): Boolean {
            val target = event.tags.dTag()
            if (target.length != 64 || !Hex.isHex64(target)) return false
            if (size == hi.size) grow()
            hi[size] = Hex.readLong(target, 0)
            lo[size] = Hex.readLong(target, 16)
            rank[size] = (event.tags.rank() ?: 0).coerceIn(0, 127).toByte()
            hops[size] = (event.tags.hops() ?: -1).coerceIn(-1, 127).toByte()
            followers[size] = event.tags.followerCount() ?: 0
            createdAt[size] = event.createdAt
            ids[size] = event.id
            size++
            return true
        }

        private fun grow() {
            val n = hi.size * 2
            hi = hi.copyOf(n)
            lo = lo.copyOf(n)
            rank = rank.copyOf(n)
            hops = hops.copyOf(n)
            followers = followers.copyOf(n)
            createdAt = createdAt.copyOf(n)
            ids = ids.copyOf(n)
        }
    }

    /** The immutable, sorted snapshot the app would query. */
    private class Index(
        val keys: LongArray, // 2 longs per entry: (hi, lo), sorted unsigned
        val rank: ByteArray,
        val hops: ByteArray,
        val followers: IntArray,
    ) {
        val size get() = rank.size

        fun find(
            hi: Long,
            lo: Long,
        ): Int {
            var low = 0
            var high = size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val c = compare(keys[2 * mid], keys[2 * mid + 1], hi, lo)
                if (c < 0) {
                    low = mid + 1
                } else if (c > 0) {
                    high = mid - 1
                } else {
                    return mid
                }
            }
            return -1
        }

        fun rankOf(pubkey: String): Int {
            val i = find(Hex.readLong(pubkey, 0), Hex.readLong(pubkey, 16))
            return if (i < 0) -1 else rank[i].toInt()
        }
    }

    /**
     * Sorts by pubkey, keeps the newest card per pubkey, drops rank 0 (Brainstorm's removal
     * marker). Returns the index and the permutation used, so the ids file can follow it.
     */
    private fun build(c: Columns): Pair<Index, IntArray> {
        val order = (0 until c.size).sortedWith { a, b -> compare(c.hi[a], c.lo[a], c.hi[b], c.lo[b]).let { if (it != 0) it else c.createdAt[b].compareTo(c.createdAt[a]) } }
        val keep = IntArray(order.size)
        var n = 0
        var prevHi = 0L
        var prevLo = 0L
        for ((k, i) in order.withIndex()) {
            val dup = k > 0 && c.hi[i] == prevHi && c.lo[i] == prevLo
            prevHi = c.hi[i]
            prevLo = c.lo[i]
            if (dup || c.rank[i].toInt() == 0) continue
            keep[n++] = i
        }
        val keys = LongArray(2 * n)
        val rank = ByteArray(n)
        val hops = ByteArray(n)
        val followers = IntArray(n)
        for (j in 0 until n) {
            val i = keep[j]
            keys[2 * j] = c.hi[i]
            keys[2 * j + 1] = c.lo[i]
            rank[j] = c.rank[i]
            hops[j] = c.hops[i]
            followers[j] = c.followers[i]
        }
        return Index(keys, rank, hops, followers) to keep.copyOf(n)
    }

    private fun save(
        index: Index,
        provider: String,
        relay: String,
        file: File,
    ) {
        val tmp = File(file.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered(1 shl 16)).use { out ->
            out.writeBytes("AWOT")
            out.writeShort(1)
            out.write(Hex.decode(provider))
            val relayBytes = relay.encodeToByteArray()
            out.writeShort(relayBytes.size)
            out.write(relayBytes)
            out.writeLong(0L) // syncCursor
            out.writeLong(0L) // lastFullCheck
            out.writeInt(index.size)
            for (v in index.keys) out.writeLong(v)
            out.write(index.rank)
            out.write(index.hops)
            for (v in index.followers) out.writeInt(v)
        }
        tmp.renameTo(file)
    }

    private fun saveIds(
        c: Columns,
        order: IntArray,
        file: File,
    ) {
        val tmp = File(file.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered(1 shl 16)).use { out ->
            out.writeInt(order.size)
            for (i in order) {
                out.write(Hex.decode(c.ids[i]!!))
                out.writeLong(c.createdAt[i])
            }
        }
        tmp.renameTo(file)
    }

    private fun load(file: File): Index {
        val bytes = file.readBytes()
        DataInputStream(bytes.inputStream()).use { input ->
            val magic = ByteArray(4)
            input.readFully(magic)
            check(magic.decodeToString() == "AWOT")
            check(input.readShort().toInt() == 1)
            input.skipNBytes(32)
            input.skipNBytes(input.readShort().toLong())
            input.readLong()
            input.readLong()
            val n = input.readInt()
            val keys = LongArray(2 * n) { input.readLong() }
            val rank = ByteArray(n).also { input.readFully(it) }
            val hops = ByteArray(n).also { input.readFully(it) }
            val followers = IntArray(n) { input.readInt() }
            return Index(keys, rank, hops, followers)
        }
    }

    private fun ms(nanos: Long) = "%,.0f ms".format(nanos / 1e6)

    private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1e6)

    private fun heapUsed(): Long {
        val rt = Runtime.getRuntime()
        repeat(2) { System.gc() }
        return rt.totalMemory() - rt.freeMemory()
    }

    @Test
    fun downloadVerifyBuildSave() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("WotNetworkIndexBenchmark skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }
        val observer = System.getProperty("wotObserver") ?: DEFAULT_OBSERVER
        val cores = Runtime.getRuntime().availableProcessors()

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        val dir = File(System.getProperty("java.io.tmpdir"), "wot-bench-${System.nanoTime()}").apply { mkdirs() }

        runBlocking {
            val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient })
            try {
                // 0. The observer's 10040 → rank provider.
                val list =
                    LIST_RELAYS.firstNotNullOfOrNull { relay ->
                        client.fetchFirst(relay, Filter(kinds = listOf(TrustProviderListEvent.KIND), authors = listOf(observer))) as? TrustProviderListEvent
                    }
                val provider = list?.serviceProviders()?.firstOrNull { it.service == ProviderTypes.rank }
                if (provider == null) {
                    println("No 30382:rank provider in the 10040 of $observer")
                    return@runBlocking
                }
                val relay = provider.relayUrl
                val filter = Filter(kinds = listOf(30382), authors = listOf(provider.pubkey))
                println("=== WoT network index benchmark ===")
                println("observer $observer")
                println("provider ${provider.pubkey} @ ${relay.url}   ($cores cores)")

                // 1. Download only, events held.
                val events = ArrayList<Event>(200_000)
                val wireChars = AtomicLong(0)
                val heapBefore = heapUsed()
                val t1 = System.nanoTime()
                val pages = AtomicInteger(0)
                val result =
                    client.fetchAllPages(relay, listOf(filter), IDLE_MS, onNewPage = { pages.incrementAndGet() }) { event ->
                        events.add(event)
                        wireChars.addAndGet(event.content.length + 64L * 3 + event.tags.sumOf { t -> t.sumOf { it.length + 3 } })
                    }
                val download = System.nanoTime() - t1
                val heapHeld = heapUsed() - heapBefore
                println("1. download        ${ms(download)}  ${"%,d".format(events.size)} cards, ${pages.get()} pages, end=${result.end}, ~${mb(wireChars.get())} of event payload, ${"%,.0f".format(events.size * 1e9 / download)} cards/s")
                println("   heap held by parsed events: ${mb(heapHeld)} (${heapHeld / events.size.coerceAtLeast(1)} B/card)")

                // 2. Verify every signature, all cores.
                val bad = AtomicInteger(0)
                val t2 = System.nanoTime()
                events
                    .chunked((events.size / (cores * 4)).coerceAtLeast(1))
                    .map { chunk ->
                        async(Dispatchers.Default) {
                            for (e in chunk) if (!e.verify()) bad.incrementAndGet()
                        }
                    }.awaitAll()
                val verify = System.nanoTime() - t2
                println("2. verify          ${ms(verify)}  ${bad.get()} invalid, ${"%,.0f".format(events.size * 1e9 / verify)} verifies/s on $cores cores (${"%.0f".format(verify / 1e3 * cores / events.size.coerceAtLeast(1))} µs/verify/core)")

                // 3. Build the index.
                val t3 = System.nanoTime()
                val columns = Columns(events.size.coerceAtLeast(16))
                var rejected = 0
                for (e in events) if (e.pubKey != provider.pubkey || e.kind != 30382 || !columns.add(e)) rejected++
                val (index, order) = build(columns)
                val buildTime = System.nanoTime() - t3
                var pass = 0
                for (r in index.rank) if (r >= MIN_SCORE) pass++
                println("3. build index     ${ms(buildTime)}  ${"%,d".format(index.size)} entries (${columns.size - index.size} dropped: dup or rank 0; $rejected rejected), ${"%,d".format(pass)} with rank >= $MIN_SCORE")

                // 4. Save.
                val indexFile = File(dir, "network-v1.bin")
                val idsFile = File(dir, "network-ids-v1.bin")
                val t4 = System.nanoTime()
                save(index, provider.pubkey, relay.url, indexFile)
                saveIds(columns, order, idsFile)
                val saveTime = System.nanoTime() - t4
                println("4. save            ${ms(saveTime)}  index ${mb(indexFile.length())}, ids ${mb(idsFile.length())}")

                // 5. Load + decode (best of 5, the first one is cold-ish).
                val loads =
                    (1..5).map {
                        val t = System.nanoTime()
                        load(indexFile)
                        System.nanoTime() - t
                    }
                val loaded = load(indexFile)
                check(loaded.size == index.size)
                val probe = events.map { it.tags.dTag() }
                var found = 0
                val t5 = System.nanoTime()
                repeat(5) { for (p in probe) if (loaded.rankOf(p) >= 0) found++ }
                val lookups = System.nanoTime() - t5
                println("5. load            first ${ms(loads.first())}, best ${ms(loads.min())}; lookup ${"%.0f".format(lookups.toDouble() / (5 * probe.size))} ns (hex read + binary search), ${found / 5} of ${probe.size} found")

                events.clear()
                println("   total (1-4, staged): ${ms(download + verify + buildTime + saveTime)}")

                // 6. The real pipeline: one pass, verify while downloading, no events held.
                delay(2_000)
                val streamColumns = Columns(1 shl 16)
                val invalid = AtomicInteger(0)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val t6 = System.nanoTime()
                val verifier =
                    ParallelEventVerifier<Unit>(
                        scope = scope,
                        parallelism = cores,
                        onInvalid = { _, _ -> invalid.incrementAndGet() },
                        onVerified = { e, _ -> if (e.pubKey == provider.pubkey && e.kind == 30382) streamColumns.add(e) },
                    )
                val streamResult = client.fetchAllPages(relay, listOf(filter), IDLE_MS) { event -> verifier.submit(event, Unit) }
                val downloaded = System.nanoTime() - t6
                verifier.close()
                verifier.join()
                val verified = System.nanoTime() - t6
                val (streamIndex, streamOrder) = build(streamColumns)
                save(streamIndex, provider.pubkey, relay.url, indexFile)
                saveIds(streamColumns, streamOrder, idsFile)
                val total = System.nanoTime() - t6
                scope.cancel()
                println("6. one-pass pipeline: download done at ${ms(downloaded)}, last verify at ${ms(verified)}, built + saved at ${ms(total)}  (${"%,d".format(streamIndex.size)} entries, ${invalid.get()} invalid, end=${streamResult.end})")
            } finally {
                client.close()
            }
        }
        dir.deleteRecursively()
        httpClient.dispatcher.executorService.shutdown()
    }
}
