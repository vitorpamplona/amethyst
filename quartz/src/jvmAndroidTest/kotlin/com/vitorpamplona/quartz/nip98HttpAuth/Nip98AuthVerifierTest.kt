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
package com.vitorpamplona.quartz.nip98HttpAuth

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Nip98AuthVerifierTest {
    private val verifier = Nip98AuthVerifier(now = { 1_000L })

    private fun signedToken(
        url: String,
        method: String,
        body: ByteArray? = null,
        signer: NostrSignerSync = NostrSignerSync(KeyPair()),
        createdAt: Long = 1_000L,
    ): Pair<String, String> {
        val template = HTTPAuthorizationEvent.build(url = url, method = method, file = body, createdAt = createdAt)
        val signed = signer.sign(template)
        return signer.pubKey to signed.toAuthToken()
    }

    @Test
    fun verifiesAValidPostWithBody() =
        runBlocking {
            val body = "hello".encodeToByteArray()
            val (pubkey, header) = signedToken("http://x/", "POST", body)
            val r = verifier.verify(header, "POST", "http://x/", body)
            assertIs<Nip98AuthVerifier.Result.Verified>(r)
            assertEquals(pubkey, r.pubkey)
        }

    @Test
    fun missingHeaderReturnsMissing() {
        runBlocking {
            val r = verifier.verify(null, "POST", "http://x/", null)
            assertIs<Nip98AuthVerifier.Result.Missing>(r)
        }
    }

    @Test
    fun wrongSchemeIsMalformed() {
        runBlocking {
            val r = verifier.verify("Bearer abc", "POST", "http://x/", null)
            assertIs<Nip98AuthVerifier.Result.Malformed>(r)
            assertTrue(r.reason.contains("Nostr"))
        }
    }

    @Test
    fun urlMismatchIsMalformed() {
        runBlocking {
            val (_, header) = signedToken("http://x/", "POST")
            val r = verifier.verify(header, "POST", "http://y/", null)
            assertIs<Nip98AuthVerifier.Result.Malformed>(r)
            assertTrue(r.reason.contains("url mismatch"))
        }
    }

    @Test
    fun methodMismatchIsMalformed() {
        runBlocking {
            val (_, header) = signedToken("http://x/", "POST")
            val r = verifier.verify(header, "GET", "http://x/", null)
            assertIs<Nip98AuthVerifier.Result.Malformed>(r)
            assertTrue(r.reason.contains("method mismatch"))
        }
    }

    @Test
    fun payloadHashMismatchIsMalformed() {
        runBlocking {
            val (_, header) = signedToken("http://x/", "POST", "alpha".encodeToByteArray())
            val r = verifier.verify(header, "POST", "http://x/", "beta".encodeToByteArray())
            assertIs<Nip98AuthVerifier.Result.Malformed>(r)
            assertTrue(r.reason.contains("payload hash"))
        }
    }

    @Test
    fun staleCreatedAtIsMalformed() {
        runBlocking {
            // Verifier's clock is fixed at 1_000; sign a token created 5
            // minutes earlier — outside the 60s tolerance.
            val (_, header) = signedToken("http://x/", "POST", createdAt = 1_000L - 600)
            val r = verifier.verify(header, "POST", "http://x/", null)
            assertIs<Nip98AuthVerifier.Result.Malformed>(r)
            assertTrue(r.reason.contains("created_at"))
        }
    }

    @Test
    fun nonAuthEventKindIsMalformed() {
        runBlocking {
            // Build a kind-1 event by hand and shove it into the header — it
            // must be rejected because NIP-98 specifically uses kind 27235.
            val signer = NostrSignerSync(KeyPair())
            val template =
                com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
                    .build("not an auth event")
            val signed = signer.sign(template)
            val token =
                "Nostr " +
                    kotlin.io.encoding.Base64
                        .encode(signed.toJson().encodeToByteArray())
            val r = verifier.verify(token, "POST", "http://x/", null)
            assertIs<Nip98AuthVerifier.Result.Malformed>(r)
            assertTrue(r.reason.contains("kind"))
        }
    }

    @Test
    fun aTokenIsAcceptedOnce() {
        runBlocking {
            val body = "hello".encodeToByteArray()
            val (_, header) = signedToken("http://x/", "POST", body)
            assertIs<Nip98AuthVerifier.Result.Verified>(verifier.verify(header, "POST", "http://x/", body))
            val again = verifier.verify(header, "POST", "http://x/", body)
            assertIs<Nip98AuthVerifier.Result.Malformed>(again)
            assertTrue(again.reason.contains("replay"))
        }
    }

    @Test
    fun aFullCacheRefusesTheNewTokenAndStillRemembersTheOld() {
        runBlocking {
            val small = Nip98AuthVerifier(now = { 1_000L }, maxReplayEntries = 2)
            val tokens = (0..2).map { n -> "body $n".encodeToByteArray().let { it to signedToken("http://x/", "POST", it).second } }
            for ((body, header) in tokens.take(2)) assertIs<Nip98AuthVerifier.Result.Verified>(small.verify(header, "POST", "http://x/", body))
            val (body, header) = tokens[2]
            assertEquals(Nip98AuthVerifier.Result.Malformed(Nip98AuthVerifier.REPLAY_CACHE_FULL), small.verify(header, "POST", "http://x/", body))
            val (oldBody, oldHeader) = tokens[0]
            assertTrue((small.verify(oldHeader, "POST", "http://x/", oldBody) as Nip98AuthVerifier.Result.Malformed).reason.contains("replay"))
        }
    }

    @Test
    fun withoutReplayRejectionATokenVerifiesAgainButOnlyForItsBody() {
        runBlocking {
            val reusable = Nip98AuthVerifier(now = { 1_000L }, rejectReplays = false)
            val body = "hello".encodeToByteArray()
            val (_, header) = signedToken("http://x/", "POST", body)
            repeat(3) { assertIs<Nip98AuthVerifier.Result.Verified>(reusable.verify(header, "POST", "http://x/", body)) }
            assertIs<Nip98AuthVerifier.Result.Malformed>(reusable.verify(header, "POST", "http://x/", "other".encodeToByteArray()))
        }
    }
}
