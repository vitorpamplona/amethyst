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
package com.vitorpamplona.quartz.nipB7Blossom

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlossomAuthorizationEventTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val hash = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"

    @Test
    fun uploadAuthHasRequiredTags() =
        runTest {
            val event = BlossomAuthorizationEvent.createUploadAuth(hash, 184292, "Uploading cat.png", signer)

            assertEquals(BlossomAuthorizationEvent.KIND, event.kind)
            assertEquals("upload", event.tags.first { it[0] == "t" }[1])
            assertEquals(hash, event.tags.first { it[0] == "x" }[1])
            assertEquals("184292", event.tags.first { it[0] == "size" }[1])
            // NIP-40 expiration must be in the future.
            val expiration = event.tags.first { it[0] == "expiration" }[1].toLong()
            assertTrue(expiration > event.createdAt)
        }

    @Test
    fun mediaAuthUsesMediaVerb() =
        runTest {
            val event = BlossomAuthorizationEvent.createMediaAuth(hash, 100, "Optimizing", signer)
            assertEquals("media", event.tags.first { it[0] == "t" }[1])
        }

    @Test
    fun serverScopeEmitsLowercaseBareDomainTags() =
        runTest {
            val event =
                BlossomAuthorizationEvent.createDeleteAuth(
                    hash,
                    "Delete blob",
                    signer,
                    servers = listOf("https://CDN.Example.com/", "https://blossom.band:443/upload"),
                )

            val serverTags = event.tags.filter { it[0] == "server" }.map { it[1] }
            assertEquals(listOf("cdn.example.com", "blossom.band"), serverTags)
        }

    @Test
    fun deduplicatesServerScopeByDomain() =
        runTest {
            val event =
                BlossomAuthorizationEvent.createUploadAuth(
                    hash,
                    1,
                    "Upload",
                    signer,
                    servers = listOf("https://cdn.example.com/a", "https://cdn.example.com/b"),
                )
            assertEquals(1, event.tags.count { it[0] == "server" })
        }

    @Test
    fun noServerScopeWhenListEmpty() =
        runTest {
            val event = BlossomAuthorizationEvent.createUploadAuth(hash, 1, "Upload", signer)
            assertTrue(event.tags.none { it[0] == "server" })
        }

    @Test
    fun authorizationHeaderIsNostrPrefixedBase64OfTheEvent() =
        runTest {
            val event = BlossomAuthorizationEvent.createListAuth(signer, "List blobs")
            val header = event.toAuthorizationHeader()

            assertTrue(header.startsWith(BlossomAuthorizationEvent.AUTH_HEADER_SCHEME))
            val token = header.removePrefix(BlossomAuthorizationEvent.AUTH_HEADER_SCHEME)
            val decoded = Base64.decode(token).decodeToString()
            assertEquals(event.toJson(), decoded)
        }

    /**
     * BUD-11 (draft) says base64url without padding, and 1.15.0 shipped exactly that —
     * and two uploads in three failed in the field. Deployed Blossom servers decode
     * with a STRICT standard decoder: padding is required and the url-safe alphabet
     * is rejected (probed 15 Sep 2026: unpadded and url-safe tokens both answer
     * "invalid base64 token"; padded standard gets past the decoder). Interop wins
     * over the draft. Several alt lengths so all three `length mod 3` cases are hit;
     * only some of them need padding, which is exactly why the field failure was
     * intermittent.
     */
    @Test
    fun authorizationTokenIsStandardPaddedBase64BecauseServersDecodeStrictly() =
        runTest {
            var sawPadding = false
            listOf("a", "List", "List blobs", "List all of the blobs", "List blobs \u00e1\u00e9\u00ed")
                .forEach { alt ->
                    val event = BlossomAuthorizationEvent.createListAuth(signer, alt)
                    val token = event.toAuthorizationHeader().removePrefix(BlossomAuthorizationEvent.AUTH_HEADER_SCHEME)

                    // kotlin.io.encoding.Base64 (default) is the strict standard decoder:
                    // it throws on missing padding and on `-`/`_`, like Go's StdEncoding.
                    assertEquals(event.toJson(), Base64.decode(token).decodeToString(), "strict standard decode for `$alt`")
                    assertTrue(token.none { it == '-' || it == '_' }, "url-safe alphabet must not appear for `$alt`, got: $token")
                    if (token.endsWith("=")) sawPadding = true
                }
            assertTrue(sawPadding, "at least one of these lengths needs padding; if none did, the encoder is still dropping it")
        }

    /**
     * BUD-11 lists `x` as optional for `GET /<sha256>`, and its Tag scoping rule
     * makes the omission load-bearing: "When `x` tags are present, the token is
     * only valid for operations on the specified blob hashes." A token cached
     * per host and reused across blobs must therefore carry no `x`.
     */
    @Test
    fun getAuthOmitsTheBlobScopeWhenNoHashIsGiven() =
        runTest {
            val event =
                BlossomAuthorizationEvent.createGetAuth(
                    hash = null,
                    alt = "Downloading media from cdn.example.com",
                    signer = signer,
                    servers = listOf("https://cdn.example.com"),
                )

            assertEquals("get", event.tags.first { it[0] == "t" }[1])
            assertTrue(event.tags.none { it[0] == "x" }, "a reusable get token must not be blob-scoped")
            assertEquals("cdn.example.com", event.tags.first { it[0] == "server" }[1])
        }

    @Test
    fun getAuthKeepsTheBlobScopeWhenAHashIsGiven() =
        runTest {
            val event = BlossomAuthorizationEvent.createGetAuth(hash, "Downloading one blob", signer)

            assertEquals("get", event.tags.first { it[0] == "t" }[1])
            assertEquals(hash, event.tags.first { it[0] == "x" }[1])
        }
}
