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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadStateTest {
    private val signer = NostrSignerInternal(KeyPair())

    @Test
    fun buildsOn30078WithReadStateCoordinateAndSelfEncryptedContent() =
        runTest {
            val slot = "0123456789abcdef0123456789abcdef"
            val content =
                ReadStateContent(
                    clientId = "phone-1",
                    contexts = mapOf("channel-a" to 1_700_000_000L, "channel-b" to 1_700_000_500L),
                )

            val event = ReadState.create(slot, content, signer)

            // Reuses NIP-78 kind:30078 rather than minting a competing subclass.
            assertEquals(AppSpecificDataEvent.KIND, event.kind)
            assertEquals("read-state:$slot", event.dTag())
            assertEquals(slot, event.readStateSlotId())
            assertTrue(ReadState.isReadState(event.tags), "must have one read-state d-tag and one t-tag")
            assertTrue(event.tags.any { it[0] == "t" && it[1] == "read-state" })

            // Content is NIP-44 self-encrypted, not plaintext JSON.
            assertTrue(event.content != content.encodeToJson())
            assertEquals(content, ReadState.decrypt(event, signer))
        }

    @Test
    fun ordinaryAppDataEventIsNotReadState() =
        runTest {
            val event = signer.sign(AppSpecificDataEvent.build("some-app", "{}"))
            assertEquals(null, event.readStateSlotId())
            assertTrue(!ReadState.isReadState(event.tags))
        }
}
