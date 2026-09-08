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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `foundation/key-packages.md`: "a transport publication is unambiguously the
 * framed `MLSMessage`, not a bare `KeyPackage` struct."
 *
 * We published bare bytes, and it cost us every invitation. A reader expecting
 * the envelope reads a bare KeyPackage's leading `0x0001 0x0001` as
 * version 1 / wire format 1 (`mls_public_message`), parses on as a
 * PublicMessage, and dies several fields later on a byte that means nothing —
 * MDK reported `UnknownValue(112)`, a number that appears nowhere in a
 * KeyPackage. A four-byte envelope is not a detail when its absence is
 * indistinguishable from a different message type.
 */
class KeyPackageFramingTest {
    private fun aKeyPackage() =
        MlsGroup
            .create(identity = ByteArray(32) { 0x0a })
            .createKeyPackage(identity = ByteArray(32) { 0x0b }, signingKey = ByteArray(32) { 1 })
            .keyPackage

    @Test
    fun framesAsAnMlsMessageWithTheKeyPackageWireFormat() {
        val kp = aKeyPackage()
        val framed = KeyPackageUtils.frameKeyPackage(kp)

        assertEquals(MlsMessage.MLS_VERSION_10, ((framed[0].toInt() and 0xFF) shl 8) or (framed[1].toInt() and 0xFF))
        assertEquals(WireFormat.KEY_PACKAGE.value, ((framed[2].toInt() and 0xFF) shl 8) or (framed[3].toInt() and 0xFF))
        assertEquals(kp.toTlsBytes().size + 4, framed.size)
    }

    @Test
    fun decodesItsOwnFraming() {
        val kp = aKeyPackage()
        val decoded = KeyPackageUtils.decodeKeyPackage(KeyPackageUtils.frameKeyPackage(kp))
        assertContentEquals(kp.toTlsBytes(), decoded.toTlsBytes())
    }

    /**
     * Bare bytes still decode. Every KeyPackage this client published before
     * the fix is bare and still inside its publication lifetime; refusing them
     * would leave our own users unable to invite each other until all of them
     * rotated.
     */
    @Test
    fun stillDecodesTheBareLegacyForm() {
        val kp = aKeyPackage()
        val decoded = KeyPackageUtils.decodeKeyPackage(kp.toTlsBytes())
        assertContentEquals(kp.toTlsBytes(), decoded.toTlsBytes())
    }

    /**
     * The two forms are told apart by the envelope, not by trial and error: a
     * bare KeyPackage's second field is its ciphersuite, which is never 5.
     */
    @Test
    fun tellsTheFormsApartByTheEnvelopeNotByGuessing() {
        val kp = aKeyPackage()
        val bare = kp.toTlsBytes()
        assertEquals(MlsMessage.MLS_VERSION_10, ((bare[0].toInt() and 0xFF) shl 8) or (bare[1].toInt() and 0xFF))
        val suite = ((bare[2].toInt() and 0xFF) shl 8) or (bare[3].toInt() and 0xFF)
        assertTrue(suite != WireFormat.KEY_PACKAGE.value, "a real ciphersuite must not collide with the wire format")
    }

    /**
     * `KeyPackageRef` is computed over the INNER KeyPackage, never the
     * envelope — framing it too would make our `i` tag disagree with every
     * other implementation's.
     */
    @Test
    fun theReferenceIsOverTheInnerKeyPackage() {
        val kp = aKeyPackage()
        val framed = KeyPackageUtils.frameKeyPackage(kp)
        assertContentEquals(kp.reference(), KeyPackageUtils.decodeKeyPackage(framed).reference())
    }
}
