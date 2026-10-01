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
package com.vitorpamplona.quartz.buzz.mpProjects

import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectMember
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectMemberTag
import com.vitorpamplona.quartz.buzz.mpProjects.tags.ProjectVisibility
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectEventTest {
    private val ownerA = "a".repeat(64)
    private val ownerB = "b".repeat(64)
    private val channel = "3580ca9b-47b4-4af9-b22a-1068778f26c6"

    private fun EventTemplate<ProjectEvent>.toEvent() = ProjectEvent("0".repeat(64), "f".repeat(64), createdAt, tags, content, "sig")

    private fun tags(vararg tags: Array<String>) = arrayOf(*tags)

    // ---- Cases from Buzz's docs/nips/NIP-MP.fixtures.json (the full file runs in jvmTest). ----

    @Test
    fun fixtureValidFull() {
        val t =
            tags(
                arrayOf("d", "platform"),
                arrayOf("name", "Platform"),
                arrayOf("description", "Relay, desktop, and mobile for the platform team."),
                arrayOf("a", "30617:$ownerA:buzz"),
                arrayOf("a", "30617:$ownerB:buzz-infra"),
                arrayOf("buzz-channel", channel),
                arrayOf("buzz-visibility", "listed"),
            )
        assertNull(ProjectValidator.validate(t))
        val ev = ProjectEvent("0".repeat(64), "f".repeat(64), 1, t, "", "sig")
        assertEquals("platform", ev.slug())
        assertEquals("Platform", ev.displayName())
        assertEquals(listOf("30617:$ownerA:buzz", "30617:$ownerB:buzz-infra"), ev.members().map { it.coordinate })
        assertEquals(Address(30617, ownerB, "buzz-infra"), ev.memberAddresses()[1])
        assertEquals(channel, ev.channelId())
        assertTrue(ev.isListed())
    }

    @Test
    fun fixtureValidMemberDTagContainsColon() {
        val t = tags(arrayOf("d", "platform"), arrayOf("a", "30617:$ownerA:buzz:infra"))
        assertNull(ProjectValidator.validate(t))
        val ev = ProjectEvent("0".repeat(64), "f".repeat(64), 1, t, "", "sig")
        assertEquals(
            "buzz:infra",
            ev
                .members()
                .single()
                .address.dTag,
        )
        assertEquals("platform", ev.displayName())
    }

    @Test
    fun fixtureValidMemberRelayHint() {
        val t = tags(arrayOf("d", "platform"), arrayOf("a", "30617:$ownerA:buzz", "wss://relay.example.com"))
        assertNull(ProjectValidator.validate(t))
        assertEquals("wss://relay.example.com", ProjectEvent("0".repeat(64), "f".repeat(64), 1, t, "", "sig").members().single().relayHint)
    }

    @Test
    fun fixtureRejections() {
        val cases =
            listOf(
                ProjectRule.D_CARDINALITY to tags(arrayOf("name", "Platform")),
                ProjectRule.D_CARDINALITY to tags(arrayOf("d", "one"), arrayOf("d", "two")),
                ProjectRule.D_EMPTY to tags(arrayOf("d", "")),
                ProjectRule.MEMBER_DUPLICATE to
                    tags(
                        arrayOf("d", "platform"),
                        arrayOf("a", "30617:$ownerA:buzz", "wss://relay-one.example.com"),
                        arrayOf("a", "30617:$ownerA:buzz", "wss://relay-two.example.com"),
                    ),
                ProjectRule.MEMBER_TAG_ARITY to tags(arrayOf("d", "platform"), arrayOf("a", "30617:$ownerA:buzz", "wss://relay.example.com", "unexpected")),
                ProjectRule.MEMBER_COORDINATE_MALFORMED to tags(arrayOf("d", "platform"), arrayOf("a", "30617:${ownerA.uppercase()}:buzz")),
                ProjectRule.MEMBER_COORDINATE_MALFORMED to tags(arrayOf("d", "platform"), arrayOf("a", "30618:$ownerA:buzz")),
                ProjectRule.MEMBER_COORDINATE_MALFORMED to tags(arrayOf("d", "platform"), arrayOf("a", "30617:$ownerA:")),
                ProjectRule.MEMBER_COORDINATE_MALFORMED to tags(arrayOf("d", "platform"), arrayOf("a", "30617:$ownerA")),
                ProjectRule.MEMBER_CAP to tags(arrayOf("d", "platform"), *Array(65) { arrayOf("a", "30617:$ownerA:repo-$it") }),
                ProjectRule.MEMBER_CAP to tags(arrayOf("d", "platform"), *Array(65) { arrayOf("a", "30617:$ownerA:buzz") }),
                ProjectRule.METADATA_CARDINALITY to tags(arrayOf("d", "platform"), arrayOf("name", "A"), arrayOf("name", "B")),
                ProjectRule.METADATA_LENGTH to tags(arrayOf("d", "platform"), arrayOf("name", "n".repeat(257))),
                ProjectRule.METADATA_LENGTH to tags(arrayOf("d", "platform"), arrayOf("description", "n".repeat(2049))),
                ProjectRule.METADATA_LENGTH to tags(arrayOf("d", "platform"), arrayOf("buzz-visibility", "v".repeat(257))),
            )
        for ((rule, t) in cases) {
            assertEquals(rule, ProjectValidator.validate(t)?.rule, t.joinToString { it.toList().toString() }.take(200))
        }
        // The cap is inclusive.
        assertNull(ProjectValidator.validate(tags(arrayOf("d", "platform"), *Array(64) { arrayOf("a", "30617:$ownerA:repo-$it") })))
    }

    @Test
    fun metadataIsNotInterpretedAtIngest() {
        val t = tags(arrayOf("d", "platform"), arrayOf("buzz-channel", "not-a-uuid"), arrayOf("buzz-visibility", "secret"), arrayOf("x-future", "1"))
        assertNull(ProjectValidator.validate(t))
        // An unknown visibility token never hides a project.
        assertEquals(ProjectVisibility.LISTED, ProjectEvent("0".repeat(64), "f".repeat(64), 1, t, "ignored", "sig").visibility())
    }

    @Test
    fun buildMirrorsTheSdkWriterPolicy() {
        val members =
            listOf(
                ProjectMember(Address(30617, ownerA, "buzz")),
                ProjectMember(Address(30617, ownerB, "buzz-infra"), "wss://relay.example.com"),
            )
        val ev =
            ProjectEvent
                .build("platform", "Platform", "Relay, desktop, and mobile.", members, channel, ProjectVisibility.UNLISTED, createdAt = 5)
                .toEvent()

        assertEquals(30621, ev.kind)
        assertEquals("", ev.content)
        assertEquals(listOf("d", "name", "description", "a", "a", "buzz-channel", "buzz-visibility"), ev.tags.map { it[0] })
        assertEquals(members, ev.members())
        assertEquals(ProjectVisibility.UNLISTED, ev.visibility())
        assertTrue(ev.isWellFormed())

        assertFailsWith<IllegalArgumentException> { ProjectEvent.build("") }
        assertFailsWith<IllegalArgumentException> { ProjectEvent.build("p", channelId = "general") }
        assertFailsWith<IllegalArgumentException> { ProjectEvent.build("p", name = "n".repeat(257)) }
        assertFailsWith<IllegalArgumentException> { ProjectEvent.build("p", members = List(2) { members[0] }) }
    }

    @Test
    fun strictCoordinateParsing() {
        assertEquals(Address(30617, ownerA, "a:b:c"), ProjectMemberTag.parseCoordinate("30617:$ownerA:a:b:c"))
        assertNull(ProjectMemberTag.parseCoordinate("30617:${"g".repeat(64)}:x"))
        assertNull(ProjectMemberTag.parseCoordinate("30617:${"a".repeat(63)}:x"))
        assertNull(ProjectMemberTag.parse(arrayOf("a")))
        assertEquals(arrayOf("a", "30617:$ownerA:x").toList(), ProjectMemberTag.assemble(ownerA, "x").toList())
    }
}
