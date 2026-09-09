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
package com.vitorpamplona.quartz.nip19Bech32

import com.vitorpamplona.quartz.nip19Bech32.entities.IPubKeyEntity
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Identity-reference parsing under adversarial input.
 *
 * Ported from the reference client's `IdentityReferenceFuzzTest`: same grammar
 * (`nostr:`, profile links, percent-encoded separators, truncated bech32,
 * multi-token clipboard text), same invariants — the parser never throws, is
 * deterministic, is idempotent on whatever it canonicalises, and never emits a
 * key that is not a 32-byte lowercase hex string.
 *
 * Seeded rather than Jazzer-driven, so it needs no fuzzing engine on the build
 * and a failure reproduces from the printed seed. That is the whole difference:
 * the oracles below are theirs.
 */
class Nip19ParserAdversarialInputTest {
    private val bech32Body = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val schemes = listOf("nostr", "http", "https", "marmot", "whitenoise", "web+nostr", "")
    private val hosts = listOf("njump.me", "primal.net", "example.com", "profile", "")
    private val separators = listOf("://", ":", "%3A%2F%2F", "%2F", "")
    private val pathPrefixes = listOf("profile/", "profile%2F", "p/", "")
    private val joiners = listOf(",", " ", "\n", "\t", "  ", ";", "")

    private fun body(
        rnd: Random,
        length: Int,
    ) = buildString { repeat(length) { append(bech32Body[rnd.nextInt(bech32Body.length)]) } }

    /**
     * A shaped-but-corrupt npub: the right alphabet and length, a checksum that
     * does not hold. This is what a truncated or mistyped paste actually looks
     * like, and it is most of the corpus on purpose.
     */
    private fun corruptNpub(rnd: Random) = "npub1" + body(rnd, 58)

    /** A real npub, checksum and all, for the cases that must SUCCEED. */
    private fun validNpub(rnd: Random) = ByteArray(32) { rnd.nextInt(256).toByte() }.toNpub()

    private fun reference(rnd: Random): String {
        val key =
            when (rnd.nextInt(6)) {
                0 -> validNpub(rnd)
                // Truncated and over-long bodies: the 58-char rule is what
                // stops a half-pasted npub from decoding to a short key.
                1 -> "npub1" + body(rnd, rnd.nextInt(1, 58))
                2 -> "npub1" + body(rnd, rnd.nextInt(59, 90))
                3 -> "nprofile1" + body(rnd, rnd.nextInt(1, 120))
                4 -> corruptNpub(rnd).uppercase()
                else -> body(rnd, rnd.nextInt(0, 70))
            }
        val scheme = schemes[rnd.nextInt(schemes.size)]
        return when (scheme) {
            "" -> key
            "nostr" -> "nostr:" + key
            "http", "https" ->
                scheme + "://" + hosts[rnd.nextInt(hosts.size)] + "/" +
                    pathPrefixes[rnd.nextInt(pathPrefixes.size)] + key

            else ->
                scheme + separators[rnd.nextInt(separators.size)] +
                    pathPrefixes[rnd.nextInt(pathPrefixes.size)] + key
        }
    }

    private fun corpus(seed: Int): List<String> {
        val rnd = Random(seed)
        return buildList {
            repeat(400) { add(reference(rnd)) }
            // Clipboard-shaped input: several references run together.
            repeat(100) {
                val count = rnd.nextInt(2, 6)
                add((0 until count).joinToString(joiners[rnd.nextInt(joiners.size)]) { reference(rnd) })
            }
            // Degenerate shapes the grammar above never produces.
            addAll(listOf("", " ", "\n", "nostr:", "npub1", "@", "nostr:@", "://", "%", "npub1 npub1"))
        }
    }

    @Test
    fun parsingNeverThrowsAndIsDeterministic() {
        val seed = 20260909
        corpus(seed).forEach { input ->
            val first =
                try {
                    Nip19Parser.uriToRoute(input)
                } catch (e: Throwable) {
                    throw AssertionError("uriToRoute threw on " + input.take(120) + " (seed " + seed + ")", e)
                }
            val second = Nip19Parser.uriToRoute(input)
            assertEquals(first?.entity, second?.entity, "parsing must be deterministic for " + input.take(120))
            assertEquals(first?.nip19raw, second?.nip19raw)
        }
    }

    @Test
    fun cleaningNeverThrowsAndIsIdempotent() {
        val seed = 20260910
        corpus(seed).forEach { input ->
            val cleaned =
                try {
                    Nip19Parser.tryParseAndClean(input)
                } catch (e: Throwable) {
                    throw AssertionError("tryParseAndClean threw on " + input.take(120) + " (seed " + seed + ")", e)
                }
            if (cleaned != null) {
                // Re-cleaning its own output must be a fixed point, or two
                // clients that clean a different number of times disagree about
                // the same paste.
                assertEquals(
                    cleaned,
                    Nip19Parser.tryParseAndClean(cleaned),
                    "cleaning is not idempotent for " + input.take(120),
                )
            }
        }
    }

    @Test
    fun aDecodedKeyIsAlwaysThirtyTwoBytesOfLowercaseHex() {
        val seed = 20260911
        corpus(seed).forEach { input ->
            val entity = Nip19Parser.uriToRoute(input)?.entity
            if (entity is IPubKeyEntity) {
                val hex = entity.hex
                assertEquals(
                    64,
                    hex.length,
                    "a pubkey entity must decode to 32 bytes, got " + hex.length + " from " + input.take(120),
                )
                assertTrue(
                    hex.all { it in '0'..'9' || it in 'a'..'f' },
                    "a decoded key must be lowercase hex, got " + hex,
                )
            }
        }
    }

    @Test
    fun aParsedReferenceReParsesToTheSameEntity() {
        // The canonical `nip19raw` is what the app stores and re-reads. If it
        // did not round-trip, a reference would decay every time it was copied
        // through the UI.
        val seed = 20260912
        corpus(seed).forEach { input ->
            val parsed = Nip19Parser.uriToRoute(input) ?: return@forEach
            val reparsed = Nip19Parser.uriToRoute(parsed.nip19raw)
            assertEquals(parsed.entity, reparsed?.entity, "re-parsing " + parsed.nip19raw + " changed the entity")
        }
    }

    @Test
    fun aWellFormedNpubIsFoundInsideEveryCarrierShape() {
        // The negative cases above are only half the contract: the parser also
        // has to keep finding a real reference through a link, a scheme it does
        // not know, and percent-encoding.
        val rnd = Random(20260913)
        val key = validNpub(rnd)
        val carriers =
            listOf(
                key,
                "nostr:" + key,
                "@" + key,
                "https://njump.me/" + key,
                "https://example.com/profile/" + key,
                "web+nostr://" + key,
                "text before nostr:" + key + " and after",
            )
        carriers.forEach { carrier ->
            val entity = Nip19Parser.uriToRoute(carrier)?.entity
            assertTrue(entity is IPubKeyEntity, "no pubkey found in " + carrier)
        }
    }
}
