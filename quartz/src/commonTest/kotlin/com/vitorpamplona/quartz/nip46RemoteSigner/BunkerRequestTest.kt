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
package com.vitorpamplona.quartz.nip46RemoteSigner

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BunkerRequestTest {
    @Test
    fun testBunkerRequestDeSerialization() {
        val requestJson = """{"id":"123","method":"sign_event","params":["{\"created_at\":1234,\"kind\":1,\"tags\":[],\"content\":\"This is an unsigned event.\"}"]}"""
        val bunkerRequest = OptimizedJsonMapper.fromJsonTo<BunkerRequest>(requestJson)

        assertTrue(bunkerRequest is BunkerRequestSign)
        assertEquals(1, bunkerRequest.event.kind)
    }

    @Test
    fun testConnectWithoutMetadata() {
        val requestJson = """{"id":"1","method":"connect","params":["abc","mysecret","sign_event"]}"""
        val request = OptimizedJsonMapper.fromJsonTo<BunkerRequest>(requestJson)

        assertTrue(request is BunkerRequestConnect)
        assertEquals("abc", request.remoteKey)
        assertEquals("mysecret", request.secret)
        assertEquals("sign_event", request.permissions)
        assertNull(request.clientMetadata)
    }

    @Test
    fun testConnectWithClientMetadata() {
        val requestJson =
            """{"id":"1","method":"connect","params":["abc","mysecret","sign_event","{\"name\":\"Amethyst\",\"url\":\"https://amethyst.social\",\"image\":\"https://amethyst.social/logo.png\"}"]}"""
        val request = OptimizedJsonMapper.fromJsonTo<BunkerRequest>(requestJson)

        assertTrue(request is BunkerRequestConnect)
        assertEquals("Amethyst", request.clientMetadata?.name)
        assertEquals("https://amethyst.social", request.clientMetadata?.url)
        assertEquals("https://amethyst.social/logo.png", request.clientMetadata?.image)
    }

    @Test
    fun testConnectWithMalformedMetadataDegradesGracefully() {
        val requestJson = """{"id":"1","method":"connect","params":["abc","mysecret","sign_event","not-json"]}"""
        val request = OptimizedJsonMapper.fromJsonTo<BunkerRequest>(requestJson)

        assertTrue(request is BunkerRequestConnect)
        assertNull(request.clientMetadata)
    }

    @Test
    fun testConnectMetadataRoundTrip() {
        val original =
            BunkerRequestConnect(
                id = "42",
                remoteKey = "abc",
                secret = "mysecret",
                permissions = "sign_event",
                clientMetadata = BunkerClientMetadata(name = "Amethyst", url = "https://amethyst.social"),
            )

        assertEquals(4, original.params.size)

        val roundTripped = OptimizedJsonMapper.fromJsonTo<BunkerRequest>(OptimizedJsonMapper.toJson(original))
        assertTrue(roundTripped is BunkerRequestConnect)
        assertEquals("Amethyst", roundTripped.clientMetadata?.name)
        assertEquals("https://amethyst.social", roundTripped.clientMetadata?.url)
        assertNull(roundTripped.clientMetadata?.image)
    }

    @Test
    fun testConnectMetadataBackfillsEmptyOptionalParams() {
        val request =
            BunkerRequestConnect(
                remoteKey = "abc",
                clientMetadata = BunkerClientMetadata(name = "Amethyst"),
            )

        // Metadata MUST sit at index 3; the omitted secret/permissions are
        // back-filled with empty strings to hold the positions.
        assertEquals(4, request.params.size)
        assertEquals("abc", request.params[0])
        assertEquals("", request.params[1])
        assertEquals("", request.params[2])
        assertTrue(request.params[3].contains("Amethyst"))

        val roundTripped = OptimizedJsonMapper.fromJsonTo<BunkerRequest>(OptimizedJsonMapper.toJson(request))
        assertTrue(roundTripped is BunkerRequestConnect)
        assertNull(roundTripped.secret)
        assertNull(roundTripped.permissions)
        assertEquals("Amethyst", roundTripped.clientMetadata?.name)
    }

    @Test
    fun testConnectEmptyMetadataNotSerialized() {
        val request =
            BunkerRequestConnect(
                remoteKey = "abc",
                secret = "mysecret",
                permissions = "sign_event",
                clientMetadata = BunkerClientMetadata(),
            )

        // An all-null metadata object adds no 4th param.
        assertEquals(3, request.params.size)
    }
}
