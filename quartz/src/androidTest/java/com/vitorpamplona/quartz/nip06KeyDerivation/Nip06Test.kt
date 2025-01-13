/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip06KeyDerivation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.toHexKey
import fr.acinq.secp256k1.Secp256k1
import junit.framework.TestCase.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Nip06Test {
    val nip06 = Nip06(Secp256k1.get())

    // private key (hex): 7f7ff03d123792d6ac594bfa67bf6d0c0ab55b6b1fdb6249303fe861f1ccba9a
    // nsec: nsec10allq0gjx7fddtzef0ax00mdps9t2kmtrldkyjfs8l5xruwvh2dq0lhhkp
    private val menemonic0 = "leader monkey parrot ring guide accident before fence cannon height naive bean"

    // private key (hex): c15d739894c81a2fcfd3a2df85a0d2c0dbc47a280d092799f144d73d7ae78add
    // nsec: nsec1c9wh8xy5eqdzln7n5t0ctgxjcrdug73gp5yj0x03gntn67h83twssdfhel
    private val menemonic1 = "what bleak badge arrange retreat wolf trade produce cricket blur garlic valid proud rude strong choose busy staff weather area salt hollow arm fade"

    // private key (hex): c15d739894c81a2fcfd3a2df85a0d2c0dbc47a280d092799f144d73d7ae78add
    // nsec: nsec1c9wh8xy5eqdzln7n5t0ctgxjcrdug73gp5yj0x03gntn67h83twssdfhel
    private val snortTest = "clog remember sample endorse mountain key rib hurry question supreme palm future stage style swing faith erase thumb then warm truth mule vivid endless"

    @Test
    fun fromSeedNip06TestVector0() {
        val privateKeyHex = nip06.privateKeyFromMnemonic(menemonic0).toHexKey()
        assertEquals("7f7ff03d123792d6ac594bfa67bf6d0c0ab55b6b1fdb6249303fe861f1ccba9a", privateKeyHex)

        val privateKeyHex21 = nip06.privateKeyFromMnemonic(menemonic0, 21).toHexKey()
        assertEquals("576390ec69951fcfbf159f2aac0965bb2e6d7a07da2334992af3225c57eaefca", privateKeyHex21)
    }

    @Test
    fun fromSeedNip06TestVector1() {
        val privateKeyHex = nip06.privateKeyFromMnemonic(menemonic1).toHexKey()
        assertEquals("c15d739894c81a2fcfd3a2df85a0d2c0dbc47a280d092799f144d73d7ae78add", privateKeyHex)

        val privateKeyHex21 = nip06.privateKeyFromMnemonic(menemonic1, 42).toHexKey()
        assertEquals("ad993054383da74e955f8b86346365b5ffd6575992e1de3738dda9f94407052b", privateKeyHex21)
    }

    @Test
    @Ignore("Snort is not correctly implemented")
    fun fromSeedNip06FromSnort() {
        val privateKeyNsec = nip06.privateKeyFromMnemonic(snortTest).toHexKey()
        assertEquals("nsec1ppw9ltr2x9qwg9a2qnmgv98tfruy2ejnja7me76mwmsreu3s8u2sscj5nt", privateKeyNsec)
    }
}
