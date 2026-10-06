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
package com.vitorpamplona.quartz.nip90Dvms

import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryRequest.DvmUserDiscoveryRequestEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DvmAccessorsTest {
    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val bob = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eventA = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eventB = "b1791d7fc9ae3d38966568c257ffb3a02cbf8394cdb4805bc70f64fc3c0b6879"
    private val relay = "wss://relay.damus.io/"
    private val address = "30023:$alice:article"

    @Test
    fun requestsNameTheirServiceProviders() {
        val tags = arrayOf(arrayOf("p", "not-a-key"), arrayOf("p", alice, relay), arrayOf("p", bob))
        val request = DvmContentDiscoveryRequestEvent("00".repeat(32), eventA, 1, tags, "", "00".repeat(64))

        assertEquals(listOf(alice, bob), request.serviceProviders())
        assertEquals(alice, request.dvmPubKey())
        assertEquals(listOf(alice, bob), request.linkedPubKeys())
        assertEquals(alice, DvmUserDiscoveryRequestEvent("00".repeat(32), eventA, 1, tags, "", "00".repeat(64)).dvmPubKey())
    }

    @Test
    fun responsesNameTheJobRequestAndTheCustomer() {
        val status = DvmStatusEvent("00".repeat(32), alice, 1, arrayOf(arrayOf("status", "processing"), arrayOf("e", eventA, relay), arrayOf("p", bob)), "", "00".repeat(64))

        assertEquals(eventA, status.jobRequestId())
        assertEquals(bob, status.customer())
        assertEquals(listOf(eventA), status.linkedEventIds())
        assertEquals(listOf(bob), status.linkedPubKeys())
    }

    @Test
    fun aResponseWithoutTagsNamesNoJob() {
        val status = DvmStatusEvent("00".repeat(32), alice, 1, arrayOf(arrayOf("status", "error")), "", "00".repeat(64))

        assertNull(status.jobRequestId())
        assertNull(status.customer())
    }
}
