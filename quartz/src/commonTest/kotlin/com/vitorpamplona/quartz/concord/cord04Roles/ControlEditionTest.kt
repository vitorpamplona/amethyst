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

import com.vitorpamplona.quartz.concord.crypto.EditionHash
import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
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
        val rumor = RumorAssembler.assembleRumor<Event>(author, 1_700_000_000L, ConcordKinds.CONTROL, tags, content)

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
                RumorAssembler.assembleRumor<Event>(author, 1L, ConcordKinds.CONTROL, arrayOf(arrayOf("vsk", "0"), arrayOf("ev", "0")), "{}"),
            ),
        )
        // unknown vsk (bit 7 retired)
        assertNull(
            ControlEdition.fromRumor(
                RumorAssembler.assembleRumor<Event>(
                    author,
                    1L,
                    ConcordKinds.CONTROL,
                    arrayOf(arrayOf("vsk", "7"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "0")),
                    "{}",
                ),
            ),
        )
    }

    @Test
    fun genesisHasNullPrevWhenEpAbsent() {
        val tags = arrayOf(arrayOf("vsk", "2"), arrayOf("eid", eid.toHexKey()), arrayOf("ev", "0"))
        val ed = ControlEdition.fromRumor(RumorAssembler.assembleRumor<Event>(author, 1L, ConcordKinds.CONTROL, tags, """{"name":"general"}"""))
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

    @Test
    fun foldWithoutGenesisReturnsNull() {
        val v1 = edition(1, ByteArray(32) { 0x05 }, """{"name":"x"}""", "id1")
        assertNull(EditionFold.foldEntity(listOf(v1)))
    }
}
