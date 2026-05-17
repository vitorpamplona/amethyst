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

/**
 * Validates [TaprootSigHash] against the BIP-341 `wallet-test-vectors.json`
 * `keyPathSpending` cases — all seven spendable inputs, covering every base
 * sighash type and both ANYONECANPAY variants.
 */
class TaprootSigHashTest {
    private val unsignedTxHex =
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

    // utxosSpent from the vector, in input order.
    private val spentOutputs =
        listOf(
            TxOut(420_000_000L, "512053a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343".hexToByteArray()),
            TxOut(462_000_000L, "5120147c9c57132f6e7ecddba9800bb0c4449251c92a1e60371ee77557b6620f3ea3".hexToByteArray()),
            TxOut(294_000_000L, "76a914751e76e8199196d454941c45d1b3a323f1433bd688ac".hexToByteArray()),
            TxOut(504_000_000L, "5120e4d810fd50586274face62b8a807eb9719cef49c04177cc6b76a9a4251d5450e".hexToByteArray()),
            TxOut(630_000_000L, "512091b64d5324723a985170e4dc5a0f84c041804f2cd12660fa5dec09fc21783605".hexToByteArray()),
            TxOut(378_000_000L, "00147dd65592d0ab2fe0d0257d571abf032cd9db93dc".hexToByteArray()),
            TxOut(672_000_000L, "512075169f4001aa68f15bbed28b218df1d0a62cbbcf1188c6665110c293c907b831".hexToByteArray()),
            TxOut(546_000_000L, "5120712447206d7a5238acc7ff53fbe94a3b64539ad291c7cdbc490b7577e4b17df5".hexToByteArray()),
            TxOut(588_000_000L, "512077e30a5522dd9f894c3f8b8bd4c4b2cf82ca7da8a3ea6a239655c39c050ab220".hexToByteArray()),
        )

    private val tx = BitcoinTransaction.parse(unsignedTxHex)

    private fun check(
        inputIndex: Int,
        hashType: Int,
        expected: String,
    ) {
        val sigHash = TaprootSigHash.compute(tx, inputIndex, spentOutputs, hashType)
        assertEquals(expected, sigHash.toHexKey(), "sighash mismatch for input $inputIndex hashType $hashType")
    }

    @Test
    fun input4_sighashDefault() = check(4, 0x00, "4f900a0bae3f1446fd48490c2958b5a023228f01661cda3496a11da502a7f7ef")

    @Test
    fun input3_sighashAll() = check(3, 0x01, "bf013ea93474aa67815b1b6cc441d23b64fa310911d991e713cd34c7f5d46669")

    @Test
    fun input6_sighashNone() = check(6, 0x02, "15f25c298eb5cdc7eb1d638dd2d45c97c4c59dcaec6679cfc16ad84f30876b85")

    @Test
    fun input0_sighashSingle() = check(0, 0x03, "2514a6272f85cfa0f45eb907fcb0d121b808ed37c6ea160a5a9046ed5526d555")

    @Test
    fun input8_sighashAllAnyoneCanPay() = check(8, 0x81, "cccb739eca6c13a8a89e6e5cd317ffe55669bbda23f2fd37b0f18755e008edd2")

    @Test
    fun input7_sighashNoneAnyoneCanPay() = check(7, 0x82, "cd292de50313804dabe4685e83f923d2969577191a3e1d2882220dca88cbeb10")

    @Test
    fun input1_sighashSingleAnyoneCanPay() = check(1, 0x83, "325a644af47e8a5a2591cda0ab0723978537318f10e6a63d4eed783b96a71a4d")
}
