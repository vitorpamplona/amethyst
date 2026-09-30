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
package com.vitorpamplona.quartz.buzz.rsReadState

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NIP-RS `d`-tag shape, "Content Validation", "Reserved Namespace" and the manual-unread override layer. */
class ReadStateValidationTest {
    private val slot = "0123456789abcdef0123456789abcdef"

    @Test
    fun slotIdMustBeExactlyThirtyTwoLowercaseHex() {
        assertTrue(ReadState.isValidSlotId(slot))
        assertTrue(ReadState.isValidSlotId(ReadState.newSlotId()))
        assertFalse(ReadState.isValidSlotId(slot.uppercase()))
        assertFalse(ReadState.isValidSlotId(slot.dropLast(1)))
        assertFalse(ReadState.isValidSlotId(slot + "0"))
        assertFalse(ReadState.isValidSlotId("phone-slot-1"))

        assertEquals(slot, ReadState.slotIdFrom("read-state:$slot"))
        assertNull(ReadState.slotIdFrom("read-state:phone-slot-1"))
        assertNull(ReadState.slotIdFrom("read-state:${slot.uppercase()}"))
        assertNull(ReadState.slotIdFrom(slot))

        assertTrue(ReadState.isReadState(arrayOf(arrayOf("d", "read-state:$slot"), arrayOf("t", "read-state"))))
        assertFalse(ReadState.isReadState(arrayOf(arrayOf("d", "read-state:abc"), arrayOf("t", "read-state"))))
    }

    @Test
    fun structuralFaultsDiscardTheWholeBlob() {
        assertFailsWith<Exception> { ReadStateContent.decodeFromJson("not json") }
        assertFailsWith<IllegalArgumentException> { ReadStateContent.decodeFromJson("[]") }
        assertFailsWith<IllegalArgumentException> { ReadStateContent.decodeFromJson("""{"client_id":"a","contexts":{}}""") }
        assertFailsWith<IllegalArgumentException> { ReadStateContent.decodeFromJson("""{"v":"1","client_id":"a","contexts":{}}""") }
        assertFailsWith<IllegalArgumentException> { ReadStateContent.decodeFromJson("""{"v":1.5,"client_id":"a","contexts":{}}""") }
        assertFailsWith<IllegalArgumentException> { ReadStateContent.decodeFromJson("""{"v":1,"contexts":{}}""") }
        assertFailsWith<IllegalArgumentException> { ReadStateContent.decodeFromJson("""{"v":1,"client_id":"a"}""") }
        assertFailsWith<IllegalArgumentException> { ReadStateContent.decodeFromJson("""{"v":1,"client_id":"a","contexts":[]}""") }
    }

    @Test
    fun unknownVersionIsIgnoredNotParsed() {
        val blob = ReadStateContent.decodeFromJson("""{"v":2,"client_id":"a","contexts":"whatever"}""")
        assertEquals(2, blob.v)
        assertFalse(blob.isSupported())
        assertTrue(blob.contexts.isEmpty())
    }

    @Test
    fun badEntriesAreDroppedAndTheRestKept() {
        val longKey = "k".repeat(257)
        val okKey = "k".repeat(256)
        val blob =
            ReadStateContent.decodeFromJson(
                """{"v":1,"client_id":"phone","contexts":{""" +
                    """"good":1700000000,"zero":0,"max":4294967295,""" +
                    """"negative":-1,"tooBig":4294967296,"fraction":1.5,"text":"yesterday","nul":null,"obj":{},""" +
                    """"$longKey":1,"$okKey":2}}""",
            )
        assertTrue(blob.isSupported())
        assertEquals(
            mapOf("good" to 1_700_000_000L, "zero" to 0L, "max" to 4_294_967_295L, okKey to 2L),
            blob.contexts,
        )
    }

    @Test
    fun keyLimitCountsUtf8Bytes() {
        // 128 two-byte characters = 256 bytes (kept); 129 = 258 bytes (dropped).
        val kept = "é".repeat(128)
        val dropped = "é".repeat(129)
        val blob = ReadStateContent.decodeFromJson("""{"v":1,"client_id":"a","contexts":{"$kept":1,"$dropped":2}}""")
        assertEquals(setOf(kept), blob.contexts.keys)
    }

    @Test
    fun moreThanTenThousandEntriesRejectsTheBlob() {
        fun blob(n: Int) = (0 until n).joinToString(",", prefix = """{"v":1,"client_id":"a","contexts":{""", postfix = "}}") { "\"c$it\":1" }

        assertEquals(10_000, ReadStateContent.decodeFromJson(blob(10_000)).contexts.size)
        assertFailsWith<IllegalArgumentException> { ReadStateContent.decodeFromJson(blob(10_001)) }
    }

    @Test
    fun escapingIsABijectionOverTheReservedPrefixes() {
        assertEquals("esc:ov_s:evil", ReadStateKeys.escape("ov_s:evil"))
        assertEquals("esc:esc:foo", ReadStateKeys.escape("esc:foo"))
        assertEquals("esc:ov_anything", ReadStateKeys.escape("ov_anything"))
        assertEquals("channel-uuid", ReadStateKeys.escape("channel-uuid"))
        listOf("ov_s:evil", "esc:foo", "esc:esc:bar", "plain", "msg:ab").forEach {
            assertEquals(it, ReadStateKeys.unescape(ReadStateKeys.escape(it)))
        }
        // Exactly one `esc:` is stripped.
        assertEquals("esc:foo", ReadStateKeys.unescape("esc:esc:foo"))
    }

    @Test
    fun frontiersUnescapeAndSkipOverrideEntries() {
        val blob =
            ReadStateContent.decodeFromJson(
                """{"v":1,"client_id":"a","contexts":{"chan":10,"esc:ov_s:evil":20,"esc:esc:foo":30,""" +
                    """"ov_s:chan":2,"ov_c:chan":1,"ov_b:chan":10,"ov_future:x":5}}""",
            )
        assertEquals(mapOf("chan" to 10L, "ov_s:evil" to 20L, "esc:foo" to 30L), blob.frontiers())
        assertEquals(mapOf("chan" to OverrideRegister(2, 1, 10)), blob.overrides())
    }

    @Test
    fun overrideGroupsAreValidatedAsAWhole() {
        val blob =
            ReadStateContent.decodeFromJson(
                """{"v":1,"client_id":"a","contexts":{""" +
                    // live: complete triple
                    """"live":5,"ov_s:live":1,"ov_c:live":0,"ov_b:live":5,""" +
                    // tombstone floor: lone ov_c
                    """"dead":5,"ov_c:dead":3,""" +
                    // partial group (no baseline): whole group rejected, frontier kept
                    """"partial":5,"ov_s:partial":1,"ov_c:partial":0,""" +
                    // one invalid sibling: whole group rejected, frontier kept
                    """"badsib":5,"ov_s:badsib":1,"ov_c:badsib":0,"ov_b:badsib":-1,""" +
                    // lone ov_s is not a tombstone
                    """"lones":5,"ov_s:lones":1}}""",
            )
        assertEquals(
            mapOf("live" to OverrideRegister(1, 0, 5), "dead" to OverrideRegister(0, 3, 0)),
            blob.overrides(),
        )
        assertEquals(setOf("live", "dead", "partial", "badsib", "lones"), blob.frontiers().keys)
        assertFalse(blob.contexts.keys.any { it.endsWith(":partial") || it.endsWith(":badsib") || it.endsWith(":lones") })
    }

    @Test
    fun anOverlongKeyRejectsItsWholeGroup() {
        val ctx = "c".repeat(252) // "ov_s:" + 252 = 257 bytes
        val blob =
            ReadStateContent.decodeFromJson(
                """{"v":1,"client_id":"a","contexts":{"ov_s:$ctx":1,"ov_c:$ctx":0,"ov_b:$ctx":5}}""",
            )
        assertTrue(blob.overrides().isEmpty())
    }

    @Test
    fun livenessIsClearWins() {
        assertTrue(OverrideRegister(1, 0, 10).isActive(frontier = 10))
        assertFalse(OverrideRegister(1, 0, 10).isActive(frontier = 11)) // natural read past B
        assertFalse(OverrideRegister(1, 1, 10).isActive(frontier = 5)) // S == C: clear wins
        assertFalse(OverrideRegister(0, 0, 10).isActive(frontier = 5))
    }

    @Test
    fun canonicalPublicationCompactsDeadRegistersToTheTombstoneFloor() {
        assertEquals(OverrideRegister(2, 1, 10), OverrideRegister(2, 1, 10).canonicalize(frontier = 10))
        assertEquals(OverrideRegister(0, 3, 0), OverrideRegister(3, 2, 10).canonicalize(frontier = 11))
        assertEquals(OverrideRegister(0, 4, 0), OverrideRegister(2, 4, 10).canonicalize(frontier = 0))
        assertNull(OverrideRegister.VIRGIN.canonicalize(frontier = 0))

        val built =
            ReadStateContent.build(
                clientId = "phone",
                frontiers = mapOf("live" to 10L, "dead" to 20L, "ov_s:weird" to 30L),
                overrides = mapOf("live" to OverrideRegister(1, 0, 10), "dead" to OverrideRegister(1, 0, 10), "virgin" to OverrideRegister.VIRGIN),
            )
        assertEquals(
            mapOf(
                "live" to 10L,
                "dead" to 20L,
                "esc:ov_s:weird" to 30L,
                "ov_s:live" to 1L,
                "ov_c:live" to 0L,
                "ov_b:live" to 10L,
                "ov_c:dead" to 1L,
            ),
            built.contexts,
        )
        // Round-trips through the wire validator unchanged.
        assertEquals(built, ReadStateContent.decodeFromJson(built.encodeToJson()))
    }

    @Test
    fun actionsBumpPastBothCountersAndRefuseAtTheCeiling() {
        val unread = OverrideRegister.VIRGIN.markUnread(frontier = 50)!!
        assertEquals(OverrideRegister(1, 0, 50), unread)
        assertTrue(unread.isActive(frontier = 50))

        val read = unread.markRead(frontierAfter = 50)!!
        assertEquals(OverrideRegister(1, 2, 50), read)
        assertFalse(read.isActive(frontier = 50))

        val max = ReadStateContent.MAX_VALUE
        assertNull(OverrideRegister(max, 0, 5).markUnread(frontier = 5))
        // At the ceiling a mark-read only succeeds when the override is already inactive.
        assertNull(OverrideRegister(max, 0, 5).markRead(frontierAfter = 5))
        assertEquals(OverrideRegister(max, 0, 5), OverrideRegister(max, 0, 5).markRead(frontierAfter = 6))
    }

    /** Two independently-dead registers must not merge into a live one — the reason tombstones exist. */
    @Test
    fun mergeTakesComponentwiseMaxAcrossBlobs() {
        val phone = ReadStateContent.build("phone", mapOf("chan" to 10L), mapOf("chan" to OverrideRegister(1, 0, 10)))
        val laptop = ReadStateContent.build("laptop", mapOf("chan" to 8L), mapOf("chan" to OverrideRegister(1, 2, 8)))
        val legacy = ReadStateContent(v = 2, clientId = "future", contexts = mapOf("chan" to 999L))

        val merged = MergedReadState.merge(listOf(phone, laptop, legacy))
        assertEquals(10L, merged.frontier("chan"))
        assertEquals(OverrideRegister(1, 2, 10), merged.overrides["chan"])
        assertFalse(merged.isOverrideActive("chan"))
        assertFalse(merged.isUnread("chan", latestMessageTs = 10))
        assertTrue(merged.isUnread("chan", latestMessageTs = 11))
        assertTrue(merged.isUnread("never-read", latestMessageTs = 1))
    }
}
