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
package com.vitorpamplona.quartz.concord.cord03Channels

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityList
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The List side of Private Channel keys (CORD-02 §8, CORD-03 §2, CORD-06 §2): rotations replace
 * the one current key in place, older keys are read from `seed` / a peer's `priors` but never
 * written, and the reference client's `channel_cuts` floor survives a round trip and refuses a
 * key below it.
 */
class ConcordChannelKeyringTest {
    private val chan = "a1".repeat(32)
    private val other = "b2".repeat(32)
    private val k0 = "10".repeat(32)
    private val k1 = "11".repeat(32)
    private val k2 = "12".repeat(32)

    private fun entry(channels: List<PrivateChannelKey>) =
        ConcordCommunityListEntry(
            id = "c0".repeat(32),
            owner = "0f".repeat(32),
            ownerSalt = "5a".repeat(32),
            root = "22".repeat(32),
            rootEpoch = 2,
            privateChannels = channels,
            name = "Test",
            addedAt = 1,
        )

    private fun roundTrip(e: ConcordCommunityListEntry): ConcordCommunityListEntry = ConcordCommunityList.decode(ConcordCommunityList.encode(listOf(e))).single()

    @Test
    fun aRotationReplacesTheKeyInPlaceKeepingUnknownFields() {
        val wire =
            """{"entries":[{"community_id":"${"c0".repeat(32)}","added_at":1,"current":{"community_id":"${"c0".repeat(32)}",
               "owner":"${"0f".repeat(32)}","owner_salt":"${"5a".repeat(32)}","community_root":"${"22".repeat(32)}","root_epoch":2,
               "channels":[{"id":"$chan","key":"$k0","epoch":0,"name":"mods","tint":"red"}],"relays":[],"name":"Test"}}]}"""
        val held = ConcordCommunityList.decode(wire).single()
        val rotated = assertNotNull(ConcordChannelKeyring.withRotatedKey(held, chan, k1, 1))
        val ch = rotated.privateChannels.single()
        assertEquals(k1, ch.key)
        assertEquals(1, ch.epoch)
        assertEquals("mods", ch.name)
        // Another client's field inside the channel object survives.
        val back = roundTrip(rotated).privateChannels.single()
        assertEquals(JsonPrimitive("red"), back.extras["tint"])
        // Never backward, never sideways.
        assertNull(ConcordChannelKeyring.withRotatedKey(rotated, chan, k2, 1))
        assertNull(ConcordChannelKeyring.withRotatedKey(rotated, chan, k2, 0))
    }

    @Test
    fun aCutRoundTripsAndRefusesOlderKeys() {
        val held = entry(listOf(PrivateChannelKey(chan, k0, 0, "mods"), PrivateChannelKey(other, k1, 3, "vip")))
        val cut = ConcordChannelKeyring.withoutChannel(held, chan, 1)
        assertEquals(listOf(other), cut.privateChannels.map { it.channelId })

        // Written as the reference client's entry-level extension and read back.
        val back = roundTrip(cut)
        val encoded = ConcordJson.instance.parseToJsonElement(ConcordCommunityList.encode(listOf(cut))).jsonObject
        val cuts = encoded["entries"]!!.jsonArray[0].jsonObject["channel_cuts"]!!.jsonArray
        assertEquals(listOf(JsonObject(mapOf("id" to JsonPrimitive(chan), "epoch" to JsonPrimitive(1L)))), cuts.toList())
        assertEquals(mapOf(chan to 1L), ConcordChannelKeyring.cutsOf(back))

        // A stale key below the cut never comes back; one at/above it (a re-grant) does.
        assertTrue(ConcordChannelKeyring.isCutOff(back, chan, 0))
        assertNull(ConcordChannelKeyring.withChannelKey(back, PrivateChannelKey(chan, k0, 0)))
        assertNotNull(ConcordChannelKeyring.withChannelKey(back, PrivateChannelKey(chan, k2, 1)))

        // Max wins; a lower cut never rolls it back.
        assertEquals(1L, ConcordChannelKeyring.cutsOf(ConcordChannelKeyring.withCut(back, chan, 0))[chan])
        assertEquals(4L, ConcordChannelKeyring.cutsOf(ConcordChannelKeyring.withCut(back, chan, 4))[chan])
    }

    @Test
    fun anUnknownFieldInsideACutSurvivesARaise() {
        val wire =
            """{"entries":[{"community_id":"${"c0".repeat(32)}","added_at":1,"channel_cuts":[{"id":"$chan","epoch":1,"why":"x"},{"id":"$other","epoch":2}],
               "current":{"community_id":"${"c0".repeat(32)}","owner":"${"0f".repeat(32)}","owner_salt":"${"5a".repeat(32)}",
               "community_root":"${"22".repeat(32)}","root_epoch":2,"channels":[],"relays":[],"name":"Test"}}]}"""
        val held = ConcordCommunityList.decode(wire).single()
        val raised = roundTrip(ConcordChannelKeyring.withCut(held, chan, 3))
        val cuts = raised.residue.entryExtras["channel_cuts"] as JsonArray
        val mine = cuts.map { it.jsonObject }.single { (it["id"] as JsonPrimitive).content == chan }
        assertEquals(JsonPrimitive("x"), mine["why"])
        assertEquals(mapOf(chan to 3L, other to 2L), ConcordChannelKeyring.cutsOf(raised))
    }

    @Test
    fun olderKeysAreReadFromSeedAndPriorsButNeverWritten() {
        val wire =
            """{"entries":[{"community_id":"${"c0".repeat(32)}","added_at":1,
               "seed":{"community_id":"${"c0".repeat(32)}","owner":"${"0f".repeat(32)}","owner_salt":"${"5a".repeat(32)}","community_root":"${"22".repeat(32)}",
                 "root_epoch":0,"channels":[{"id":"$chan","key":"$k0","epoch":0,"name":"mods"}],"relays":[],"name":"Test"},
               "current":{"community_id":"${"c0".repeat(32)}","owner":"${"0f".repeat(32)}","owner_salt":"${"5a".repeat(32)}","community_root":"${"22".repeat(32)}",
                 "root_epoch":2,"channels":[{"id":"$chan","key":"$k2","epoch":2,"name":"mods","priors":[{"key":"$k1","epoch":1,"retired_at":5}]}],"relays":[],"name":"Test"}}]}"""
        val held = ConcordCommunityList.decode(wire).single()
        assertEquals(listOf(1L to k1, 0L to k0), ConcordChannelKeyring.historicalKeys(held, chan).map { it.epoch to it.key })
        // The next privatisation climbs past every generation this entry knows of.
        assertEquals(3, ConcordChannelKeyring.nextChannelEpoch(held, chan))
        assertEquals(10, ConcordChannelKeyring.nextChannelEpoch(held, chan, observedFloor = 9))
        assertEquals(1, ConcordChannelKeyring.nextChannelEpoch(held, other))

        // A rotation we launch adds no prior of its own (CORD-02 §8 keeps intermediate keys out of the List).
        val rotated = assertNotNull(ConcordChannelKeyring.withRotatedKey(held, chan, "13".repeat(32), 3))
        val priors = rotated.privateChannels.single().extras[ConcordChannelKeyring.PRIORS] as JsonArray
        assertEquals(1, priors.size)
        assertFalse(ConcordChannelKeyring.historicalKeys(rotated, chan).any { it.key == "13".repeat(32) })
    }
}
