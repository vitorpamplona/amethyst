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
package com.vitorpamplona.quartz.nip77Negentropy

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Nip77SerializationTest {
    // =========================================================================
    // NEG-MSG Message (relay-to-client) Tests
    // =========================================================================

    @Test
    fun serializeNegMsgMessage_matchesJackson() {
        val msg = NegMsgMessage("neg-sub1", "abcdef0123456789")
        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeNegMsgMessage_jackson() {
        val json = """["NEG-MSG","neg-sub1","abcdef0123456789"]"""
        val deserialized = JacksonMapper.fromJsonToMessage(json)

        assertTrue(deserialized is NegMsgMessage)
        assertEquals("neg-sub1", deserialized.subId)
        assertEquals("abcdef0123456789", deserialized.message)
    }

    @Test
    fun deserializeNegMsgMessage_kotlin() {
        val json = """["NEG-MSG","neg-sub1","abcdef0123456789"]"""
        val deserialized = KotlinSerializationMapper.fromJsonToMessage(json)

        assertTrue(deserialized is NegMsgMessage)
        assertEquals("neg-sub1", deserialized.subId)
        assertEquals("abcdef0123456789", deserialized.message)
    }

    @Test
    fun negMsgMessage_crossDeserialization() {
        val msg = NegMsgMessage("neg-sub1", "abcdef0123456789")

        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinDeserialized = KotlinSerializationMapper.fromJsonToMessage(jacksonJson)
        assertTrue(kotlinDeserialized is NegMsgMessage)
        assertEquals(msg.subId, kotlinDeserialized.subId)
        assertEquals(msg.message, kotlinDeserialized.message)

        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        val jacksonDeserialized = JacksonMapper.fromJsonToMessage(kotlinJson)
        assertTrue(jacksonDeserialized is NegMsgMessage)
        assertEquals(msg.subId, jacksonDeserialized.subId)
        assertEquals(msg.message, jacksonDeserialized.message)
    }

    // =========================================================================
    // NEG-ERR Message (relay-to-client) Tests
    // =========================================================================

    @Test
    fun serializeNegErrMessage_matchesJackson() {
        val msg = NegErrMessage("neg-sub1", "blocked: query too large")
        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeNegErrMessage_jackson() {
        val json = """["NEG-ERR","neg-sub1","blocked: query too large"]"""
        val deserialized = JacksonMapper.fromJsonToMessage(json)

        assertTrue(deserialized is NegErrMessage)
        assertEquals("neg-sub1", deserialized.subId)
        assertEquals("blocked: query too large", deserialized.reason)
    }

    @Test
    fun deserializeNegErrMessage_kotlin() {
        val json = """["NEG-ERR","neg-sub1","closed: timeout"]"""
        val deserialized = KotlinSerializationMapper.fromJsonToMessage(json)

        assertTrue(deserialized is NegErrMessage)
        assertEquals("neg-sub1", deserialized.subId)
        assertEquals("closed: timeout", deserialized.reason)
    }

    @Test
    fun negErrMessage_crossDeserialization() {
        val msg = NegErrMessage("neg-sub1", "blocked: too many records")

        val jacksonJson = JacksonMapper.toJson(msg)
        val kotlinDeserialized = KotlinSerializationMapper.fromJsonToMessage(jacksonJson)
        assertTrue(kotlinDeserialized is NegErrMessage)
        assertEquals(msg.reason, kotlinDeserialized.reason)

        val kotlinJson = KotlinSerializationMapper.toJson(msg)
        val jacksonDeserialized = JacksonMapper.fromJsonToMessage(kotlinJson)
        assertTrue(jacksonDeserialized is NegErrMessage)
        assertEquals(msg.reason, jacksonDeserialized.reason)
    }

    // =========================================================================
    // NEG-OPEN Command (client-to-relay) Tests
    // =========================================================================

    @Test
    fun serializeNegOpenCmd_matchesJackson() {
        val filter = Filter(kinds = listOf(1), since = 1000L)
        val cmd = NegOpenCmd("neg-sub1", filter, "61abcdef")
        val jacksonJson = JacksonMapper.toJson(cmd)
        val kotlinJson = KotlinSerializationMapper.toJson(cmd)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeNegOpenCmd_jackson() {
        val json = """["NEG-OPEN","neg-sub1",{"kinds":[1],"since":1000},"61abcdef"]"""
        val deserialized = JacksonMapper.fromJsonToCommand(json)

        assertTrue(deserialized is NegOpenCmd)
        assertEquals("neg-sub1", deserialized.subId)
        assertEquals(listOf(1), deserialized.filter.kinds)
        assertEquals(1000L, deserialized.filter.since)
        assertEquals("61abcdef", deserialized.initialMessage)
    }

    @Test
    fun deserializeNegOpenCmd_kotlin() {
        val json = """["NEG-OPEN","neg-sub1",{"kinds":[1],"since":1000},"61abcdef"]"""
        val deserialized = KotlinSerializationMapper.fromJsonToCommand(json)

        assertTrue(deserialized is NegOpenCmd)
        assertEquals("neg-sub1", deserialized.subId)
        assertEquals(listOf(1), deserialized.filter.kinds)
        assertEquals("61abcdef", deserialized.initialMessage)
    }

    @Test
    fun negOpenCmd_crossDeserialization() {
        val filter = Filter(kinds = listOf(1, 7), limit = 100)
        val cmd = NegOpenCmd("neg-sub1", filter, "61aabbccdd")

        val jacksonJson = JacksonMapper.toJson(cmd)
        val kotlinDeserialized = KotlinSerializationMapper.fromJsonToCommand(jacksonJson)
        assertTrue(kotlinDeserialized is NegOpenCmd)
        assertEquals(cmd.subId, kotlinDeserialized.subId)
        assertEquals(cmd.initialMessage, kotlinDeserialized.initialMessage)

        val kotlinJson = KotlinSerializationMapper.toJson(cmd)
        val jacksonDeserialized = JacksonMapper.fromJsonToCommand(kotlinJson)
        assertTrue(jacksonDeserialized is NegOpenCmd)
        assertEquals(cmd.subId, jacksonDeserialized.subId)
        assertEquals(cmd.initialMessage, jacksonDeserialized.initialMessage)
    }

    // =========================================================================
    // NEG-MSG Command (client-to-relay) Tests
    // =========================================================================

    @Test
    fun serializeNegMsgCmd_matchesJackson() {
        val cmd = NegMsgCmd("neg-sub1", "aabbccdd")
        val jacksonJson = JacksonMapper.toJson(cmd)
        val kotlinJson = KotlinSerializationMapper.toJson(cmd)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeNegMsgCmd_jackson() {
        val json = """["NEG-MSG","neg-sub1","aabbccdd"]"""
        val deserialized = JacksonMapper.fromJsonToCommand(json)

        assertTrue(deserialized is NegMsgCmd)
        assertEquals("neg-sub1", deserialized.subId)
        assertEquals("aabbccdd", deserialized.message)
    }

    @Test
    fun deserializeNegMsgCmd_kotlin() {
        val json = """["NEG-MSG","neg-sub1","aabbccdd"]"""
        val deserialized = KotlinSerializationMapper.fromJsonToCommand(json)

        assertTrue(deserialized is NegMsgCmd)
        assertEquals("neg-sub1", deserialized.subId)
        assertEquals("aabbccdd", deserialized.message)
    }

    // =========================================================================
    // NEG-CLOSE Command (client-to-relay) Tests
    // =========================================================================

    @Test
    fun serializeNegCloseCmd_matchesJackson() {
        val cmd = NegCloseCmd("neg-sub1")
        val jacksonJson = JacksonMapper.toJson(cmd)
        val kotlinJson = KotlinSerializationMapper.toJson(cmd)
        assertEquals(jacksonJson, kotlinJson)
    }

    @Test
    fun deserializeNegCloseCmd_jackson() {
        val json = """["NEG-CLOSE","neg-sub1"]"""
        val deserialized = JacksonMapper.fromJsonToCommand(json)

        assertTrue(deserialized is NegCloseCmd)
        assertEquals("neg-sub1", deserialized.subId)
    }

    @Test
    fun deserializeNegCloseCmd_kotlin() {
        val json = """["NEG-CLOSE","neg-sub1"]"""
        val deserialized = KotlinSerializationMapper.fromJsonToCommand(json)

        assertTrue(deserialized is NegCloseCmd)
        assertEquals("neg-sub1", deserialized.subId)
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Test
    fun negOpenCmd_isValid() {
        val valid = NegOpenCmd("sub1", Filter(), "61abcd")
        assertTrue(valid.isValid())

        val invalidSubId = NegOpenCmd("", Filter(), "61abcd")
        assertTrue(!invalidSubId.isValid())

        val invalidMsg = NegOpenCmd("sub1", Filter(), "")
        assertTrue(!invalidMsg.isValid())
    }

    @Test
    fun negMsgCmd_isValid() {
        val valid = NegMsgCmd("sub1", "abcd")
        assertTrue(valid.isValid())

        val invalidSubId = NegMsgCmd("", "abcd")
        assertTrue(!invalidSubId.isValid())
    }

    @Test
    fun negCloseCmd_isValid() {
        val valid = NegCloseCmd("sub1")
        assertTrue(valid.isValid())

        val invalid = NegCloseCmd("")
        assertTrue(!invalid.isValid())
    }

    @Test
    fun negMsgMessage_label() {
        assertEquals("NEG-MSG", NegMsgMessage("sub1", "msg").label())
    }

    @Test
    fun negErrMessage_label() {
        assertEquals("NEG-ERR", NegErrMessage("sub1", "error").label())
    }
}
