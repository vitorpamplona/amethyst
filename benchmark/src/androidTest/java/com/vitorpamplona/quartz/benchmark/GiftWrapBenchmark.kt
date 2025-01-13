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
package com.vitorpamplona.quartz.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.CryptoUtils
import com.vitorpamplona.quartz.nip01Core.KeyPair
import com.vitorpamplona.quartz.nip01Core.checkSignature
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip59Giftwrap.GiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.SealedGossipEvent
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will output the
 * result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class GiftWrapBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    fun basePerformanceTest(
        message: String,
        expectedLength: Int,
    ) {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        var events: NIP17Factory.Result? = null
        val countDownLatch = CountDownLatch(1)

        NIP17Factory().createMsgNIP17(
            message,
            listOf(receiver.pubKey),
            sender,
        ) {
            events = it
            countDownLatch.countDown()
        }

        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        val countDownLatch2 = CountDownLatch(1)

        Assert.assertEquals(
            expectedLength,
            events!!
                .wraps
                .sumOf { it.toJson().length },
        )

        // Simulate Receiver
        events!!.wraps.forEach {
            it.checkSignature()

            val keyToUse = if (it.recipientPubKey() == sender.pubKey) sender else receiver

            it.cachedGift(keyToUse) { event ->
                event.checkSignature()

                if (event is SealedGossipEvent) {
                    event.cachedGossip(keyToUse) { innerData ->
                        Assert.assertEquals(message, innerData.content)
                        countDownLatch2.countDown()
                    }
                } else {
                    Assert.fail("Wrong Event")
                }
            }
        }

        assertTrue(countDownLatch2.await(1, TimeUnit.SECONDS))
    }

    fun receivePerformanceTest(message: String) {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        var giftWrap: GiftWrapEvent? = null
        val countDownLatch = CountDownLatch(1)

        NIP17Factory().createMsgNIP17(
            message,
            listOf(receiver.pubKey),
            sender,
        ) {
            giftWrap = it.wraps.first()
            countDownLatch.countDown()
        }

        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        val keyToUse = if (giftWrap!!.recipientPubKey() == sender.pubKey) sender else receiver
        val giftWrapJson = giftWrap!!.toJson()

        // Simulate Receiver
        benchmarkRule.measureRepeated {
            CryptoUtils.clearCache()
            val counter = CountDownLatch(1)

            val wrap = Event.fromJson(giftWrapJson) as GiftWrapEvent
            wrap.checkSignature()

            wrap.cachedGift(keyToUse) { seal ->
                seal.checkSignature()

                if (seal is SealedGossipEvent) {
                    seal.cachedGossip(keyToUse) { innerData ->
                        Assert.assertEquals(message, innerData.content)
                        counter.countDown()
                    }
                } else {
                    Assert.fail("Wrong Event")
                }
            }

            TestCase.assertTrue(counter.await(1, TimeUnit.SECONDS))
        }
    }

    @Test
    fun tinyMessageHardCoded() {
        benchmarkRule.measureRepeated { basePerformanceTest("Hola, que tal?", 3402) }
    }

    @Test
    fun regularMessageHardCoded() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hi, honey, can you drop by the market and get some bread?", 3746)
        }
    }

    @Test
    fun longMessageHardCoded() {
        benchmarkRule.measureRepeated {
            basePerformanceTest(
                "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. ",
                5114,
            )
        }
    }

    @Test
    fun receivesTinyMessage() {
        receivePerformanceTest("Hola, que tal?")
    }

    @Test
    fun receivesRegularMessage() {
        receivePerformanceTest("Hi, honey, can you drop by the market and get some bread?")
    }

    @Test
    fun receivesLongMessageHardCoded() {
        receivePerformanceTest(
            "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. ",
        )
    }
}
