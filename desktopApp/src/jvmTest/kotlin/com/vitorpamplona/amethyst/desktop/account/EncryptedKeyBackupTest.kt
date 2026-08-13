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
package com.vitorpamplona.amethyst.desktop.account

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies the exact key-backup glue the UI uses: encode a generated key to an
 * `nsec`, decode it back to hex, NIP-49-encrypt it to `ncryptsec1…`, and prove
 * the encrypted blob decrypts back to the original key with the right password
 * (and fails with the wrong one). This is the "how do I restore my encrypted
 * backup" contract — the encrypt path is dead weight if it can't round-trip.
 */
class EncryptedKeyBackupTest {
    @Test
    fun encryptedBackupRoundTrips() {
        val keyPair = KeyPair()
        val nsec = keyPair.privKey!!.toNsec()
        val password = "correct horse battery staple"

        // Same calls the UI makes: nsec -> hex -> Nip49 encrypt.
        val hex = decodePrivateKeyAsHexOrNull(nsec)
        assertNotNull(hex, "nsec should decode to hex")

        val ncryptsec = Nip49().encrypt(hex, password)
        assertTrue(ncryptsec.startsWith("ncryptsec1"), "expected ncryptsec1 prefix, got: $ncryptsec")

        // Restore path: decrypt with the correct password recovers the original key.
        val decryptedHex = Nip49().decrypt(ncryptsec, password)
        assertEquals(hex, decryptedHex, "decrypt must recover the original private key")
        assertEquals(keyPair.pubKey.toHexKey(), KeyPair(privKey = decryptedHex.hexToByteArray()).pubKey.toHexKey())
    }

    @Test
    fun wrongPasswordFails() {
        val keyPair = KeyPair()
        val hex = decodePrivateKeyAsHexOrNull(keyPair.privKey!!.toNsec())!!
        val ncryptsec = Nip49().encrypt(hex, "the right password")

        assertFailsWith<Throwable> {
            Nip49().decrypt(ncryptsec, "the WRONG password")
        }
    }
}
