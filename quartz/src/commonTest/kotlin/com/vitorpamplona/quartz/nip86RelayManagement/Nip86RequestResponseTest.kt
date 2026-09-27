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
package com.vitorpamplona.quartz.nip86RelayManagement

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Method
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip86RequestResponseTest {
    private val client = Nip86Client(RelayUrlNormalizer.normalize("wss://relay.example.com"), NostrSignerInternal(KeyPair()))
    private val id = "c".repeat(64)
    private val pk = "a".repeat(64)

    private fun json(req: Nip86Request) = client.serializeRequest(req)

    @Test
    fun eventAllowAndBanMethods() {
        assertEquals("""{"method":"allowevent","params":["$id","ok"]}""", json(Nip86Request.allowEvent(id, "ok")))
        assertEquals("""{"method":"unallowevent","params":["$id"]}""", json(Nip86Request.unallowEvent(id)))
        assertEquals("""{"method":"unbanevent","params":["$id","oops"]}""", json(Nip86Request.unbanEvent(id, "oops")))
        assertEquals("""{"method":"listallowedevents","params":[]}""", json(Nip86Request.listAllowedEvents()))
        assertEquals("""{"method":"listdisallowedkinds","params":[]}""", json(Nip86Request.listDisallowedKinds()))
    }

    @Test
    fun roleMethods() {
        assertEquals(
            """{"method":"createrole","params":["28b7e50f","king","ruler of the relay",37,1]}""",
            json(Nip86Request.createRole("28b7e50f", "king", "ruler of the relay", 37, 1)),
        )
        assertEquals("""{"method":"editrole","params":["r",null,null,null,null]}""", json(Nip86Request.editRole("r")))
        assertEquals("""{"method":"deleterole","params":["r"]}""", json(Nip86Request.deleteRole("r")))
        assertEquals("""{"method":"assignrole","params":["$pk","r"]}""", json(Nip86Request.assignRole(pk, "r")))
        assertEquals("""{"method":"unassignrole","params":["$pk","r"]}""", json(Nip86Request.unassignRole(pk, "r")))
    }

    @Test
    fun claimMethods() {
        assertEquals("""{"method":"listclaims","params":[]}""", json(Nip86Request.listClaims()))
        assertEquals("""{"method":"createclaim","params":["abc"]}""", json(Nip86Request.createClaim("abc")))
        assertEquals("""{"method":"deleteclaim","params":["abc"]}""", json(Nip86Request.deleteClaim("abc")))
        assertEquals(Nip86Method.CREATE_CLAIM, JsonMapper.fromJson<Nip86Request>(json(Nip86Request.createClaim("abc"))).method)
    }

    @Test
    fun parsesNewResponses() {
        val allowed = client.parseAllowedEvents(client.parseResponse("""{"result":[{"id":"$id","reason":"ok"},{"id":"$id"}]}"""))!!
        assertEquals(listOf(id, id), allowed.map { it.id })
        assertEquals(listOf("ok", null), allowed.map { it.reason })

        assertEquals(listOf(4, 1059), client.parseDisallowedKinds(client.parseResponse("""{"result":[4,1059]}""")))
        assertEquals(listOf("a", "b"), client.parseClaims(client.parseResponse("""{"result":["a","b"]}""")))
        assertEquals(true, client.parseBooleanResult(client.parseResponse("""{"result":true}""")))
    }
}
