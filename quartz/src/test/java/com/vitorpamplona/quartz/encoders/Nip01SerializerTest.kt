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
package com.vitorpamplona.quartz.encoders

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.crypto.nip01.EventHasher
import com.vitorpamplona.quartz.events.Event
import junit.framework.TestCase.assertEquals
import org.junit.Test

class Nip01SerializerTest {
    val specialEncoders =
        "Test\b\bTest\n\nTest\t\tTest\u000c\u000cTest\r\rTest\\Test\\\\Test\"Test/Test//Test"

    @Test()
    fun fastJsonEncoderTest() {
        val mapper = Nip01Serializer.StringWriter()
        val expected = jacksonObjectMapper().writeValueAsString(specialEncoders)

        Nip01Serializer().escapeStringInto(specialEncoders, mapper)
        val encoded = mapper.toString()
        assertEquals(expected, "\"" + encoded + "\"")
    }

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

    @Test()
    fun fastEventSerializerTest() {
        val event = Event.fromJson(payload2)
        val mapper = Nip01Serializer.StringWriter()

        Nip01Serializer().serializeEventInto(event, mapper)

        val encoded = mapper.toString()
        val eventJson = EventHasher.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)

        assertEquals(eventJson, encoded)
    }

    @Test()
    fun fastEventIdCheckTest() {
        val event = Event.fromJson(payload2)

        assertEquals("98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758", event.generateId())
    }

    val payload3 = """
        {
  "id": "6cccb576158965cf0f06fb4e476f85a02f0011ae783a4e905126a3db3871e43d",
  "pubkey": "ee6ea13ab9fe5c4a68eaf9b1a34fe014a66b40117c50ee2a614f4cda959b6e74",
  "created_at": 1698062466,
  "kind": 14,
  "tags": [
    [
      "p",
      "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    ]
  ],
  "content": "Oh yeah I hadn't seen this ",
  "sig": ""
}"""

    @Test()
    fun fastEventSerializerTestPayload3() {
        val event = Event.fromJson(payload3)

        val mapper = Nip01Serializer.StringWriter()

        Nip01Serializer().serializeEventInto(event, mapper)

        val encoded = mapper.toString()
        val eventJson = EventHasher.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)

        assertEquals(eventJson, encoded)
    }

    @Test()
    fun fastEventIdCheckTestPayload3() {
        val event = Event.fromJson(payload3)

        assertEquals("6cccb576158965cf0f06fb4e476f85a02f0011ae783a4e905126a3db3871e43d", event.generateId())
    }

    val payload4 = "{\"id\":\"5fd48fd3fb2890a00538067869306d788ff4331896360dc9c7e43d43e01b481b\",\"pubkey\":\"9770fb48aa3861dd393eb857e740f2df6f18e0ead43bad1d30c65e5c198200a6\",\"created_at\":1701673247,\"kind\":1,\"tags\":[[\"imeta\",\"url https://image.nostr.build/790b061d9661df88b06feb7448694cc421671da42217ca8381f02b4def63707f.jpg\",\"blurhash enF~?FRpNbkBjG.AkCbHfkafx_R+V^V_WVt9WBf6axoeoJf4bXWBaz\",\"dim 1536x2048\"],[\"imeta\",\"url https://video.nostr.build/3dd8562e8306c3128a72dee888c0fe587c2b24b8b643ae19b82010aaab95c37c.mp4\",\"blurhash e5A0~^S4R#W,j]~XR%s;o4j?s*M|t3t6ayxot9RiV[RjEH%3WBR%xb\",\"dim 720x1280\"],[\"imeta\",\"url https://image.nostr.build/9249f13cb8df68e00706f4f1434b8f0cd731b0515479df06353a0aef7a798619.jpg\",\"blurhash egFFpp\$LV?kCjs.TRiaej[fP9xIpoKf6fksls+WBbHj[xZn\$WWofjt\",\"dim 1920x3412\"],[\"t\",\"iceland\"],[\"r\",\"https://image.nostr.build/790b061d9661df88b06feb7448694cc421671da42217ca8381f02b4def63707f.jpg\"],[\"r\",\"https://video.nostr.build/3dd8562e8306c3128a72dee888c0fe587c2b24b8b643ae19b82010aaab95c37c.mp4\"],[\"r\",\"https://image.nostr.build/9249f13cb8df68e00706f4f1434b8f0cd731b0515479df06353a0aef7a798619.jpg\"]],\"content\":\"Icelandic calm to your heart \uD83D\uDC9A\uD83D\uDE0C\\n\\n(Memories from this summer)\\n\\n#Iceland , 2023 https://image.nostr.build/790b061d9661df88b06feb7448694cc421671da42217ca8381f02b4def63707f.jpg https://video.nostr.build/3dd8562e8306c3128a72dee888c0fe587c2b24b8b643ae19b82010aaab95c37c.mp4 https://image.nostr.build/9249f13cb8df68e00706f4f1434b8f0cd731b0515479df06353a0aef7a798619.jpg \",\"sig\":\"d6410be4b47bc97fca486eb619dd2507e7332bcd1049e405a047c90eedd2be46007c09d7702361b8442df78932e5da4055ee1c5fef08f938a4d39d828dc20957\"}"

    @Test()
    fun fastEventSerializerTestPayload4() {
        val event = Event.fromJson(payload4)

        val mapper = Nip01Serializer.StringWriter()

        Nip01Serializer().serializeEventInto(event, mapper)

        val encoded = mapper.toString()
        val eventJson = EventHasher.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)

        assertEquals(eventJson, encoded)
    }

    @Test()
    fun fastEventIdCheckTestPayload4() {
        val event = Event.fromJson(payload4)

        // assertEquals(event.generateId(), event.generateId2())
        assertEquals("5fd48fd3fb2890a00538067869306d788ff4331896360dc9c7e43d43e01b481b", event.generateId())
    }

    val payload5 = "{\"id\":\"d1f097d3d9fcfb00df0c8ab5469be6484b14707d1e947c574ed636281d8dfd26\",\"pubkey\":\"dd664d5e4016433a8cd69f005ae1480804351789b59de5af06276de65633d319\",\"created_at\":1706435280,\"kind\":4550,\"tags\":[[\"a\",\"34550:026d8b7e7bcc2b417a84f10edb71b427fe76069905090b147b401a6cf60c3f27:Catholic\",\"wss://christpill.nostr1.com\"],[\"e\",\"0b8e4fade30fdb57f3887da224682fe9756ee79c408961e46393555bb0367022\"],[\"p\",\"026d8b7e7bcc2b417a84f10edb71b427fe76069905090b147b401a6cf60c3f27\"],[\"k\",\"1\"]],\"content\":\"{\\\"id\\\":\\\"0b8e4fade30fdb57f3887da224682fe9756ee79c408961e46393555bb0367022\\\",\\\"pubkey\\\":\\\"026d8b7e7bcc2b417a84f10edb71b427fe76069905090b147b401a6cf60c3f27\\\",\\\"created_at\\\":1698838786,\\\"kind\\\":1,\\\"tags\\\":[[\\\"a\\\",\\\"34550:026d8b7e7bcc2b417a84f10edb71b427fe76069905090b147b401a6cf60c3f27:Catholic\\\",\\\"\\\",\\\"reply\\\"],[\\\"t\\\",\\\"catholic\\\"],[\\\"t\\\",\\\"catholic\\\"]],\\\"content\\\":\\\"It's so funny at Mass, when you're a sacristan or something because everyone watches and emulates you, so if you forget to stand or kneel at the right moment, everyone remains seated.\\\\n\\\\nAnd when you're like, Oh woops! And stand up, there's a loud wave of people suddenly standing up, too. \uD83D\uDE02\\\\n\\\\n#catholic\\\",\\\"sig\\\":\\\"92c087e6364dd6c1fbf3bf5baddd66f0d86019fb3ba95e36135345d7c8b137f2147de8d628ec974eb493c2dbce2ca581c56c146d282f06a930280c7addc4d021\\\"}\",\"sig\":\"d67c067a08879138989275cb6f062f58e8a523b192bff5e65340becbac05060bb9c7ac6f75727c8fd2229f95a54fe404d18971950a72c1f17618535e7495e09d\"}"

    @Test()
    fun fastEventSerializerTestPayload5() {
        val event = Event.fromJson(payload5)

        val mapper = Nip01Serializer.StringWriter()

        Nip01Serializer().serializeEventInto(event, mapper)

        val encoded = mapper.toString()
        val eventJson = EventHasher.makeJsonForId(event.pubKey, event.createdAt, event.kind, event.tags, event.content)

        assertEquals(eventJson, encoded)
    }

    @Test()
    fun fastEventIdCheckTestPayload5() {
        val event = Event.fromJson(payload5)

        assertEquals("d1f097d3d9fcfb00df0c8ab5469be6484b14707d1e947c574ed636281d8dfd26", event.generateId())
    }
}
