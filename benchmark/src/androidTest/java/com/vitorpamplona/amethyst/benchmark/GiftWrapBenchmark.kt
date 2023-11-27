package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.NIP24Factory
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will
 * output the result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class GiftWrapBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    fun basePerformanceTest(message: String, expectedLength: Int) {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        var events: NIP24Factory.Result? = null
        val countDownLatch = CountDownLatch(1)

        NIP24Factory().createMsgNIP24(
            message,
            listOf(receiver.pubKey),
            sender
        ) {
            events = it
            countDownLatch.countDown()
        }

        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        val countDownLatch2 = CountDownLatch(1)

        Assert.assertEquals(expectedLength, events!!.wraps.map { it.toJson() }.joinToString("").length)

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

        NIP24Factory().createMsgNIP24(
            message,
            listOf(receiver.pubKey),
            sender
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

            wrap.cachedGift(keyToUse) {seal ->
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
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hola, que tal?", 2946)
        }
    }

    @Test
    fun regularMessageHardCoded() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hi, honey, can you drop by the market and get some bread?", 3098)
        }
    }

    @Test
    fun longMessageHardCoded() {
        benchmarkRule.measureRepeated {
            basePerformanceTest(
                "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. ",
                3738
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
            "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. "
        )
    }


/*
    @Test
    fun tinyMessageHardCodedCompressed() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hola, que tal?", 2318)
        }
    }

    @Test
    fun regularMessageHardCodedCompressed() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hi, honey, can you drop by the market and get some bread?", 2406)
        }
    }

    @Test
    fun longMessageHardCodedCompressed() {
        benchmarkRule.measureRepeated {
            basePerformanceTest(
                "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. ",
                2722
            )
        }
    }*/

/*
    @Test
    fun tinyMessageJSONCompressed() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hola, que tal?", 2318)
        }
    }

    @Test
    fun regularMessageJSONCompressed() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hi, honey, can you drop by the market and get some bread?", 2394)
        }
    }

    @Test
    fun longMessageJSONCompressed() {
        benchmarkRule.measureRepeated {
            basePerformanceTest(
                "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. ",
                2714
            )
        }
    }*/

/*
    @Test
    fun tinyMessageJSON() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hola, que tal?", 3154)
        }
    }

    @Test
    fun regularMessageJSON() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hi, honey, can you drop by the market and get some bread?", 3298)
        }
    }

    @Test
    fun longMessageJSON() {
        benchmarkRule.measureRepeated {
            basePerformanceTest(
                "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. ",
                3938
            )
        }
    }*/

/*
    @Test
    fun tinyMessageJackson() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hola, que tal?", 3154)
        }
    }

    @Test
    fun regularMessageJackson() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hi, honey, can you drop by the market and get some bread?", 3298)
        }
    }

    @Test
    fun longMessageJackson() {
        benchmarkRule.measureRepeated {
            basePerformanceTest(
                "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. ",
                3938
            )
        }
    } */
/*
    @Test
    fun tinyMessageKotlin() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hola, que tal?", 3154)
        }
    }

    @Test
    fun regularMessageKotlin() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hi, honey, can you drop by the market and get some bread?", 3298)
        }
    }

    @Test
    fun longMessageKotlin() {
        benchmarkRule.measureRepeated {
            basePerformanceTest(
                "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. ",
                3938
            )
        }
    }*/
/*
    @Test
    fun tinyMessageCSV() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hola, que tal?", 2960)
        }
    }

    @Test
    fun regularMessageCSV() {
        benchmarkRule.measureRepeated {
            basePerformanceTest("Hi, honey, can you drop by the market and get some bread?", 3112)
        }
    }

    @Test
    fun longMessageCSV() {
        benchmarkRule.measureRepeated {
            basePerformanceTest(
                "My queen, you are nothing short of royalty to me. You possess more beauty in the nail of your pinkie toe than everything else in this world combined. I am astounded by your grace, generosity, and graciousness. I am so lucky to know you. ",
                3752
            )
        }
    }*/
}