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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArtifactHeadResolverTest {
    private val artifactId = "04737c81-e5e8-4412-bb47-f446813cfeba"
    private val home = "9b353519-f4fe-4757-aef4-bec6cc0ae54c"
    private val destination = "17cfe3ca-b3d0-4a76-a2a4-4eaf0d3a4109"

    private fun id(n: Int) = n.toString().padStart(64, '0')

    private fun revision(
        n: Int,
        op: ArtifactOp,
        prev: Int?,
        createdAt: Long = 1000L + n,
        channel: String = home,
        d: String = artifactId,
    ): ArtifactEvent {
        val title = if (op == ArtifactOp.DELETE) null else "T$n"
        val tpl = ArtifactEvent.build(d, channel, "buzz.task", op, title, if (op == ArtifactOp.DELETE) "" else "c$n", prev?.let { id(it) }, createdAt = createdAt)
        return ArtifactEvent(id(n), "f".repeat(64), tpl.createdAt, tpl.tags, tpl.content, "sig")
    }

    @Test
    fun emptyIsUnknown() {
        assertEquals(ArtifactHead.Unknown, ArtifactHeadResolver.resolve(artifactId, emptyList()))
    }

    @Test
    fun linearChainResolvesToTheTipRegardlessOfTimestampsOrOrder() {
        val create = revision(1, ArtifactOp.CREATE, null, createdAt = 5000)
        val update = revision(2, ArtifactOp.UPDATE, 1, createdAt = 10)
        val update2 = revision(3, ArtifactOp.UPDATE, 2, createdAt = 20)

        val head = ArtifactHeadResolver.resolve(artifactId, listOf(update2, create, update, update))
        assertIs<ArtifactHead.Current>(head)
        assertEquals(id(3), head.revision.id)
        assertTrue(head.chainComplete)

        assertEquals(listOf(id(3), id(2), id(1)), ArtifactHeadResolver.history(head.revision, listOf(create, update, update2)).map { it.id })
    }

    @Test
    fun otherIdentitiesAndMalformedRevisionsAreIgnored() {
        val create = revision(1, ArtifactOp.CREATE, null)
        val foreign = revision(2, ArtifactOp.UPDATE, 1, d = "5b0d1c1e-8a7e-4c1a-9d3b-2f1e0a9b8c7d")
        val head = ArtifactHeadResolver.resolve(artifactId, listOf(create, foreign))
        assertIs<ArtifactHead.Current>(head)
        assertEquals(id(1), head.revision.id)
    }

    @Test
    fun aForkIsAmbiguousNotResolvedByTimestamp() {
        val create = revision(1, ArtifactOp.CREATE, null)
        val x = revision(2, ArtifactOp.UPDATE, 1, createdAt = 2000)
        val y = revision(3, ArtifactOp.UPDATE, 1, createdAt = 3000)
        val head = ArtifactHeadResolver.resolve(artifactId, listOf(create, x, y))
        assertIs<ArtifactHead.Ambiguous>(head)
        assertEquals(setOf(id(2), id(3)), head.candidates.map { it.id }.toSet())
    }

    @Test
    fun aRedactedMiddleRevisionStillResolvesAcrossTheGap() {
        // 1 <- 2 <- (3 redacted) <- 4: tips are 2 and 4; the create-rooted segment is older.
        val head =
            ArtifactHeadResolver.resolve(
                artifactId,
                listOf(revision(1, ArtifactOp.CREATE, null), revision(2, ArtifactOp.UPDATE, 1), revision(4, ArtifactOp.UPDATE, 3)),
            )
        assertIs<ArtifactHead.Current>(head)
        assertEquals(id(4), head.revision.id)
        assertEquals(false, head.chainComplete)
    }

    @Test
    fun twoGapsAreAmbiguous() {
        val head =
            ArtifactHeadResolver.resolve(
                artifactId,
                listOf(revision(1, ArtifactOp.CREATE, null), revision(4, ArtifactOp.UPDATE, 3), revision(6, ArtifactOp.UPDATE, 5)),
            )
        assertIs<ArtifactHead.Ambiguous>(head)
    }

    @Test
    fun destinationReaderSeesOnlyTheMoveAndLater() {
        val move = revision(3, ArtifactOp.MOVE, 2, channel = destination)
        val update = revision(4, ArtifactOp.UPDATE, 3, channel = destination)
        val head = ArtifactHeadResolver.resolve(artifactId, listOf(update, move))
        assertIs<ArtifactHead.Current>(head)
        assertEquals(id(4), head.revision.id)
        assertEquals(false, head.chainComplete)
    }

    @Test
    fun sourceReaderSeesTheRemoval() {
        val create = revision(1, ArtifactOp.CREATE, null)
        val update = revision(2, ArtifactOp.UPDATE, 1)
        val removalTpl = ArtifactRemovalEvent.build(artifactId, home, id(2))
        val removal = ArtifactRemovalEvent(id(9), "e".repeat(64), removalTpl.createdAt, removalTpl.tags, removalTpl.content, "sig")

        val head = ArtifactHeadResolver.resolve(artifactId, listOf(create, update), listOf(removal))
        assertIs<ArtifactHead.MovedAway>(head)
        assertEquals(id(2), head.lastRevision.id)

        // A reader of both channels sees the move revision, which supersedes the removal's prev.
        val move = revision(3, ArtifactOp.MOVE, 2, channel = destination)
        val both = ArtifactHeadResolver.resolve(artifactId, listOf(create, update, move), listOf(removal))
        assertIs<ArtifactHead.Current>(both)
        assertEquals(id(3), both.revision.id)
    }

    @Test
    fun deletedHeadIsReportedAsDeletedAndRestoreRevivesIt() {
        val create = revision(1, ArtifactOp.CREATE, null)
        val delete = revision(2, ArtifactOp.DELETE, 1)
        val deleted = ArtifactHeadResolver.resolve(artifactId, listOf(create, delete))
        assertIs<ArtifactHead.Current>(deleted)
        assertTrue(deleted.isDeleted)

        val restore = revision(3, ArtifactOp.RESTORE, 2)
        val restored = ArtifactHeadResolver.resolve(artifactId, listOf(create, delete, restore))
        assertIs<ArtifactHead.Current>(restored)
        assertEquals(false, restored.isDeleted)
    }

    @Test
    fun aPrevCycleIsAmbiguous() {
        val head = ArtifactHeadResolver.resolve(artifactId, listOf(revision(2, ArtifactOp.UPDATE, 3), revision(3, ArtifactOp.UPDATE, 2)))
        assertIs<ArtifactHead.Ambiguous>(head)
        assertTrue(head.candidates.isEmpty())
    }
}
