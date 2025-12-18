/**
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
package com.vitorpamplona.quartz.nip01Core.hints

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.bloom.MurmurHash3
import kotlin.test.Test
import kotlin.test.assertEquals

class MurMur3Test {
    abstract class Case<T>(
        val bytes: ByteArray,
        val seed: Int,
        val result: T,
    )

    class HexCase<T>(
        hex: HexKey,
        seed: Int,
        result: T,
    ) : Case<T>(hex.hexToByteArray(), seed, result)

    class StringCase<T>(
        str: String,
        seed: Int,
        result: T,
    ) : Case<T>(str.encodeToByteArray(), seed, result)

    val testCases =
        listOf(
            HexCase("9fd4e9a905ca9e1a3086fa4c0a1ed829dbf18c15ec05af95c76b78d3d2f5651b", 886838366, -525456393),
            HexCase("e6c8f70f0d35a983bfebd00e5f29787c009c52971cfb4ac3a49b534b256b59cc", 1717487548, 1605080838),
            HexCase("7f7113833feb31e877f193e2fc75a64e9c70252c3ae3c73373ff34430ae40ea6", 1275582690, 225480992),
            HexCase("61770be6ec9df0f490743318e796e28ae34609732b61d365947871532d77d697", 514559346, 1424957638),
            HexCase("375f46b4687ba3cd035db303fa294d943816e64ca6b3adcda2ae40e8ac9d91a0", 1898708424, 1730418066),
            HexCase("c67044cd1d07a2aeb92b7bec973b6feb8abb9197840c59c101cacaa992489d49", 294602161, -1944496371),
            HexCase("49db4bfcc4da62e38c4076843cdde1425570806f09f121f5e7f2507c5ee1db85", 910710684, 944243368),
            HexCase("c5e98a30dead5ade4900b26eabae3435cfcdb64ff5e55c99641915a0c6ee73fc", 1107230285, 1550302684),
            HexCase("b0ed2e7568e6b4e1d5e5bab46fde01149331b824e48a281798d7216dde8f5890", 1013875681, -1265544300),
            HexCase("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", 1674416787, -1821262025),
            // special cases
            HexCase("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", Int.MAX_VALUE, -422576759),
            HexCase("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", Int.MAX_VALUE + 1, 851385048),
            HexCase("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", Int.MIN_VALUE, 851385048),
            HexCase("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", 0, 1615518380),
            HexCase("fd", 1, 975430984),
            HexCase("00", 1, 0),
            HexCase("FF", 1, -797126820),
            HexCase("3033", 1, 1435178296),
            HexCase("0000", 1, -2047822809),
            HexCase("FFFF", 1, 1459517456),
            HexCase("3652a8", 1, 103723868),
            HexCase("000000", 1, 821347078),
            HexCase("FFFFFF", 1, -761438248),
            HexCase("00000000", 1, 2028806445),
            HexCase("FFFFFFFF", 1, 919009801),
            HexCase("", 0, 0),
            HexCase("", 1, 1364076727),
            StringCase("a", 0x9747b28c.toInt(), 0x7FA09EA6),
            StringCase("Hello, world!", 0x9747b28c.toInt(), 0x24884CBA),
            StringCase("The quick brown fox jumps over the lazy dog", 0x9747b28c.toInt(), 0x2FA826CD),
        )

    @Test
    fun testMurMur() {
        val hasher = MurmurHash3()
        testCases.forEach {
            assertEquals(
                it.result,
                hasher.hash(it.bytes, it.seed),
            )
        }
    }

    /**
     * Test the [MurmurHash3.hash128] algorithm.
     *
     *
     * Reference data is taken from the Python library `mmh3`.
     *
     * @see [mmh3](https://pypi.org/project/mmh3/)
     */
    @Test
    fun testApacheCodecCase() {
        val hasher = MurmurHash3()

        assertEquals(
            Pair(-1283037231234402493, 4008679871770363303),
            hasher.hash128x64("Test".encodeToByteArray(), 0),
        )

        assertEquals(
            Pair(367045018717440780, -6709042769114136948),
            hasher.hash128x64("aabbcc".hexToByteArray(), 0),
        )
    }

    /**
     * Test the [MurmurHash3.hash128] algorithm.
     *
     *
     * Reference data is taken from the Python library `mmh3`.
     *
     * @see [mmh3](https://pypi.org/project/mmh3/)
     */
    @Test
    fun testHash128() {
        val hasher = MurmurHash3()

        val testCases =
            listOf(
                HexCase("", 0, Pair(0L, 0L)),
                HexCase("2e", 0, Pair(-2808653841080383123L, -2531784594030660343L)),
                HexCase("2ef6", 0, Pair(-1284575471001240306L, -8226941173794461820L)),
                HexCase("2ef6f9", 0, Pair(1645529003294647142L, 4109127559758330427L)),
                HexCase("2ef6f9b8", 0, Pair(-4117979116203940765L, -8362902660322042742L)),
                HexCase("2ef6f9b8f7", 0, Pair(2559943399590596158L, 4738005461125350075L)),
                HexCase("2ef6f9b8f754", 0, Pair(-1651760031591552651L, -5386079254924224461L)),
                HexCase("2ef6f9b8f75463", 0, Pair(-6208043960690815609L, 7862371518025305074L)),
                HexCase("2ef6f9b8f7546390", 0, Pair(-5150023478423646337L, 8346305334874564507L)),
                HexCase("2ef6f9b8f75463903e", 0, Pair(7658274117911906792L, -4962914659382404165L)),
                HexCase("2ef6f9b8f75463903e4d", 0, Pair(1309458104226302269L, 570003296096149119L)),
                HexCase("2ef6f9b8f75463903e4dc3", 0, Pair(7440169453173347487L, -3489345781066813740L)),
                HexCase("2ef6f9b8f75463903e4dc3dc", 0, Pair(-5698784298612201352L, 3595618450161835420L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c", 0, Pair(-3822574792738072442L, 6878153771369862041L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14", 0, Pair(3705084673301918328L, 3202155281274291907L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c1496", 0, Pair(-6797166743928506931L, -4447271093653551597L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f", 0, Pair(5240533565589385084L, -5575481185288758327L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26", 0, Pair(-8467620131382649428L, -6450630367251114468L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f2628", 0, Pair(3632866961828686471L, -5957695976089313500L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287c", 0, Pair(-6450283648077271139L, -7908632714374518059L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfc", 0, Pair(226350826556351719L, 8225586794606475685L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb9", 0, Pair(-2382996224496980401L, 2188369078123678011L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c", 0, Pair(-1337544762358780825L, 7004253486151757299L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f", 0, Pair(2889033453638709716L, -4099509333153901374L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0d", 0, Pair(-8644950936809596954L, -5144522919639618331L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5", 0, Pair(-5628571865255520773L, -839021001655132087L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac", 0, Pair(-5226774667293212446L, -505255961194269502L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55", 0, Pair(1337107025517938142L, 3260952073019398505L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55c6", 0, Pair(9149852874328582511L, 1880188360994521535L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55c676", 0, Pair(-4035957988359881846L, -7709057850766490780L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55c6764a", 0, Pair(-3842593823306330815L, 3805147088291453755L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55c6764a6d", 0, Pair(4030161393619149616L, -2813603781312455238L)),
                HexCase("8faf156628ddab2a2773526a029b3720cc7bec23e15d955454ed22b0bec3b8d7cd70d4cf59e35528da1d47430b1b0ef4f21cfd31a11c1cfb251cd04ffb90c76f9cdf6213d33d78d563e89569585421c2dd70bc221fd2926fb2aa742310c50dd8c30a917fde0a8b7a9fbd14503fa26ab0532f3a6860d0952023ed0e530ea52363b7bc9b6a876b22f52d1172f90d8c75a5bf07a92d42093bdc9d258f1baa2b5de93176aedad1f70deba9e47bd904fcd8d257a855d090a2a6e5dee0b1e82b4bd5943462850a69329d996362c019de3b36c91858efffed25def06f67619e15f7de457fc6f2de00f3e90275e807b2edd4896ca796f203d9a400d2e6bca7de420cf1ff042e9d5f4515dbb7344ea26d0e43a7196bbc048b618e4062e8e16043c45c4e1eb097ebe7a92a8217eaf2149089a7aa1c5a335829c28e4e358f7896337e5dd33d721e684862dbfa5d94a540b7e1923b45bcc19a84b7448b227f47ae8dfeccc82388718d6aa064996d2b43c95618af3fe47ec4a022710ffb2ff5906e72e67305738e219cb9dc74304df4c0f5df544fd695b656d131b7bd83a955838201ba79eea24c758d1566613c0eff9ac19b90e48cb97ea9f9ee2a82d9657953e30d7458c1a0e36baeae51e6e45c00bdebb0f1744bde299c680e3c80a621e28ebafe116ba64b0b9167edfebe88031db6e50b17946a4a0d9656bf4c72be80334d87783c8791706b5d2b87c0ef75c07c2036ee286b56931b2aabc9c6076a7e71606210db0684e4bf22f79270e6f2eb73423c568e21c4311aaa2ed96cef1779fb67ef50a2e6c5d0d7546f885459f0caa567ef1e6eb8e543ae333d53b00212b0864de1485ccaab3b6937032fdc8b52b70f377d380cb11375330036fa38f412daec58eb0703e634bc74fc1783790ce4fa4c283d886bab3308ab416e9e34f318ccc08d23e5ea88c54e47881dbd36fa284a49dfe9a4f8ec0e47b19a71d6f18257695660b8ed88606928bfd7a93a987e2a3e716e9d9b9e8826e996975ea4d43ab2d1328892f4238392b15badd2191a8dcb1db8e64f00bb7dabb8f9eb931b8d43660a6d79a00c9c304212222e8fba67dedec0df927ff25880fbbdb5eccc2e276d0eaed67b7400183a92c6de538a901d37ec26e117b370acbceaac7aac94e11c75bf24792fd81e62200b9385defc87fa233ccb07723dc45412a4a9af3ea28d575a81adf79a419dda28393fa98b7253ee3b23b6f0b98add856974ae5487896d650aa02db5e6b43eca0da9645f27695472d3dedf9a3da94d880a174503f15da8d38d56f3e66634a2e6a23b07d82551edc1b531b7694ab0cb9861990573c7b8269c1779651c84c940def38f9304ce2d83c7e3475e4a5ee795433bf7c6a39dc1ec2850ad096739c30e4bc2a5a718846501e8d1959b", 0, Pair(4687904561145274318, -5540587635714528950)),
            )

        for (case in testCases) {
            assertEquals(
                case.result,
                hasher.hash128x64(case.bytes, case.seed.toLong()),
                "Case: ${case.bytes.toHexKey()}",
            )
        }
    }

    /**
     * Test the [MurmurHash3.hash128] algorithm.
     *
     *
     * Reference data is taken from the Python library `mmh3`.
     *
     * @see [mmh3](https://pypi.org/project/mmh3/)
     */
    @Test
    fun testHash128Half() {
        val hasher = MurmurHash3()

        val testCases =
            listOf(
                HexCase("", 0, Pair(0L, 0L)),
                HexCase("2e", 0, Pair(-2808653841080383123L, -2531784594030660343L)),
                HexCase("2ef6", 0, Pair(-1284575471001240306L, -8226941173794461820L)),
                HexCase("2ef6f9", 0, Pair(1645529003294647142L, 4109127559758330427L)),
                HexCase("2ef6f9b8", 0, Pair(-4117979116203940765L, -8362902660322042742L)),
                HexCase("2ef6f9b8f7", 0, Pair(2559943399590596158L, 4738005461125350075L)),
                HexCase("2ef6f9b8f754", 0, Pair(-1651760031591552651L, -5386079254924224461L)),
                HexCase("2ef6f9b8f75463", 0, Pair(-6208043960690815609L, 7862371518025305074L)),
                HexCase("2ef6f9b8f7546390", 0, Pair(-5150023478423646337L, 8346305334874564507L)),
                HexCase("2ef6f9b8f75463903e", 0, Pair(7658274117911906792L, -4962914659382404165L)),
                HexCase("2ef6f9b8f75463903e4d", 0, Pair(1309458104226302269L, 570003296096149119L)),
                HexCase("2ef6f9b8f75463903e4dc3", 0, Pair(7440169453173347487L, -3489345781066813740L)),
                HexCase("2ef6f9b8f75463903e4dc3dc", 0, Pair(-5698784298612201352L, 3595618450161835420L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c", 0, Pair(-3822574792738072442L, 6878153771369862041L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14", 0, Pair(3705084673301918328L, 3202155281274291907L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c1496", 0, Pair(-6797166743928506931L, -4447271093653551597L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f", 0, Pair(5240533565589385084L, -5575481185288758327L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26", 0, Pair(-8467620131382649428L, -6450630367251114468L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f2628", 0, Pair(3632866961828686471L, -5957695976089313500L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287c", 0, Pair(-6450283648077271139L, -7908632714374518059L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfc", 0, Pair(226350826556351719L, 8225586794606475685L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb9", 0, Pair(-2382996224496980401L, 2188369078123678011L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c", 0, Pair(-1337544762358780825L, 7004253486151757299L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f", 0, Pair(2889033453638709716L, -4099509333153901374L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0d", 0, Pair(-8644950936809596954L, -5144522919639618331L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5", 0, Pair(-5628571865255520773L, -839021001655132087L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac", 0, Pair(-5226774667293212446L, -505255961194269502L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55", 0, Pair(1337107025517938142L, 3260952073019398505L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55c6", 0, Pair(9149852874328582511L, 1880188360994521535L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55c676", 0, Pair(-4035957988359881846L, -7709057850766490780L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55c6764a", 0, Pair(-3842593823306330815L, 3805147088291453755L)),
                HexCase("2ef6f9b8f75463903e4dc3dc5c14969f26287cfcb91c3f0dd5ac55c6764a6d", 0, Pair(4030161393619149616L, -2813603781312455238L)),
                HexCase("8faf156628ddab2a2773526a029b3720cc7bec23e15d955454ed22b0bec3b8d7cd70d4cf59e35528da1d47430b1b0ef4f21cfd31a11c1cfb251cd04ffb90c76f9cdf6213d33d78d563e89569585421c2dd70bc221fd2926fb2aa742310c50dd8c30a917fde0a8b7a9fbd14503fa26ab0532f3a6860d0952023ed0e530ea52363b7bc9b6a876b22f52d1172f90d8c75a5bf07a92d42093bdc9d258f1baa2b5de93176aedad1f70deba9e47bd904fcd8d257a855d090a2a6e5dee0b1e82b4bd5943462850a69329d996362c019de3b36c91858efffed25def06f67619e15f7de457fc6f2de00f3e90275e807b2edd4896ca796f203d9a400d2e6bca7de420cf1ff042e9d5f4515dbb7344ea26d0e43a7196bbc048b618e4062e8e16043c45c4e1eb097ebe7a92a8217eaf2149089a7aa1c5a335829c28e4e358f7896337e5dd33d721e684862dbfa5d94a540b7e1923b45bcc19a84b7448b227f47ae8dfeccc82388718d6aa064996d2b43c95618af3fe47ec4a022710ffb2ff5906e72e67305738e219cb9dc74304df4c0f5df544fd695b656d131b7bd83a955838201ba79eea24c758d1566613c0eff9ac19b90e48cb97ea9f9ee2a82d9657953e30d7458c1a0e36baeae51e6e45c00bdebb0f1744bde299c680e3c80a621e28ebafe116ba64b0b9167edfebe88031db6e50b17946a4a0d9656bf4c72be80334d87783c8791706b5d2b87c0ef75c07c2036ee286b56931b2aabc9c6076a7e71606210db0684e4bf22f79270e6f2eb73423c568e21c4311aaa2ed96cef1779fb67ef50a2e6c5d0d7546f885459f0caa567ef1e6eb8e543ae333d53b00212b0864de1485ccaab3b6937032fdc8b52b70f377d380cb11375330036fa38f412daec58eb0703e634bc74fc1783790ce4fa4c283d886bab3308ab416e9e34f318ccc08d23e5ea88c54e47881dbd36fa284a49dfe9a4f8ec0e47b19a71d6f18257695660b8ed88606928bfd7a93a987e2a3e716e9d9b9e8826e996975ea4d43ab2d1328892f4238392b15badd2191a8dcb1db8e64f00bb7dabb8f9eb931b8d43660a6d79a00c9c304212222e8fba67dedec0df927ff25880fbbdb5eccc2e276d0eaed67b7400183a92c6de538a901d37ec26e117b370acbceaac7aac94e11c75bf24792fd81e62200b9385defc87fa233ccb07723dc45412a4a9af3ea28d575a81adf79a419dda28393fa98b7253ee3b23b6f0b98add856974ae5487896d650aa02db5e6b43eca0da9645f27695472d3dedf9a3da94d880a174503f15da8d38d56f3e66634a2e6a23b07d82551edc1b531b7694ab0cb9861990573c7b8269c1779651c84c940def38f9304ce2d83c7e3475e4a5ee795433bf7c6a39dc1ec2850ad096739c30e4bc2a5a718846501e8d1959b", 0, Pair(4687904561145274318, -5540587635714528950)),
            )

        for (case in testCases) {
            assertEquals(
                case.result.first,
                hasher.hash128x64Half(case.bytes, case.seed.toLong()),
                "Case: ${case.bytes.toHexKey()}",
            )
        }
    }
}
