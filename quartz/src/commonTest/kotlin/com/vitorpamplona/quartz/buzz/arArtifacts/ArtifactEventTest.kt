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
package com.vitorpamplona.quartz.buzz.arArtifacts

import com.vitorpamplona.quartz.buzz.arArtifacts.tags.ArtifactOp
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtifactEventTest {
    private val artifactId = "04737c81-e5e8-4412-bb47-f446813cfeba"
    private val home = "9b353519-f4fe-4757-aef4-bec6cc0ae54c"
    private val other = "17cfe3ca-b3d0-4a76-a2a4-4eaf0d3a4109"
    private val prev = "a".repeat(64)

    private fun EventTemplate<ArtifactEvent>.toEvent(id: String = "0".repeat(64)) = ArtifactEvent(id, "f".repeat(64), createdAt, tags, content, "sig")

    /** The same envelope as upstream's `event()` test helper in `buzz-core/src/artifact.rs`. */
    private fun envelope(
        op: String,
        extra: List<Array<String>> = emptyList(),
    ): Array<Array<String>> {
        val tags =
            mutableListOf(
                arrayOf("ar", "1"),
                arrayOf("d", artifactId),
                arrayOf("h", home),
                arrayOf("type", "buzz.task"),
                arrayOf("op", op),
            )
        if (op != "delete") tags.add(arrayOf("title", "Title"))
        if (op != "create") tags.add(arrayOf("prev", prev))
        tags.addAll(extra)
        return tags.toTypedArray()
    }

    private fun validate(
        op: String,
        extra: List<Array<String>> = emptyList(),
        content: String = "",
    ) = ArtifactValidator.validate(envelope(op, extra), content)

    @Test
    fun lifecycleEnvelopesAreValid() {
        for (op in listOf("create", "update", "move", "delete", "restore")) {
            assertIs<ArtifactValidation.Valid>(validate(op), op)
        }
        // Client tags and non-JSON content are opaque to the envelope.
        assertIs<ArtifactValidation.Valid>(validate("create", listOf(arrayOf("project", "opaque", "anything")), "not json"))
    }

    @Test
    fun malformedAndDeletePayloadsAreRejected() {
        val cases =
            listOf(
                Triple(listOf(arrayOf("title", "duplicate")), "create", ""),
                Triple(listOf(arrayOf("ar", "2")), "create", ""),
                Triple(listOf(arrayOf("prev", "a".repeat(64))), "create", ""),
                Triple(listOf(arrayOf("root", "A".repeat(64))), "create", ""),
                Triple(listOf(arrayOf("project", "hidden")), "delete", ""),
                Triple(listOf(arrayOf("title", "hidden")), "delete", ""),
                Triple(emptyList(), "delete", "hidden"),
                Triple(listOf(arrayOf("x", "x".repeat(4097))), "create", ""),
                Triple(listOf(arrayOf("x".repeat(129), "x")), "create", ""),
            )
        for ((extra, op, content) in cases) {
            assertIs<ArtifactValidation.Invalid>(validate(op, extra, content), "${extra.map { it.toList() }} $op")
        }
    }

    @Test
    fun envelopeDetailRules() {
        val base = envelope("create")

        fun replace(
            name: String,
            value: String,
        ) = base.map { if (it[0] == name) arrayOf(name, value) else it }.toTypedArray()

        assertEquals(ArtifactValidation.Invalid("unsupported artifact envelope version"), ArtifactValidator.validate(replace("ar", "2"), ""))
        assertIs<ArtifactValidation.Invalid>(ArtifactValidator.validate(replace("d", artifactId.uppercase()), ""))
        assertIs<ArtifactValidation.Invalid>(ArtifactValidator.validate(replace("h", "00000000-0000-0000-0000-000000000000"), ""))
        assertIs<ArtifactValidation.Invalid>(ArtifactValidator.validate(replace("type", "task"), ""))
        assertIs<ArtifactValidation.Invalid>(ArtifactValidator.validate(replace("type", "buzz.Task"), ""))
        assertIs<ArtifactValidation.Invalid>(ArtifactValidator.validate(replace("type", "buzz..task"), ""))
        assertIs<ArtifactValidation.Invalid>(ArtifactValidator.validate(replace("type", "buzz.1task"), ""))
        assertIs<ArtifactValidation.Valid>(ArtifactValidator.validate(replace("type", "acme.ops_board.v-2"), ""))
        assertEquals(ArtifactValidation.Invalid("invalid artifact operation"), ArtifactValidator.validate(replace("op", "archive"), ""))
        assertIs<ArtifactValidation.Invalid>(ArtifactValidator.validate(replace("title", "   "), ""))
        assertIs<ArtifactValidation.Invalid>(ArtifactValidator.validate(replace("title", "x".repeat(513)), ""))
        assertIs<ArtifactValidation.Valid>(ArtifactValidator.validate(replace("title", "x".repeat(512)), ""))
        // A three-element envelope tag is rejected even when the extra element is empty.
        assertIs<ArtifactValidation.Invalid>(ArtifactValidator.validate(base.map { if (it[0] == "title") arrayOf("title", "T", "") else it }.toTypedArray(), ""))
        // Missing envelope tag.
        assertEquals(ArtifactValidation.Invalid("missing required envelope tag"), ArtifactValidator.validate(base.filter { it[0] != "type" }.toTypedArray(), ""))
        // Too many tags.
        val tooMany = (base.toList() + List(ArtifactValidator.MAX_TAGS) { arrayOf("x", "$it") }).toTypedArray()
        assertEquals(ArtifactValidation.Invalid("too many tags"), ArtifactValidator.validate(tooMany, ""))
        // A delete may carry a NIP-OA auth tag.
        assertIs<ArtifactValidation.Valid>(validate("delete", listOf(arrayOf("auth", "b".repeat(64), "", "c".repeat(128)))))
    }

    @Test
    fun canonicalIdentifiers() {
        assertFalse(ArtifactIds.isCanonicalUuid("00000000-0000-0000-0000-000000000000"))
        assertFalse(ArtifactIds.isCanonicalUuid(artifactId.replace("-", "")))
        assertFalse(ArtifactIds.isCanonicalUuid(artifactId.uppercase()))
        assertTrue(ArtifactIds.isCanonicalUuid(artifactId))
        assertTrue(ArtifactIds.isEventId("f".repeat(64)))
        assertFalse(ArtifactIds.isEventId("F".repeat(64)))
        assertFalse(ArtifactIds.isEventId("f".repeat(63)))
    }

    @Test
    fun createRoundTrips() {
        val tpl =
            ArtifactEvent.create(artifactId, home, "buzz.task", "Fix the payment timeout", "{\"version\":1,\"status\":\"open\"}") {
                add(arrayOf("assignee", "b".repeat(64)))
                add(arrayOf("project", other))
            }
        val ev = tpl.toEvent()

        assertEquals(45010, ev.kind)
        assertEquals(
            listOf("ar", "d", "h", "type", "title", "op", "assignee", "project"),
            ev.tags.map { it[0] },
        )
        assertEquals(artifactId, ev.artifactId())
        assertEquals(home, ev.home())
        assertEquals("buzz.task", ev.type())
        assertEquals("Fix the payment timeout", ev.title())
        assertEquals(ArtifactOp.CREATE, ev.op())
        assertNull(ev.prev())
        assertNull(ev.root())
        assertEquals(listOf("assignee", "project"), ev.clientTags().map { it[0] })

        val envelope = ev.envelopeOrNull()!!
        assertEquals(ArtifactEnvelope(artifactId, home, "buzz.task", ArtifactOp.CREATE, null, null), envelope)
    }

    @Test
    fun updateCarriesEnvelopeAndClientTagsForward() {
        val root = "c".repeat(64)
        val created =
            ArtifactEvent
                .create(artifactId, home, "buzz.task", "Title", "v1", root) {
                    add(arrayOf("project", other))
                }.toEvent("1".repeat(64))

        val updated = ArtifactEvent.update(created, "Renamed", "v2").toEvent("2".repeat(64))
        assertEquals(ArtifactOp.UPDATE, updated.op())
        assertEquals(created.id, updated.prev())
        assertEquals(root, updated.root())
        assertEquals(home, updated.home())
        assertEquals("Renamed", updated.title())
        assertEquals(listOf(other), updated.clientTags().map { it[1] })
        assertTrue(updated.isWellFormed())

        val dropped = ArtifactEvent.update(created, "Renamed", "v2", keepClientTags = false).toEvent()
        assertTrue(dropped.clientTags().isEmpty())
    }

    @Test
    fun deletePreservesTypeHomeAndRootAndStripsEverythingElse() {
        val root = "c".repeat(64)
        val current =
            ArtifactEvent
                .create(artifactId, home, "buzz.task", "Title", "payload", root) {
                    add(arrayOf("assignee", "b".repeat(64)))
                }.toEvent("1".repeat(64))

        val deletion = ArtifactEvent.delete(current).toEvent("2".repeat(64))
        assertEquals(ArtifactOp.DELETE, deletion.op())
        assertTrue(deletion.isDelete())
        assertEquals("", deletion.content)
        assertNull(deletion.title())
        assertEquals(root, deletion.root())
        assertEquals("buzz.task", deletion.type())
        assertEquals(current.id, deletion.prev())
        assertTrue(deletion.clientTags().isEmpty())
        assertTrue(deletion.isWellFormed())
    }

    @Test
    fun buildRefusesWhatTheRelayWouldReject() {
        assertFailsWith<IllegalArgumentException> { ArtifactEvent.build(artifactId.uppercase(), home, "buzz.task", ArtifactOp.CREATE, "T", "") }
        assertFailsWith<IllegalArgumentException> { ArtifactEvent.build(artifactId, home, "task", ArtifactOp.CREATE, "T", "") }
        assertFailsWith<IllegalArgumentException> { ArtifactEvent.build(artifactId, home, "buzz.task", ArtifactOp.CREATE, " ", "") }
        assertFailsWith<IllegalArgumentException> { ArtifactEvent.build(artifactId, home, "buzz.task", ArtifactOp.CREATE, "T", "", prev = prev) }
        assertFailsWith<IllegalArgumentException> { ArtifactEvent.build(artifactId, home, "buzz.task", ArtifactOp.UPDATE, "T", "") }
        assertFailsWith<IllegalArgumentException> { ArtifactEvent.build(artifactId, home, "buzz.task", ArtifactOp.DELETE, "T", "", prev = prev) }
        assertFailsWith<IllegalArgumentException> { ArtifactEvent.build(artifactId, home, "buzz.task", ArtifactOp.DELETE, null, "x", prev = prev) }
        assertFailsWith<IllegalArgumentException> { ArtifactEvent.build(artifactId, home, "buzz.task", ArtifactOp.CREATE, "T", "", root = "A".repeat(64)) }
        // Client tags added by the initializer are validated too.
        assertFailsWith<IllegalArgumentException> {
            ArtifactEvent.build(artifactId, home, "buzz.task", ArtifactOp.CREATE, "T", "") { add(arrayOf("x", "x".repeat(4097))) }
        }
        assertFailsWith<IllegalArgumentException> {
            ArtifactEvent.build(artifactId, home, "buzz.task", ArtifactOp.DELETE, null, "", prev = prev) { add(arrayOf("project", other)) }
        }
    }

    @Test
    fun moveBuildsIntoANewHome() {
        val ev = ArtifactEvent.build(artifactId, other, "buzz.task", ArtifactOp.MOVE, "Title", "snapshot", prev = prev).toEvent()
        assertEquals(other, ev.home())
        assertEquals(ArtifactOp.MOVE, ev.op())
        assertTrue(ev.isWellFormed())
    }
}
