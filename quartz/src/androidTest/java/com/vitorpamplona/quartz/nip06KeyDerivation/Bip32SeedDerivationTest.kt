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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Bip32SeedDerivationTest {
    val seedDerivation = Bip32SeedDerivation()

    val masterBitcoin =
        seedDerivation.generate(
            Bip39Mnemonics.toSeed("gun please vital unable phone catalog explain raise erosion zoo truly exist", ""),
        )

    val nostrMnemonic0 =
        seedDerivation.generate(
            Bip39Mnemonics.toSeed("leader monkey parrot ring guide accident before fence cannon height naive bean", ""),
        )

    val nostrMnemonic1 =
        seedDerivation.generate(
            Bip39Mnemonics.toSeed("what bleak badge arrange retreat wolf trade produce cricket blur garlic valid proud rude strong choose busy staff weather area salt hollow arm fade", ""),
        )

    @Test
    fun restoreBIP44Wallet() {
        val privateKey = seedDerivation.derivePrivateKey(masterBitcoin, KeyPath("m/44'/1'/0'"))
        assertEquals("50b3e7905c642309c8a8b73df5a49757a10f2bebb5804571b9db9004cce8a190", privateKey.toHexKey())
    }

    @Test
    fun restoreBIP49Wallet() {
        val privateKey = seedDerivation.derivePrivateKey(masterBitcoin, KeyPath("m/49'/1'/0'"))
        assertEquals("154c02c0b66899291a19012207642ba096a2d3ebf51baf153c9495976feb1b30", privateKey.toHexKey())
    }

    @Test
    fun restoreBIP84Wallet() {
        val privateKey = seedDerivation.derivePrivateKey(masterBitcoin, KeyPath("m/84'/1'/0'"))
        assertEquals("53e8c09a0e3ddcd8d68821c1e99e823966e99df91fb253e1f453a443ba543cb2", privateKey.toHexKey())
    }

    @Test
    fun testGenerateMasterKeyForNostrMnemonics0() {
        assertEquals(
            "dbbcc0e112894d1430d5bc348d1bd72e8ac339952702be1fe572de80fe1b7fcb",
            nostrMnemonic0.secretkeybytes.toHexKey(),
        )
    }

    @Test
    fun testGenerateMasterKeyForNostrMnemonics1() {
        assertEquals(
            "d58d40d5724435552fa442350b75e0ff95a19d990e908e3a516bcc88f780108f",
            nostrMnemonic1.secretkeybytes.toHexKey(),
        )
    }
}
