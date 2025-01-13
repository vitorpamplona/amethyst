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
import androidx.test.platform.app.InstrumentationRegistry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.CryptoUtils
import com.vitorpamplona.quartz.nip01Core.toHexKey
import com.vitorpamplona.quartz.utils.Hex
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Bip39MnemonicsTest {
    private val tests =
        jacksonObjectMapper()
            .readTree(
                InstrumentationRegistry
                    .getInstrumentation()
                    .context.assets
                    .open("bip39.vectors.json"),
            )

    @Test
    fun toSeed() {
        val mnemonics = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val passphrase = ""
        val seed = Bip39Mnemonics.toSeed(mnemonics, passphrase)
        assertEquals(
            "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4",
            Hex.encode(seed),
        )
    }

    @Test
    fun referenceTests() {
        tests.get("english").map {
            val raw = it.get(0).asText()
            val mnemonics = it.get(1).asText()
            val seed = it.get(2).asText()
            assertEquals(Bip39Mnemonics.toMnemonics(Hex.decode(raw)).joinToString(" "), mnemonics)
            assertEquals(Hex.encode(Bip39Mnemonics.toSeed(Bip39Mnemonics.toMnemonics(Hex.decode(raw)), "TREZOR")), seed)
        }
    }

    @Test
    fun validateMnemonicsValid() {
        for (i in 0..99) {
            for (length in listOf(16, 20, 24, 28, 32, 36, 40)) {
                val mnemonics = Bip39Mnemonics.toMnemonics(CryptoUtils.random(length))
                Bip39Mnemonics.validate(mnemonics)
            }
        }
    }

    @Test
    fun testNostrMnemonics1() {
        assertEquals(
            "173b9c5f0d165502d08a4d122b2c9bf1e33e27806eac119713600a263c1241101dc55fb7cffb8f48a59b19a5ba65b037904f907bb8d08eb5bff8a17e85c2ee93",
            Bip39Mnemonics.toSeed("leader monkey parrot ring guide accident before fence cannon height naive bean", "").toHexKey(),
        )
    }

    @Test
    fun testNostrMnemonics2() {
        assertEquals(
            "5e2bd11b4d371f25098ed95ded029e2b9268cf188e6b764023bafbbd8fe843244fb72ca8f66c9378085d69fcb4d4224e709ffe071acafa7b7d5eb54b2905d553",
            Bip39Mnemonics.toSeed("what bleak badge arrange retreat wolf trade produce cricket blur garlic valid proud rude strong choose busy staff weather area salt hollow arm fade", "").toHexKey(),
        )
    }

    @Test
    fun testSnortMnemonics() {
        assertEquals(
            "b8fb0abca0a032d74b6a58def8ba51402810528d0a7bb9acec8fa1880e1003e13c4421b916cb4c8a861f2694b255010fdc91c2d1ac24c8ce51287ac65d1eb399",
            Bip39Mnemonics.toSeed("clog remember sample endorse mountain key rib hurry question supreme palm future stage style swing faith erase thumb then warm truth mule vivid endless", "").toHexKey(),
        )
    }

    @Test
    fun validateMnemonicsInvalid() {
        val invalidMnemonics =
            listOf(
                "",
                // one word missing
                "gravity machine north sort system female filter attitude volume fold club stay feature office ecology stable narrow",
                // one extra word
                "gravity machine north sort system female filter attitude volume fold club stay feature office ecology stable narrow fog fog",
                // wrong word
                "gravity machine north sort system female filter attitude volume fold club stay feature office ecology stable narrow fig",
            )
        invalidMnemonics.map {
            try {
                Bip39Mnemonics.validate(it)
                fail()
            } catch (e: Exception) {
                assertTrue(true)
            }
        }
    }
}
