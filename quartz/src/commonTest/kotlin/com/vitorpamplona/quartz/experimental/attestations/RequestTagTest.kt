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
package com.vitorpamplona.quartz.experimental.attestations

import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.RequestTag
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestTagTest {
    private val pk = "1".repeat(64)

    @Test
    fun aRequestTagPointsAtAnAttestationRequest() {
        val request = "${AttestationRequestEvent.KIND}:$pk:claim"
        val parsed: RequestTag? = RequestTag.parse(arrayOf("request", request))
        assertEquals(request, parsed?.toTag())
        assertEquals(request, RequestTag.parseAddressId(arrayOf("request", request)))
    }

    @Test
    fun anythingElseIsNotARequest() {
        assertNull(RequestTag.parse(arrayOf("request", "30023:$pk:post")))
    }

    @Test
    fun theRequestsPTagsAreItsAttestors() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        val event = AttestationRequestEvent("0".repeat(64), pk, 1, arrayOf(arrayOf("d", "x"), arrayOf("p", a), arrayOf("p", b)), "", "0".repeat(128))
        assertEquals(listOf(a, b), event.attestorPubKeys())
    }
}
