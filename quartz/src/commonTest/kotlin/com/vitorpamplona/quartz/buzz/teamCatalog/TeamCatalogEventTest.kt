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
package com.vitorpamplona.quartz.buzz.teamCatalog

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamCatalogEventTest {
    private val teamId = "builtin-team:welcome"

    private fun EventTemplate<TeamCatalogEvent>.toEvent() = TeamCatalogEvent("0".repeat(64), "f".repeat(64), createdAt, tags, content, "sig")

    private fun event(
        content: String,
        tags: Array<Array<String>> = arrayOf(arrayOf("d", teamId), arrayOf("shared", "true")),
    ) = TeamCatalogEvent("0".repeat(64), "f".repeat(64), 1, tags, content, "sig")

    private val reviewer =
        TeamCatalogMember(
            memberKey = "k".repeat(64),
            displayName = "Reviewer",
            systemPrompt = "Review every PR.",
            runtime = "goose",
            namePool = listOf("Ada", "Grace"),
            respondTo = "owner-only",
            parallelism = 2,
            sessionPolicy = AcpSessionPolicy.THREAD,
        )

    @Test
    fun encodesInUpstreamFieldOrderAndOmitsAbsentFields() {
        val content =
            TeamCatalogContent(
                v = 1,
                name = "Welcome",
                description = "Starter team",
                members = listOf(reviewer, TeamCatalogMember(memberKey = "m2", displayName = "Scribe")),
            )
        // serde_json output of the same `TeamCatalogContent` (declaration order, skip_serializing_if).
        val expected =
            """{"v":1,"name":"Welcome","description":"Starter team","members":[""" +
                """{"member_key":"${"k".repeat(64)}","display_name":"Reviewer","system_prompt":"Review every PR.","runtime":"goose",""" +
                """"name_pool":["Ada","Grace"],"respond_to":"owner-only","parallelism":2,"session_policy":"thread"},""" +
                """{"member_key":"m2","display_name":"Scribe"}]}"""
        assertEquals(expected, content.encodeToJson())
        assertEquals(content, TeamCatalogContent.decodeFromJson(expected))
    }

    @Test
    fun emptyMembersAreStillWritten() {
        assertEquals("""{"v":1,"name":"Solo","members":[]}""", TeamCatalogContent(v = 1, name = "Solo", members = emptyList()).encodeToJson())
    }

    @Test
    fun buildRoundTrips() {
        val content = TeamCatalogContent(v = 1, name = "Welcome", instructions = "Be kind.", members = listOf(reviewer))
        val ev = TeamCatalogEvent.build(content, teamId, shared = true, createdAt = 100).toEvent()

        assertEquals(30178, ev.kind)
        assertEquals(teamId, ev.teamId())
        assertTrue(ev.isShared())
        assertTrue(ev.isEnvelopeValid())
        assertEquals(content, ev.catalog())

        val unshared = TeamCatalogEvent.build(content, teamId, shared = false, createdAt = 100).toEvent()
        assertFalse(unshared.isShared())
        assertEquals(listOf("d"), unshared.tags.map { it[0] })
    }

    @Test
    fun republishingNeverSortsBehindThePriorHead() {
        val content = TeamCatalogContent(v = 1, name = "Welcome", members = emptyList())
        val future = 4_000_000_000L
        assertEquals(future + 1, TeamCatalogEvent.build(content, teamId, shared = true, priorHeadCreatedAt = future).createdAt)
    }

    @Test
    fun readerRejectsWhatUpstreamRejects() {
        // Missing `v`, unsupported `v`, wrong-typed field, over the member cap, repeated key.
        assertNull(event("""{"name":"No Version","members":[]}""").catalogOrNull())
        assertNull(event("""{"v":2,"name":"Future","members":[]}""").catalogOrNull())
        assertNull(event("""{"v":1,"name":"Bad","members":[{"member_key":"m1","display_name":"One","parallelism":"lots"}]}""").catalogOrNull())
        val tooMany = (0..TeamCatalogContent.MAX_MEMBERS).joinToString(",") { """{"member_key":"m$it","display_name":"M$it"}""" }
        assertNull(event("""{"v":1,"name":"Too Many","members":[$tooMany]}""").catalogOrNull())
        assertNull(event("""{"v":1,"name":"Twins","members":[{"member_key":"k","display_name":"One"},{"member_key":"k","display_name":"Two"}]}""").catalogOrNull())
        assertNull(event("""{"v":1,"name":"  ","members":[]}""").catalogOrNull())
    }

    @Test
    fun membersViolatingTheV1ContractAreRejected() {
        val fields =
            listOf(
                """"parallelism":999""",
                """"parallelism":0""",
                """"respond_to":"everyone"""",
                """"runtime":""""",
                """"model":"${"m".repeat(257)}"""",
                """"name_pool":[""]""",
                """"builtin_slug":"reviewer"""",
                """"projection_hash":"${"a".repeat(64)}"""",
                """"builtin_slug":"reviewer","projection_hash":"xyz"""",
                """"avatar_url":"javascript:alert(1)"""",
            )
        for (field in fields) {
            assertNull(event("""{"v":1,"name":"T","members":[{"member_key":"k","display_name":"One",$field}]}""").catalogOrNull(), field)
        }
        val ok = event("""{"v":1,"name":"T","members":[{"member_key":"k","display_name":"One","avatar_url":"https://example.com/a.png","parallelism":32}]}""")
        assertNotNull(ok.catalogOrNull())
    }

    @Test
    fun sessionPolicyIsLenientLikeUpstream() {
        fun policy(raw: String) = TeamCatalogContent.decodeFromJson("""{"v":1,"name":"T","members":[{"member_key":"k","display_name":"One","session_policy":$raw}]}""").members[0].sessionPolicy

        assertEquals(AcpSessionPolicy.THREAD, policy("\"thread\""))
        assertEquals(AcpSessionPolicy.CHANNEL, policy("\"channel\""))
        assertEquals(AcpSessionPolicy.CHANNEL, policy("\"weird\""))
        assertEquals(AcpSessionPolicy.CHANNEL, policy("5"))
        assertEquals(AcpSessionPolicy.CHANNEL, policy("null"))
        // The default (channel) is never written.
        assertFalse(TeamCatalogMember("k", "One").let { TeamCatalogContent(1, "T", members = listOf(it)) }.encodeToJson().contains("session_policy"))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val parsed = event("""{"v":1,"name":"T","future":{"x":1},"members":[{"member_key":"k","display_name":"One","later":true}]}""").catalogOrNull()
        assertNotNull(parsed)
        assertEquals("One", parsed.members[0].displayName)
    }

    @Test
    fun envelopeRules() {
        val ok = arrayOf(arrayOf("d", teamId))
        assertNull(TeamCatalogEvent.envelopeError(ok))
        assertNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d", teamId), arrayOf("shared", "true"))))
        assertNotNull(TeamCatalogEvent.envelopeError(emptyArray()))
        assertNotNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d", ""))))
        assertNotNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d"), arrayOf("d", teamId))))
        assertNotNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d", "a".repeat(65)))))
        assertNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d", "a".repeat(64)))))
        assertNotNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d", "team 1"))))
        assertNotNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d", "team\n1"))))
        assertNotNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d", teamId), arrayOf("shared", "false"))))
        assertNotNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d", teamId), arrayOf("shared", "true", "extra"))))
        assertNotNull(TeamCatalogEvent.envelopeError(arrayOf(arrayOf("d", teamId), arrayOf("shared", "true"), arrayOf("shared", "true"))))
    }

    @Test
    fun buildRefusesInvalidInput() {
        val content = TeamCatalogContent(v = 1, name = "T", members = emptyList())
        assertFailsWith<IllegalArgumentException> { TeamCatalogEvent.build(content, "", shared = true) }
        assertFailsWith<IllegalArgumentException> { TeamCatalogEvent.build(content.copy(v = 2), teamId, shared = true) }
    }
}
