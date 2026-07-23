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
package com.vitorpamplona.quartz.buzz.audit

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditEntryEventTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val actor = NostrSignerInternal(KeyPair()).pubKey

    @Test
    fun buildsActionActorAndObject() =
        runTest {
            val detail = """{"reason":"policy"}"""
            val event =
                signer.sign(
                    AuditEntryEvent.build(
                        action = AuditAction.MEMBER_REMOVED,
                        detail = detail,
                        actorPubKey = actor,
                        objectId = "channel-42",
                    ),
                )

            assertEquals(48001, event.kind)
            assertEquals(AuditAction.MEMBER_REMOVED, event.action())
            assertEquals(actor, event.actor())
            assertEquals("channel-42", event.objectId())
            assertEquals(detail, event.detail())
        }

    @Test
    fun unknownActionStringRoundTrips() =
        runTest {
            val event = signer.sign(AuditEntryEvent.build(AuditAction.AUTH_SUCCESS, "{}"))
            assertEquals(AuditAction.AUTH_SUCCESS, event.action())
            assertEquals(AuditAction.UNKNOWN, AuditAction.fromWire("something_new"))
        }
}
