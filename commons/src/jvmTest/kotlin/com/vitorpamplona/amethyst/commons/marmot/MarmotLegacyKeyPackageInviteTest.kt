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

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.LegacyKeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a White Noise user's legacy kind 443 KeyPackage can join.
 *
 * MDK 0.8 publishes MIP-era KeyPackages (`marmot_group_data` 0xF2EE, last resort 0x000A) as
 * kind 443. Finding one is only half of inviting its author: the MLS layer then holds the leaf
 * to the group's `required_capabilities`. A legacy MIP-01 group takes it; a current-profile group
 * requires `app_data_dictionary` and the account identity proof, which an MDK 0.8 leaf does not
 * carry, so the add is refused before anything is published.
 */
class MarmotLegacyKeyPackageInviteTest {
    /** A real kind 443 by MDK 0.8.0 (relay census 2026-10-08); public key material only. */
    private val mdk080 =
        Event.fromJson(
            """{"id":"1d83c042c3a77e851af8e0dac48f2d82dbb6047620c43ef79dd997dd15961084","pubkey":"4432f98af8f12d5cc896428a197519a5ac07fff3c670d716088a856d037007a7","created_at":1791418861,"kind":443,"tags":[["mls_protocol_version","1.0"],["mls_ciphersuite","0x0001"],["mls_extensions","0x000a","0xf2ee"],["mls_proposals","0x000a"],["relays","wss://relay.damus.io","wss://relay.primal.net"],["i","d6f1613adc3f9125d5ee573d8c706dd77c811d88802fe97c992d6b418f2a7c13"],["client","MDK/0.8.0"],["encoding","base64"]],"content":"AAEAASA7bBr5krMo3qNgX9yhCgKml2orYMwipD51ZCA3xghxAyA+xGOSqsn29GnkXJ4jkTpEqsMvLkkV6iyZP3UEKJjadiC8+rXHrIk3tb5QoHK+U/xijziwcrLd++oOh3qNaj+KvgABIEQy+Yr48S1cyJZCihl1GaWsB//zxnDXFgiKhW0DcAenAgABBAABGhoGAAry7srKBAAKqqoEAAHq6gEAAAAAasbT3QAAAABrNZ/tAEBA5yjIIEnGDcTJLr2r0lga1d51t3dmdiEIh2DdcAwNNM9CzbNZwPg0MzAD1CSL55lkkEGxcafYizGGtBAFaSvpDwMACgBAQKMzRb1q5AtQTwG1snfWLAIgF0dmppOD9FQys0D2nqC0fUAOs/Q1GeAu7W1csN2YqEaHBK0jJSgo+8lU9eSL+ws=","sig":"135abdb1991f23762e2e6365d51cd25d5b395d716abae9101430782e13518b97073d184b0d644e94aff0101f6a9b4c09196bf0247513acf6ecccb687f70e5231"}""",
        ) as LegacyKeyPackageEvent

    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private fun manager(signer: NostrSignerInternal) = MarmotManager(signer, SnapshotStateStore(), publisher = ACCEPTING_RELAY)

    @Test
    fun aLegacyGroupAddsA443OnlyUser() =
        runBlocking {
            val signer = NostrSignerInternal(KeyPair())
            val manager = manager(signer)
            val gid = "c".repeat(64)
            manager.createGroup(gid, MarmotGroupData(nostrGroupId = gid, adminPubkeys = listOf(signer.pubKey), relays = listOf(relay.url)))

            val (_, welcome) = manager.addMember(gid, mdk080, listOf(relay))

            assertNotNull(welcome, "the invitee gets a Welcome")
            assertEquals(mdk080.pubKey, welcome.recipientPubKey)
            assertTrue(manager.memberPubkeys(gid).any { it.pubkey == mdk080.pubKey }, "the MDK user is now a member")
        }

    @Test
    fun aCurrentProfileGroupRefusesTheMipEraLeaf() =
        runBlocking<Unit> {
            val signer = NostrSignerInternal(KeyPair())
            val manager = manager(signer)
            val gid = "d".repeat(64)
            manager.createCurrentProfileGroup(gid, listOf(relay.url))

            val refusal = assertFails { manager.addMember(gid, mdk080, listOf(relay)) }
            assertTrue(
                refusal.message.orEmpty().contains("required_capabilities"),
                "refused by the group's required capabilities: ${refusal.message}",
            )
            assertTrue(manager.memberPubkeys(gid).none { it.pubkey == mdk080.pubKey })
        }
}
