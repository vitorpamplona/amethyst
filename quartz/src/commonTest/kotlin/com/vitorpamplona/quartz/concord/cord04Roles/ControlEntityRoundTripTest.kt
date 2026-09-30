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
package com.vitorpamplona.quartz.concord.cord04Roles

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ControlEntityRoundTripTest {
    private fun parse(json: String): JsonObject = ConcordJson.instance.parseToJsonElement(json).jsonObject

    @Test
    fun renameCarriesUnknownMetadataFieldsThrough() {
        // CORD-02 §6: another client's `custom`, a newer protocol field, and the CORD-08 timer must
        // all survive a rename by a client that models none of them.
        val head = """{"name":"Old","message_expiration":2592000,"custom":{"rules":"be nice"},"av_brokers":["https://b.example"]}"""
        val renamed = ConcordJson.decodeOrNull<MetadataEntity>(head)!!.copy(name = "New")
        val out = parse(ConcordJson.encodePreserving(MetadataEntity.serializer(), renamed, head))
        assertEquals("New", (out["name"] as JsonPrimitive).content)
        assertEquals(JsonPrimitive(2592000), out["message_expiration"])
        assertEquals(parse("""{"rules":"be nice"}"""), out["custom"])
        assertEquals(parse(head)["av_brokers"], out["av_brokers"])
    }

    @Test
    fun aModeledFieldCanStillBeCleared() {
        val head = """{"name":"X","description":"gone soon","custom":{"k":1}}"""
        val cleared = ConcordJson.decodeOrNull<MetadataEntity>(head)!!.copy(description = null)
        val out = parse(ConcordJson.encodePreserving(MetadataEntity.serializer(), cleared, head))
        assertFalse("description" in out)
        assertEquals(parse("""{"k":1}"""), out["custom"])
    }

    @Test
    fun channelEditKeepsLegacyVoiceFlagAndCustom() {
        val head = """{"name":"lounge","private":false,"voice":true,"custom":{"topic":"x"}}"""
        val renamed = ConcordJson.decodeOrNull<ChannelEntity>(head)!!.copy(name = "hangout")
        val out = parse(ConcordJson.encodePreserving(ChannelEntity.serializer(), renamed, head))
        assertEquals(JsonPrimitive(true), out["voice"])
        assertEquals(parse("""{"topic":"x"}"""), out["custom"])
        assertEquals(JsonPrimitive("hangout"), out["name"])
    }

    @Test
    fun genesisHasNothingToPreserve() {
        val out = parse(ConcordJson.encodePreserving(ChannelEntity.serializer(), ChannelEntity(name = "general"), null))
        assertEquals(JsonPrimitive("general"), out["name"])
    }

    @Test
    fun messageExpirationParsesPerCord08() {
        fun secs(json: String) = ConcordJson.decodeOrNull<MetadataEntity>(json)!!.messageExpirationSecs()
        assertEquals(2592000L, secs("""{"name":"a","message_expiration":2592000}"""))
        assertEquals(86400L, secs("""{"name":"a","message_expiration":86400.9}"""))
        assertNull(secs("""{"name":"a"}"""))
        assertNull(secs("""{"name":"a","message_expiration":0}"""))
        assertNull(secs("""{"name":"a","message_expiration":-5}"""))
        assertNull(secs("""{"name":"a","message_expiration":"2592000"}"""))
        assertNull(secs("""{"name":"a","message_expiration":{"v":1}}"""))
        assertNull(secs("""{"name":"a","message_expiration":null}"""))
    }

    @Test
    fun settingAndClearingTheTimer() {
        val on = MetadataEntity(name = "a").withMessageExpiration(604800)
        assertEquals(604800L, on.messageExpirationSecs())
        val off = on.withMessageExpiration(null)
        assertNull(off.messageExpirationSecs())
        assertFalse("message_expiration" in parse(ConcordJson.encodePreserving(MetadataEntity.serializer(), off, null)))
    }
}
