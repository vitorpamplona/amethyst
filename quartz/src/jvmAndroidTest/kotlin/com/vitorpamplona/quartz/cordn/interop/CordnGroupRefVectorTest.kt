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
package com.vitorpamplona.quartz.cordn.interop

import com.vitorpamplona.quartz.cordn.appGroupRef.CordnGroupRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * `cordn1…` refs against cordn's own bech32 codec.
 *
 * The strings below came out of `@cordn/core`'s `encodeGroupRef`, not out of
 * ours (see `quartz/tools/cordn-vector-gen`). A group ref is the one cordn
 * artifact a user copies and pastes by hand, so a divergence here is not a
 * protocol nuance — it is a link that works in one client and not the other.
 *
 * Both directions are checked: we must decode what they produce, and produce
 * byte-identical output for the same input. The second half is the one that
 * catches TLV ordering, since §3 lets a decoder accept any order and would hide
 * the difference.
 */
class CordnGroupRefVectorTest {
    private val vectors: JsonObject =
        Json
            .parseToJsonElement(
                checkNotNull(CordnGroupRefVectorTest::class.java.getResourceAsStream("/cordn/coordinator-contracts.json")) {
                    "missing cordn contract vectors"
                }.use { it.readBytes() }
                    .decodeToString(),
            ).jsonObject

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.content

    private fun JsonObject.toRef() =
        CordnGroupRef(
            gid = text("gid")!!,
            coordinatorPubKey = text("coordinatorPubkey"),
            relays = this["relays"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
        )

    @Test
    fun `we decode every ref cordn encodes`() {
        val cases = vectors["groupRefs"]!!.jsonArray.map { it.jsonObject }
        assertEquals(6, cases.size, "the vector file lost cases")

        cases.forEach { case ->
            val decoded = CordnGroupRef.decode(case.text("encoded")!!)
            assertEquals(case.toRef(), decoded, "decoding ${case.text("encoded")}")
        }
    }

    @Test
    fun `we encode byte-identically to cordn`() {
        vectors["groupRefs"]!!.jsonArray.map { it.jsonObject }.forEach { case ->
            assertEquals(
                case.text("encoded"),
                case.toRef().encode(),
                "encoding gid=${case.text("gid")}",
            )
        }
    }

    @Test
    fun `an all-uppercase ref decodes, as it does for cordn`() {
        // Bech32 forbids mixed case but permits uppercase, and their
        // `decodeGroupRef` accepts it even though their `isGroupRef` screen
        // does not. A ref pasted in caps has to keep working.
        val case = vectors["groupRefUppercase"]!!.jsonObject
        assertEquals(case.toRef(), CordnGroupRef.decode(case.text("encoded")!!))
    }

    @Test
    fun `we refuse everything cordn refuses`() {
        vectors["groupRefRejects"]!!.jsonArray.forEach {
            val bad = it.jsonPrimitive.content
            assertFails("must refuse ${bad.ifEmpty { "<empty>" }}") { CordnGroupRef.decode(bad) }
        }
    }

    @Test
    fun `a non-ASCII gid survives byte for byte`() {
        // §4.1: the coordinator keys its cursor space on these bytes, so any
        // normalisation on our side silently splits a group in two.
        val case = vectors["groupRefs"]!!.jsonArray.map { it.jsonObject }.last { it.text("gid")!!.any { c -> c.code > 127 } }
        val decoded = CordnGroupRef.decode(case.text("encoded")!!)
        assertEquals(case.text("gid"), decoded.gid)
        assertEquals(case.text("encoded"), decoded.encode())
    }
}
