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
package com.vitorpamplona.quartz.concord.cord05Invites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wire conformance for the CORD-05 Invite List (kind 13303). The whole point of this document is
 * cross-client: a link minted in Armada must be refreshable from Amethyst and back, so the field
 * names and the merge key are contract, not preference.
 */
class ConcordInviteListTest {
    // The spec's own example document, verbatim in shape.
    private val specJson =
        """
        { "entries": [
            { "token": "aa11",
              "signer_sk": "bb22",
              "community_id": "cc33",
              "url": "https://vector.chat/invite/naddr1abc#frag",
              "label": "Reddit",
              "created_at": 1719800000,
              "expires_at": 1722400000 } ],
          "tombstones": [ { "token": "dd44", "community_id": "cc33" } ] }
        """.trimIndent()

    @Test
    fun readsTheSpecDocumentIntoTypedEntries() {
        val doc = ConcordInviteList.decodeOrNull(specJson)!!

        assertEquals(1, doc.entries.size)
        val e = doc.entries.first()
        assertEquals("aa11", e.token)
        assertEquals("bb22", e.signerSk)
        assertEquals("cc33", e.communityId)
        assertEquals("https://vector.chat/invite/naddr1abc#frag", e.url)
        assertEquals("Reddit", e.label)
        assertEquals(1719800000L, e.createdAt)
        assertEquals(1722400000L, e.expiresAt)

        assertEquals(1, doc.tombstones.size)
        assertEquals("dd44", doc.tombstones.first().token)
        assertEquals("cc33", doc.tombstones.first().communityId)
    }

    @Test
    fun emitsTheSnakeCaseKeysAnotherClientReads() {
        val json = ConcordInviteList.encode(ConcordInviteList.decodeOrNull(specJson)!!)
        // Field names are the interop contract — a camelCase slip silently orphans every link.
        for (key in listOf("\"token\"", "\"signer_sk\"", "\"community_id\"", "\"url\"", "\"created_at\"", "\"expires_at\"", "\"entries\"", "\"tombstones\"")) {
            assertTrue(json.contains(key), "missing wire key $key")
        }
    }

    @Test
    fun keepsUnknownKeysAcrossADecodeEncodeCycle() {
        // Armada types the entry and tombstone as `[k: string]: unknown`, so dropping a key we do
        // not model deletes another client's data on our next publish.
        val withExtras =
            """
            { "entries": [ { "token": "aa11", "signer_sk": "bb22", "community_id": "cc33",
                             "url": "u", "created_at": 1, "future_field": {"a":1} } ],
              "tombstones": [ { "token": "dd44", "community_id": "cc33", "why": "revoked" } ],
              "doc_level_unknown": 7 }
            """.trimIndent()

        val round = ConcordInviteList.encode(ConcordInviteList.decodeOrNull(withExtras)!!)

        assertTrue(round.contains("future_field"), "entry-level unknown key dropped")
        assertTrue(round.contains("doc_level_unknown"), "document-level unknown key dropped")
        assertTrue(round.contains("\"why\""), "tombstone unknown key dropped")
    }

    @Test
    fun mergesByTokenAndLetsTombstonesWin() {
        val base =
            ConcordInviteListDocument(
                entries =
                    listOf(
                        ConcordInviteListEntry("t1", "sk1", "c", "url1", createdAt = 1),
                        ConcordInviteListEntry("t2", "sk2", "c", "url2", createdAt = 2),
                    ),
            )
        // Another device minted t3 and retired t1.
        val patch =
            ConcordInviteListDocument(
                entries = listOf(ConcordInviteListEntry("t3", "sk3", "c", "url3", createdAt = 3)),
                tombstones = listOf(ConcordInviteListTombstone("t1", "c")),
            )

        val merged = ConcordInviteList.merge(base, patch)
        val tokens = merged.entries.map { it.token }.toSet()

        assertEquals(setOf("t2", "t3"), tokens, "merge is keyed by token; a tombstoned link is dropped")
        assertTrue(merged.tombstones.any { it.token == "t1" }, "the tombstone must persist or a stale device resurrects the link")
    }

    @Test
    fun aMalformedDocumentYieldsNullSoCallersCannotOverwriteWithIt() {
        // Null, not empty: a caller that republishes an "empty" list over this replaceable
        // coordinate destroys every signer_sk it failed to read.
        assertEquals(null, ConcordInviteList.decodeOrNull("not json"))
        assertEquals(null, ConcordInviteList.decodeOrNull("{\"entries\":\"wrong type\"}"))
    }

    @Test
    fun anUnreadableListIsDistinguishableFromAnEmptyOne() {
        // The whole point of the null: a caller must be able to tell "I could not read it" from
        // "there is nothing in it". Publishing a merge onto the latter is fine; onto the former it
        // destroys every signer_sk on this replaceable coordinate.
        assertEquals(null, ConcordInviteList.decodeOrNull("<not json>"))

        val empty = ConcordInviteList.decodeOrNull("""{"entries":[],"tombstones":[]}""")
        assertEquals(0, empty!!.entries.size, "a genuinely empty list decodes, it does not fail")

        // And a merge onto an empty base keeps the patch, so starting a first list still works.
        val patch = ConcordInviteListDocument(entries = listOf(ConcordInviteListEntry("t", "sk", "c", "u")))
        assertEquals(listOf("t"), ConcordInviteList.merge(empty, patch).entries.map { it.token })
    }

    @Test
    fun theSignerPubKeyIsTheCoordinateTheBundleLivesAt() {
        // Refreshing or revoking a link means writing at exactly this author, so it must derive from
        // the secret we kept rather than being stored (and drifting) separately.
        val sk = "11".repeat(32)
        val entry = ConcordInviteListEntry("t", sk, "c", "u")
        assertEquals(64, entry.signerPubKeyHex().length)
        assertEquals(entry.signerPubKeyHex(), ConcordInviteListEntry("t2", sk, "c", "u2").signerPubKeyHex())
    }

    @Test
    fun anExpiredLinkIsNotRefreshable() {
        val live = ConcordInviteListEntry("t", "sk", "c", "u", expiresAt = 100)
        val forever = ConcordInviteListEntry("t", "sk", "c", "u", expiresAt = null)

        assertTrue(live.isExpired(nowSecs = 101), "an elapsed link can no longer be joined")
        assertTrue(!live.isExpired(nowSecs = 99))
        assertTrue(!forever.isExpired(nowSecs = Long.MAX_VALUE), "no expiry means it never elapses")
    }
}
