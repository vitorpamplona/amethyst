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
package com.vitorpamplona.amethyst.desktop.testrelay

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * Phase 2.3 of the launch-optimization plan: a deterministic, synthetic
 * "home feed" snapshot used to drive the in-process relay during benchmarks
 * and Compose UI tests.
 *
 * The original plan called for a real-world capture (e.g. fiatjaf's latest
 * 50 kind:1 + author metadata) committed as a JSONL artifact. Because we
 * cannot run `amy` against live relays from this environment, the fixture
 * is generated in code from a fixed RNG seed. The shape mirrors real-world
 * home-feed payloads:
 *
 *  - one owner ViewOnly account,
 *  - kind:10002 advertised-relay list,
 *  - kind:3 contact list with 50 follows,
 *  - kind:0 metadata for each followee,
 *  - 50 kind:1 text notes from a mix of followees over the last 7 days.
 *
 * Switching to a real-world fixture later is a drop-in: replace
 * [LaunchFixture.events] with a parser of `*.jsonl` resource files. The
 * server, builder, and benchmark harness do not care about the source.
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 2.3.
 */
class LaunchFixture private constructor(
    val ownerKeyPair: KeyPair,
    val events: List<Event>,
) {
    val ownerPubKeyHex: String get() = ownerKeyPair.pubKey.toHexKey()

    companion object {
        /**
         * Builds the canonical home-feed snapshot for the benchmark/test scenarios.
         *
         * The seed is fixed so successive calls produce byte-identical fixtures
         * — important for benchmark reproducibility — but a caller can pass
         * a different seed when probing edge cases.
         */
        fun build(
            seed: Long = SEED,
            followCount: Int = FOLLOW_COUNT,
            noteCount: Int = NOTE_COUNT,
            nowSeconds: Long = FIXED_NOW,
        ): LaunchFixture {
            val rng = SeededRng(seed)
            val owner = keyPairFromSeed(rng.nextLong())
            val ownerSigner = NostrSignerSync(owner)

            val followees = List(followCount) { keyPairFromSeed(rng.nextLong()) }

            val events =
                buildList {
                    add(advertisedRelays(ownerSigner, nowSeconds))
                    add(contactList(ownerSigner, followees, nowSeconds))
                    followees.forEachIndexed { idx, kp ->
                        add(metadata(kp, idx, nowSeconds))
                    }
                    repeat(noteCount) { i ->
                        val author = followees[rng.nextInt(followees.size)]
                        val ageSeconds = rng.nextLongInRange(MIN_AGE_SECONDS, MAX_AGE_SECONDS)
                        add(textNote(author, i, nowSeconds - ageSeconds))
                    }
                }

            return LaunchFixture(owner, events)
        }

        private const val SEED = 0xA3F71E5L
        private const val FOLLOW_COUNT = 50
        private const val NOTE_COUNT = 50

        // Pin "now" so the fixture is fully deterministic across machines /
        // timezones. The 7-day hydration window in LocalRelayStore is
        // wall-clock based, so callers seeding events.db for warm-boot runs
        // should pass a fresh `nowSeconds` to keep the events in window.
        const val FIXED_NOW: Long = 1_750_000_000L

        private const val MIN_AGE_SECONDS = 60L * 5L // 5 minutes ago
        private const val MAX_AGE_SECONDS = 60L * 60L * 24L * 2L // 2 days ago

        private fun keyPairFromSeed(seed: Long): KeyPair {
            // Derive a 32-byte private key deterministically from the seed.
            // Avoids depending on system entropy and keeps the fixture stable.
            val key = ByteArray(32)
            var v = seed.toULong() xor 0x9E3779B97F4A7C15uL
            for (i in 0 until 32) {
                v = v * 6364136223846793005uL + 1442695040888963407uL
                key[i] = ((v shr 56).toInt() and 0xFF).toByte()
            }
            // secp256k1 valid private key range: 1..n-1. Force into a known-good range
            // by clamping the top byte; n's top byte is 0xFF FF FF FF FE BA AE DC E6.
            key[0] = (key[0].toInt() and 0x7F).toByte()
            if (key.all { it == 0.toByte() }) key[31] = 1
            return KeyPair(privKey = key)
        }

        private fun advertisedRelays(
            signer: NostrSignerSync,
            nowSeconds: Long,
        ): Event =
            signer.sign<AdvertisedRelayListEvent>(
                createdAt = nowSeconds - 60,
                kind = AdvertisedRelayListEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("r", "wss://test.invalid"),
                    ),
                content = "",
            )

        private fun contactList(
            signer: NostrSignerSync,
            followees: List<KeyPair>,
            nowSeconds: Long,
        ): Event {
            val tags = followees.map { arrayOf("p", it.pubKey.toHexKey()) }.toTypedArray()
            return signer.sign<ContactListEvent>(
                createdAt = nowSeconds - 30,
                kind = ContactListEvent.KIND,
                tags = tags,
                content = "",
            )
        }

        private fun metadata(
            keyPair: KeyPair,
            index: Int,
            nowSeconds: Long,
        ): Event {
            val signer = NostrSignerSync(keyPair)
            return signer.sign<MetadataEvent>(
                createdAt = nowSeconds - 3600 - index * 7L,
                kind = MetadataEvent.KIND,
                tags = emptyArray(),
                content = """{"name":"Test User $index","about":"Synthetic launch fixture user","picture":""}""",
            )
        }

        private fun textNote(
            keyPair: KeyPair,
            index: Int,
            createdAt: Long,
        ): Event {
            val signer = NostrSignerSync(keyPair)
            return signer.sign<TextNoteEvent>(
                createdAt = createdAt,
                kind = TextNoteEvent.KIND,
                tags = emptyArray(),
                content = "Synthetic note #$index — fixed text so first paint timing is stable.",
            )
        }
    }
}

/**
 * Tiny xorshift64* PRNG. Deterministic, fast, no platform deps. Used only
 * by the fixture builder.
 */
private class SeededRng(
    seed: Long,
) {
    private var state: ULong = (if (seed == 0L) 1L else seed).toULong()

    fun nextLong(): Long {
        var x = state
        x = x xor (x shr 12)
        x = x xor (x shl 25)
        x = x xor (x shr 27)
        state = x
        return (x * 2685821657736338717uL).toLong()
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        val v = nextLong() ushr 1
        return (v % bound.toLong()).toInt()
    }

    fun nextLongInRange(
        from: Long,
        until: Long,
    ): Long {
        require(until > from)
        val span = until - from
        val v = nextLong() ushr 1
        return from + (v % span)
    }
}
