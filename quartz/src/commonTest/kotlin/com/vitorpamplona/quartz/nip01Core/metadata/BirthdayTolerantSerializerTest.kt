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

class BirthdayTolerantSerializerTest {
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
     * Regression for the Ditto / divine.video profile (npub1jvnpg4c…, "MK Fain")
     * whose `birthday` is the non-spec string "10-24". Before the tolerant
     * serializer this threw and [MetadataEvent.contactMetaData] returned null,
     * dropping the whole profile.
     */
    @Test
    fun stringBirthdayDoesNotDropTheProfile() {
        val meta =
            metaWith(
                """{"name":"MK Fain","about":"Team Soapbox","picture":"https://blossom.ditto.pub/x.jpg","nip05":"mk@ditto.pub","birthday":"10-24"}""",
            ).contactMetaData()

        assertIs<UserMetadata>(meta, "profile must still parse despite the malformed birthday")
        assertEquals("MK Fain", meta.name)
        assertEquals("https://blossom.ditto.pub/x.jpg", meta.picture)
        assertEquals("mk@ditto.pub", meta.nip05)
        assertNull(meta.birthday, "non-object birthday must be ignored, not fatal")
    }

    @Test
    fun otherNonObjectBirthdaysAreIgnored() {
        // number, array, and JSON null are all non-spec for `birthday`.
        listOf(
            """{"name":"A","birthday":1024}""",
            """{"name":"A","birthday":[10,24]}""",
            """{"name":"A","birthday":null}""",
        ).forEach { json ->
            val meta = metaWith(json).contactMetaData()
            assertIs<UserMetadata>(meta, "profile must survive birthday=$json")
            assertEquals("A", meta.name)
            assertNull(meta.birthday)
        }
    }

    /**
     * End-to-end check that a dropped birthday does not leak back into the
     * serialized profile. The omission itself is the decoder's default-null
     * suppression (encodeDefaults stays false on JsonMapper), not the serializer —
     * this just pins the real-world JsonMapper output.
     */
    @Test
    fun nullBirthdayIsOmittedOnSerialization() {
        val meta = metaWith("""{"name":"A","birthday":"10-24"}""").contactMetaData()
        assertIs<UserMetadata>(meta)
        val serialized = JsonMapper.toJson(meta)
        assertTrue("birthday" !in serialized, "a null birthday should not be serialized back out: $serialized")
    }
}
