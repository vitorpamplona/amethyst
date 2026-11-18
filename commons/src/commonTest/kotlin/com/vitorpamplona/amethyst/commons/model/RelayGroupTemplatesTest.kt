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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The kind 9000 / 9002 shapes we send to NIP-29 and Buzz relays, and how a create refusal reads. */
class RelayGroupTemplatesTest {
    private val pk = "bb".repeat(32)
    private val gid = "11111111-1111-4111-8111-111111111111"
    private val relay = RelayUrlNormalizer.normalize("wss://buzz.example.com")

    private fun buzzMetadata(): GroupMetadataEvent =
        EventFactory.create(
            "00".repeat(32),
            "aa".repeat(32),
            100,
            GroupMetadataEvent.KIND,
            arrayOf(
                arrayOf("d", gid),
                arrayOf("name", "general"),
                arrayOf("t", "stream"),
                arrayOf("t", "workspace"),
                arrayOf("P", pk),
                arrayOf("created_at", "90"),
                arrayOf("join_role", "member"),
                arrayOf("public"),
            ),
            "",
            "22".repeat(64),
        ) as GroupMetadataEvent

    private fun edit(
        isBuzz: Boolean,
        buzzLabels: List<String>? = null,
    ) = relayGroupEditTemplate(
        gid,
        currentMetadata = buzzMetadata(),
        isBuzz = isBuzz,
        name = "renamed",
        about = null,
        picture = null,
        banner = null,
        status = emptySet(),
        // What the edit form pre-fills: the 39000's hashtags, which on Buzz are the type and labels.
        hashtags = buzzMetadata().hashtags(),
        geohashes = emptyList(),
        parent = null,
        children = emptyList(),
        buzzLabels = buzzLabels,
    ).tags.map { it.toList() }

    @Test
    fun buzzPutUserSendsTheRoleTagWithABarePTag() {
        // buzz d0dee1cb5 reads p[2] before `role`: "moderator" there is refused, and an edit's old
        // role first won over the new one.
        val tags = relayGroupPutUserTemplate(gid, pk, listOf("member", "admin"), buzzRoleFor(listOf("member", "admin"))).tags.map { it.toList() }
        assertEquals(listOf(listOf("h", gid), listOf("p", pk), listOf("role", "admin")), tags)

        val moderator = relayGroupPutUserTemplate(gid, pk, listOf("moderator"), buzzRoleFor(listOf("moderator"))).tags.map { it.toList() }
        assertEquals(listOf(listOf("h", gid), listOf("p", pk), listOf("role", "member")), moderator)
    }

    @Test
    fun nip29PutUserKeepsTheRolesInThePTag() {
        val tags = relayGroupPutUserTemplate(gid, pk, listOf("moderator"), null).tags.map { it.toList() }
        assertEquals(listOf(listOf("h", gid), listOf("p", pk, "moderator")), tags)
    }

    @Test
    fun buzzEditNeverEchoesTheChannelTypeOrLabels() {
        val tags = edit(isBuzz = true)
        assertTrue(tags.none { it[0] == "t" }, "a Buzz rename must not send t tags: $tags")
        // Nor the relay-internal tags of its 39000.
        assertTrue(tags.none { it[0] == "P" || it[0] == "join_role" || it[0] == "created_at" }, "$tags")
        assertTrue(listOf("visibility", "open") in tags)
    }

    @Test
    fun buzzEditSendsLabelsOnlyWhenTheyChange() {
        assertEquals(listOf(listOf("t", "team:core")), edit(isBuzz = true, buzzLabels = listOf("team:core")).filter { it[0] == "t" })
        assertEquals(listOf(listOf("t", "")), edit(isBuzz = true, buzzLabels = emptyList()).filter { it[0] == "t" })
        assertFailsWith<IllegalArgumentException> { edit(isBuzz = true, buzzLabels = listOf("stream")) }
    }

    @Test
    fun nip29EditStillCarriesHashtagsAndUnmanagedTags() {
        val tags = edit(isBuzz = false)
        assertTrue(listOf("t", "stream") in tags && listOf("t", "workspace") in tags, "$tags")
        assertTrue(listOf("join_role", "member") in tags, "$tags")
    }

    @Test
    fun aRefusedCreateReportsTheRelaysReason() {
        val refused = mapOf(relay to PublishResult(false, "invalid: an open channel named \"general\" already exists"))
        assertEquals("an open channel named \"general\" already exists", relayGroupCreateRefusal(refused))
        assertNull(relayGroupCreateRefusal(mapOf(relay to PublishResult(true, ""))))
        // The relay never weighed in: nothing to report, the create may still land.
        assertNull(relayGroupCreateRefusal(mapOf(relay to PublishResult(false, PublishResult.NO_RESPONSE))))
    }
}
