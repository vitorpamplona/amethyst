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
package com.vitorpamplona.quartz.nip60Cashu.seed

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip06KeyDerivation.Bip39Mnemonics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * NUT-13 test vectors verbatim from
 * https://github.com/cashubtc/nuts/blob/main/tests/13-tests.md
 * Identical inputs MUST produce identical outputs — these vectors are the
 * cross-wallet recovery contract.
 */
class CashuDeterministicTest {
    private val mnemonic = "half depart obvious quality work element tank gorilla view sugar picture humble"
    private val seed by lazy { Bip39Mnemonics.toSeed(mnemonic, passphrase = "") }

    // ============================================================
    // Version 1 keyset (00 prefix, 16 hex chars)
    // ============================================================

    private val v1KeysetId = "009a1f293253e41e"

    @Test
    fun v1Counter0() {
        assertEquals(
            "485875df74771877439ac06339e284c3acfcd9be7abf3bc20b516faeadfe77ae",
            CashuDeterministic.secretBytes(seed, v1KeysetId, 0L).toHexKey(),
        )
        assertEquals(
            "ad00d431add9c673e843d4c2bf9a778a5f402b985b8da2d5550bf39cda41d679",
            CashuDeterministic.blindingFactor(seed, v1KeysetId, 0L).toHexKey(),
        )
    }

    @Test
    fun v1Counter1() {
        assertEquals(
            "8f2b39e8e594a4056eb1e6dbb4b0c38ef13b1b2c751f64f810ec04ee35b77270",
            CashuDeterministic.secretBytes(seed, v1KeysetId, 1L).toHexKey(),
        )
        assertEquals(
            "967d5232515e10b81ff226ecf5a9e2e2aff92d66ebc3edf0987eb56357fd6248",
            CashuDeterministic.blindingFactor(seed, v1KeysetId, 1L).toHexKey(),
        )
    }

    @Test
    fun v1Counter2() {
        assertEquals(
            "bc628c79accd2364fd31511216a0fab62afd4a18ff77a20deded7b858c9860c8",
            CashuDeterministic.secretBytes(seed, v1KeysetId, 2L).toHexKey(),
        )
        assertEquals(
            "b20f47bb6ae083659f3aa986bfa0435c55c6d93f687d51a01f26862d9b9a4899",
            CashuDeterministic.blindingFactor(seed, v1KeysetId, 2L).toHexKey(),
        )
    }

    @Test
    fun v1Counter3() {
        assertEquals(
            "59284fd1650ea9fa17db2b3acf59ecd0f2d52ec3261dd4152785813ff27a33bf",
            CashuDeterministic.secretBytes(seed, v1KeysetId, 3L).toHexKey(),
        )
        assertEquals(
            "fb5fca398eb0b1deb955a2988b5ac77d32956155f1c002a373535211a2dfdc29",
            CashuDeterministic.blindingFactor(seed, v1KeysetId, 3L).toHexKey(),
        )
    }

    @Test
    fun v1Counter4() {
        assertEquals(
            "576c23393a8b31cc8da6688d9c9a96394ec74b40fdaf1f693a6bb84284334ea0",
            CashuDeterministic.secretBytes(seed, v1KeysetId, 4L).toHexKey(),
        )
        assertEquals(
            "5f09bfbfe27c439a597719321e061e2e40aad4a36768bb2bcc3de547c9644bf9",
            CashuDeterministic.blindingFactor(seed, v1KeysetId, 4L).toHexKey(),
        )
    }

    // ============================================================
    // Version 2 keyset (01 prefix, 33 bytes / 66 hex chars)
    // ============================================================

    private val v2KeysetId = "015ba18a8adcd02e715a58358eb618da4a4b3791151a4bee5e968bb88406ccf76a"

    @Test
    fun v2Counter0() {
        assertEquals(
            "db5561a07a6e6490f8dadeef5be4e92f7cebaecf2f245356b5b2a4ec40687298",
            CashuDeterministic.secretBytes(seed, v2KeysetId, 0L).toHexKey(),
        )
        assertEquals(
            "6d26181a3695e32e9f88b80f039ba1ae2ab5a200ad4ce9dbc72c6d3769f2b035",
            CashuDeterministic.blindingFactor(seed, v2KeysetId, 0L).toHexKey(),
        )
    }

    @Test
    fun v2Counter4() {
        assertEquals(
            "5e89fc5d30d0bf307ddf0a3ac34aa7a8ee3702169dafa3d3fe1d0cae70ecd5ef",
            CashuDeterministic.secretBytes(seed, v2KeysetId, 4L).toHexKey(),
        )
        assertEquals(
            "5550337312d223ba62e3f75cfe2ab70477b046d98e3e71804eade3956c7b98cf",
            CashuDeterministic.blindingFactor(seed, v2KeysetId, 4L).toHexKey(),
        )
    }

    // ============================================================
    // Cross-checks — sanity properties
    // ============================================================

    @Test
    fun secretAndBlindingDiffer() {
        // Leaf index 0 vs 1 must produce different outputs even for the same
        // (seed, keyset, counter) — otherwise the same value is used as both
        // the secret AND the blinding factor, a security disaster.
        assertNotEquals(
            CashuDeterministic.secretBytes(seed, v1KeysetId, 0L).toHexKey(),
            CashuDeterministic.blindingFactor(seed, v1KeysetId, 0L).toHexKey(),
        )
    }

    @Test
    fun counterAdvanceChangesOutput() {
        assertNotEquals(
            CashuDeterministic.secretBytes(seed, v1KeysetId, 0L).toHexKey(),
            CashuDeterministic.secretBytes(seed, v1KeysetId, 1L).toHexKey(),
        )
    }

    @Test
    fun differentKeysetsProduceDifferentSecrets() {
        assertNotEquals(
            CashuDeterministic.secretBytes(seed, v1KeysetId, 0L).toHexKey(),
            CashuDeterministic.secretBytes(seed, v2KeysetId, 0L).toHexKey(),
        )
    }

    @Test
    fun secretAsAsciiIsLowercaseHexOfBytes() {
        val bytes = CashuDeterministic.secretBytes(seed, v1KeysetId, 0L)
        val ascii = CashuDeterministic.secretAsAscii(seed, v1KeysetId, 0L)
        assertEquals(bytes.toHexKey(), ascii.decodeToString())
        // Belt-and-braces: result must be exactly 64 lowercase hex chars (32 bytes * 2).
        assertEquals(64, ascii.size)
    }

    // ============================================================
    // keysetIdToInt — the modular-reduction helper
    // ============================================================

    @Test
    fun keysetIdToIntZero() {
        assertEquals(0L, CashuDeterministic.keysetIdToInt("00"))
    }

    @Test
    fun keysetIdToIntSmall() {
        // 0x12 = 18
        assertEquals(18L, CashuDeterministic.keysetIdToInt("12"))
    }

    @Test
    fun keysetIdToIntFitsIn31Bits() {
        // All 0xFF bytes ⇒ value mod (2^31 - 1) is always < 2^31 - 1.
        val v1 = CashuDeterministic.keysetIdToInt("ffffffffffffffff")
        val v2 = CashuDeterministic.keysetIdToInt("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        // Strict less than 2^31 - 1.
        assertEquals(true, v1 in 0..0x7FFFFFFEL)
        assertEquals(true, v2 in 0..0x7FFFFFFEL)
    }
}
