package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.encoders.Bech32
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BechBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun npubEncoding() {
        val myUser = Hex.decode("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
        benchmarkRule.measureRepeated {
            assertEquals("npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z",myUser.toNpub())
        }
    }

    @Test
    fun npubDecoding() {
        val myUser = "npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"
        val expected = listOf(70, 12, 37, -26, -126, -3, -89, -125, 43, 82, -47, -14, 45, 61, 34, -77, 23, 109, -105, 47, 96, -36, -36, 50, 18, -19, -116, -110, -17, -123, 6, 92).map { it.toByte() }
        benchmarkRule.measureRepeated {
            assertEquals(expected, myUser.bechToBytes().toList())
        }
    }

}