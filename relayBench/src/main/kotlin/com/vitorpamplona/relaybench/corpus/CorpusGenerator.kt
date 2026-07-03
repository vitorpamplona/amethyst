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
package com.vitorpamplona.relaybench.corpus

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventAssembler
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Deterministic synthetic corpus with a realistic social shape:
 *
 * - Zipf-distributed author popularity (a few loud accounts, a long tail).
 * - Profiles (kind 0) + contact lists (kind 3) for every author.
 * - Notes (kind 1), ~1/3 of them replies clustered into threads.
 * - Reposts (kind 6), reactions (kind 7) and zap request/receipt pairs
 *   (9734 inside 9735) targeting earlier notes with a recency bias, so
 *   "hot threads" and "popular people" exist for the query scenarios.
 * - ~15% of notes carry `t` hashtags drawn from a small popular set.
 *
 * Every random draw comes from a single seeded [Random] consumed in a fixed
 * order, timestamps derive from the spec's fixed baseTime, and even the
 * BIP-340 aux nonces are seed-derived — so the same [CorpusSpec] reproduces
 * a byte-identical corpus (ids *and* signatures) on any machine. That is
 * what lets two parties compare relay results against "corpus seed 1,
 * n=10000" without shipping the file. (Deterministic nonces are fine here:
 * each signature covers a distinct message, and these keys sign nothing
 * outside the benchmark.)
 *
 * Signing is the expensive part and is parallelized in three dependency
 * waves (referenced ids must exist before the referencing template is
 * finalized): 1) profiles/contacts/root notes, 2) replies, 3) reactions,
 * reposts and zaps.
 */
object CorpusGenerator {
    private val WORDS =
        (
            "the of to and in that for on with as it is was at by from be this have or an are not you your " +
                "we they he she will one all would there what so up out if about who get which go me when make " +
                "can like time no just him know take people into year good some could them see other than then " +
                "now look only come its over think also back after use two how our work first well way even new " +
                "want because any these give day most us nostr relay zap note client protocol key sign event " +
                "freedom bitcoin coffee build ship test run friend photo music idea question answer thanks great"
        ).split(" ")

    private val HASHTAGS =
        listOf(
            "nostr",
            "bitcoin",
            "introductions",
            "foodstr",
            "plebchain",
            "art",
            "music",
            "photography",
            "zapathon",
            "asknostr",
        )

    private val REACTIONS = listOf("+", "+", "+", "+", "🤙", "❤️", "🫂", "😂")

    private enum class SlotType { PROFILE, CONTACTS, ROOT_NOTE, REPLY, REPOST, REACTION, ZAP }

    private class Slot(
        val type: SlotType,
        val createdAt: Long,
        val actor: Int,
        var targetSlot: Int = -1,
        var rootSlot: Int = -1,
        var text: String = "",
        var hashtags: List<String> = emptyList(),
        var follows: List<Int> = emptyList(),
    )

    fun generate(
        spec: CorpusSpec,
        log: (String) -> Unit = {},
    ): Corpus {
        val rng = Random(spec.seed)
        val n = spec.events
        val authorCount = (n / 20).coerceIn(20, 2000)

        // Sequential draws → deterministic keys.
        val authorKeys = List(authorCount) { KeyPair(privKey = rng.nextBytes(32)) }
        val authorHex = authorKeys.map { it.pubKey.toHexKey() }
        val zapperKeys = List(min(5, authorCount)) { KeyPair(privKey = rng.nextBytes(32)) }
        val zapperHex = zapperKeys.map { it.pubKey.toHexKey() }

        /** Seed-derived BIP-340 aux nonce, unique per (slot, sub-signature). */
        fun nonce(
            slot: Int,
            sub: Int,
        ): ByteArray =
            MessageDigest
                .getInstance("SHA-256")
                .digest("relaybench-nonce:${spec.seed}:$slot:$sub".toByteArray())

        fun sign(
            key: KeyPair,
            pubHex: String,
            slot: Int,
            sub: Int,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): Event = EventAssembler.hashAndSign(pubHex, createdAt, kind, tags, content, key.privKey!!, nonce(slot, sub))

        // Zipf-ish popularity: weight of rank r ∝ 1/(r+1)^0.9.
        val weights = DoubleArray(authorCount) { 1.0 / (it + 1.0).pow(0.9) }
        val cumulative = DoubleArray(authorCount)
        var acc = 0.0
        for (i in weights.indices) {
            acc += weights[i]
            cumulative[i] = acc
        }

        fun sampleAuthor(): Int {
            val x = rng.nextDouble() * acc
            val idx = cumulative.toList().binarySearch { it.compareTo(x) }
            return if (idx >= 0) idx else (-idx - 1).coerceAtMost(authorCount - 1)
        }

        fun sentence(): String {
            val count = 5 + rng.nextInt(35)
            return (0 until count).joinToString(" ") { WORDS[rng.nextInt(WORDS.size)] }
        }

        // ---- Pass 1: plan every slot sequentially (all randomness here). ----
        val step = spec.spanSeconds.toDouble() / n

        fun createdAt(slot: Int): Long =
            spec.baseTime - spec.spanSeconds + (slot * step).toLong() +
                rng.nextLong(0, (step * 0.9).toLong().coerceAtLeast(1))

        val bulkCount = (n - 2 * authorCount).coerceAtLeast(0)
        val bulkTypes = ArrayList<SlotType>(bulkCount)
        repeat(bulkCount) {
            val x = rng.nextDouble()
            bulkTypes +=
                when {
                    x < 0.36 -> SlotType.ROOT_NOTE
                    x < 0.55 -> SlotType.REPLY
                    x < 0.63 -> SlotType.REPOST
                    x < 0.93 -> SlotType.REACTION
                    else -> SlotType.ZAP
                }
        }

        val slots = ArrayList<Slot>(n)
        // Profiles and contact lists are each author's oldest events.
        for (a in 0 until authorCount) {
            if (slots.size >= n) break
            slots += Slot(SlotType.PROFILE, createdAt(slots.size), a)
            if (slots.size >= n) break
            val followCount = 10 + rng.nextInt(min(120, authorCount))
            val follows = (0 until followCount).map { sampleAuthor() }.distinct()
            slots +=
                Slot(SlotType.CONTACTS, createdAt(slots.size), a).apply { this.follows = follows }
        }

        val noteSlots = ArrayList<Int>() // indices of ROOT_NOTE and REPLY slots

        fun sampleNoteSlot(): Int {
            // 70% recency-biased (hot content), 30% uniform (long tail).
            val size = noteSlots.size
            return if (rng.nextDouble() < 0.7) {
                val back = (rng.nextDouble().pow(2) * min(size, 500)).toInt()
                noteSlots[size - 1 - back.coerceAtMost(size - 1)]
            } else {
                noteSlots[rng.nextInt(size)]
            }
        }

        for (type in bulkTypes) {
            if (slots.size >= n) break
            val idx = slots.size
            val effective = if (noteSlots.isEmpty() && type != SlotType.ROOT_NOTE) SlotType.ROOT_NOTE else type
            val slot = Slot(effective, createdAt(idx), sampleAuthor())
            when (effective) {
                SlotType.ROOT_NOTE -> {
                    slot.text = sentence()
                    if (rng.nextDouble() < 0.15) {
                        slot.hashtags = List(1 + rng.nextInt(2)) { HASHTAGS[(rng.nextDouble().pow(2) * HASHTAGS.size).toInt()] }.distinct()
                    }
                    noteSlots += idx
                }
                SlotType.REPLY -> {
                    slot.text = sentence()
                    slot.targetSlot = sampleNoteSlot()
                    slot.rootSlot =
                        slots[slot.targetSlot].let { t -> if (t.type == SlotType.REPLY) t.rootSlot else slot.targetSlot }
                    noteSlots += idx
                }
                SlotType.REPOST, SlotType.REACTION, SlotType.ZAP -> {
                    slot.targetSlot = sampleNoteSlot()
                    if (effective == SlotType.REACTION) slot.text = REACTIONS[rng.nextInt(REACTIONS.size)]
                }
                else -> {}
            }
            slots += slot
        }

        // ---- Pass 2: sign in dependency waves, in parallel. ----
        val events = arrayOfNulls<Event>(slots.size)

        fun signSlot(i: Int): Event {
            val s = slots[i]
            val key = authorKeys[s.actor]
            val pub = authorHex[s.actor]
            return when (s.type) {
                SlotType.PROFILE ->
                    sign(
                        key,
                        pub,
                        i,
                        0,
                        s.createdAt,
                        0,
                        emptyArray(),
                        """{"name":"user-${s.actor}","about":"synthetic relaybench profile ${s.actor}","picture":"https://example.com/${s.actor}.png"}""",
                    )
                SlotType.CONTACTS ->
                    sign(
                        key,
                        pub,
                        i,
                        0,
                        s.createdAt,
                        3,
                        s.follows.map { arrayOf("p", authorHex[it]) }.toTypedArray(),
                        "",
                    )
                SlotType.ROOT_NOTE ->
                    sign(
                        key,
                        pub,
                        i,
                        0,
                        s.createdAt,
                        1,
                        s.hashtags.map { arrayOf("t", it) }.toTypedArray(),
                        if (s.hashtags.isEmpty()) s.text else s.text + " " + s.hashtags.joinToString(" ") { "#$it" },
                    )
                SlotType.REPLY -> {
                    val root = events[s.rootSlot]!!
                    sign(
                        key,
                        pub,
                        i,
                        0,
                        s.createdAt,
                        1,
                        arrayOf(arrayOf("e", root.id, "", "root"), arrayOf("p", root.pubKey)),
                        s.text,
                    )
                }
                SlotType.REPOST -> {
                    val target = events[s.targetSlot]!!
                    sign(
                        key,
                        pub,
                        i,
                        0,
                        s.createdAt,
                        6,
                        arrayOf(arrayOf("e", target.id), arrayOf("p", target.pubKey)),
                        OptimizedJsonMapper.toJson(target),
                    )
                }
                SlotType.REACTION -> {
                    val target = events[s.targetSlot]!!
                    sign(
                        key,
                        pub,
                        i,
                        0,
                        s.createdAt,
                        7,
                        arrayOf(arrayOf("e", target.id), arrayOf("p", target.pubKey), arrayOf("k", "1")),
                        s.text,
                    )
                }
                SlotType.ZAP -> {
                    val target = events[s.targetSlot]!!
                    val request =
                        sign(
                            key,
                            pub,
                            i,
                            0,
                            s.createdAt,
                            9734,
                            arrayOf(arrayOf("e", target.id), arrayOf("p", target.pubKey), arrayOf("relays", "ws://localhost")),
                            "",
                        )
                    val zapperIdx = s.actor % zapperKeys.size
                    sign(
                        zapperKeys[zapperIdx],
                        zapperHex[zapperIdx],
                        i,
                        1,
                        s.createdAt,
                        9735,
                        arrayOf(
                            arrayOf("p", target.pubKey),
                            arrayOf("e", target.id),
                            arrayOf("bolt11", "lnbc210n1relaybenchfake${s.actor}"),
                            arrayOf("description", OptimizedJsonMapper.toJson(request)),
                        ),
                        "",
                    )
                }
            }
        }

        val waves =
            listOf(
                slots.indices.filter { slots[it].type in setOf(SlotType.PROFILE, SlotType.CONTACTS, SlotType.ROOT_NOTE) },
                slots.indices.filter { slots[it].type == SlotType.REPLY },
                slots.indices.filter { slots[it].type in setOf(SlotType.REPOST, SlotType.REACTION, SlotType.ZAP) },
            )
        val start = System.nanoTime()
        runBlocking {
            waves.forEach { wave ->
                coroutineScope {
                    wave
                        .chunked(512)
                        .map { chunk ->
                            async(Dispatchers.Default) { chunk.forEach { events[it] = signSlot(it) } }
                        }.awaitAll()
                }
            }
        }
        val signed = events.filterNotNull()
        log("  signed ${signed.size} events (${zapCount(signed)} of them zap receipts embed a signed 9734) in ${(System.nanoTime() - start) / 1_000_000} ms")
        return Corpus(signed, "synthetic seed=${spec.seed} n=${spec.events}", spec)
    }

    private fun zapCount(events: List<Event>) = events.count { it.kind == 9735 }
}
