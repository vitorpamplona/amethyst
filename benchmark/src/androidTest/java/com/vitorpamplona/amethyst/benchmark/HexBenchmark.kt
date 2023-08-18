package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will
 * output the result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class HexBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    val TestHex = "48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf"

    @Test
    fun hexDecodeOurs() {
        benchmarkRule.measureRepeated {
            com.vitorpamplona.quartz.encoders.Hex.decode(TestHex)
        }
    }

    @Test
    fun hexEncodeOurs() {
        val bytes = com.vitorpamplona.quartz.encoders.Hex.decode(TestHex)

        benchmarkRule.measureRepeated {
            assertEquals(TestHex, com.vitorpamplona.quartz.encoders.Hex.encode(bytes))
        }
    }

    @Test
    fun hexDecodeBaseSecp() {
        benchmarkRule.measureRepeated {
            fr.acinq.secp256k1.Hex.decode(TestHex)
        }
    }

    @Test
    fun hexEncodeBaseSecp() {
        val bytes = fr.acinq.secp256k1.Hex.decode(TestHex)

        benchmarkRule.measureRepeated {
            assertEquals(TestHex, fr.acinq.secp256k1.Hex.encode(bytes))
        }
    }
}