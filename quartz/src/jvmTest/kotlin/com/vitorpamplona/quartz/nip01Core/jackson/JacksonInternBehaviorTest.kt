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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

// Regression for security review 2026-04-24 §2.3 / Finding #5: the Jackson event/tag
// deserializers used to call String.intern() on attacker-controlled fields (id, pubKey,
// every tag value). Each interned string is permanently allocated in the JVM StringTable,
// so a hostile relay could grow process memory without bound by flooding events with
// unique ids / pubkeys / tag values.
//
// Post-fix: id, pubKey, and tag values (index >= 1) are stored as freshly-allocated
// Strings; only the small protocol-defined set of tag keys (index 0: "p", "e", "a", …)
// is still interned for hashmap performance.
class JacksonInternBehaviorTest {
    private val id = "0000000000000000000000000000000000000000000000000000000000000001"
    private val pubKey = "0000000000000000000000000000000000000000000000000000000000000002"
    private val sig = "0".repeat(128)

    private fun jsonWithTagValue(tagValue: String): String = """{"id":"$id","pubkey":"$pubKey","created_at":1,"kind":1,"tags":[["t","$tagValue"]],"content":"","sig":"$sig"}"""

    private fun parse(json: String): Event = JacksonMapper.mapper.readValue(json)

    @Test
    fun tagValuesAreNotInterned() {
        // A unique-per-test value that no other code path is likely to have placed in the StringTable.
        val unique = "anti-intern-tag-value-${System.nanoTime()}"
        val a = parse(jsonWithTagValue(unique))
        val b = parse(jsonWithTagValue(unique))

        assertEquals(unique, a.tags[0][1])
        assertEquals(unique, b.tags[0][1])
        // Without intern, two parses produce distinct String references.
        assertNotSame(
            a.tags[0][1],
            b.tags[0][1],
            "tag values must not be interned (would let hostile relays grow the StringTable without bound)",
        )
    }

    @Test
    fun idAndPubKeyAreNotInterned() {
        val a = parse(jsonWithTagValue("v"))
        val b = parse(jsonWithTagValue("v"))

        assertEquals(id, a.id)
        assertEquals(pubKey, a.pubKey)
        assertNotSame(a.id, b.id, "id must not be interned")
        assertNotSame(a.pubKey, b.pubKey, "pubKey must not be interned")
    }

    @Test
    fun tagKeysAreInterned() {
        // Use a tag key unique to this test so no class-load literal pre-interns it.
        val uniqueKey = "anti-intern-tag-key-${System.nanoTime()}"
        val json =
            """{"id":"$id","pubkey":"$pubKey","created_at":1,"kind":1,"tags":[["$uniqueKey","v1"]],"content":"","sig":"$sig"}"""
        val a = parse(json)
        val b = parse(json)

        assertEquals(uniqueKey, a.tags[0][0])
        // Tag keys remain interned for hashmap performance — finite protocol-defined set.
        assertSame(
            a.tags[0][0],
            b.tags[0][0],
            "tag keys (index 0) should still be interned",
        )
    }
}
