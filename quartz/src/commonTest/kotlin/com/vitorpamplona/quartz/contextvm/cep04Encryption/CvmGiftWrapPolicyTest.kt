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
package com.vitorpamplona.quartz.contextvm.cep04Encryption

import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.contextvm.core.CvmTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `CVM-4-*` and `CVM-19-*`: encryption policy and wrap-kind negotiation. */
class CvmGiftWrapPolicyTest {
    @Test
    fun `CVM-4-01 REQUIRED refuses to downgrade for a peer that cannot encrypt`() {
        // The whole reason this client does not use the permissive default:
        // a coordinator that never advertises encryption would otherwise get
        // plaintext JSON-RPC on public relays.
        val crypto = CvmGiftWrap(encryptionMode = EncryptionMode.REQUIRED)
        assertTrue(crypto.shouldEncrypt(peerSupportsEncryption = true))
        assertFailsWith<CvmEncryptionException> { crypto.shouldEncrypt(peerSupportsEncryption = false) }
    }

    @Test
    fun `CVM-4-02 OPTIONAL follows the peer and DISABLED never encrypts`() {
        val optional = CvmGiftWrap(encryptionMode = EncryptionMode.OPTIONAL)
        assertTrue(optional.shouldEncrypt(peerSupportsEncryption = true))
        assertFalse(optional.shouldEncrypt(peerSupportsEncryption = false))

        val disabled = CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED)
        assertFalse(disabled.shouldEncrypt(peerSupportsEncryption = true))
    }

    @Test
    fun `CVM-19-01 prefers the ephemeral wrap when the peer supports it`() {
        val crypto = CvmGiftWrap(giftWrapMode = GiftWrapMode.EPHEMERAL)
        assertEquals(CvmKinds.EPHEMERAL_GIFT_WRAP, crypto.negotiatedWrapKind(peerSupportsEphemeral = true))
    }

    @Test
    fun `CVM-19-02 falls back to the persistent wrap rather than refusing`() {
        // Preferring 21059 must never mean refusing to talk to a 1059-only
        // server: CEP-19 is additive, not a requirement.
        val crypto = CvmGiftWrap(giftWrapMode = GiftWrapMode.EPHEMERAL)
        assertEquals(CvmKinds.GIFT_WRAP, crypto.negotiatedWrapKind(peerSupportsEphemeral = false))
    }

    @Test
    fun `CVM-19-03 a persistent-mode client stays on 1059 regardless`() {
        val crypto = CvmGiftWrap(giftWrapMode = GiftWrapMode.PERSISTENT)
        assertEquals(CvmKinds.GIFT_WRAP, crypto.negotiatedWrapKind(peerSupportsEphemeral = true))
    }

    @Test
    fun `CVM-4-03 advertises the capability flags matching its own configuration`() {
        val ephemeral = CvmGiftWrap().capabilityTags().map { it[0] }
        assertTrue(ephemeral.contains(CvmTags.SUPPORT_ENCRYPTION))
        assertTrue(ephemeral.contains(CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL))

        val persistent = CvmGiftWrap(giftWrapMode = GiftWrapMode.PERSISTENT).capabilityTags().map { it[0] }
        assertTrue(persistent.contains(CvmTags.SUPPORT_ENCRYPTION))
        assertFalse(
            persistent.contains(CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL),
            "a persistent-mode client must not claim ephemeral support",
        )

        assertTrue(CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED).capabilityTags().isEmpty())
    }

    @Test
    fun `CVM-4-04 reads the peer's advertised encryption flags`() {
        val crypto = CvmGiftWrap()
        val tags = arrayOf(CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION))
        assertTrue(crypto.peerSupportsEncryption(tags))
        assertFalse(crypto.peerSupportsEphemeral(tags))

        val both = tags + arrayOf(CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL))
        assertTrue(crypto.peerSupportsEphemeral(both))
    }

    @Test
    fun `CVM-19-04 both wrap kinds are recognised on the way in`() {
        // A client subscribes to both because CEP-19's fallback means either may
        // arrive regardless of which it sends.
        assertTrue(CvmKinds.isGiftWrap(CvmKinds.GIFT_WRAP))
        assertTrue(CvmKinds.isGiftWrap(CvmKinds.EPHEMERAL_GIFT_WRAP))
        assertFalse(CvmKinds.isGiftWrap(CvmKinds.MESSAGE))
    }
}
