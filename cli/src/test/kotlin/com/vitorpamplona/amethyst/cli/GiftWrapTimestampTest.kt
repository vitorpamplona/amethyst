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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `amy gift wrap` dates the seal and the wrap at random up to two days in the past by default
 * (NIP-17's rule, which nak and nostr-tools apply to every gift wrap); `--no-backdate` stamps
 * both with the current time.
 */
class GiftWrapTimestampTest {
    private val recipient = NostrSignerInternal(KeyPair())
    private val inner = NostrSignerSync(KeyPair()).sign(TextNoteEvent.build("psst"))

    /** Wraps [inner] for [recipient] [times] times; returns each (seal, wrap) `created_at` pair. */
    private fun wrap(
        times: Int,
        vararg flags: String,
    ): List<Pair<Long, Long>> =
        withSharedAmyHome {
            assertEquals(0, amy("--secret-backend", "plaintext", "--account", "alice", "init").exit)
            List(times) {
                // Flags before --to: a bare boolean flag would take the event JSON as its value.
                val r = amy("--json", "gift", "wrap", *flags, "--to", recipient.pubKey, inner.toJson())
                assertEquals(0, r.exit, r.stderr)
                val wrap = Event.fromJson(JsonNode.parse(r.stdoutLines.single())["event"].element.toString()) as GiftWrapEvent
                val seal = runBlocking { wrap.unwrapThrowing(recipient) }
                seal.createdAt to wrap.createdAt
            }
        }

    @Test
    fun sealAndWrapAreBackdatedByDefault() {
        val before = TimeUtils.now()
        val layers = wrap(3)
        for ((seal, wrap) in layers) {
            for (t in listOf(seal, wrap)) {
                assertTrue(t in (before - TimeUtils.twoDays())..TimeUtils.now(), "created_at $t outside the two days before $before")
            }
        }
        // Each draw is uniform over two days: three landing in the last minute is ~(60/172800)^3.
        assertTrue(layers.any { it.first < before - 60 }, "no seal is backdated: $layers")
        assertTrue(layers.any { it.second < before - 60 }, "no wrap is backdated: $layers")
    }

    @Test
    fun noBackdateStampsBothWithTheCurrentTime() {
        val before = TimeUtils.now()
        val (seal, wrap) = wrap(1, "--no-backdate").single()
        for (t in listOf(seal, wrap)) {
            assertTrue(t in before..TimeUtils.now(), "created_at $t is not the current time ($before)")
        }
    }
}
