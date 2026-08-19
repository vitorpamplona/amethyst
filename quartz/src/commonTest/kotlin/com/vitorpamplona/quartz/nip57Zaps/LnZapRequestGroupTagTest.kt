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
package com.vitorpamplona.quartz.nip57Zaps

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Zapping NIP-29 group content is itself group content.
 *
 * Without the room's `h` tag the receipt the provider publishes is an unscoped kind-9735: the host
 * relay has no reason to serve it to the room, and the recipient's group notification query (`#h` =
 * their rooms) never matches it — so a zap on a group message would be invisible exactly where it was
 * meant to be seen. Reactions already copy the tag (`ReactionAction`); this pins the same rule for zaps.
 */
class LnZapRequestGroupTagTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    private val nostrSigner = NostrSignerInternal(signer.key)

    private val relays = setOf(NormalizedRelayUrl("wss://groups.example.com/"))

    private fun event(
        kind: Int,
        tags: Array<Array<String>>,
    ) = Event(
        id = "a".repeat(64),
        pubKey = "b".repeat(64),
        createdAt = 1_700_000_000L,
        kind = kind,
        tags = tags,
        content = "",
        sig = "c".repeat(128),
    )

    private suspend fun zapRequestFor(zapped: Event) =
        LnZapRequestEvent.create(
            zappedEvent = zapped,
            relays = relays,
            signer = nostrSigner,
            pollOption = null,
            message = "",
            zapType = LnZapEvent.ZapType.PUBLIC,
            toUserPubHex = null,
        )

    @Test
    fun `zapping a group message carries the room's h tag`() =
        runTest {
            val groupMessage = event(9, arrayOf(arrayOf("h", "chan-engineering")))

            val hTag = zapRequestFor(groupMessage).tags.firstOrNull { it[0] == "h" }

            assertTrue(hTag != null, "a zap on group content must stay scoped to the room")
            assertEquals("chan-engineering", hTag[1])
        }

    @Test
    fun `zapping a membership notification carries the room it announces`() =
        runTest {
            // The kind-44100 an invite card renders: relay-signed, `h`-scoped to the channel it added
            // the viewer to. Zapping it is zapping something that happened inside that room.
            val invite =
                event(
                    44100,
                    arrayOf(
                        arrayOf("p", "d".repeat(64)),
                        arrayOf("h", "chan-design"),
                    ),
                )

            val hTag = zapRequestFor(invite).tags.firstOrNull { it[0] == "h" }

            assertTrue(hTag != null, "a membership notification is group content too")
            assertEquals("chan-design", hTag[1])
        }

    @Test
    fun `zapping an ordinary note stays unscoped`() =
        runTest {
            val note = event(1, arrayOf(arrayOf("t", "nostr")))

            assertNull(
                zapRequestFor(note).tags.firstOrNull { it[0] == "h" },
                "a note that belongs to no room must not claim one",
            )
        }
}
