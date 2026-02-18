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
package com.vitorpamplona.quartz.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verifyId
import com.vitorpamplona.quartz.nip01Core.crypto.verifySignature
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.messages.changeSubject
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    ): GiftWrapEvent =
        runBlocking {
            GiftWrapEvent.create(
                event =
                    SealedRumorEvent.create(
                        event =
                            sender.sign(
                                ChatMessageEvent.build(
                                    msg = "Hi there! This is a test message",
                                    to = listOf(PTag(receiver.pubKey)),
                                ) {
                                    changeSubject("Party Tonight")
                                    zapraiser(10000)
                                    contentWarning("nsfw")
                                },
                            ),
                        encryptTo = receiver.pubKey,
                        signer = sender,
                    ),
                recipientPubKey = receiver.pubKey,
            )
        }

    fun createSeal(
        sender: NostrSigner,
        receiver: NostrSigner,
    ): SealedRumorEvent =
        runBlocking {
            val msg =
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
                )

            SealedRumorEvent.create(
                event = msg,
                encryptTo = receiver.pubKey,
                signer = sender,
            )
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
                Nip44.decrypt(
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
            Nip44.decrypt(
                wrap.content,
                receiver.keyPair.privKey!!,
                wrap.pubKey.hexToByteArray(),
            )

        benchmarkRule.measureRepeated { assertNotNull(innerJson.let { Event.fromJson(it) }) }
    }

    @Test
    fun decryptSealedEvent() {
        val sender = NostrSignerInternal(KeyPair())
        val receiver = NostrSignerInternal(KeyPair())

        val seal = createSeal(sender, receiver)

        benchmarkRule.measureRepeated {
            assertNotNull(
                Nip44.decrypt(
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
            Nip44.decrypt(
                seal.content,
                receiver.keyPair.privKey!!,
                seal.pubKey.hexToByteArray(),
            )

        benchmarkRule.measureRepeated { assertNotNull(innerJson.let { Rumor.fromJson(it) }) }
    }
}
