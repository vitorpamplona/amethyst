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
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.AllowedEvent
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.AllowedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedEvent
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Method
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

            // unbanevent removes the entry without allow-listing it.
            server.dispatch(admin, Nip86Request.unbanEvent(eventId))
            assertTrue(banStore.listBannedEvents().isEmpty())
            assertTrue(banStore.listAllowedEvents().isEmpty())
        }
    }

    @Test
    fun allowEventAddsToAllowListWithReasonAndLiftsBan() {
        runBlocking {
            val (server, banStore, _) = fixture()
            server.dispatch(admin, Nip86Request.banEvent(eventId, "spam"))

            val ok = server.dispatch(admin, Nip86Request.allowEvent(eventId, "reviewed"))
            assertEquals(true, (ok.result as JsonPrimitive).boolean)
            assertTrue(banStore.isAllowedEvent(eventId))
            assertFalse(banStore.isBannedEvent(eventId))

            val resp = server.dispatch(admin, Nip86Request.listAllowedEvents())
            val list = Json.decodeFromJsonElement(ListSerializer(AllowedEvent.serializer()), resp.result as JsonArray)
            assertEquals(1, list.size)
            assertEquals(eventId, list[0].id)
            assertEquals("reviewed", list[0].reason)

            // banevent moves it back off the allow list.
            server.dispatch(admin, Nip86Request.banEvent(eventId, "again"))
            assertTrue(banStore.isBannedEvent(eventId))
            assertFalse(banStore.isAllowedEvent(eventId))

            // unallowevent / unbanevent never add to the opposite list.
            server.dispatch(admin, Nip86Request.allowEvent(eventId))
            server.dispatch(admin, Nip86Request.unallowEvent(eventId))
            assertFalse(banStore.isAllowedEvent(eventId))
            assertFalse(banStore.isBannedEvent(eventId))
        }
    }

    @Test
    fun banAndAllowPubkeyAreMutuallyExclusive() {
        runBlocking {
            val (server, banStore, _) = fixture()
            server.dispatch(admin, Nip86Request.allowPubkey(pk, "trusted"))
            server.dispatch(admin, Nip86Request.banPubkey(pk, "spam"))
            assertTrue(banStore.isBanned(pk))
            assertFalse(banStore.isAllowedPubkey(pk))

            server.dispatch(admin, Nip86Request.allowPubkey(pk))
            assertTrue(banStore.isAllowedPubkey(pk))
            assertFalse(banStore.isBanned(pk))

            server.dispatch(admin, Nip86Request.unallowPubkey(pk))
            assertFalse(banStore.isAllowedPubkey(pk))
            assertFalse(banStore.isBanned(pk))
        }
    }

    @Test
    fun listDisallowedKinds() {
        runBlocking {
            val (server, _, _) = fixture()
            server.dispatch(admin, Nip86Request.disallowKind(4))
            server.dispatch(admin, Nip86Request.disallowKind(1059))
            val resp = server.dispatch(admin, Nip86Request.listDisallowedKinds())
            assertEquals(listOf(4, 1059), (resp.result as JsonArray).map { it.jsonPrimitive.int })
        }
    }

    @Test
    fun roleLifecycle() {
        runBlocking {
            val (server, banStore, _) = fixture()
            val created = server.dispatch(admin, Nip86Request.createRole("mod", "Moderator", "keeps order", 120, 1))
            assertNull(created.error)
            assertEquals(RelayRole("mod", "Moderator", "keeps order", 120, 1), banStore.getRole("mod"))

            // Creating an existing id is refused; edit replaces it.
            assertNotNull(server.dispatch(admin, Nip86Request.createRole("mod")).error)
            assertNull(server.dispatch(admin, Nip86Request.editRole("mod", "Mods", null, 200, null)).error)
            assertEquals(RelayRole("mod", "Mods", null, 200, null), banStore.getRole("mod"))
            assertNotNull(server.dispatch(admin, Nip86Request.editRole("nope", "x")).error)

            assertNull(server.dispatch(admin, Nip86Request.assignRole(pk, "mod")).error)
            assertEquals(listOf("mod"), banStore.rolesOf(pk))
            assertNotNull(server.dispatch(admin, Nip86Request.assignRole(pk, "nope")).error)

            assertNull(server.dispatch(admin, Nip86Request.unassignRole(pk, "mod")).error)
            assertEquals(emptyList(), banStore.rolesOf(pk))

            server.dispatch(admin, Nip86Request.assignRole(pk2, "mod"))
            assertNull(server.dispatch(admin, Nip86Request.deleteRole("mod")).error)
            assertNull(banStore.getRole("mod"))
            assertEquals(emptyList(), banStore.rolesOf(pk2), "deleting a role unassigns it")
        }
    }

    @Test
    fun roleParamsAcceptStringNumbersAndRejectBadHues() {
        runBlocking {
            val (server, banStore, _) = fixture()
            val stringy =
                Nip86Request(
                    method = Nip86Method.CREATE_ROLE,
                    params =
                        buildJsonArray {
                            add(JsonPrimitive("king"))
                            add(JsonPrimitive("king"))
                            add(JsonPrimitive("ruler of the relay"))
                            add(JsonPrimitive("37"))
                            add(JsonPrimitive("1"))
                        },
                )
            assertNull(server.dispatch(admin, stringy).error)
            assertEquals(RelayRole("king", "king", "ruler of the relay", 37, 1), banStore.getRole("king"))

            // Only the id is required.
            val idOnly = Nip86Request(method = Nip86Method.CREATE_ROLE, params = buildJsonArray { add(JsonPrimitive("bare")) })
            assertNull(server.dispatch(admin, idOnly).error)
            assertEquals(RelayRole("bare"), banStore.getRole("bare"))

            assertNotNull(server.dispatch(admin, Nip86Request.createRole("hot", color = 361)).error)
            assertNull(banStore.getRole("hot"))
            assertNotNull(server.dispatch(admin, Nip86Request(method = Nip86Method.CREATE_ROLE)).error)
        }
    }

    @Test
    fun claimLifecycle() {
        runBlocking {
            val (server, banStore, _) = fixture()
            assertNull(server.dispatch(admin, Nip86Request.createClaim("invite-1")).error)
            assertNull(server.dispatch(admin, Nip86Request.createClaim("invite-2")).error)
            assertTrue(banStore.isValidClaim("invite-1"))

            val listed = server.dispatch(admin, Nip86Request.listClaims())
            assertEquals(setOf("invite-1", "invite-2"), (listed.result as JsonArray).map { it.jsonPrimitive.content }.toSet())

            assertNull(server.dispatch(admin, Nip86Request.deleteClaim("invite-1")).error)
            assertFalse(banStore.isValidClaim("invite-1"))
            assertNotNull(server.dispatch(admin, Nip86Request.createClaim(" ")).error)
        }
    }

    @Test
    fun supportedMethodsAreAllDispatchable() {
        runBlocking {
            val (server, _, _) = fixture()
            // Every advertised method must reach a real handler, never "method not supported".
            server.supportedMethods.forEach { method ->
                val resp = server.dispatch(admin, Nip86Request(method = method))
                assertFalse(resp.error?.startsWith("method not supported") == true, "advertised but not handled: " + method)
            }
            listOf(
                Nip86Method.UNBAN_EVENT,
                Nip86Method.UNALLOW_EVENT,
                Nip86Method.LIST_ALLOWED_EVENTS,
                Nip86Method.LIST_DISALLOWED_KINDS,
                Nip86Method.CREATE_ROLE,
                Nip86Method.ASSIGN_ROLE,
                Nip86Method.LIST_CLAIMS,
                Nip86Method.CREATE_CLAIM,
            ).forEach { assertTrue(it in server.supportedMethods, it) }
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
            assertTrue(resp.error.contains("frobnicate"))
        }
    }

    @Test
    fun missingParamsAreReportedAsErrors() {
        runBlocking {
            val (server, _, _) = fixture()
            // banpubkey requires at least one positional param.
            val resp = server.dispatch(admin, Nip86Request(method = Nip86Method.BAN_PUBKEY))
            assertNotNull(resp.error)
            assertTrue(resp.error.startsWith("invalid params"))
        }
    }

    @Test
    fun dispatchRejectsPubkeyNotOnAllowList() {
        runBlocking {
            val (server, banStore, _) = fixture()
            val intruder = "e".repeat(64)
            val resp = server.dispatch(intruder, Nip86Request.banPubkey(pk, "spam"))
            assertNotNull(resp.error)
            assertTrue(resp.error.contains("not on the admin list"))
            // And no state was mutated.
            assertTrue(!banStore.isBanned(pk))
        }
    }

    @Test
    fun emptyAllowListRejectsEveryPubkey() {
        val store = BanStore()
        val holder = Holder(Nip11RelayInformation(name = "x"))
        val server = Nip86Server(banStore = store, infoHolder = holder) // no allowList
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
