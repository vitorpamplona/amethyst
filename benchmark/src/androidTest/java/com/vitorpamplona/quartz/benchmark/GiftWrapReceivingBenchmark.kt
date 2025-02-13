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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.verifyId
import com.vitorpamplona.quartz.nip01Core.verifySignature
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.messages.changeSubject
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
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
class GiftWrapReceivingBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    fun createWrap(
        sender: NostrSigner,
        receiver: NostrSigner,
    ): GiftWrapEvent {
        val countDownLatch = CountDownLatch(1)
        var wrap: GiftWrapEvent? = null

        sender.sign(
            ChatMessageEvent.build(
                msg = "Hi there! This is a test message",
                to =
                    listOf(
                        PTag(receiver.pubKey),
                    ),
            ) {
                changeSubject("Party Tonight")
                zapraiser(10000)
                contentWarning("nsfw")
            },
        ) {
            SealedRumorEvent.create(
                event = it,
                encryptTo = receiver.pubKey,
                signer = sender,
            ) {
                GiftWrapEvent.create(
                    event = it,
                    recipientPubKey = receiver.pubKey,
                ) {
                    wrap = it
                    countDownLatch.countDown()
                }
            }
        }

        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        return wrap!!
    }

    fun createSeal(
        sender: NostrSigner,
        receiver: NostrSigner,
    ): SealedRumorEvent {
        val countDownLatch = CountDownLatch(1)
        var seal: SealedRumorEvent? = null

        sender.sign(
            ChatMessageEvent.build(
                msg = "Hi there! This is a test message",
                to =
                    listOf(
                        PTag(receiver.pubKey),
                    ),
            ) {
                changeSubject("Party Tonight")
                zapraiser(10000)
                contentWarning("nsfw")
            },
        ) {
            SealedRumorEvent.create(
                event = it,
                encryptTo = receiver.pubKey,
                signer = sender,
            ) {
                seal = it
                countDownLatch.countDown()
            }
        }

        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        return seal!!
    }

    @Test
    fun parseWrapFromString() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val str = createWrap(sender, receiver).toJson()

        benchmarkRule.measureRepeated { Event.fromJson(str) }
    }

    @Test
    fun checkId() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val wrap = createWrap(sender, receiver)

        benchmarkRule.measureRepeated { wrap.verifyId() }
    }

    @Test
    fun checkSignature() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val wrap = createWrap(sender, receiver)

        benchmarkRule.measureRepeated { wrap.verifySignature() }
    }

    @Test
    fun decryptWrapEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val wrap = createWrap(sender, receiver)

        benchmarkRule.measureRepeated {
            assertNotNull(
                CryptoUtils.decryptNIP44(
                    wrap.content,
                    receiver.keyPair.privKey!!,
                    wrap.pubKey.hexToByteArray(),
                ),
            )
        }
    }

    @Test
    fun parseWrappedEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val wrap = createWrap(sender, receiver)

        val innerJson =
            CryptoUtils.decryptNIP44(
                wrap.content,
                receiver.keyPair.privKey!!,
                wrap.pubKey.hexToByteArray(),
            )

        benchmarkRule.measureRepeated { assertNotNull(innerJson?.let { Event.fromJson(it) }) }
    }

    @Test
    fun decryptSealedEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val seal = createSeal(sender, receiver)

        benchmarkRule.measureRepeated {
            assertNotNull(
                CryptoUtils.decryptNIP44(
                    seal.content,
                    receiver.keyPair.privKey!!,
                    seal.pubKey.hexToByteArray(),
                ),
            )
        }
    }

    @Test
    fun parseSealedEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val seal = createSeal(sender, receiver)

        val innerJson =
            CryptoUtils.decryptNIP44(
                seal.content,
                receiver.keyPair.privKey!!,
                seal.pubKey.hexToByteArray(),
            )

        benchmarkRule.measureRepeated { assertNotNull(innerJson?.let { Rumor.fromJson(it) }) }
    }
}
