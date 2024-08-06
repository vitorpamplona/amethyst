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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.Nip01Serializer
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventFactory
import com.vitorpamplona.quartz.utils.TimeUtils
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will output the
 * result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class EventBenchmark {
    val payload1 =
        "[\"EVENT\",\"40b9\",{\"id\":\"48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf\"," +
            "\"kind\":1,\"pubkey\":\"3d842afecd5e293f28b6627933704a3fb8ce153aa91d790ab11f6a752d44a42d\"," +
            "\"created_at\":1677940007,\"content\":" +
            "\"I got asked about follower count again today. Why does my follower count go down when " +
            "I delete public relays (in our list) and replace them with filter.nostr.wine? \\n\\nI’ll " +
            "give you one final explanation to rule them all. First, let’s go over how clients calculate " +
            "your follower count.\\n\\n1. Your client sends a request to all your connected relays asking " +
            "for accounts who follow you\\n2. Relays answer back with the events requested\\n3. The client " +
            "aggregates the event total and displays it\\n\\nEach relay has a set limit on how many stored " +
            "events it will return per request. For some relays it’s 500, others 1000, some as high as 5000. " +
            "Let’s say for simplicity that all your public relays use 500 as their limit. If you ask 10 " +
            "relays for your followers the max possible answer you can get is 5000. That won’t change if " +
            "you have 20,000 followers or 100,000. You may get back a “different” 5000 each time, but you’ll " +
            "still cap out at 5000 because that is the most events your client will receive.\u2028\u2028Our " +
            "limit on filter.nostr.wine is 2000 events. If you replace 10 public relays with only " +
            "filter.nostr.wine, the MOST followers you will ever get back from our filter relay is 2000. " +
            "That doesn’t mean you only have 2000 followers or that your reach is reduced in any way.\\n\\nAs " +
            "long as you are writing to and reading from the same public relays, neither your reach nor any " +
            "content was lost. That concludes my TED talk. I hope you all have a fantastic day and weekend.\"," +
            "\"tags\":[],\"sig\":\"dcaf8ab98bb9179017b35bd814092850d1062b26c263dff89fb1ae8c019a324139d1729012d" +
            "9d05ff0a517f76b1117d869b2cc7d36bea8aa5f4b94c5e2548aa8\"}]"

    val payload2 =
        """
    {
  "content": "Astral:\n\nhttps://void.cat/d/A5Fba5B1bcxwEmeyoD9nBs.webp\n\nIris:\n\nhttps://void.cat/d/44hTcVvhRps6xYYs99QsqA.webp\n\nSnort:\n\nhttps://void.cat/d/4nJD5TRePuQChM5tzteYbU.webp\n\nAmethyst agrees with Astral which I suspect are both wrong. nostr:npub13sx6fp3pxq5rl70x0kyfmunyzaa9pzt5utltjm0p8xqyafndv95q3saapa nostr:npub1v0lxxxxutpvrelsksy8cdhgfux9l6a42hsj2qzquu2zk7vc9qnkszrqj49 nostr:npub1g53mukxnjkcmr94fhryzkqutdz2ukq4ks0gvy5af25rgmwsl4ngq43drvk nostr:npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z ",
  "created_at": 1683596206,
  "id": "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758",
  "kind": 1,
  "pubkey": "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d",
  "sig": "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce",
  "tags": [
    [
      "e",
      "27ac621d7dc4a932e1a79f984308e7d20656dd6fddb2ce9cdfcb6a67b9a7bcc3",
      "",
      "root"
    ],
    [
      "e",
      "be7245af96210a0dd048cab4ad38e52dbd6c09a53ea21a7edb6be8898e5727cc",
      "",
      "reply"
    ],
    [
      "p",
      "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"
    ],
    [
      "p",
      "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"
    ],
    [
      "p",
      "3f770d65d3a764a9c5cb503ae123e62ec7598ad035d836e2a810f3877a745b24"
    ],
    [
      "p",
      "ec4d241c334311b3a304433ee3442be29d0e88e7ec19b85edf2bba29b93565e2"
    ],
    [
      "p",
      "0fe0b18b4dbf0e0aa40fcd47209b2a49b3431fc453b460efcf45ca0bd16bd6ac"
    ],
    [
      "p",
      "8c0da4862130283ff9e67d889df264177a508974e2feb96de139804ea66d6168"
    ],
    [
      "p",
      "63fe6318dc58583cfe16810f86dd09e18bfd76aabc24a0081ce2856f330504ed"
    ],
    [
      "p",
      "4523be58d395b1b196a9b8c82b038b6895cb02b683d0c253a955068dba1facd0"
    ],
    [
      "p",
      "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    ]
  ],
  "seenOn": [
    "wss://nostr.wine/"
  ]
}
"""

    @get:Rule val benchmarkRule = BenchmarkRule()

    @Test
    fun parseREQString() {
        benchmarkRule.measureRepeated { Event.mapper.readTree(payload1) }
    }

    @Test
    fun parseEvent() {
        val msg = Event.mapper.readTree(payload1)

        benchmarkRule.measureRepeated { Event.fromJson(msg[2]) }
    }

    @Test
    fun checkSignature() {
        val msg = Event.mapper.readTree(payload1)
        val event = Event.fromJson(msg[2])
        benchmarkRule.measureRepeated {
            // Should pass
            assertTrue(event.hasVerifiedSignature())
        }
    }

    @Test
    fun checkIDHashPayload1() {
        val msg = Event.mapper.readTree(payload1)
        val event = Event.fromJson(msg[2])

        benchmarkRule.measureRepeated {
            // Should pass
            assertTrue(event.hasCorrectIDHash())
        }
    }

    @Test
    fun toMakeJsonForID() {
        val event = Event.fromJson(payload2)

        benchmarkRule.measureRepeated { assertNotNull(event.makeJsonForId()) }
    }

    @Test
    fun sha256() {
        val event = Event.fromJson(payload2)
        val byteArray = event.makeJsonForId().toByteArray()

        benchmarkRule.measureRepeated {
            // Should pass
            assertNotNull(CryptoUtils.sha256(byteArray))
        }
    }

    @Test
    fun checkIDHashPayload2Slow() {
        val event = Event.fromJson(payload2)
        benchmarkRule.measureRepeated {
            // Should pass
            assertTrue(event.hasCorrectIDHash())
        }
    }

    @Test
    fun checkIDHashPayload2Fast() {
        val event = Event.fromJson(payload2)
        benchmarkRule.measureRepeated {
            // Should pass
            assertTrue(event.hasCorrectIDHash2())
        }
    }

    @Test
    fun eventSerializerTest() {
        val event = Event.fromJson(payload2)

        benchmarkRule.measureRepeated {
            val mapper = Nip01Serializer.StringWriter()
            Nip01Serializer().serializeEventInto(event, mapper)
        }
    }

    val specialEncoders =
        "Test\b\bTest\n\nTest\t\tTest\u000c\u000cTest\r\rTest\\Test\\\\Test\"Test/Test//Test"

    @Test
    fun jsonStringEncoderJackson() {
        val jsonMapper = jacksonObjectMapper()
        benchmarkRule.measureRepeated {
            jsonMapper.writeValueAsString(specialEncoders)
        }
    }

    @Test
    fun jsonStringEncoderOurs() {
        val serializer = Nip01Serializer()
        benchmarkRule.measureRepeated {
            serializer.escapeStringInto(specialEncoders, Nip01Serializer.StringWriter())
        }
    }

    @Test
    fun jsonStringEncoderSha256Jackson() {
        val jsonMapper = jacksonObjectMapper()
        benchmarkRule.measureRepeated {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(jsonMapper.writeValueAsString(specialEncoders).toByteArray())
        }
    }

    @Test
    fun jsonStringEncoderSha256Ours() {
        val serializer = Nip01Serializer()
        benchmarkRule.measureRepeated {
            serializer.escapeStringInto(specialEncoders, Nip01Serializer.BufferedDigestWriter(MessageDigest.getInstance("SHA-256")))
        }
    }

    @Test
    fun eventFactoryPerformanceTest() {
        val now = TimeUtils.now()
        val tags = arrayOf(arrayOf(""))
        benchmarkRule.measureRepeated {
            EventFactory.create("id", "pubkey", now, 1, tags, "content", "sig")
        }
    }
}
