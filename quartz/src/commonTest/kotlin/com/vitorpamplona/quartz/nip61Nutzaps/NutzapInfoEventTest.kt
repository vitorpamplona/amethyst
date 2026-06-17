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
package com.vitorpamplona.quartz.nip61Nutzaps

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.tags.NutzapMintTag
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutzapInfoEventTest {
    private val signer =
        NostrSignerInternal(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    @Test
    fun buildEmptyHasNoMintsRelaysOrPubkey() =
        runTest {
            val event = signer.sign(NutzapInfoEvent.buildEmpty())

            assertEquals(NutzapInfoEvent.KIND, event.kind)
            // The durable "I don't accept nutzaps" signal: a reader finds no
            // shared mint and no pubkey to lock proofs against.
            assertTrue(event.mints().isEmpty(), "empty kind:10019 must carry no mint tags")
            assertTrue(event.relays().isEmpty(), "empty kind:10019 must carry no relay tags")
            assertNull(event.p2pkPubkey(), "empty kind:10019 must carry no P2PK pubkey")
        }

    @Test
    fun emptyReplacesAFullInfoAtTheSameAddress() =
        runTest {
            val full =
                signer.sign(
                    NutzapInfoEvent.build(
                        mints = listOf(NutzapMintTag("https://mint.example.com", listOf("sat"))),
                        relays = listOf(RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!),
                        p2pkPubkey = "02".padEnd(66, 'a'),
                    ),
                )
            val empty = signer.sign(NutzapInfoEvent.buildEmpty())

            // Replaceable: same kind + pubkey + fixed d-tag → same address, so
            // the empty event replaces the full one on every relay.
            assertEquals(full.address(), empty.address())
            assertTrue(full.mints().isNotEmpty())
            assertTrue(empty.mints().isEmpty())
        }

    @Test
    fun deletionTargetsTheReplaceableAddress() =
        runTest {
            val empty = signer.sign(NutzapInfoEvent.buildEmpty())
            val deletion = signer.sign(DeletionEvent.build(listOf(empty)))

            // NIP-09 deletion of a replaceable event needs the `a` address
            // coordinate so compliant relays drop all versions, plus the `e`
            // id for relays that only track by event id.
            assertTrue(
                deletion.deleteAddressesWithKind(NutzapInfoEvent.KIND),
                "deletion must carry the kind:10019 address coordinate",
            )
            assertTrue(
                deletion.deleteEventIds().contains(empty.id),
                "deletion must reference the empty event id",
            )
        }
}
