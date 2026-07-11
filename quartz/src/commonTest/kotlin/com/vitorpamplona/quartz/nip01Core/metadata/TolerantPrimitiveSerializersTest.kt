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
package com.vitorpamplona.quartz.nip01Core.metadata

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TolerantPrimitiveSerializersTest {
    private fun metaWith(content: String): MetadataEvent =
        EventFactory.create(
            id = "ed269c23907649461da4b0fe109eed689ed1a562d33873b97ed01496dd02b87c",
            pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
            createdAt = 1L,
            kind = MetadataEvent.KIND,
            tags = emptyArray(),
            content = content,
            sig = "00".repeat(64),
        ) as MetadataEvent

    /**
     * Regression for profiles seen in the wild that publish `"nip05":{}`
     * (an empty object where NIP-05 mandates a string). Before the tolerant
     * serializer this threw and [MetadataEvent.contactMetaData] returned null,
     * dropping the whole profile.
     */
    @Test
    fun objectNip05DoesNotDropTheProfile() {
        val meta =
            metaWith(
                """{"name":"alice","about":"welcome to follow me","picture":"https://example.com/a.jpg","nip05":{},"lud06":null,"lud16":null}""",
            ).contactMetaData()

        assertIs<UserMetadata>(meta, "profile must still parse despite the malformed nip05")
        assertEquals("alice", meta.name)
        assertEquals("welcome to follow me", meta.about)
        assertEquals("https://example.com/a.jpg", meta.picture)
        assertNull(meta.nip05, "non-string nip05 must be ignored, not fatal")
        assertNull(meta.lud06)
        assertNull(meta.lud16)
    }

    @Test
    fun otherNonStringValuesAreIgnoredFieldByField() {
        val meta =
            metaWith(
                """{"name":{"first":"A"},"display_name":["A"],"picture":null,"about":"bio","website":{}}""",
            ).contactMetaData()

        assertIs<UserMetadata>(meta)
        assertNull(meta.name)
        assertNull(meta.displayName)
        assertNull(meta.picture)
        assertNull(meta.website)
        assertEquals("bio", meta.about, "well-formed fields must survive the malformed ones")
    }

    /** Pre-existing lenient behavior: bare primitives still decode into string fields. */
    @Test
    fun numberAndBooleanPrimitivesStillDecodeIntoStringFields() {
        val meta = metaWith("""{"name":123,"about":true}""").contactMetaData()

        assertIs<UserMetadata>(meta)
        assertEquals("123", meta.name)
        assertEquals("true", meta.about)
    }

    @Test
    fun malformedBotFlagIsIgnored() {
        listOf(
            """{"name":"A","bot":{}}""",
            """{"name":"A","bot":[true]}""",
            """{"name":"A","bot":"maybe"}""",
            """{"name":"A","bot":null}""",
        ).forEach { json ->
            val meta = metaWith(json).contactMetaData()
            assertIs<UserMetadata>(meta, "profile must survive bot field in $json")
            assertEquals("A", meta.name)
            assertNull(meta.bot)
        }
    }

    @Test
    fun stringBotFlagStillDecodes() {
        val meta = metaWith("""{"name":"A","bot":"true"}""").contactMetaData()
        assertIs<UserMetadata>(meta)
        assertEquals(true, meta.bot)
    }

    /**
     * Content that is not JSON at all (some relays hand back things like
     * "Relay initialized") cannot be salvaged: contactMetaData must return
     * null without throwing.
     */
    @Test
    fun nonJsonContentReturnsNull() {
        assertNull(metaWith("Relay initialized").contactMetaData())
        assertNull(metaWith("").contactMetaData())
    }

    /** Dropped fields must not leak back into the serialized profile as nulls. */
    @Test
    fun droppedFieldsAreOmittedOnSerialization() {
        val meta = metaWith("""{"name":"A","nip05":{}}""").contactMetaData()
        assertIs<UserMetadata>(meta)
        val serialized = JsonMapper.toJson(meta)
        assertTrue("nip05" !in serialized, "a dropped nip05 should not be serialized back out: $serialized")
        assertTrue("\"name\":\"A\"" in serialized, "valid fields must round-trip: $serialized")
    }
}
