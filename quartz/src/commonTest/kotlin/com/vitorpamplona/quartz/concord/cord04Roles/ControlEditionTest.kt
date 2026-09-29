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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.concord.cord04Roles.control.ControlEditionEvent
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.EditionHash
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ControlEditionTest {
    private val author = KeyPair().pubKey.toHexKey()
    private val eid = ByteArray(32) { 0xAB.toByte() }

    private fun edition(
        version: Long,
        prevHash: ByteArray?,
        content: String,
        rumorId: String,
    ) = ControlEdition(
        entityKind = ControlEntityKind.CHANNEL,
        entityId = eid,
        version = version,
        prevHash = prevHash,
        authorityCitation = null,
        content = content,
        author = author,
        rumorId = rumorId,
        createdAt = 1_700_000_000L + version,
    )

    // ---- fromRumor ------------------------------------------------------------

    @Test
    fun fromRumorParsesTagsAndComputesHash() {
        val prev = ByteArray(32) { 0x01 }
        val grantId = ByteArray(32) { 0x02 }
        val grantHash = ByteArray(32) { 0x03 }
        val content = """{"member":"aa","role_ids":["bb"]}"""
        val tags =
            arrayOf(
                arrayOf("vsk", "3"),
                arrayOf("eid", eid.toHexKey()),
                arrayOf("ev", "4"),
                arrayOf("ep", prev.toHexKey()),
                arrayOf("vac", grantId.toHexKey(), "2", grantHash.toHexKey()),
            )
        val rumor = RumorAssembler.assembleRumor<Event>(author, 1_700_000_000L, ControlEditionEvent.KIND, tags, content)

        val ed = ControlEdition.fromRumor(rumor)
        assertNotNull(ed)
        assertEquals(ControlEntityKind.GRANT, ed.entityKind)
        assertContentEquals(eid, ed.entityId)
        assertEquals(4, ed.version)
        assertContentEquals(prev, ed.prevHash)
        assertEquals(author, ed.author)
        assertEquals(rumor.id, ed.rumorId)
        assertContentEquals(EditionHash.hash(eid, 4, prev, content), ed.hash)
        assertNotNull(ed.authorityCitation)
        assertContentEquals(grantId, ed.authorityCitation.grantId)
        assertEquals(2, ed.authorityCitation.grantVersion)
    }

    @Test
    fun fromRumorRejectsMalformed() {
        // wrong kind
        assertNull(ControlEdition.fromRumor(RumorAssembler.assembleRumor<Event>(author, 1L, 9, arrayOf(arrayOf("vsk", "0")), "{}")))
        // missing eid
        assertNull(
            ControlEdition.fromRumor(
                RumorAssembler.assembleRumor<Event>(author, 1L, ControlEditionEvent.KIND, arrayOf(arrayOf("vsk", "0"), arrayOf("ev", "0")), "{}"),
            ),
        )
        // unknown vsk (bit 7 retired)
        assertNull(
            ControlEdition.fromRumor(
                RumorAssembler.assembleRumor<Event>(
                    author,
                    1L,
                    ControlEditionEvent.KIND,
                    arrayOf(arrayOf("vsk", "7"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "0")),
                    "{}",
                ),
            ),
        )
    }

    private fun parse(vararg tags: Array<String>) = ControlEdition.fromRumor(RumorAssembler.assembleRumor<Event>(author, 1L, ControlEditionEvent.KIND, arrayOf(*tags), "{}"))

    private val cite = arrayOf("vac", ByteArray(32) { 0x02 }.toHexKey(), "2", ByteArray(32) { 0x03 }.toHexKey())

    @Test
    fun versionTagsMustBeCanonicalDecimals() {
        // CORD-01 §5: no sign, no leading zeros. toLongOrNull() would read all of these as 4.
        assertNotNull(parse(arrayOf("vsk", "2"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "4")))
        for (bad in listOf("04", "+4", "-4", "4.0", "0x4", "1e2", "", " 4")) {
            assertNull(parse(arrayOf("vsk", "2"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", bad)), "ev '$bad'")
        }
        assertNotNull(parse(arrayOf("vsk", "2"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "0")), "a legacy v0 chain still reads")

        // The vac version too, and a malformed vac rejects the edition rather than reading as "no citation".
        assertEquals(2L, parse(arrayOf("vsk", "2"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "4"), cite)?.authorityCitation?.grantVersion)
        for (bad in listOf("02", "+2")) {
            val vac = arrayOf("vac", cite[1], bad, cite[3])
            assertNull(parse(arrayOf("vsk", "2"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "4"), vac), "vac version '$bad'")
        }

        // And the sub-kind: "03" is not the Grant sub-kind.
        assertNull(parse(arrayOf("vsk", "03"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "4")))
    }

    @Test
    fun aDuplicatedMachineryTagMakesTheEditionInvalid() {
        // Two readers could each take a different copy, so the edition is ambiguous (Armada `parseEdition`).
        val base = arrayOf(arrayOf("vsk", "2"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "4"))
        assertNotNull(parse(*base))
        assertNull(parse(*base, arrayOf("vsk", "2")), "duplicate vsk")
        assertNull(parse(*base, arrayOf("eid", ByteArray(32).toHexKey())), "duplicate eid")
        assertNull(parse(*base, arrayOf("ev", "5")), "duplicate ev")
        assertNull(parse(*base, arrayOf("ep", ByteArray(32).toHexKey()), arrayOf("ep", ByteArray(32) { 1 }.toHexKey())), "duplicate ep")
        assertNull(parse(*base, cite, cite), "duplicate vac")
        // Even a duplicate that fails to parse on its own: "04" beside "4" is still two ev tags.
        assertNull(parse(*base, arrayOf("ev", "04")), "a second, non-canonical ev")
    }

    @Test
    fun subKindsThatAreNotControlEditionsAreRefusedAndUnknownOnesKept() {
        // 6/9 belong to the kind-33301 invite marker, 7 is retired, 10 is the dissolution tombstone.
        for (vsk in listOf("6", "7", "9", "10")) {
            assertNull(parse(arrayOf("vsk", vsk), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "1")), "vsk $vsk")
        }
        // A sub-kind this client does not model (Pins 11, Signals 12, anything newer) is kept, raw.
        val pins = parse(arrayOf("vsk", "11"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "1"))
        assertNotNull(pins)
        assertNull(pins.entityKind)
        assertEquals("11", pins.vsk)
        assertEquals(ControlEntityKind.CHANNEL.wire, parse(arrayOf("vsk", "2"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "1"))?.vsk)
    }

    @Test
    fun anEditionUnderAnEncryptedSealIsNotAControlEdition() =
        runTest {
            // CORD-02 §5: Control Plane seals MUST be plaintext (20014) — only those survive a
            // compaction re-wrap with the author's signature intact.
            val signer = NostrSignerInternal(KeyPair())
            val plane = ConcordKeyDerivation.controlPlaneKey(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 0)
            val rumor = ControlEditionBuilder.rumor(signer.pubKey, ControlEntityKind.CHANNEL, eid, 1, null, """{"name":"general"}""", 1L)

            val plaintext = ConcordStreamEnvelope.open(ConcordStreamEnvelope.wrap(rumor, plane, signer, encrypted = false, createdAt = 1L), plane)
            assertNotNull(ControlEdition.fromOpened(plaintext))

            val encrypted = ConcordStreamEnvelope.open(ConcordStreamEnvelope.wrap(rumor, plane, signer, encrypted = true, createdAt = 1L), plane)
            assertEquals(ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED, encrypted.sealKind)
            assertNull(ControlEdition.fromOpened(encrypted))
        }

    @Test
    fun genesisHasNullPrevWhenEpAbsent() {
        val tags = arrayOf(arrayOf("vsk", "2"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "0"))
        val ed = ControlEdition.fromRumor(RumorAssembler.assembleRumor<Event>(author, 1L, ControlEditionEvent.KIND, tags, """{"name":"general"}"""))
        assertNotNull(ed)
        assertNull(ed.prevHash)
    }

    // ---- fold -----------------------------------------------------------------

    @Test
    fun foldWalksIntactChainToHead() {
        val v0 = edition(0, null, """{"name":"general"}""", "id0")
        val v1 = edition(1, v0.hash, """{"name":"lounge"}""", "id1")
        val v2 = edition(2, v1.hash, """{"name":"lobby"}""", "id2")
        // order shuffled to prove fold is order-independent
        val head = EditionFold.foldEntity(listOf(v2, v0, v1))
        assertEquals(v2.rumorId, head?.rumorId)
    }

    @Test
    fun foldStopsAtBreakAndRefusesDowngrade() {
        val v0 = edition(0, null, """{"name":"general"}""", "id0")
        // v2 present but v1 missing ⇒ head cannot advance past v0
        val v2 = edition(2, ByteArray(32) { 0x09 }, """{"name":"lobby"}""", "id2")
        assertEquals(v0.rumorId, EditionFold.foldEntity(listOf(v0, v2))?.rumorId)

        // v1 with a prev that does not chain from v0 is ignored
        val badV1 = edition(1, ByteArray(32) { 0x07 }, """{"name":"x"}""", "id1")
        assertEquals(v0.rumorId, EditionFold.foldEntity(listOf(v0, badV1))?.rumorId)
    }

    @Test
    fun foldTieBreaksOnLowerRumorId() {
        val a = edition(0, null, """{"name":"a"}""", "aaa")
        val b = edition(0, null, """{"name":"b"}""", "bbb")
        assertEquals("aaa", EditionFold.foldEntity(listOf(b, a))?.rumorId)
    }

    /**
     * A lone edition with a dangling `prev` and no genesis is the compacted head of a Refounded
     * community (CORD-06 §3): a fresh joiner never holds the prior epoch it chains onto, so the
     * head is accepted as the baseline rather than dropped (CORD-04 §1). Dropping it was the bug
     * that hid a refounded community's icon, name, and edited channels. See [EditionFoldTest].
     */
    @Test
    fun foldWithoutGenesisAcceptsCompactedHead() {
        val v1 = edition(1, ByteArray(32) { 0x05 }, """{"name":"x"}""", "id1")
        assertEquals("id1", EditionFold.foldEntity(listOf(v1))?.rumorId)
    }
}
