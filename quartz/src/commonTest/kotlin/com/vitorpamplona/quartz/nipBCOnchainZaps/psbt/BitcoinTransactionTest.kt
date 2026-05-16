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
package com.vitorpamplona.quartz.nipBCOnchainZaps.psbt

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BitcoinTransactionTest {
    // The Bitcoin genesis block coinbase transaction — a well-known legacy tx.
    private val genesisCoinbaseHex =
        "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff" +
            "4d04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72" +
            "206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73ffffffff" +
            "0100f2052a01000000434104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f" +
            "61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac00000000"
    private val genesisCoinbaseTxid =
        "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"

    // BIP-341 wallet-test-vectors keyPathSpending: the raw unsigned transaction.
    private val bip341UnsignedTxHex =
        "02000000097de20cbff686da83a54981d2b9bab3586f4ca7e48f57f5b55963115f3b334e9c0100000000" +
            "00000000d7b7cab57b1393ace2d064f4d4a2cb8af6def61273e127517d44759b6dafdd9900000000" +
            "00fffffffff8e1f583384333689228c5d28eac13366be082dc57441760d957275419a41842000000" +
            "0000fffffffff0689180aa63b30cb162a73c6d2a38b7eeda2a83ece74310fda0843ad604853b0100" +
            "000000feffffffaa5202bdf6d8ccd2ee0f0202afbbb7461d9264a25e5bfd3c5a52ee1239e0ba6c00" +
            "00000000feffffff956149bdc66faa968eb2be2d2faa29718acbfe3941215893a2a3446d32acd050" +
            "000000000000000000e664b9773b88c09c32cb70a2a3e4da0ced63b7ba3b22f848531bbb1d5d5f4c" +
            "94010000000000000000e9aa6b8e6c9de67619e6a3924ae25696bb7b694bb677a632a74ef7eadfd4" +
            "eabf0000000000ffffffffa778eb6a263dc090464cd125c466b5a99667720b1c110468831d058aa1" +
            "b82af10100000000ffffffff0200ca9a3b000000001976a91406afd46bcdfd22ef94ac122aa11f24" +
            "1244a37ecc88ac807840cb0000000020ac9a87f5594be208f8532db38cff670c450ed2fea8fcdefc" +
            "c9a663f78bab962b0065cd1d"

    @Test
    fun computesGenesisCoinbaseTxid() {
        val tx = BitcoinTransaction.parse(genesisCoinbaseHex)
        assertEquals(genesisCoinbaseTxid, tx.txid())
    }

    @Test
    fun genesisCoinbaseRoundTrips() {
        val tx = BitcoinTransaction.parse(genesisCoinbaseHex)
        assertEquals(genesisCoinbaseHex, tx.serialize().toHexKey())
        assertEquals(1, tx.inputs.size)
        assertEquals(1, tx.outputs.size)
        assertEquals(5_000_000_000L, tx.outputs[0].valueSats)
    }

    @Test
    fun parsesBip341UnsignedTransaction() {
        val tx = BitcoinTransaction.parse(bip341UnsignedTxHex)
        assertEquals(2L, tx.version)
        assertEquals(9, tx.inputs.size)
        assertEquals(2, tx.outputs.size)
        assertEquals(1_000_000_000L, tx.outputs[0].valueSats)
        assertEquals(3_410_000_000L, tx.outputs[1].valueSats)
        // No witness on the unsigned tx → legacy serialization round-trips exactly.
        assertEquals(bip341UnsignedTxHex, tx.serialize().toHexKey())
    }

    @Test
    fun segwitSerializationAddsMarkerFlagAndWitness() {
        val base = BitcoinTransaction.parse(bip341UnsignedTxHex)
        val withWitness =
            base.copy(
                inputs =
                    base.inputs.mapIndexed { i, input ->
                        if (i == 0) input.copy(witness = listOf(ByteArray(64) { 0x11 })) else input
                    },
            )
        val serialized = withWitness.serialize().toHexKey()
        // version(4 bytes = 8 hex) then segwit marker+flag 0001
        assertTrue(serialized.substring(8, 12) == "0001", "expected segwit marker+flag")
        // txid is witness-stripped, so it is unchanged by adding a witness.
        assertEquals(base.txid(), withWitness.txid())
    }

    @Test
    fun varIntRoundTrips() {
        val values = listOf(0L, 1L, 0xFCL, 0xFDL, 0xFFFFL, 0x10000L, 0xFFFFFFFFL, 0x100000000L)
        for (v in values) {
            val bytes = BitcoinWriter().writeVarInt(v).toByteArray()
            assertEquals(v, BitcoinReader(bytes).readVarInt(), "varint round-trip failed for $v")
        }
    }
}
