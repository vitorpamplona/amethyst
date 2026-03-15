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
package com.vitorpamplona.quartz.nip01Core.kotlinSerialization

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KotlinSerializationMapperTest {
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

    // =========================================================================
    // TagArray Tests
    // =========================================================================

    @Test
    fun serializeTagArray_matchesJackson() {
        val jacksonJson = JacksonMapper.toJson(tags)
        val kotlinJson = KotlinSerializationMapper.toJson(tags)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeTagArray_matchesJackson() {
        val json = JacksonMapper.toJson(tags)
        val deserialized = KotlinSerializationMapper.fromJsonToTagArray(json)
        tags.forEachIndexed { index, tag ->
            assertContentEquals(tag, deserialized[index])
        }
    }

    @Test
    fun tagArrayRoundTrip() {
        val json = KotlinSerializationMapper.toJson(tags)
        val deserialized = KotlinSerializationMapper.fromJsonToTagArray(json)
        assertEquals(tags.size, deserialized.size)
        tags.forEachIndexed { index, tag ->
            assertContentEquals(tag, deserialized[index])
        }
    }

    @Test
    fun tagArrayWithNullValues() {
        val json = """[["key",null,"value"]]"""
        val deserialized = KotlinSerializationMapper.fromJsonToTagArray(json)
        assertEquals(1, deserialized.size)
        assertEquals("key", deserialized[0][0])
        assertEquals("", deserialized[0][1]) // null -> ""
        assertEquals("value", deserialized[0][2])
    }

    @Test
    fun emptyTagArray() {
        val json = "[]"
        val deserialized = KotlinSerializationMapper.fromJsonToTagArray(json)
        assertEquals(0, deserialized.size)
    }

    // =========================================================================
    // Event Tests
    // =========================================================================

    @Test
    fun serializeEvent_matchesJackson() {
        val jacksonJson = JacksonMapper.toJson(followCard)
        val kotlinJson = KotlinSerializationMapper.toJson(followCard)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeEvent_matchesJackson() {
        val json = JacksonMapper.toJson(followCard)
        val deserialized = KotlinSerializationMapper.fromJson(json)

        assertEquals(followCard.id, deserialized.id)
        assertEquals(followCard.pubKey, deserialized.pubKey)
        assertEquals(followCard.createdAt, deserialized.createdAt)
        assertEquals(followCard.kind, deserialized.kind)
        assertEquals(followCard.content, deserialized.content)
        assertEquals(followCard.sig, deserialized.sig)
        tags.forEachIndexed { index, tag ->
            assertContentEquals(tag, deserialized.tags[index])
        }
    }

    @Test
    fun eventRoundTrip() {
        val json = KotlinSerializationMapper.toJson(followCard)
        val deserialized = KotlinSerializationMapper.fromJson(json)

        assertEquals(followCard.id, deserialized.id)
        assertEquals(followCard.kind, deserialized.kind)
        assertEquals(followCard.createdAt, deserialized.createdAt)
        assertEquals(followCard.pubKey, deserialized.pubKey)
        assertEquals(followCard.content, deserialized.content)
        assertEquals(followCard.sig, deserialized.sig)
    }

    @Test
    fun deserializeEventWithUnknownFields() {
        val json =
            """{"id":"abc123","pubkey":"def456","created_at":12345,"kind":1,"tags":[],"content":"test","sig":"sig123","unknown_field":"ignored"}"""
        // Should not throw with unknown fields, should be ignored
        val deserialized = KotlinSerializationMapper.fromJson(json)
        assertEquals("abc123", deserialized.id)
        assertEquals("def456", deserialized.pubKey)
        assertEquals("test", deserialized.content)
    }

    @Test
    fun deserializeEventWithSpecialCharactersInContent() {
        val content = "Hello \"world\" \n\ttab\\backslash"
        val event =
            FollowListEvent(
                id = "abc",
                pubKey = "def",
                createdAt = 1000,
                tags = emptyArray(),
                content = content,
                sig = "sig",
            )
        val json = KotlinSerializationMapper.toJson(event)
        val deserialized = KotlinSerializationMapper.fromJson(json)
        assertEquals(content, deserialized.content)
    }

    @Test
    fun crossDeserializationEvent() {
        // Serialize with Jackson, deserialize with Kotlin Serialization
        val jacksonJson = JacksonMapper.toJson(followCard)
        val kotlinDeserialized = KotlinSerializationMapper.fromJson(jacksonJson)

        assertEquals(followCard.id, kotlinDeserialized.id)
        assertEquals(followCard.pubKey, kotlinDeserialized.pubKey)
        assertEquals(followCard.kind, kotlinDeserialized.kind)

        // Serialize with Kotlin Serialization, deserialize with Jackson
        val kotlinJson = KotlinSerializationMapper.toJson(followCard)
        val jacksonDeserialized = JacksonMapper.fromJson(kotlinJson)

        assertEquals(followCard.id, jacksonDeserialized.id)
        assertEquals(followCard.pubKey, jacksonDeserialized.pubKey)
        assertEquals(followCard.kind, jacksonDeserialized.kind)
    }

    // =========================================================================
    // Rumor Tests
    // =========================================================================

    @Test
    fun serializeRumor_matchesJackson() {
        val jacksonJson = JacksonMapper.toJson(followCardRumor)
        val kotlinJson = KotlinSerializationMapper.toJson(followCardRumor)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeRumor_matchesJackson() {
        val json = JacksonMapper.toJson(followCardRumor)
        val deserialized = KotlinSerializationMapper.fromJsonToRumor(json)

        assertEquals(followCardRumor.id, deserialized.id)
        assertEquals(followCardRumor.pubKey, deserialized.pubKey)
        assertEquals(followCardRumor.createdAt, deserialized.createdAt)
        assertEquals(followCardRumor.kind, deserialized.kind)
        assertEquals(followCardRumor.content, deserialized.content)
    }

    @Test
    fun rumorRoundTrip() {
        val json = KotlinSerializationMapper.toJson(followCardRumor)
        val deserialized = KotlinSerializationMapper.fromJsonToRumor(json)

        assertEquals(followCardRumor.id, deserialized.id)
        assertEquals(followCardRumor.kind, deserialized.kind)
    }

    @Test
    fun rumorWithNullFields() {
        val rumor = Rumor(null, null, null, null, null, null)
        val json = KotlinSerializationMapper.toJson(rumor)
        assertEquals("{}", json)

        val deserialized = KotlinSerializationMapper.fromJsonToRumor(json)
        assertNull(deserialized.id)
        assertNull(deserialized.pubKey)
        assertNull(deserialized.createdAt)
        assertNull(deserialized.kind)
        assertNull(deserialized.tags)
        assertNull(deserialized.content)
    }

    @Test
    fun rumorPartialFields() {
        val rumor = Rumor("abc", null, 1000, 1, null, "content")
        val json = KotlinSerializationMapper.toJson(rumor)
        val deserialized = KotlinSerializationMapper.fromJsonToRumor(json)

        assertEquals("abc", deserialized.id)
        assertNull(deserialized.pubKey)
        assertEquals(1000L, deserialized.createdAt)
        assertEquals(1, deserialized.kind)
        assertNull(deserialized.tags)
        assertEquals("content", deserialized.content)
    }

    // =========================================================================
    // EventTemplate Tests
    // =========================================================================

    @Test
    fun serializeTemplate_matchesJackson() {
        val jacksonJson = JacksonMapper.toJson(followCardTemplate)
        val kotlinJson = KotlinSerializationMapper.toJson(followCardTemplate)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeTemplate_matchesJackson() {
        val json = JacksonMapper.toJson(followCardTemplate)
        val deserialized = KotlinSerializationMapper.fromJsonToEventTemplate(json)

        assertEquals(followCardTemplate.kind, deserialized.kind)
        assertEquals(followCardTemplate.createdAt, deserialized.createdAt)
        assertEquals(followCardTemplate.content, deserialized.content)
        tags.forEachIndexed { index, tag ->
            assertContentEquals(tag, deserialized.tags[index])
        }
    }

    @Test
    fun templateRoundTrip() {
        val json = KotlinSerializationMapper.toJson(followCardTemplate)
        val deserialized = KotlinSerializationMapper.fromJsonToEventTemplate(json)

        assertEquals(followCardTemplate.kind, deserialized.kind)
        assertEquals(followCardTemplate.createdAt, deserialized.createdAt)
        assertEquals(followCardTemplate.content, deserialized.content)
    }

    @Test
    fun templateWithDifferentFieldOrder() {
        // Fields in different order than expected
        val json = """{"kind":1,"content":"test","created_at":1234,"tags":[]}"""
        val deserialized = KotlinSerializationMapper.fromJsonToEventTemplate(json)
        assertEquals(1, deserialized.kind)
        assertEquals("test", deserialized.content)
        assertEquals(1234L, deserialized.createdAt)
    }

    // =========================================================================
    // Filter Tests
    // =========================================================================

    @Test
    fun emptyFilter_matchesJackson() {
        val filter = Filter()
        val jacksonJson = JacksonMapper.toJson(filter)
        val kotlinJson = KotlinSerializationMapper.toJson(filter)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun filterWithAllFields_matchesJackson() {
        val filter =
            Filter(
                ids = listOf("abc123" + "0".repeat(58)),
                authors = listOf("def456" + "0".repeat(58)),
                kinds = listOf(1, 2, 3),
                tags = mapOf("p" to listOf("3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da")),
                tagsAll = mapOf("p" to listOf("3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da")),
                since = 1000L,
                until = 2000L,
                limit = 50,
                search = "hello",
            )
        val jacksonJson = JacksonMapper.toJson(filter)
        val kotlinJson = KotlinSerializationMapper.toJson(filter)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun filterRoundTrip() {
        val expectedTagValue = "3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da"
        val filter =
            Filter(
                tags = mapOf("p" to listOf(expectedTagValue)),
                tagsAll = mapOf("p" to listOf(expectedTagValue)),
            )
        val json = KotlinSerializationMapper.toJson(filter)
        val deserialized = KotlinSerializationMapper.fromJsonTo<Filter>(json)

        assertEquals(true, deserialized.tags?.keys?.contains("p"))
        assertEquals(listOf(expectedTagValue), deserialized.tags?.get("p"))
        assertEquals(true, deserialized.tagsAll?.keys?.contains("p"))
        assertEquals(listOf(expectedTagValue), deserialized.tagsAll?.get("p"))
    }

    @Test
    fun deserializeEmptyFilter() {
        val json = Filter().toJson()
        val deserialized = KotlinSerializationMapper.fromJsonTo<Filter>(json)
        assertNull(deserialized.ids)
    }

    @Test
    fun crossDeserializationFilter() {
        val expectedTagValue = "3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da"
        val filter =
            Filter(
                tags = mapOf("p" to listOf(expectedTagValue)),
                tagsAll = mapOf("p" to listOf(expectedTagValue)),
            )

        // Jackson serialized -> Kotlin deserialized
        val jacksonJson = JacksonMapper.toJson(filter)
        val kotlinDeserialized = KotlinSerializationMapper.fromJsonTo<Filter>(jacksonJson)
        assertEquals(listOf(expectedTagValue), kotlinDeserialized.tags?.get("p"))
        assertEquals(listOf(expectedTagValue), kotlinDeserialized.tagsAll?.get("p"))

        // Kotlin serialized -> Jackson deserialized
        val kotlinJson = KotlinSerializationMapper.toJson(filter)
        val jacksonDeserialized = JacksonMapper.fromJsonTo<Filter>(kotlinJson)
        assertEquals(listOf(expectedTagValue), jacksonDeserialized.tags?.get("p"))
        assertEquals(listOf(expectedTagValue), jacksonDeserialized.tagsAll?.get("p"))
    }

    // =========================================================================
    // Message Tests
    // =========================================================================

    @Test
    fun serializeEventMessage_matchesJackson() {
        val msg = EventMessage("sub1", followCard)
        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeEventMessage() {
        val msg = EventMessage("sub1", followCard)
        val json = KotlinSerializationMapper.toJson(msg)
        val deserialized = KotlinSerializationMapper.fromJsonToMessage(json)

        assertTrue(deserialized is EventMessage)
        assertEquals("sub1", deserialized.subId)
        assertEquals(followCard.id, deserialized.event.id)
    }

    @Test
    fun serializeNoticeMessage_matchesJackson() {
        val msg = NoticeMessage("something went wrong")
        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun serializeOkMessage_matchesJackson() {
        val msg = OkMessage("abc123", true, "success")
        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeOkMessage() {
        val msg = OkMessage("abc123", false, "rate limited")
        val json = KotlinSerializationMapper.toJson(msg)
        val deserialized = KotlinSerializationMapper.fromJsonToMessage(json)

        assertTrue(deserialized is OkMessage)
        assertEquals("abc123", deserialized.eventId)
        assertEquals(false, deserialized.success)
        assertEquals("rate limited", deserialized.message)
    }

    @Test
    fun serializeAuthMessage_matchesJackson() {
        val msg = AuthMessage("challenge123")
        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun serializeNotifyMessage_matchesJackson() {
        val msg = NotifyMessage("notification text")
        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun serializeClosedMessage_matchesJackson() {
        val msg = ClosedMessage("sub1", "subscription closed")
        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeEoseMessage() {
        val json = """["EOSE","sub123"]"""
        val deserialized = KotlinSerializationMapper.fromJsonToMessage(json)
        assertTrue(deserialized is EoseMessage)
        assertEquals("sub123", (deserialized).subId)
    }

    @Test
    fun serializeCountMessage_matchesJackson() {
        val msg = CountMessage("q1", CountResult(42, false))
        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun crossDeserializationMessages() {
        val messages =
            listOf(
                NoticeMessage("test"),
                AuthMessage("challenge"),
                NotifyMessage("notify"),
                ClosedMessage("sub1", "reason"),
            )

        for (msg in messages) {
            val jacksonJson = JacksonMapper.toJson(msg)
            val kotlinDeserialized = KotlinSerializationMapper.fromJsonToMessage(jacksonJson)
            assertEquals(msg.label(), kotlinDeserialized.label())

            val kotlinJson = KotlinSerializationMapper.toJson(msg)
            val jacksonDeserialized = JacksonMapper.fromJsonToMessage(kotlinJson)
            assertEquals(msg.label(), jacksonDeserialized.label())
        }
    }

    // =========================================================================
    // Command Tests
    // =========================================================================

    @Test
    fun serializeReqCmd_matchesJackson() {
        val filter =
            Filter(
                kinds = listOf(1),
                limit = 10,
            )
        val cmd = ReqCmd("sub1", listOf(filter))
        val jacksonJson = JacksonMapper.toJson(cmd)
        val kotlinJson = KotlinSerializationMapper.toJson(cmd)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeReqCmd() {
        val filter = Filter(kinds = listOf(1), limit = 10)
        val cmd = ReqCmd("sub1", listOf(filter))
        val json = KotlinSerializationMapper.toJson(cmd)
        val deserialized = KotlinSerializationMapper.fromJsonToCommand(json)

        assertTrue(deserialized is ReqCmd)
        assertEquals("sub1", deserialized.subId)
        assertEquals(1, deserialized.filters.size)
        assertEquals(listOf(1), deserialized.filters[0].kinds)
        assertEquals(10, deserialized.filters[0].limit)
    }

    @Test
    fun serializeEventCmd_matchesJackson() {
        val cmd = EventCmd(followCard)
        val jacksonJson = JacksonMapper.toJson(cmd)
        val kotlinJson = KotlinSerializationMapper.toJson(cmd)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun serializeCloseCmd_matchesJackson() {
        val cmd = CloseCmd("sub1")
        val jacksonJson = JacksonMapper.toJson(cmd)
        val kotlinJson = KotlinSerializationMapper.toJson(cmd)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun crossDeserializationCommands() {
        val cmd = CloseCmd("sub1")

        val jacksonJson = JacksonMapper.toJson(cmd)
        val kotlinDeserialized = KotlinSerializationMapper.fromJsonToCommand(jacksonJson)
        assertTrue(kotlinDeserialized is CloseCmd)
        assertEquals("sub1", (kotlinDeserialized).subId)

        val kotlinJson = KotlinSerializationMapper.toJson(cmd)
        val jacksonDeserialized = JacksonMapper.fromJsonToCommand(kotlinJson)
        assertTrue(jacksonDeserialized is CloseCmd)
        assertEquals("sub1", (jacksonDeserialized).subId)
    }

    // =========================================================================
    // BunkerRequest Tests
    // =========================================================================

    @Test
    fun serializeBunkerRequest_matchesJackson() {
        val req = BunkerRequest("id1", "connect", arrayOf("pubkey", "secret"))
        val jacksonJson = JacksonMapper.toJson(req)
        val kotlinJson = KotlinSerializationMapper.toJson(req)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeBunkerRequest() {
        val json = """{"id":"id1","method":"ping","params":[]}"""
        val deserialized = KotlinSerializationMapper.fromJsonTo<BunkerRequest>(json)
        assertEquals("id1", deserialized.id)
        assertEquals("ping", deserialized.method)
    }

    @Test
    fun crossDeserializationBunkerRequest() {
        val req = BunkerRequest("id1", "sign_event", arrayOf("{\"created_at\":1234,\"kind\":1,\"tags\":[],\"content\":\"This is an unsigned event.\"}"))
        val jacksonJson = JacksonMapper.toJson(req)
        val kotlinDeserialized = KotlinSerializationMapper.fromJsonTo<BunkerRequest>(jacksonJson)
        assertEquals(req.id, kotlinDeserialized.id)
        assertEquals(req.method, kotlinDeserialized.method)
        assertContentEquals(req.params, kotlinDeserialized.params)

        val kotlinJson = KotlinSerializationMapper.toJson(req)
        val jacksonDeserialized = JacksonMapper.fromJsonTo<BunkerRequest>(kotlinJson)
        assertEquals(req.id, jacksonDeserialized.id)
        assertEquals(req.method, jacksonDeserialized.method)
        assertContentEquals(req.params, jacksonDeserialized.params)
    }

    // =========================================================================
    // BunkerResponse Tests
    // =========================================================================

    @Test
    fun serializeBunkerResponse_matchesJackson() {
        val resp = BunkerResponse("id1", "ok", null)
        val jacksonJson = JacksonMapper.toJson(resp)
        val kotlinJson = KotlinSerializationMapper.toJson(resp)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun serializeBunkerResponseWithError_matchesJackson() {
        val resp = BunkerResponse("id1", null, "something went wrong")
        val jacksonJson = JacksonMapper.toJson(resp)
        val kotlinJson = KotlinSerializationMapper.toJson(resp)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeBunkerResponse() {
        val json = """{"id":"id1","result":"pong"}"""
        val deserialized = KotlinSerializationMapper.fromJsonTo<BunkerResponse>(json)
        assertEquals("id1", deserialized.id)
        assertNotNull(deserialized.result)
    }

    // =========================================================================
    // OptimizedSerializable toJson dispatch Tests
    // =========================================================================

    @Test
    fun toJsonDispatchForFilter() {
        val filter = Filter(kinds = listOf(1))
        val jacksonJson = JacksonMapper.toJson(filter)
        val kotlinJson = KotlinSerializationMapper.toJson(filter)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun toJsonDispatchForRumor() {
        val jacksonJson = JacksonMapper.toJson(followCardRumor)
        val kotlinJson = KotlinSerializationMapper.toJson(followCardRumor)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun toJsonDispatchForEventTemplate() {
        val jacksonJson = JacksonMapper.toJson(followCardTemplate)
        val kotlinJson = KotlinSerializationMapper.toJson(followCardTemplate)
        assertEquals(jacksonJson, kotlinJson)
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun emptyContentEvent() {
        val event =
            FollowListEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 0,
                tags = emptyArray(),
                content = "",
                sig = "c".repeat(64),
            )
        val json = KotlinSerializationMapper.toJson(event)
        val deserialized = KotlinSerializationMapper.fromJson(json)
        assertEquals("", deserialized.content)
        assertEquals(0, deserialized.tags.size)
    }

    @Test
    fun largeTagArray() {
        val largeTags = Array(100) { i -> arrayOf("p", "key$i") }
        val json = KotlinSerializationMapper.toJson(largeTags)
        val deserialized = KotlinSerializationMapper.fromJsonToTagArray(json)
        assertEquals(100, deserialized.size)
        assertEquals("key99", deserialized[99][1])
    }

    @Test
    fun eventWithUnicodeContent() {
        val content = "Hello \uD83D\uDE00 world \u00E9\u00E8\u00EA"
        val event =
            FollowListEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1000,
                tags = emptyArray(),
                content = content,
                sig = "c".repeat(64),
            )
        val json = KotlinSerializationMapper.toJson(event)
        val deserialized = KotlinSerializationMapper.fromJson(json)
        assertEquals(content, deserialized.content)
    }

    @Test
    fun filterWithMultipleTagTypes() {
        val filter =
            Filter(
                tags =
                    mapOf(
                        "p" to listOf("pubkey1", "pubkey2"),
                        "e" to listOf("eventid1"),
                        "t" to listOf("nostr", "bitcoin"),
                    ),
            )
        val json = KotlinSerializationMapper.toJson(filter)
        val deserialized = KotlinSerializationMapper.fromJsonTo<Filter>(json)

        assertEquals(3, deserialized.tags?.size)
        assertEquals(listOf("pubkey1", "pubkey2"), deserialized.tags?.get("p"))
        assertEquals(listOf("eventid1"), deserialized.tags?.get("e"))
        assertEquals(listOf("nostr", "bitcoin"), deserialized.tags?.get("t"))
    }
}
