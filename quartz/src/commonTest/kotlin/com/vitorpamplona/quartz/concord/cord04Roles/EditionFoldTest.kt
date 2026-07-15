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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditionFoldTest {
    private val author = KeyPair().pubKey.toHexKey()
    private val eid = ByteArray(32) { 0xAB.toByte() }

    private fun edition(
        version: Long,
        prevHash: ByteArray?,
        content: String,
        rumorId: String = "id-v$version",
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

    /** A normal chain (v0 genesis → v1 → v2) folds to the highest intact version. */
    @Test
    fun foldsIntactChainToHead() {
        val v0 = edition(0, null, "genesis")
        val v1 = edition(1, v0.hash, "one")
        val v2 = edition(2, v1.hash, "two")

        val head = EditionFold.foldEntity(listOf(v2, v0, v1))
        assertEquals(2, head?.version)
        assertEquals("two", head?.content)
    }

    /**
     * The Refounding case (CORD-06 §3): a fresh joiner holds only the compacted head, whose
     * `prev` cites the prior epoch it never fetched. With no genesis present, the head is
     * accepted as the baseline rather than dropped — the bug that hid a refounded community's
     * icon, name, and edited channels.
     */
    @Test
    fun acceptsDanglingCompactedHeadWhenNoGenesis() {
        val danglingHead = edition(5, ByteArray(32) { 0x99.toByte() }, "compacted-head")

        val head = EditionFold.foldEntity(listOf(danglingHead))
        assertEquals(5, head?.version)
        assertEquals("compacted-head", head?.content)
    }

    /** A dangling head plus post-refounding edits chains forward from the accepted baseline. */
    @Test
    fun advancesFromDanglingHeadAsNewEditionsArrive() {
        val danglingHead = edition(5, ByteArray(32) { 0x99.toByte() }, "compacted-head")
        val v6 = edition(6, danglingHead.hash, "post-refound edit")

        val head = EditionFold.foldEntity(listOf(v6, danglingHead))
        assertEquals(6, head?.version)
        assertEquals("post-refound edit", head?.content)
    }

    /** A genuine mid-chain gap still fails closed at the intact prefix — no silent jump past the hole. */
    @Test
    fun stopsAtGapWhenGenesisPresent() {
        val v0 = edition(0, null, "genesis")
        // v1 is missing; v2 cites a hash we don't hold, so it can't chain onto v0.
        val v2 = edition(2, ByteArray(32) { 0x77.toByte() }, "orphan")

        val head = EditionFold.foldEntity(listOf(v2, v0))
        assertEquals(0, head?.version)
        assertEquals("genesis", head?.content)
    }

    /** No editions at all → no head. */
    @Test
    fun emptyFoldsToNull() {
        assertNull(EditionFold.foldEntity(emptyList()))
    }
}
