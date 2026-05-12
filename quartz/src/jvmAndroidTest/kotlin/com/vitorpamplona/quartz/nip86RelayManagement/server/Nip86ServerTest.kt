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
package com.vitorpamplona.quartz.nip86RelayManagement.server

import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.AllowedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedEvent
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Method
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Nip86ServerTest {
    private fun fixture(): Triple<Nip86Server, BanStore, Holder> {
        val store = BanStore()
        val holder = Holder(Nip11RelayInformation(name = "before", description = "before-desc"))
        val server = Nip86Server(banStore = store, infoHolder = holder, allowList = setOf(admin))
        return Triple(server, store, holder)
    }

    /** Admin pubkey used in all dispatch calls — must match the allow-list above. */
    private val admin = "d".repeat(64)

    private class Holder(
        var current: Nip11RelayInformation,
    ) : Nip86Server.InfoHolder {
        override fun get() = current

        override fun set(info: Nip11RelayInformation) {
            current = info
        }
    }

    private val pk = "a".repeat(64)
    private val pk2 = "b".repeat(64)
    private val eventId = "c".repeat(64)

    @Test
    fun supportedMethodsRoundTrip() {
        runBlocking {
            val (server, _, _) = fixture()
            val resp = server.dispatch(admin, Nip86Request.supportedMethods())
            assertNull(resp.error)
            val arr = resp.result as JsonArray
            val names = arr.map { it.jsonPrimitive.content }
            assertTrue(Nip86Method.SUPPORTED_METHODS in names)
            assertTrue(Nip86Method.BAN_PUBKEY in names)
            assertTrue(Nip86Method.CHANGE_RELAY_NAME in names)
        }
    }

    @Test
    fun banPubkeyMutatesStoreAndListsRoundTripWithReason() {
        runBlocking {
            val (server, banStore, _) = fixture()

            val ok = server.dispatch(admin, Nip86Request.banPubkey(pk, "spam"))
            assertEquals(true, (ok.result as JsonPrimitive).boolean)
            assertTrue(banStore.isBanned(pk))

            val list = server.dispatch(admin, Nip86Request.listBannedPubkeys())
            val parsed =
                kotlinx.serialization.json.Json
                    .decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(BannedPubkey.serializer()),
                        list.result as JsonArray,
                    )
            assertEquals(1, parsed.size)
            assertEquals(pk, parsed[0].pubkey)
            assertEquals("spam", parsed[0].reason)

            server.dispatch(admin, Nip86Request.unbanPubkey(pk))
            assertTrue(banStore.listBannedPubkeys().isEmpty())
        }
    }

    @Test
    fun allowPubkeyAndListRoundTrip() {
        runBlocking {
            val (server, banStore, _) = fixture()
            server.dispatch(admin, Nip86Request.allowPubkey(pk, "trusted"))
            server.dispatch(admin, Nip86Request.allowPubkey(pk2))
            assertTrue(banStore.hasAllowList())

            val resp = server.dispatch(admin, Nip86Request.listAllowedPubkeys())
            val list =
                kotlinx.serialization.json.Json
                    .decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(AllowedPubkey.serializer()),
                        resp.result as JsonArray,
                    )
            assertEquals(2, list.size)
            assertEquals(setOf(pk, pk2), list.map { it.pubkey }.toSet())
        }
    }

    @Test
    fun banEventMarksIdAndDeletesFromStoreWhenStorePresent() {
        runBlocking {
            val (server, banStore, _) = fixture()
            server.dispatch(admin, Nip86Request.banEvent(eventId, "off-topic"))
            assertTrue(banStore.isBannedEvent(eventId))

            val resp = server.dispatch(admin, Nip86Request.listBannedEvents())
            val list =
                kotlinx.serialization.json.Json
                    .decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(BannedEvent.serializer()),
                        resp.result as JsonArray,
                    )
            assertEquals(1, list.size)
            assertEquals(eventId, list[0].id)
            assertEquals("off-topic", list[0].reason)

            // allowevent (which is "unban") removes the entry.
            server.dispatch(admin, Nip86Request.allowEvent(eventId))
            assertTrue(banStore.listBannedEvents().isEmpty())
        }
    }

    @Test
    fun allowKindAndDisallowKind() {
        runBlocking {
            val (server, banStore, _) = fixture()
            server.dispatch(admin, Nip86Request.allowKind(1))
            server.dispatch(admin, Nip86Request.allowKind(7))
            server.dispatch(admin, Nip86Request.disallowKind(4))

            val list = server.dispatch(admin, Nip86Request.listAllowedKinds())
            val ints = (list.result as JsonArray).map { it.jsonPrimitive.int }
            assertEquals(listOf(1, 7), ints)

            assertTrue(banStore.isKindAllowed(1))
            assertTrue(banStore.isKindAllowed(7))
            assertEquals(false, banStore.isKindAllowed(4))
            assertEquals(false, banStore.isKindAllowed(99))
        }
    }

    @Test
    fun changeRelayNameDescriptionIconRewriteInfoDoc() {
        runBlocking {
            val (server, _, holder) = fixture()
            assertEquals("before", holder.current.name)

            server.dispatch(admin, Nip86Request.changeRelayName("after"))
            assertEquals("after", holder.current.name)

            server.dispatch(admin, Nip86Request.changeRelayDescription("nice relay"))
            assertEquals("nice relay", holder.current.description)

            server.dispatch(admin, Nip86Request.changeRelayIcon("https://x/icon.png"))
            assertEquals("https://x/icon.png", holder.current.icon)
        }
    }

    @Test
    fun unsupportedMethodReturnsError() {
        runBlocking {
            val (server, _, _) = fixture()
            val resp = server.dispatch(admin, Nip86Request(method = "frobnicate"))
            assertNotNull(resp.error)
            assertTrue(resp.error!!.contains("frobnicate"))
        }
    }

    @Test
    fun missingParamsAreReportedAsErrors() {
        runBlocking {
            val (server, _, _) = fixture()
            // banpubkey requires at least one positional param.
            val resp = server.dispatch(admin, Nip86Request(method = Nip86Method.BAN_PUBKEY))
            assertNotNull(resp.error)
            assertTrue(resp.error!!.startsWith("invalid params"))
        }
    }

    @Test
    fun dispatchRejectsPubkeyNotOnAllowList() {
        runBlocking {
            val (server, banStore, _) = fixture()
            val intruder = "e".repeat(64)
            val resp = server.dispatch(intruder, Nip86Request.banPubkey(pk, "spam"))
            assertNotNull(resp.error)
            assertTrue(resp.error!!.contains("not on the admin list"))
            // And no state was mutated.
            assertTrue(!banStore.isBanned(pk))
        }
    }

    @Test
    fun emptyAllowListDisablesTheServer() {
        val store = BanStore()
        val holder = Holder(Nip11RelayInformation(name = "x"))
        val server = Nip86Server(banStore = store, infoHolder = holder) // no allowList
        assertEquals(false, server.isEnabled())
        assertEquals(false, server.isAuthorized(admin))
    }

    @Test
    fun allowListIsCaseInsensitive() {
        val store = BanStore()
        val holder = Holder(Nip11RelayInformation(name = "x"))
        // Configure in upper-case; lookups normalize.
        val server = Nip86Server(banStore = store, infoHolder = holder, allowList = setOf(admin.uppercase()))
        assertTrue(server.isAuthorized(admin))
        assertTrue(server.isAuthorized(admin.uppercase()))
    }
}
