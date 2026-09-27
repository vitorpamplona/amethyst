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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The inner rumors the app sends for reactions and deletions in a Marmot group.
 * Both used to leave the group as NIP-17 gift wraps to the target's author; they
 * are now plain MIP-03 inner events, so their shape is what peers validate.
 */
class MarmotInnerRumorBuildersTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val manager = MarmotManager(signer, SnapshotStateStore())

    private val target =
        Event(
            id = "b".repeat(64),
            pubKey = "c".repeat(64),
            createdAt = 1_790_000_000,
            kind = 9,
            tags = emptyArray(),
            content = "react to me",
            sig = "",
        )

    @Test
    fun aReactionNamesItsTargetFirstAndIsAnUnsignedRumor() =
        runBlocking<Unit> {
            val rumor = manager.buildReactionRumor(target, "🎉")
            assertEquals(ReactionEvent.KIND, rumor.kind)
            assertEquals("🎉", rumor.content)
            assertEquals(signer.pubKey, rumor.pubKey)
            assertTrue(rumor.sig.isEmpty(), "MIP-03 inner events are unsigned rumors")
            // MDK attaches a reaction to the FIRST e tag.
            assertEquals(target.id, rumor.tags.first { it[0] == "e" }[1])
        }

    @Test
    fun aCustomEmojiReactionCarriesItsImage() =
        runBlocking<Unit> {
            val rumor = manager.buildReactionRumor(target, ":soapbox:https://example.com/soapbox.png")
            assertEquals(":soapbox:", rumor.content)
            val emoji = rumor.tags.first { it[0] == "emoji" }
            assertEquals(listOf("emoji", "soapbox", "https://example.com/soapbox.png"), emoji.take(3))
        }

    @Test
    fun aDeletionTargetsEachRetractedEvent() =
        runBlocking<Unit> {
            val rumor = manager.buildDeletionRumor(listOf(target))
            assertEquals(DeletionRequestEvent.KIND, rumor.kind)
            assertEquals(signer.pubKey, rumor.pubKey)
            assertTrue(rumor.sig.isEmpty())
            assertEquals(listOf(target.id), rumor.tags.filter { it[0] == "e" }.map { it[1] })
        }
}
