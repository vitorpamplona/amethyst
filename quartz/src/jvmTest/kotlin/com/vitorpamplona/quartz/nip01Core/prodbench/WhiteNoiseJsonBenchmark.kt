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
package com.vitorpamplona.quartz.nip01Core.prodbench

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.verifyId
import com.vitorpamplona.quartz.utils.Hex
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Head-to-head JSON benchmark: whitenoise-android's `org.json` event path against
 * Quartz's Jackson path, on identical inputs.
 *
 * whitenoise-android declares no `kotlinx-serialization`, Moshi, Gson or Jackson.
 * Its 22 production files that touch JSON all use `org.json`
 * (`JSONObject`/`JSONArray`). The event path benchmarked here is vendored verbatim
 * from `app/src/main/java/dev/ipf/whitenoise/android/core/nostr/NostrEvent.kt`
 * (master @ 67d33de) - `NostrEvent.fromJson`, `canonicalJson`, `computedIdHex`,
 * `toHex` and `hexToBytes`.
 *
 * CAVEAT: on a device, `org.json` resolves to AOSP's `libcore` implementation from
 * `android.jar`, not to the `org.json:json` Maven artifact used here (their own
 * build comment: "Real org.json for JVM unit tests - the android.jar stubs throw
 * on use"). The two are different implementations, so treat the absolute JVM
 * numbers as indicative of the approach, not as a device measurement.
 *
 * Run with: ./gradlew :quartz:jvmTest --tests "*.WhiteNoiseJsonBenchmark"
 */
class WhiteNoiseJsonBenchmark {
    private val rnd = Random(20260910)

    private fun hex(bytes: Int) = Hex.encode(rnd.nextBytes(bytes))

    private fun note(
        tagCount: Int,
        contentChars: Int,
    ): Event {
        val alphabet = "abcdefghij quoted text \n"
        val tags =
            Array(tagCount) {
                if (it % 2 == 0) arrayOf("p", hex(32), "wss://relay.example.com") else arrayOf("e", hex(32), "", "reply")
            }
        val content = buildString { repeat(contentChars) { append(alphabet[it % alphabet.length]) } }
        return Event(hex(32), hex(32), 1_770_000_000L + rnd.nextInt(1_000_000), 1, tags, content, hex(64))
    }

    private fun followList(entries: Int): Event {
        val tags = Array(entries) { arrayOf("p", hex(32), "wss://relay.example.com", "petname$it") }
        return Event(hex(32), hex(32), 1_770_000_000L, 3, tags, "", hex(64))
    }

    /** A note-sized corpus: what a feed REQ actually streams back. */
    private val smallEvents = List(CORPUS) { note(tagCount = it % 5, contentChars = 140) }

    /** A kind:3 follow list - the big-event case every client hits on login. */
    private val largeEvents = List(8) { followList(500) }

    private val smallJson = smallEvents.map { it.toJson() }
    private val largeJson = largeEvents.map { it.toJson() }

    private val smallParsedWn = smallJson.map { WnNostrEvent.fromJson(JSONObject(it))!! }
    private val smallParsedQz = smallJson.map { Event.fromJson(it) }
    private val largeParsedWn = largeJson.map { WnNostrEvent.fromJson(JSONObject(it))!! }
    private val largeParsedQz = largeJson.map { Event.fromJson(it) }

    private val digest32 = MessageDigest.getInstance("SHA-256").digest("quartz".toByteArray())
    private val digest32Hex = Hex.encode(digest32)

    private inline fun bench(
        warmup: Int,
        iterations: Int,
        op: (Int) -> Any?,
    ): Double {
        var sink = 0
        repeat(warmup) { sink += op(it).hashCode() }
        val start = System.nanoTime()
        repeat(iterations) { sink += op(it).hashCode() }
        val elapsed = System.nanoTime() - start
        check(sink != Int.MIN_VALUE)
        return elapsed.toDouble() / iterations
    }

    private fun row(
        name: String,
        wn: Double,
        qz: Double,
    ) = String.format(Locale.US, "%-46s %11.2f us %11.2f us %9.1fx", name, wn / 1000.0, qz / 1000.0, wn / qz)

    @Test
    fun agreementGate() {
        // The vendored whitenoise path and Quartz must agree on every field and on the id.
        smallJson.forEachIndexed { i, json ->
            val wn = WnNostrEvent.fromJson(JSONObject(json))!!
            val qz = Event.fromJson(json)
            assertEquals(qz.id, wn.id)
            assertEquals(qz.pubKey, wn.pubkey)
            assertEquals(qz.createdAt, wn.createdAt)
            assertEquals(qz.kind, wn.kind)
            assertEquals(qz.content, wn.content)
            assertEquals(qz.tags.size, wn.tags.size)
            assertEquals(
                EventHasher.hashId(qz.pubKey, qz.createdAt, qz.kind, qz.tags, qz.content),
                wn.computedIdHex(),
                "canonical id serialization diverges on event $i",
            )
        }
        assertTrue(WnHex.toHex(digest32).equals(digest32Hex, ignoreCase = true))
        assertTrue(WnHex.hexToBytes(digest32Hex)!!.contentEquals(Hex.decode(digest32Hex)))
    }

    @Test
    fun benchmarkJson() {
        val parseSmallWn = bench(WARMUP, ITERS) { WnNostrEvent.fromJson(JSONObject(smallJson[it % CORPUS])) }
        val parseSmallQz = bench(WARMUP, ITERS) { Event.fromJson(smallJson[it % CORPUS]) }

        val parseLargeWn = bench(WARMUP_L, ITERS_L) { WnNostrEvent.fromJson(JSONObject(largeJson[it % 8])) }
        val parseLargeQz = bench(WARMUP_L, ITERS_L) { Event.fromJson(largeJson[it % 8]) }

        val idSmallWn = bench(WARMUP, ITERS) { smallParsedWn[it % CORPUS].computedIdHex() }
        val idSmallQz =
            bench(WARMUP, ITERS) {
                val e = smallParsedQz[it % CORPUS]
                EventHasher.hashId(e.pubKey, e.createdAt, e.kind, e.tags, e.content)
            }

        val idLargeWn = bench(WARMUP_L, ITERS_L) { largeParsedWn[it % 8].computedIdHex() }
        val idLargeQz =
            bench(WARMUP_L, ITERS_L) {
                val e = largeParsedQz[it % 8]
                EventHasher.hashId(e.pubKey, e.createdAt, e.kind, e.tags, e.content)
            }

        // What validation actually calls: recompute and compare against the claimed id.
        val checkWn =
            bench(WARMUP, ITERS) {
                val e = smallParsedWn[it % CORPUS]
                e.computedIdHex().equals(e.id, ignoreCase = true)
            }
        val checkQz = bench(WARMUP, ITERS) { smallParsedQz[it % CORPUS].verifyId() }

        val toHexWn = bench(WARMUP, ITERS) { WnHex.toHex(digest32) }
        val toHexQz = bench(WARMUP, ITERS) { digest32.toHexKey() }

        val fromHexWn = bench(WARMUP, ITERS) { WnHex.hexToBytes(digest32Hex) }
        val fromHexQz = bench(WARMUP, ITERS) { Hex.decode(digest32Hex) }

        println()
        println("JSON + hashing - whitenoise (org.json) vs Quartz (Jackson), JVM ${System.getProperty("java.version")}")
        println("=".repeat(92))
        println(String.format(Locale.US, "%-46s %14s %14s %10s", "operation", "whitenoise", "Quartz", "ratio"))
        println("-".repeat(92))
        println(row("parse kind:1 note (0-4 tags, 140 chars)", parseSmallWn, parseSmallQz))
        println(row("parse kind:3 follow list (500 p tags)", parseLargeWn, parseLargeQz))
        println(row("event id: canonical serialize + sha256", idSmallWn, idSmallQz))
        println(row("event id, kind:3 (500 tags)", idLargeWn, idLargeQz))
        println(row("verify id (recompute + compare)", checkWn, checkQz))
        println(row("32 bytes to hex", toHexWn, toHexQz))
        println(row("64 hex chars to 32 bytes", fromHexWn, fromHexQz))
        println("=".repeat(92))
        println(
            String.format(
                Locale.US,
                "Ingesting 1,000 kind:1 notes (parse + id check): whitenoise %.1f ms   Quartz %.1f ms",
                (parseSmallWn + checkWn) * 1000 / 1e6,
                (parseSmallQz + checkQz) * 1000 / 1e6,
            ),
        )
        println()
    }

    companion object {
        private const val CORPUS = 64
        private const val WARMUP = 20_000
        private const val ITERS = 200_000
        private const val WARMUP_L = 200
        private const val ITERS_L = 2_000
    }
}

/**
 * Vendored verbatim from whitenoise-android
 * `app/src/main/java/dev/ipf/whitenoise/android/core/nostr/NostrEvent.kt` (master @ 67d33de).
 * Renamed only. Do not "improve" it - the point is to measure what that app ships.
 */
private data class WnNostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    fun canonicalJson(): String =
        buildString {
            append('[')
            append('0')
            append(',')
            appendNostrJsonString(pubkey)
            append(',')
            append(createdAt)
            append(',')
            append(kind)
            append(',')
            append('[')
            tags.forEachIndexed { index, tag ->
                if (index > 0) append(',')
                append('[')
                tag.forEachIndexed { tagIndex, value ->
                    if (tagIndex > 0) append(',')
                    appendNostrJsonString(value)
                }
                append(']')
            }
            append(']')
            append(',')
            appendNostrJsonString(content)
            append(']')
        }

    fun computedIdHex(): String = WnHex.toHex(WnHex.sha256(canonicalJson().toByteArray(Charsets.UTF_8)))

    companion object {
        fun fromJson(json: JSONObject): WnNostrEvent? {
            val tags = json.optJSONArray("tags") ?: return null
            val createdAt =
                (json.opt("created_at") as? Number)
                    ?.toString()
                    ?.toLongOrNull()
                    ?.takeIf { it >= 0 }
                    ?: return null
            val kindLong =
                (json.opt("kind") as? Number)
                    ?.toString()
                    ?.toLongOrNull()
                    ?.takeIf { it in 0..Int.MAX_VALUE }
                    ?: return null
            return WnNostrEvent(
                id = (json.opt("id") as? String)?.lowercase(Locale.US)?.takeIf { it.isHex(64) } ?: return null,
                pubkey = (json.opt("pubkey") as? String)?.lowercase(Locale.US)?.takeIf { it.isHex(64) } ?: return null,
                createdAt = createdAt,
                kind = kindLong.toInt(),
                tags = tags.toStringListsOrNull() ?: return null,
                content = json.opt("content") as? String ?: return null,
                sig = (json.opt("sig") as? String)?.lowercase(Locale.US)?.takeIf { it.isHex(128) } ?: return null,
            )
        }
    }
}

private object WnHex {
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun toHex(bytes: ByteArray): String = bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    fun hexToBytes(value: String): ByteArray? {
        if (value.length % 2 != 0 || !value.isHex(value.length)) return null
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}

private fun StringBuilder.appendNostrJsonString(value: String) {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
    append('"')
}

private fun String.isHex(expectedLength: Int): Boolean = length == expectedLength && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun JSONArray.toStringListsOrNull(): List<List<String>>? =
    buildList {
        for (index in 0 until length()) {
            val tagArray = optJSONArray(index) ?: return null
            add(
                buildList {
                    for (tagIndex in 0 until tagArray.length()) {
                        add(tagArray.opt(tagIndex) as? String ?: return null)
                    }
                },
            )
        }
    }
