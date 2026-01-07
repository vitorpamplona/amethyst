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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class JacksonMapperTest {
    val tags =
        arrayOf(
            arrayOf("title", "Retro Computer Fans"),
            arrayOf("d", "xmbspe8rddsq"),
            arrayOf("image", "https://blog.johnnovak.net/2022/04/15/achieving-period-correct-graphics-in-personal-computer-emulators-part-1-the-amiga/img/dream-setup.jpg"),
            arrayOf("p", "3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da"),
            arrayOf("p", "9a9a4aa0e43e57873380ab22e8a3df12f3c4cf5bb3a804c6e3fed0069a6e2740"),
            arrayOf("p", "4f5dd82517b11088ce00f23d99f06fe8f3e2e45ecf47bc9c2f90f34d5c6f7382"),
            arrayOf("p", "ac92102a2ecb873c488e0125354ef5a97075a16198668c360eda050007ed42cd"),
            arrayOf("p", "47f54409a4620eb35208a3bc1b53555bf3d0656b246bf0471a93208e20672f6f"),
            arrayOf("p", "2624911545afb7a2b440cf10f5c69308afa33aae26fca664d8c94623dc0f1baf"),
            arrayOf("p", "6641f26f5c59f7010dbe3e42e4593398e27c087497cb7d20e0e7633a17e48a94"),
            arrayOf("description", "Retro computer fans and enthusiasts "),
        )

    val followCard =
        FollowListEvent(
            id = "eca31634fce7c9068b56fa8db9f387da70bdcceb3986a77ca1a9844f3128eb5f",
            pubKey = "3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da",
            createdAt = 1761736286,
            tags = tags,
            content = "",
            sig = "3aa388edafad151e81cb0228fe04e115dbbcaa851c666bfe3c8740b6cd99575f0fc3ba2d47acda86f7626564a05e9dbc05ef452a7bd0ac00f828dbad0e1bae6c",
        )

    val followCardRumor =
        Rumor(
            id = followCard.id,
            pubKey = followCard.pubKey,
            createdAt = followCard.createdAt,
            kind = followCard.kind,
            tags = followCard.tags,
            content = followCard.content,
        )

    val followCardTemplate =
        EventTemplate<Event>(
            createdAt = followCard.createdAt,
            kind = followCard.kind,
            tags = followCard.tags,
            content = followCard.content,
        )

    @Test
    fun serializeTagArray() {
        val serialized = JacksonMapper.toJson(tags)

        assertEquals(
            "[[\"title\",\"Retro Computer Fans\"],[\"d\",\"xmbspe8rddsq\"],[\"image\",\"https://blog.johnnovak.net/2022/04/15/achieving-period-correct-graphics-in-personal-computer-emulators-part-1-the-amiga/img/dream-setup.jpg\"],[\"p\",\"3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da\"],[\"p\",\"9a9a4aa0e43e57873380ab22e8a3df12f3c4cf5bb3a804c6e3fed0069a6e2740\"],[\"p\",\"4f5dd82517b11088ce00f23d99f06fe8f3e2e45ecf47bc9c2f90f34d5c6f7382\"],[\"p\",\"ac92102a2ecb873c488e0125354ef5a97075a16198668c360eda050007ed42cd\"],[\"p\",\"47f54409a4620eb35208a3bc1b53555bf3d0656b246bf0471a93208e20672f6f\"],[\"p\",\"2624911545afb7a2b440cf10f5c69308afa33aae26fca664d8c94623dc0f1baf\"],[\"p\",\"6641f26f5c59f7010dbe3e42e4593398e27c087497cb7d20e0e7633a17e48a94\"],[\"description\",\"Retro computer fans and enthusiasts \"]]",
            serialized,
        )

        val deserialized = JacksonMapper.fromJsonToTagArray(serialized)

        tags.forEachIndexed { index, tag ->
            assertContentEquals(tag, deserialized[index])
        }
    }

    @Test
    fun serializeEvent() {
        val serialized = JacksonMapper.toJson(followCard)

        assertEquals(
            "{\"id\":\"eca31634fce7c9068b56fa8db9f387da70bdcceb3986a77ca1a9844f3128eb5f\",\"pubkey\":\"3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da\",\"created_at\":1761736286,\"kind\":39089,\"tags\":[[\"title\",\"Retro Computer Fans\"],[\"d\",\"xmbspe8rddsq\"],[\"image\",\"https://blog.johnnovak.net/2022/04/15/achieving-period-correct-graphics-in-personal-computer-emulators-part-1-the-amiga/img/dream-setup.jpg\"],[\"p\",\"3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da\"],[\"p\",\"9a9a4aa0e43e57873380ab22e8a3df12f3c4cf5bb3a804c6e3fed0069a6e2740\"],[\"p\",\"4f5dd82517b11088ce00f23d99f06fe8f3e2e45ecf47bc9c2f90f34d5c6f7382\"],[\"p\",\"ac92102a2ecb873c488e0125354ef5a97075a16198668c360eda050007ed42cd\"],[\"p\",\"47f54409a4620eb35208a3bc1b53555bf3d0656b246bf0471a93208e20672f6f\"],[\"p\",\"2624911545afb7a2b440cf10f5c69308afa33aae26fca664d8c94623dc0f1baf\"],[\"p\",\"6641f26f5c59f7010dbe3e42e4593398e27c087497cb7d20e0e7633a17e48a94\"],[\"description\",\"Retro computer fans and enthusiasts \"]],\"content\":\"\",\"sig\":\"3aa388edafad151e81cb0228fe04e115dbbcaa851c666bfe3c8740b6cd99575f0fc3ba2d47acda86f7626564a05e9dbc05ef452a7bd0ac00f828dbad0e1bae6c\"}",
            serialized,
        )

        val deserialized = JacksonMapper.fromJson(serialized)

        assertEquals(followCard.id, deserialized.id)
        assertEquals(followCard.kind, deserialized.kind)
        assertEquals(followCard.createdAt, deserialized.createdAt)
        assertEquals(followCard.pubKey, deserialized.pubKey)
        assertEquals(followCard.content, deserialized.content)
        assertEquals(followCard.sig, deserialized.sig)

        tags.forEachIndexed { index, tag ->
            assertContentEquals(tag, deserialized.tags[index])
        }
    }

    @Test
    fun serializeRumor() {
        val serialized = JacksonMapper.toJson(followCardRumor)

        assertEquals(
            "{\"id\":\"eca31634fce7c9068b56fa8db9f387da70bdcceb3986a77ca1a9844f3128eb5f\",\"pubkey\":\"3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da\",\"created_at\":1761736286,\"kind\":39089,\"tags\":[[\"title\",\"Retro Computer Fans\"],[\"d\",\"xmbspe8rddsq\"],[\"image\",\"https://blog.johnnovak.net/2022/04/15/achieving-period-correct-graphics-in-personal-computer-emulators-part-1-the-amiga/img/dream-setup.jpg\"],[\"p\",\"3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da\"],[\"p\",\"9a9a4aa0e43e57873380ab22e8a3df12f3c4cf5bb3a804c6e3fed0069a6e2740\"],[\"p\",\"4f5dd82517b11088ce00f23d99f06fe8f3e2e45ecf47bc9c2f90f34d5c6f7382\"],[\"p\",\"ac92102a2ecb873c488e0125354ef5a97075a16198668c360eda050007ed42cd\"],[\"p\",\"47f54409a4620eb35208a3bc1b53555bf3d0656b246bf0471a93208e20672f6f\"],[\"p\",\"2624911545afb7a2b440cf10f5c69308afa33aae26fca664d8c94623dc0f1baf\"],[\"p\",\"6641f26f5c59f7010dbe3e42e4593398e27c087497cb7d20e0e7633a17e48a94\"],[\"description\",\"Retro computer fans and enthusiasts \"]],\"content\":\"\"}",
            serialized,
        )

        val deserialized = JacksonMapper.fromJson(serialized)

        assertEquals(followCardRumor.id, deserialized.id)
        assertEquals(followCardRumor.kind, deserialized.kind)
        assertEquals(followCardRumor.createdAt, deserialized.createdAt)
        assertEquals(followCardRumor.pubKey, deserialized.pubKey)
        assertEquals(followCardRumor.content, deserialized.content)

        tags.forEachIndexed { index, tag ->
            assertContentEquals(tag, deserialized.tags[index])
        }
    }

    @Test
    fun serializeTemplate() {
        val serialized = JacksonMapper.toJson(followCardTemplate)

        assertEquals(
            "{\"created_at\":1761736286,\"kind\":39089,\"tags\":[[\"title\",\"Retro Computer Fans\"],[\"d\",\"xmbspe8rddsq\"],[\"image\",\"https://blog.johnnovak.net/2022/04/15/achieving-period-correct-graphics-in-personal-computer-emulators-part-1-the-amiga/img/dream-setup.jpg\"],[\"p\",\"3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da\"],[\"p\",\"9a9a4aa0e43e57873380ab22e8a3df12f3c4cf5bb3a804c6e3fed0069a6e2740\"],[\"p\",\"4f5dd82517b11088ce00f23d99f06fe8f3e2e45ecf47bc9c2f90f34d5c6f7382\"],[\"p\",\"ac92102a2ecb873c488e0125354ef5a97075a16198668c360eda050007ed42cd\"],[\"p\",\"47f54409a4620eb35208a3bc1b53555bf3d0656b246bf0471a93208e20672f6f\"],[\"p\",\"2624911545afb7a2b440cf10f5c69308afa33aae26fca664d8c94623dc0f1baf\"],[\"p\",\"6641f26f5c59f7010dbe3e42e4593398e27c087497cb7d20e0e7633a17e48a94\"],[\"description\",\"Retro computer fans and enthusiasts \"]],\"content\":\"\"}",
            serialized,
        )

        val deserialized = JacksonMapper.fromJsonToEventTemplate(serialized)

        assertEquals(followCardTemplate.kind, deserialized.kind)
        assertEquals(followCardTemplate.createdAt, deserialized.createdAt)
        assertEquals(followCardTemplate.content, deserialized.content)

        tags.forEachIndexed { index, tag ->
            assertContentEquals(tag, deserialized.tags[index])
        }
    }
}
