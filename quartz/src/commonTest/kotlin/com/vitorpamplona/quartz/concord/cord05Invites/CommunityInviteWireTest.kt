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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityInviteWireTest {
    private val hex = "a".repeat(64)

    @Test
    fun freshCommunityInviteStillCarriesEveryRequiredField() {
        // A genesis public community sits at epoch 0 with no private channel grants: these are
        // all Kotlin defaults, and Armada/Accordion refuse the join unless they are on the wire.
        val invite = CommunityInvite(communityId = hex, owner = hex, ownerSalt = hex, communityRoot = hex)
        val json = ConcordJson.instance.encodeToJsonElement(CommunityInvite.serializer(), invite).jsonObject

        assertEquals(0L, json["root_epoch"]?.jsonPrimitive?.long)
        assertEquals("", json["name"]?.jsonPrimitive?.content)
        assertEquals(0, json["channels"]?.jsonArray?.size)
        assertEquals(0, json["relays"]?.jsonArray?.size)
    }
}
