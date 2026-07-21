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
package com.vitorpamplona.quartz.buzz.erReminders

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventReminderEventTest {
    private val author = NostrSignerInternal(KeyPair())

    // Sign the template generically and wrap it, so the test does not depend on the
    // kind being registered in EventFactory (which central registration owns).
    private suspend fun sealReminder(
        payload: EventReminderPayload,
        identifier: String,
        notBefore: Long? = null,
        expiration: Long? = null,
    ): EventReminderEvent {
        val ciphertext = author.nip44Encrypt(payload.encodeToJson(), author.pubKey)
        val t = EventReminderEvent.build(ciphertext, identifier, notBefore, expiration)
        val signed: Event = author.sign(t.createdAt, t.kind, t.tags, t.content)
        return EventReminderEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, signed.sig)
    }

    @Test
    fun encryptedRoundTripForAuthor() =
        runTest {
            val payload =
                EventReminderPayload(
                    target =
                        ReminderTarget(
                            id = "a".repeat(64),
                            a = "30023:${"b".repeat(64)}:my-article",
                            relays = listOf("wss://relay.example"),
                            preview = "Read this later",
                        ),
                    status = "pending",
                    note = "private note",
                )

            val event = sealReminder(payload, "reminder-1234", notBefore = 1_717_000_000L, expiration = 1_720_000_000L)

            assertEquals(30300, event.kind)
            assertEquals("reminder-1234", event.dTag())
            assertEquals(1_717_000_000L, event.notBefore())
            assertEquals(1_720_000_000L, event.expiration())
            assertTrue(event.content.isNotEmpty(), "content must be ciphertext")
            assertTrue(event.tags.any { it[0] == "alt" }, "alt tag must be present")

            val decrypted = event.decrypt(author)
            assertEquals(payload, decrypted)
            assertEquals(ReminderStatus.PENDING, decrypted.statusOrNull())
            assertEquals("private note", decrypted.note)
            assertEquals("Read this later", decrypted.target?.preview)
        }

    @Test
    fun bookmarkOmitsNotBefore() =
        runTest {
            val payload = EventReminderPayload(status = "done", note = "saved item")
            val event = sealReminder(payload, "bookmark-1")

            assertNull(event.notBefore())
            assertEquals(ReminderStatus.DONE, event.decrypt(author).statusOrNull())
        }

    @Test
    fun unknownStatusDecodesToNull() {
        val payload = EventReminderPayload.decodeFromJson("""{"status":"snoozed","note":"x"}""")
        assertEquals("snoozed", payload.status)
        assertNull(payload.statusOrNull())
    }
}
