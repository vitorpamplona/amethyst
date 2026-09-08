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
package com.vitorpamplona.quartz.marmot.foundation.appEvents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `foundation/application-messages.md` conformance.
 *
 * The load-bearing test is [matchesTheSpecPublishedSystemEventFixture]: the
 * spec publishes one complete kind `1210` event together with the exact id it
 * hashes to. Because decoders MUST reject a payload whose id does not match,
 * canonical encoding is not a style question — one extra space or a reordered
 * member and every peer rejects everything we send. Matching the published id
 * is the only way to know we are right rather than merely self-consistent.
 */
class MarmotAppEventTest {
    private val alice = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"

    @Test
    fun matchesTheSpecPublishedSystemEventFixture() {
        val row = MarmotSystemEvent(systemType = MarmotSystemType.GROUP_DISBANDED, actor = alice)
        val event = row.toAppEvent(author = alice, createdAt = 1700000000L)

        assertEquals(
            "{\"v\":1,\"system_type\":\"group_disbanded\"," +
                "\"text\":\"Group disbanded\"," +
                "\"data\":{\"actor\":\"" + alice + "\"}}",
            row.toContentJson(),
        )
        assertEquals("126e47076e4d0a75ed260b279c33ed433acd764fc80e2de2e0315a64116d1f52", event.id)
        assertTrue(event.hasValidId())
    }

    @Test
    fun roundTripsThroughItsCanonicalJson() {
        val event =
            MarmotAppEvent.build(
                pubKey = alice,
                kind = MarmotAppEvent.KIND_CHAT,
                content = "hello",
                createdAt = 1700000000L,
            )
        val decoded = MarmotAppEvent.decode(event.toJson())
        assertEquals(event.id, decoded.id)
        assertEquals(event.content, decoded.content)
        assertEquals(event.toJson(), decoded.toJson())
    }

    /**
     * A signed inner event would be republishable as a public statement by its
     * author, which is exactly why the signature is left out — so a payload
     * carrying one is refused, not merely ignored.
     */
    @Test
    fun rejectsAPayloadCarryingASignature() {
        val event = MarmotAppEvent.build(alice, 9, "hi", 1700000000L)
        val withSig = event.toJson().dropLast(1) + ",\"sig\":\"" + "0".repeat(128) + "\"}"
        val failure = assertFailsWith<IllegalArgumentException> { MarmotAppEvent.decode(withSig) }
        assertTrue(failure.message!!.contains("signature"))
    }

    /** Two implementations that disagree about what to ignore disagree about the id. */
    @Test
    fun rejectsAnUnknownTopLevelMember() {
        val event = MarmotAppEvent.build(alice, 9, "hi", 1700000000L)
        val extra = event.toJson().dropLast(1) + ",\"nonce\":\"x\"}"
        assertFailsWith<IllegalArgumentException> { MarmotAppEvent.decode(extra) }
    }

    /**
     * "Last one wins" and "first one wins" are both defensible, which is the
     * problem: identical bytes would yield different events, and so different
     * ids, on two clients.
     */
    @Test
    fun rejectsDuplicateKeys() {
        val event = MarmotAppEvent.build(alice, 9, "hi", 1700000000L)
        val dup = event.toJson().dropLast(1) + ",\"content\":\"something else\"}"
        val failure = assertFailsWith<IllegalArgumentException> { MarmotAppEvent.decode(dup) }
        assertTrue(failure.message!!.contains("duplicate"))
    }

    /** A repeated key inside a nested VALUE is not a top-level duplicate. */
    @Test
    fun doesNotConfuseANestedKeyForATopLevelOne() {
        val nested = "{\"a\":1,\"a\":2}"
        val event = MarmotAppEvent.build(alice, 1210, nested, 1700000000L)
        assertEquals(event.id, MarmotAppEvent.decode(event.toJson()).id)
    }

    @Test
    fun rejectsAMismatchedId() {
        val event = MarmotAppEvent.build(alice, 9, "hi", 1700000000L)
        val tampered = event.toJson().replace("\"content\":\"hi\"", "\"content\":\"bye\"")
        val failure = assertFailsWith<IllegalArgumentException> { MarmotAppEvent.decode(tampered) }
        assertTrue(failure.message!!.contains("id does not match"))
    }

    @Test
    fun rejectsAMissingMember() {
        val incomplete =
            "{\"id\":\"x\",\"pubkey\":\"" + alice + "\",\"created_at\":1,\"kind\":9,\"tags\":[]}"
        assertFailsWith<IllegalArgumentException> { MarmotAppEvent.decode(incomplete) }
    }

    /** Escaping one more character than a peer does changes the hash. */
    @Test
    fun escapesExactlyTheNip01Set() {
        val awkward = "quote \"q\" backslash \\\\ newline \\n tab \\t"
        val event = MarmotAppEvent.build(alice, 9, awkward, 1700000000L)
        assertTrue(event.hasValidId(), "our serialization must agree with EventHasher's")
        assertEquals(awkward, MarmotAppEvent.decode(event.toJson()).content)
    }

    // --- kind 1009 -------------------------------------------------------

    @Test
    fun anEditNamesExactlyOneTarget() {
        val edit = MarmotMessageEdit("a".repeat(64), "fixed", 1700000000L, alice)
        val parsed = MarmotMessageEdit.fromAppEvent(edit.toAppEvent())!!
        assertEquals("a".repeat(64), parsed.targetId)
        assertEquals("fixed", parsed.replacement)

        // An edit naming two targets leaves each client to pick one, and they
        // would not all pick the same.
        val twoTargets =
            MarmotAppEvent.build(
                alice,
                MarmotAppEvent.KIND_EDIT,
                "fixed",
                1700000000L,
                arrayOf(arrayOf("e", "a".repeat(64)), arrayOf("e", "b".repeat(64))),
            )
        assertNull(MarmotMessageEdit.fromAppEvent(twoTargets))
    }

    /** Authorship is by ACCOUNT, so another device of the same account may edit. */
    @Test
    fun onlyTheOriginalAuthorsAccountMayEdit() {
        val bob = "b".repeat(64)
        val mine = MarmotMessageEdit("a".repeat(64), "fixed", 1700000000L, alice)
        assertTrue(MarmotMessageEdit.isAuthorized(mine, alice))

        val theirs = MarmotMessageEdit("a".repeat(64), "vandalised", 1700000001L, bob)
        assertFalse(MarmotMessageEdit.isAuthorized(theirs, alice))
    }

    /**
     * Two devices of one account can stamp the same second; without a
     * deterministic tie-break two readers would render different text for the
     * same message forever.
     */
    @Test
    fun theLatestEditWinsAndTiesBreakDeterministically() {
        val target = "a".repeat(64)
        val older = MarmotMessageEdit(target, "first", 1700000000L, alice)
        val newer = MarmotMessageEdit(target, "second", 1700000001L, alice)
        assertEquals("second", MarmotMessageEdit.selectOverlay(listOf(newer, older))!!.replacement)
        assertEquals("second", MarmotMessageEdit.selectOverlay(listOf(older, newer))!!.replacement)

        val tieA = MarmotMessageEdit(target, "aaa", 1700000005L, alice)
        val tieB = MarmotMessageEdit(target, "bbb", 1700000005L, alice)
        assertEquals(
            MarmotMessageEdit.selectOverlay(listOf(tieA, tieB))!!.replacement,
            MarmotMessageEdit.selectOverlay(listOf(tieB, tieA))!!.replacement,
        )
    }

    // --- kind 1210 -------------------------------------------------------

    @Test
    fun aSystemRowRoundTripsItsStructuredFields() {
        val row =
            MarmotSystemEvent(
                systemType = MarmotSystemType.MEMBER_ADDED,
                actor = alice,
                subject = "b".repeat(64),
            )
        val parsed = MarmotSystemEvent.fromAppEvent(row.toAppEvent(alice, 1700000000L))!!
        assertEquals(MarmotSystemType.MEMBER_ADDED, parsed.systemType)
        assertEquals(alice, parsed.actor)
        assertEquals("b".repeat(64), parsed.subject)
        assertNull(parsed.name)
    }

    @Test
    fun aRenameCarriesTheNewName() {
        val row = MarmotSystemEvent(MarmotSystemType.GROUP_RENAMED, actor = alice, name = "Book Club")
        val parsed = MarmotSystemEvent.fromAppEvent(row.toAppEvent(alice, 1700000000L))!!
        assertEquals("Book Club", parsed.name)
    }

    /**
     * The registry grows. Protocol processing MUST NOT reject an otherwise-valid
     * app payload just because its semantics are unfamiliar — the payload still
     * decodes; only the row interpretation is declined.
     */
    @Test
    fun anUnknownSystemTypeIsDeclinedNotRejected() {
        val content = "{\"v\":1,\"system_type\":\"something_new\",\"text\":\"?\",\"data\":{}}"
        val event = MarmotAppEvent.build(alice, MarmotAppEvent.KIND_SYSTEM, content, 1700000000L)
        assertEquals(event.id, MarmotAppEvent.decode(event.toJson()).id)
        assertNull(MarmotSystemEvent.fromAppEvent(event))
    }
}
