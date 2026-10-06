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
package com.vitorpamplona.quartz.nip17Dm.messages

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatMessageHintProviderTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val third = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eventId2 = "b1a2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val addressId = "30023:$other:my-article"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    private val message =
        ChatMessageEvent(
            id,
            pk,
            1L,
            arrayOf(
                arrayOf("p", other, relay),
                arrayOf("zap", third, relay, "1"),
                arrayOf("e", eventId2, relay, "reply"),
                arrayOf("q", eventId, relay, other),
                arrayOf("q", addressId, relay),
                arrayOf("subject", "Weekend plans"),
            ),
            "hello",
            sig,
        )

    @Test
    fun zapSplitsJoinTheRecipients() {
        assertEquals(listOf(other, third), message.linkedPubKeys())
        assertEquals(listOf(other, third), message.pubKeyHints().map { it.pubkey })
    }

    @Test
    fun withoutZapSplitsOnlyTheRecipientsAreLinked() {
        val plain = ChatMessageEvent(id, pk, 1L, arrayOf(arrayOf("p", other, relay), arrayOf("p", third)), "hi", sig)
        assertEquals(listOf(other, third), plain.linkedPubKeys())
        assertEquals(listOf(other), plain.pubKeyHints().map { it.pubkey })
    }

    @Test
    fun quotesAreLinked() {
        assertEquals(listOf(eventId2, eventId), message.linkedEventIds())
        assertEquals(listOf(eventId2, eventId), message.eventHints().map { it.eventId })
        assertEquals(listOf(addressId), message.linkedAddressIds())
        assertEquals(listOf(addressId), message.addressHints().map { it.addressId })
    }

    @Test
    fun subjectIsSearchable() {
        assertEquals("Weekend plans\nhello", message.indexableContent())
        assertEquals(message.indexableContent(), message.rejoined())

        val fields = SearchFieldExtractor.extract(message) as IndexableFields.Tiered
        assertEquals(listOf("Weekend plans"), fields.primary)
        assertEquals("hello", fields.text)
    }
}

private fun SearchableEvent.rejoined(): String =
    buildList {
        forEachIndexableField { f ->
            if (f != null) add(f)
            true
        }
    }.joinToString(indexableSeparator())
