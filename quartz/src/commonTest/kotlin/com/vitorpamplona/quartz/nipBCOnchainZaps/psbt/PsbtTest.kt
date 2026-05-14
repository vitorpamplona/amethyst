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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PsbtTest {
    private fun sampleTx() =
        BitcoinTransaction(
            version = 2L,
            inputs =
                listOf(
                    TxIn(OutPoint("a".repeat(64), 0L), sequence = 0xFFFFFFFFL),
                    TxIn(OutPoint("b".repeat(64), 3L), sequence = 0xFFFFFFFFL),
                ),
            outputs =
                listOf(
                    TxOut(25_000L, "5120${"c".repeat(64)}".hexToByteArray()),
                    TxOut(99_000L, "5120${"d".repeat(64)}".hexToByteArray()),
                ),
            lockTime = 0L,
        )

    @Test
    fun emptyPsbtSerializesToExpectedBytes() {
        // version 2, 0 inputs, 0 outputs, locktime 0 → 10-byte tx.
        val tx = BitcoinTransaction(2L, emptyList(), emptyList(), 0L)
        val psbt = Psbt.fromUnsignedTx(tx)
        // magic | keylen 01 | keytype 00 | valuelen 0a | <10-byte tx> | global separator 00
        assertEquals("70736274ff01000a0200000000000000000000", psbt.toHex())
    }

    @Test
    fun rejectsBadMagic() {
        assertFailsWith<PsbtParseException> {
            Psbt.parse("00112233445566778899")
        }
    }

    @Test
    fun roundTripsWithTypedFields() {
        val tx = sampleTx()
        val psbt = Psbt.fromUnsignedTx(tx)

        // Input 0: witness utxo + tap internal key + sighash type.
        psbt.setInputWitnessUtxo(0, TxOut(120_000L, "5120${"e".repeat(64)}".hexToByteArray()))
        psbt.setInputTapInternalKey(0, "f".repeat(64).hexToByteArray())
        psbt.setInputSighashType(0, TaprootSigHash.SIGHASH_DEFAULT)

        // Input 1: a different witness utxo.
        psbt.setInputWitnessUtxo(1, TxOut(80_000L, "0014${"1".repeat(40)}".hexToByteArray()))

        // Output 0: change-style tap internal key.
        psbt.setOutputTapInternalKey(0, "2".repeat(64).hexToByteArray())

        val reparsed = Psbt.parse(psbt.serialize())

        assertEquals(tx.txid(), reparsed.unsignedTx.txid())
        assertEquals(120_000L, reparsed.inputWitnessUtxo(0)!!.valueSats)
        assertEquals("f".repeat(64), reparsed.inputTapInternalKey(0)!!.toHexKey())
        assertEquals(TaprootSigHash.SIGHASH_DEFAULT, reparsed.inputSighashType(0))
        assertEquals(80_000L, reparsed.inputWitnessUtxo(1)!!.valueSats)
        assertNull(reparsed.inputTapInternalKey(1))
        assertNull(reparsed.inputTapKeySig(0))
        // Exact byte round-trip.
        assertEquals(psbt.toHex(), reparsed.toHex())
    }

    @Test
    fun keySigAccessorEnforcesLength() {
        val psbt = Psbt.fromUnsignedTx(sampleTx())
        assertFailsWith<IllegalArgumentException> {
            psbt.setInputTapKeySig(0, ByteArray(32))
        }
        psbt.setInputTapKeySig(0, ByteArray(64) { 0x07 })
        assertTrue(psbt.inputTapKeySig(0)!!.size == 64)
    }

    @Test
    fun preservesUnknownRecords() {
        val psbt = Psbt.fromUnsignedTx(sampleTx())
        // An unrecognized global keytype must survive a round-trip untouched.
        psbt.global.records.add(PsbtRecord(0x7E, ByteArray(0), byteArrayOf(0x01, 0x02, 0x03)))
        val reparsed = Psbt.parse(psbt.serialize())
        val unknown = reparsed.global.records.firstOrNull { it.keyType == 0x7E }
        assertTrue(unknown != null && unknown.value.contentEquals(byteArrayOf(0x01, 0x02, 0x03)))
    }
}
