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
package com.vitorpamplona.quartz.buzz.plPushLease

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushLeaseEventTest {
    private val author = NostrSignerInternal(KeyPair())

    // Sign generically and wrap, so the test does not depend on EventFactory registration.
    private suspend fun sealLease(
        descriptor: PushLeaseDescriptor,
        installationId: String,
        expiration: Long,
        executorKeyId: String,
    ): PushLeaseEvent {
        val ciphertext = author.nip44Encrypt(descriptor.encodeToJson(), author.pubKey)
        val t = PushLeaseEvent.build(ciphertext, installationId, expiration, executorKeyId)
        val signed: Event = author.sign(t.createdAt, t.kind, t.tags, t.content)
        return PushLeaseEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, signed.sig)
    }

    @Test
    fun encryptedRoundTripForAuthor() =
        runTest {
            val descriptor =
                PushLeaseDescriptor(
                    v = 1,
                    origin = "wss://relay.example",
                    generation = 7,
                    active = true,
                    appProfile = "com.example.app",
                    transport = "webpush",
                    endpoint = "https://push.example/endpoint/abc",
                    subscriptions =
                        listOf(
                            PushSubscription(
                                filter = Json.parseToJsonElement("""{"kinds":[7,9],"#p":["deadbeef"]}""").jsonObject,
                                className = "mentions",
                                ignore = listOf(Json.parseToJsonElement("""{"kinds":[1]}""").jsonObject),
                                suppress = PushSuppress(pTagsMax = 10),
                            ),
                        ),
                )

            val event = sealLease(descriptor, "installation-42", 1_720_000_000L, "gateway-key-1")

            assertEquals(30350, event.kind)
            assertEquals("installation-42", event.installationId())
            assertEquals(1_720_000_000L, event.expiration())
            assertEquals("gateway-key-1", event.executorKeyId())
            assertTrue(event.content.isNotEmpty(), "content must be ciphertext")
            assertTrue(event.tags.any { it[0] == "alt" }, "alt tag must be present")

            val decrypted = event.decrypt(author)
            assertEquals(descriptor, decrypted)
            assertEquals("com.example.app", decrypted.appProfile)
            assertEquals("mentions", decrypted.subscriptions?.first()?.className)
            assertEquals(
                10L,
                decrypted.subscriptions
                    ?.first()
                    ?.suppress
                    ?.pTagsMax,
            )
        }

    @Test
    fun appProfileSerializesAsSnakeCase() {
        val json = PushLeaseDescriptor(v = 1, origin = "wss://r", generation = 0, active = false, appProfile = "x").encodeToJson()
        assertTrue(json.contains("\"app_profile\""), "must use snake_case wire name app_profile")
    }

    @Test
    fun classFieldSerializesAsClass() {
        val sub = PushSubscription(filter = Json.parseToJsonElement("{}").jsonObject, className = "dm")
        val json = Json.encodeToString(PushSubscription.serializer(), sub)
        assertTrue(json.contains("\"class\""), "must use wire name class")
    }
}
