/**
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
package com.vitorpamplona.quartz.nip22Comments

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.verify
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nip73ExternalIds.location.geohashedScope
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeolocatedComments {
    private val privateKey = "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561".hexToByteArray()
    private val signer = NostrSignerSync(KeyPair(privateKey))

    val event =
        CommentEvent(
            "fecb2ecf61a1433d417a784d10bd1e8ec19a916170a53ca8fb3a15fc666a6592",
            "f8ff11c7a7d3478355d3b4d174e5a473797a906ea4aa61aa9b6bc0652c1ea17a",
            1747753115,
            arrayOf(
                arrayOf("alt", "Reply to geo:drt3n"),
                arrayOf("I", "geo:drt3n"),
                arrayOf("I", "geo:drt3"),
                arrayOf("I", "geo:drt"),
                arrayOf("I", "geo:dr"),
                arrayOf("I", "geo:d"),
                arrayOf("K", "geo"),
                arrayOf("i", "geo:drt3n"),
                arrayOf("i", "geo:drt3"),
                arrayOf("i", "geo:drt"),
                arrayOf("i", "geo:dr"),
                arrayOf("i", "geo:d"),
                arrayOf("k", "geo"),
            ),
            "testing",
            "12070e663272f1227c639fb834eb2122fc7bb995f4c49e55ebb1dfe2135ef7347d44810bacd2e64fd26b8826fd47d2800ce6c3d3b579bb3afe39088ffd4faa60",
        )

    @Test
    fun verifyEvent() {
        assertTrue(event.verify())
    }

    @Test
    fun testScopes() {
        assertEquals("drt3n", event.geohashedScope())
        assertTrue(event.isTaggedScope("drt3n", GeohashId::match))
        assertTrue(event.isTaggedScope(GeohashId.toScope("drt3n")))
        assertFalse(event.isTaggedScope("drt3n"))
    }

    @Test
    fun testCreation() {
        val event =
            signer.sign(
                CommentEvent.replyExternalIdentity(
                    "Message",
                    GeohashId("drt3n"),
                ),
            )

        assertEquals("drt3n", event!!.geohashedScope())
        assertTrue(event.isTaggedScope(GeohashId.toScope("drt3n")))
        assertFalse(event.isTaggedScope("drt3n"))

        assertTrue(event.hasScopeKind(GeohashId.KIND))
        assertTrue(event.hasRootScopeKind(GeohashId.KIND))
        assertTrue(event.hasReplyScopeKind(GeohashId.KIND))
    }
}
