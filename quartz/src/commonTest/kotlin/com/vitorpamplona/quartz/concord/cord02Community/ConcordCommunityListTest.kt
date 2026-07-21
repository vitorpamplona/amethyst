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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConcordCommunityListTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val other = NostrSignerInternal(KeyPair())

    private fun entry(
        id: String,
        name: String,
        epoch: Long = 0,
    ) = ConcordCommunityListEntry(
        id = id,
        owner = "0f".repeat(32),
        ownerSalt = "aa".repeat(32),
        root = "bb".repeat(32),
        rootEpoch = epoch,
        relays = listOf("wss://relay.example"),
        name = name,
    )

    @Test
    fun selfEncryptedListRoundTrips() =
        runTest {
            val entries = listOf(entry("11".repeat(32), "Gamers"), entry("22".repeat(32), "Nostrichs"))
            val event = ConcordCommunityList.build(signer, entries, createdAt = 1_700_000_000L)

            assertEquals(ConcordCommunityListEvent.KIND, event.kind)
            assertFalse(event.content.contains("Gamers")) // encrypted on the wire

            val parsed = ConcordCommunityList.parse(event, signer)
            assertEquals(2, parsed.size)
            assertEquals("Gamers", parsed[0].name)
            assertEquals(listOf("wss://relay.example"), parsed[0].relays)
        }

    @Test
    fun onlyTheOwnerCanDecrypt() =
        runTest {
            val event = ConcordCommunityList.build(signer, listOf(entry("11".repeat(32), "Secret")), createdAt = 1L)
            assertTrue(ConcordCommunityList.parse(event, other).isEmpty()) // wrong key ⇒ nothing
        }

    @Test
    fun decodesArmadaWireDocument() {
        // A document as Soapbox Armada writes it (communityList.ts): {entries:[{community_id,
        // seed, current, added_at}], tombstones:[]} with snake_case JoinMaterial.
        val json =
            """
            {
              "entries": [
                {
                  "community_id": "${"11".repeat(32)}",
                  "seed": {
                    "community_id": "${"11".repeat(32)}",
                    "owner": "${"0f".repeat(32)}",
                    "owner_salt": "${"aa".repeat(32)}",
                    "community_root": "${"bb".repeat(32)}",
                    "root_epoch": 0,
                    "channels": [],
                    "relays": ["wss://relay.ditto.pub"],
                    "name": "Soapbox"
                  },
                  "current": {
                    "community_id": "${"11".repeat(32)}",
                    "owner": "${"0f".repeat(32)}",
                    "owner_salt": "${"aa".repeat(32)}",
                    "community_root": "${"cc".repeat(32)}",
                    "root_epoch": 2,
                    "channels": [
                      { "id": "${"ee".repeat(32)}", "key": "${"dd".repeat(32)}", "epoch": 2, "name": "secret" }
                    ],
                    "relays": ["wss://relay.ditto.pub"],
                    "name": "Soapbox",
                    "held_roots": [ { "epoch": 1, "key": "${"bb".repeat(32)}" } ]
                  },
                  "added_at": 1700000000000
                }
              ],
              "tombstones": []
            }
            """.trimIndent()

        val entries = ConcordCommunityList.decode(json)
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("11".repeat(32), e.id)
        assertEquals("Soapbox", e.name)
        assertEquals("cc".repeat(32), e.root) // hydrated from `current`, not `seed`
        assertEquals(2L, e.rootEpoch)
        assertEquals(1700000000000L, e.addedAt)
        assertEquals(listOf("wss://relay.ditto.pub"), e.relays)
        assertEquals(1, e.privateChannels.size)
        assertEquals("ee".repeat(32), e.privateChannels[0].channelId)
        assertEquals("secret", e.privateChannels[0].name)
        assertEquals(1, e.heldRoots.size)
        assertEquals(1L, e.heldRoots[0].epoch)
    }

    @Test
    fun tombstoneAfterAddDropsEntry() {
        val jm = """{"community_id":"${"11".repeat(32)}","owner":"${"0f".repeat(32)}","owner_salt":"${"aa".repeat(32)}","community_root":"${"bb".repeat(32)}","root_epoch":0,"channels":[],"relays":[],"name":"Gone"}"""
        val json =
            """{"entries":[{"community_id":"${"11".repeat(32)}","seed":$jm,"current":$jm,"added_at":100}],"tombstones":[{"community_id":"${"11".repeat(32)}","removed_at":200}]}"""
        assertTrue(ConcordCommunityList.decode(json).isEmpty()) // removed after add ⇒ not live
    }

    // ---- unknown-key preservation --------------------------------------------
    //
    // Armada's communityList.ts entry type ends in `[k: string]: unknown` — unknown keys are
    // part of the contract, and every write of ours must hand them back. A top-level-only
    // catch-all would not be enough: most of the data lives inside `seed`/`current`.

    /** A document carrying an unmodelled key at every depth of the structure. */
    private fun docWithExtrasEverywhere() =
        """
        {
          "entries": [
            {
              "community_id": "${"11".repeat(32)}",
              "seed": {
                "community_id": "${"11".repeat(32)}",
                "owner": "${"0f".repeat(32)}",
                "owner_salt": "${"aa".repeat(32)}",
                "community_root": "${"bb".repeat(32)}",
                "root_epoch": 0,
                "channels": [],
                "relays": ["wss://relay.ditto.pub"],
                "name": "Soapbox",
                "x_seed": "seed-only"
              },
              "current": {
                "community_id": "${"11".repeat(32)}",
                "owner": "${"0f".repeat(32)}",
                "owner_salt": "${"aa".repeat(32)}",
                "community_root": "${"cc".repeat(32)}",
                "root_epoch": 2,
                "channels": [
                  { "id": "${"ee".repeat(32)}", "key": "${"dd".repeat(32)}", "epoch": 2, "name": "secret", "x_channel": "chan-only" }
                ],
                "relays": ["wss://relay.ditto.pub"],
                "name": "Soapbox",
                "held_roots": [ { "epoch": 1, "key": "${"bb".repeat(32)}", "x_held": "held-only" } ],
                "refounder": "${"99".repeat(32)}",
                "x_current": "current-only"
              },
              "added_at": 1700000000000,
              "x_entry": "entry-only"
            }
          ],
          "tombstones": [
            { "community_id": "${"22".repeat(32)}", "removed_at": 5, "x_tomb": "tomb-only" }
          ],
          "x_doc": "doc-only"
        }
        """.trimIndent()

    private fun String.asJson() = Json.parseToJsonElement(this).jsonObject

    private fun JsonObject.entry0() = this["entries"]!!.jsonArray[0].jsonObject

    @Test
    fun unknownKeysSurviveAtEveryLevel() {
        val doc = ConcordCommunityList.decodeDocument(docWithExtrasEverywhere())
        assertEquals(1, doc.entries.size)

        val out = ConcordCommunityList.encode(doc.entries, doc.residue).asJson()

        // document root
        assertEquals("doc-only", out["x_doc"]?.jsonPrimitive?.content)
        // tombstone (verbatim — we read tombstones but never author one)
        val tomb = out["tombstones"]!!.jsonArray[0].jsonObject
        assertEquals("tomb-only", tomb["x_tomb"]?.jsonPrimitive?.content)
        assertEquals("22".repeat(32), tomb["community_id"]?.jsonPrimitive?.content)

        val entry = out.entry0()
        // entry level
        assertEquals("entry-only", entry["x_entry"]?.jsonPrimitive?.content)
        // seed (the immutable join anchor, handed back untouched)
        assertEquals("seed-only", entry["seed"]!!.jsonObject["x_seed"]?.jsonPrimitive?.content)

        val current = entry["current"]!!.jsonObject
        // current join material
        assertEquals("current-only", current["x_current"]?.jsonPrimitive?.content)
        // nested channel
        assertEquals(
            "chan-only",
            current["channels"]!!
                .jsonArray[0]
                .jsonObject["x_channel"]
                ?.jsonPrimitive
                ?.content,
        )
        // nested held root
        assertEquals(
            "held-only",
            current["held_roots"]!!
                .jsonArray[0]
                .jsonObject["x_held"]
                ?.jsonPrimitive
                ?.content,
        )

        // and none of the modelled data was disturbed on the way through
        assertEquals("cc".repeat(32), current["community_root"]?.jsonPrimitive?.content)
        assertEquals("Soapbox", current["name"]?.jsonPrimitive?.content)
        assertEquals(
            "secret",
            current["channels"]!!
                .jsonArray[0]
                .jsonObject["name"]
                ?.jsonPrimitive
                ?.content,
        )
        // the container the preserver uses internally never leaks onto the wire
        assertFalse(out.toString().contains("__extras"))
    }

    @Test
    fun refounderSurvivesARoundTrip() {
        // `refounder` is Armada's; nothing in Amethyst reads it. It used to be a typed field that
        // was parsed, never mapped into the domain, and therefore destroyed on the first write.
        val out = roundTrip(docWithExtrasEverywhere())
        val current = out.entry0()["current"]!!.jsonObject
        assertEquals("99".repeat(32), current["refounder"]?.jsonPrimitive?.content)
    }

    @Test
    fun unknownKeysSurviveASecondRoundTrip() {
        // Read-modify-write is not a one-shot: the preserved keys must still be there after the
        // document has been through us twice.
        val once = roundTrip(docWithExtrasEverywhere())
        val twice = roundTrip(once.toString())
        assertEquals("doc-only", twice["x_doc"]?.jsonPrimitive?.content)
        assertEquals("entry-only", twice.entry0()["x_entry"]?.jsonPrimitive?.content)
        assertEquals(
            "seed-only",
            twice
                .entry0()["seed"]!!
                .jsonObject["x_seed"]
                ?.jsonPrimitive
                ?.content,
        )
        val current = twice.entry0()["current"]!!.jsonObject
        assertEquals("current-only", current["x_current"]?.jsonPrimitive?.content)
        assertEquals("99".repeat(32), current["refounder"]?.jsonPrimitive?.content)
        assertEquals(
            "chan-only",
            current["channels"]!!
                .jsonArray[0]
                .jsonObject["x_channel"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "held-only",
            current["held_roots"]!!
                .jsonArray[0]
                .jsonObject["x_held"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    private fun roundTrip(json: String): JsonObject {
        val doc = ConcordCommunityList.decodeDocument(json)
        return ConcordCommunityList.encode(doc.entries, doc.residue).asJson()
    }

    @Test
    fun modelledFieldWinsOverAPreservedKeyOfTheSameName() {
        // A preserved bag must never shadow a field we model: if both carry `name`, the value we
        // hold in the domain is the one that reaches the wire — exactly once.
        val decoded = ConcordCommunityList.decodeDocument(docWithExtrasEverywhere()).entries[0]
        val poisoned =
            ConcordCommunityListEntry(
                id = decoded.id,
                owner = decoded.owner,
                ownerSalt = decoded.ownerSalt,
                root = decoded.root,
                rootEpoch = decoded.rootEpoch,
                heldRoots = decoded.heldRoots.map { HeldRoot(it.epoch, it.key, buildJsonObject { put("key", "STALE") }) },
                privateChannels = decoded.privateChannels.map { PrivateChannelKey(it.channelId, it.key, it.epoch, it.name, buildJsonObject { put("name", "STALE") }) },
                relays = decoded.relays,
                name = "Renamed",
                addedAt = decoded.addedAt,
                residue =
                    ConcordEntryResidue(
                        entryExtras = buildJsonObject { put("community_id", "STALE") },
                        seed = decoded.residue.seed,
                        currentExtras =
                            buildJsonObject {
                                put("name", "STALE")
                                put("x_current", "kept")
                            },
                    ),
            )

        val out = ConcordCommunityList.encode(listOf(poisoned)).asJson()
        val entry = out.entry0()
        assertEquals(decoded.id, entry["community_id"]?.jsonPrimitive?.content)

        val current = entry["current"]!!.jsonObject
        assertEquals("Renamed", current["name"]?.jsonPrimitive?.content)
        assertEquals("kept", current["x_current"]?.jsonPrimitive?.content) // non-colliding key untouched
        assertEquals(
            "secret",
            current["channels"]!!
                .jsonArray[0]
                .jsonObject["name"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "bb".repeat(32),
            current["held_roots"]!!
                .jsonArray[0]
                .jsonObject["key"]
                ?.jsonPrimitive
                ?.content,
        )
        assertFalse(out.toString().contains("STALE"))
    }

    @Test
    fun tombstonesAreHandedBackInsteadOfDropped() {
        // Dropping tombstones on write would resurrect communities another client deliberately
        // removed, on top of losing whatever unknown keys they carried.
        val out = roundTrip(docWithExtrasEverywhere())
        assertEquals(1, out["tombstones"]!!.jsonArray.size)
        assertNotNull(out["tombstones"]!!.jsonArray[0].jsonObject["removed_at"])
    }

    @Test
    fun aFreshEntryStillSeedsItsJoinAnchor() {
        // No residue to hand back for a brand-new join: `seed` is minted from the current material.
        val out = ConcordCommunityList.encode(listOf(entry("11".repeat(32), "New", epoch = 3))).asJson()
        val entry = out.entry0()
        assertEquals("bb".repeat(32), entry["seed"]!!.jsonObject["community_root"]?.jsonPrimitive?.content)
        assertEquals("bb".repeat(32), entry["current"]!!.jsonObject["community_root"]?.jsonPrimitive?.content)
    }

    @Test
    fun mergeKeepsFreshestEpochPerCommunity() {
        val a = listOf(entry("11".repeat(32), "Old", epoch = 1))
        val b = listOf(entry("11".repeat(32), "New", epoch = 3), entry("22".repeat(32), "Other", epoch = 0))
        val merged = ConcordCommunityList.merge(a, b)
        assertEquals(2, merged.size)
        assertEquals("New", merged.first { it.id == "11".repeat(32) }.name) // higher epoch wins
    }
}
