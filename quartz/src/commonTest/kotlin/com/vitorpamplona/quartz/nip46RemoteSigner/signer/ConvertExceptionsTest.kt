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
package com.vitorpamplona.quartz.nip46RemoteSigner.signer

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConvertExceptionsTest {
    private val remote =
        NostrSignerRemote.fromBunkerUri(
            "bunker://${"a".repeat(64)}?relay=wss://r.com",
            NostrSignerInternal(KeyPair()),
            EmptyNostrClient,
        )

    @Test
    fun successfulReturnsBug() {
        val result = SignerResult.RequestAddressed.Successful(PingResult("pong"))
        val ex = remote.convertExceptions("Test", result)
        assertIs<IllegalStateException>(ex)
        assertTrue(ex.message!!.contains("bug"))
    }

    @Test
    fun rejectedReturnsManuallyUnauthorized() {
        val result = SignerResult.RequestAddressed.Rejected<PingResult>()
        val ex = remote.convertExceptions("Test", result)
        assertIs<SignerExceptions.ManuallyUnauthorizedException>(ex)
    }

    @Test
    fun timedOutReturnsTimedOutException() {
        val result = SignerResult.RequestAddressed.TimedOut<PingResult>()
        val ex = remote.convertExceptions("Test", result)
        assertIs<SignerExceptions.TimedOutException>(ex)
    }

    @Test
    fun couldNotPerformReturnsCouldNotPerformException() {
        val result = SignerResult.RequestAddressed.ReceivedButCouldNotPerform<PingResult>("custom msg")
        val ex = remote.convertExceptions("Test", result)
        assertIs<SignerExceptions.CouldNotPerformException>(ex)
        assertTrue(ex.message!!.contains("custom msg"))
    }

    @Test
    fun couldNotParseReturnsIllegalState() {
        val result = SignerResult.RequestAddressed.ReceivedButCouldNotParseEventFromResult<PingResult>("{bad}")
        val ex = remote.convertExceptions("Test", result)
        assertIs<IllegalStateException>(ex)
        assertTrue(ex.message!!.contains("{bad}"))
    }

    @Test
    fun couldNotVerifyReturnsIllegalState() {
        val event =
            Event(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1L,
                kind = 1,
                tags = emptyArray(),
                content = "",
                sig = "c".repeat(128),
            )
        val result = SignerResult.RequestAddressed.ReceivedButCouldNotVerifyResultingEvent<PingResult>(event)
        val ex = remote.convertExceptions("Test", result)
        assertIs<IllegalStateException>(ex)
        assertTrue(ex.message!!.contains("verify"))
    }
}
