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
package com.vitorpamplona.quartz.utils.secp256k1

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Full BIP-340 test vectors from
 * https://github.com/bitcoin/bips/blob/master/bip-0340/test-vectors.csv
 */
class SchnorrTest {
    // ============================================================
    // BIP-340 signing tests (vectors with secret keys)
    // ============================================================

    @Test
    fun bip340Vector0Sign() {
        val sig =
            Secp256k1.signSchnorr(
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000003".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
            )
        assertEquals(
            "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca8215" +
                "25f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0",
            sig.toHexKey(),
        )
    }

    @Test
    fun bip340Vector0Verify() {
        assertTrue(
            Secp256k1.verifySchnorr(
                (
                    "E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA8215" +
                        "25F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0"
                ).hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
                "F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector1Sign() {
        val sig =
            Secp256k1.signSchnorr(
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray(),
            )
        assertEquals(
            "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de3341" +
                "8906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a",
            sig.toHexKey(),
        )
    }

    @Test
    fun bip340Vector1Verify() {
        assertTrue(
            Secp256k1.verifySchnorr(
                (
                    "6896BD60EEAE296DB48A229FF71DFE071BDE413E6D43F917DC8DCF8C78DE3341" +
                        "8906D11AC976ABCCB20B091292BFF4EA897EFCB639EA871CFA95F6DE339E4B0A"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector2Sign() {
        val sig =
            Secp256k1.signSchnorr(
                "7E2D58D8B3BCDF1ABADEC7829054F90DDA9805AAB56C77333024B9D0A508B75C".hexToByteArray(),
                "C90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B14E5C9".hexToByteArray(),
                "C87AA53824B4D7AE2EB035A2B5BBBCCC080E76CDC6D1692C4B0B62D798E6D906".hexToByteArray(),
            )
        assertEquals(
            "5831aaeed7b44bb74e5eab94ba9d4294c49bcf2a60728d8b4c200f50dd313c1b" +
                "ab745879a5ad954a72c45a91c3a51d3c7adea98d82f8481e0e1e03674a6f3fb7",
            sig.toHexKey(),
        )
    }

    @Test
    fun bip340Vector3Sign() {
        val sig =
            Secp256k1.signSchnorr(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF".hexToByteArray(),
                "0B432B2677937381AEF05BB02A66ECD012773062CF3FA2549E44F58ED2401710".hexToByteArray(),
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF".hexToByteArray(),
            )
        assertEquals(
            "7eb0509757e246f19449885651611cb965ecc1a187dd51b64fda1edc9637d5ec" +
                "97582b9cb13db3933705b32ba982af5af25fd78881ebb32771fc5922efc66ea3",
            sig.toHexKey(),
        )
    }

    @Test
    fun bip340Vector15Sign() {
        // Empty message
        val sig =
            Secp256k1.signSchnorr(
                ByteArray(0),
                "0340034003400340034003400340034003400340034003400340034003400340".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
            )
        assertEquals(
            "71535db165ecd9fbbc046e5ffaea61186bb6ad436732fccc25291a55895464cf" +
                "6069ce26bf03466228f19a3a62db8a649f2d560fac652827d1af0574e427ab63",
            sig.toHexKey(),
        )
    }

    @Test
    fun bip340Vector16Sign() {
        // 1-byte message
        val sig =
            Secp256k1.signSchnorr(
                "11".hexToByteArray(),
                "0340034003400340034003400340034003400340034003400340034003400340".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
            )
        assertEquals(
            "08a20a0afef64124649232e0693c583ab1b9934ae63b4c3511f3ae1134c6a303" +
                "ea3173bfea6683bd101fa5aa5dbc1996fe7cacfc5a577d33ec14564cec2bacbf",
            sig.toHexKey(),
        )
    }

    @Test
    fun bip340Vector17Sign() {
        // 17-byte message
        val sig =
            Secp256k1.signSchnorr(
                "0102030405060708090A0B0C0D0E0F1011".hexToByteArray(),
                "0340034003400340034003400340034003400340034003400340034003400340".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
            )
        assertEquals(
            "5130f39a4059b43bc7cac09a19ece52b5d8699d1a71e3c52da9afdb6b50ac370" +
                "c4a482b77bf960f8681540e25b6771ece1e5a37fd80e5a51897c5566a97ea5a5",
            sig.toHexKey(),
        )
    }

    @Test
    fun bip340Vector18Sign() {
        // 100-byte message
        val sig =
            Secp256k1.signSchnorr(
                (
                    "9999999999999999999999999999999999999999" +
                        "9999999999999999999999999999999999999999" +
                        "9999999999999999999999999999999999999999" +
                        "9999999999999999999999999999999999999999" +
                        "9999999999999999999999999999999999999999"
                ).hexToByteArray(),
                "0340034003400340034003400340034003400340034003400340034003400340".hexToByteArray(),
                "0000000000000000000000000000000000000000000000000000000000000000".hexToByteArray(),
            )
        assertEquals(
            "403b12b0d8555a344175ea7ec746566303321e5dbfa8be6f091635163eca79a8" +
                "585ed3e3170807e7c03b720fc54c7b23897fcba0e9d0b4a06894cfd249f22367",
            sig.toHexKey(),
        )
    }

    // ============================================================
    // BIP-340 verify-only tests (vectors 4-14)
    // ============================================================

    @Test
    fun bip340Vector4Verify() {
        assertTrue(
            Secp256k1.verifySchnorr(
                (
                    "00000000000000000000003B78CE563F89A0ED9414F5AA28AD0D96D6795F9C63" +
                        "76AFB1548AF603B3EB45C9F8207DEE1060CB71C04E80F593060B07D28308D7F4"
                ).hexToByteArray(),
                "4DF3C3F68FCC83B27E9D42C90431A72499F17875C81A599B566C9889B9696703".hexToByteArray(),
                "D69C3509BB99E412E68B0FE8544E72837DFA30746D8BE2AA65975F29D22DC7B9".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector5VerifyFail() {
        // public key not on the curve
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769" +
                        "69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "EEFDEA4CDB677750A420FEE807EACF21EB9898AE79B9768766E4FAA04A2D4A34".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector6VerifyFail() {
        // has_even_y(R) is false
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "FFF97BD5755EEEA420453A14355235D382F6472F8568A18B2F057A1460297556" +
                        "3CC27944640AC607CD107AE10923D9EF7A73C643E166BE5EBEAFA34B1AC553E2"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector7VerifyFail() {
        // negated message
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "1FA62E331EDBC21C394792D2AB1100A7B432B013DF3F6FF4F99FCB33E0E1515F" +
                        "28890B3EDB6E7189B630448B515CE4F8622A954CFE545735AAEA5134FCCDB2BD"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector8VerifyFail() {
        // negated s value
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769" +
                        "961764B3AA9B2FFCB6EF947B6887A226E8D7C93E00C5ED0C1834FF0D0C2E6DA6"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector9VerifyFail() {
        // sG - eP is infinite (x(inf) as 0)
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                        "123DDA8328AF9C23A94C1FEECFD123BA4FB73476F0D594DCB65C6425BD186051"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector10VerifyFail() {
        // sG - eP is infinite (x(inf) as 1)
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "0000000000000000000000000000000000000000000000000000000000000001" +
                        "7615FBAF5AE28864013C099742DEADB4DBA87F11AC6754F93780D5A1837CF197"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector11VerifyFail() {
        // sig[0:32] is not an X coordinate on the curve
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "4A298DACAE57395A15D0795DDBFD1DCB564DA82B0F269BC70A74F8220429BA1D" +
                        "69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector12VerifyFail() {
        // sig[0:32] is equal to field size
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F" +
                        "69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector13VerifyFail() {
        // sig[32:64] is equal to curve order
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769" +
                        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659".hexToByteArray(),
            ),
        )
    }

    @Test
    fun bip340Vector14VerifyFail() {
        // public key exceeds field size
        assertFalse(
            Secp256k1.verifySchnorr(
                (
                    "6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769" +
                        "69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B"
                ).hexToByteArray(),
                "243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89".hexToByteArray(),
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC30".hexToByteArray(),
            ),
        )
    }

    // ============================================================
    // Sign then verify round-trip
    // ============================================================

    @Test
    fun signVerifyRoundTrip() {
        val seckey =
            "f410f88bcec6cbfda04d6a273c7b1dd8bba144cd45b71e87109cfa11dd7ed561"
                .hexToByteArray()
        val msg = ByteArray(32) { 0x42 }
        val sig = Secp256k1.signSchnorr(msg, seckey, null)
        val pubkey = Secp256k1.pubkeyCreate(seckey)
        val compressed = Secp256k1.pubKeyCompress(pubkey)
        // x-only pubkey is compressed[1..33]
        val xonly = compressed.copyOfRange(1, 33)
        assertTrue(Secp256k1.verifySchnorr(sig, msg, xonly))
    }
}
