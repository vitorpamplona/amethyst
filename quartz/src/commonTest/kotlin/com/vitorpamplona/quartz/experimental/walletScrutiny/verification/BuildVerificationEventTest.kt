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
package com.vitorpamplona.quartz.experimental.walletScrutiny.verification

import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.board.UnrecognizedKind30301Event
import com.vitorpamplona.quartz.experimental.walletScrutiny.WalletScrutinyFixtures
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle.AssetBundleEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BasedOnTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatus
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.OutputFileTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildVerificationEventTest {
    private val attachment = "f9fb84b7506cdbace4e2f4261358798e76e26767c784a3ead1a18f7c3d2ab741"
    private val basedOnId = "0adde8c9878ff0a9e50fac04b4e730f8eb21eb34ff57e185b9a9cd4259b005e9"
    private val basedOnAuthor = "1f9e547c2f31942623b8ad1d07713282e8640fd8cf474e9f79f18ace8af216ed"

    private fun create(
        tags: TagArray,
        content: String = "",
    ): Event = EventFactory.create("0".repeat(64), "1".repeat(64), 1L, BuildVerificationEvent.KIND, tags, content, "")

    private fun real() = assertIs<BuildVerificationEvent>(Event.fromJson(WalletScrutinyFixtures.REAL_BUILD_VERIFICATION))

    @Test
    fun realVerificationAccessors() {
        val verification = real()
        assertEquals("io.hexawallet.bitcoinkeeper:2.6.3:android:be7e304ae334b79c2f445efcf07ac8b0c4a9e120fbd35e87b3d85b03db945fea", verification.dTag())
        assertEquals("io.hexawallet.bitcoinkeeper", verification.productId())
        assertEquals("2.6.3", verification.version())
        assertEquals("android", verification.platform())
        assertEquals("ftbfs", verification.statusCode())
        assertEquals(BuildStatus.FTBFS, verification.status())
        assertFalse(verification.isReproducible())
        assertEquals(4, verification.hashes().size)
        assertEquals(listOf(attachment), verification.fileAttachments())
        assertEquals(
            listOf(
                OutputFileTag(
                    "io.hexawallet.bitcoinkeeper_bc63fb293d075a929abc0f216a179756e009c1bdb71e9f4878bdf334be293eb5_script.cast",
                    "e4fd671d7946ce912663e6c6ec82200a3578519722bf50909293253dfeacd2fa",
                ),
            ),
            verification.outputFiles(),
        )
        assertEquals(BasedOnTag(basedOnId, basedOnAuthor), verification.basedOn())
        assertNull(verification.issueTrackerUrl())
        assertEquals("Automatic verification by WalletScrutiny Build Server", verification.description())
        assertTrue(verification.report()!!.startsWith("**Failed to build.** We could not build version 2.6.3"))
    }

    @Test
    fun coversTheBundleItVerifies() {
        val bundle = assertIs<AssetBundleEvent>(Event.fromJson(WalletScrutinyFixtures.REAL_ASSET_BUNDLE))
        assertTrue(real().covers(bundle))

        val partial = assertIs<BuildVerificationEvent>(create(arrayOf(arrayOf("i", "x"), arrayOf("status", "reproducible"), arrayOf("x", bundle.hashes().first()))))
        assertFalse(partial.covers(bundle))
    }

    @Test
    fun edgesAreTheAttachmentsAndTheBasis() {
        val verification = real()
        assertEquals(listOf(attachment, basedOnId), verification.linkedEventIds())
        assertEquals(listOf(basedOnAuthor), verification.linkedPubKeys())
        assertEquals(emptyList(), verification.eventHints())
        assertEquals(emptyList(), verification.pubKeyHints())
    }

    @Test
    fun indexesTheDescriptionAndTheReport() {
        val verification = real()
        val content = verification.indexableContent()
        assertTrue(content.startsWith("Automatic verification by WalletScrutiny Build Server\n**Failed to build.**"))

        val visited = mutableListOf<String>()
        verification.forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(content, visited.joinToString(verification.indexableSeparator()))

        var seen = 0
        verification.forEachIndexableField {
            seen++
            false
        }
        assertEquals(1, seen)
    }

    @Test
    fun theKindIsSplitByTags() {
        // WalletScrutiny: `i` + `status`.
        assertIs<BuildVerificationEvent>(create(arrayOf(arrayOf("i", "app"), arrayOf("status", "reproducible"))))
        // Kanban board: a title, or a named column.
        assertIs<KanbanBoardEvent>(create(arrayOf(arrayOf("d", "b"), arrayOf("title", "Board"))))
        assertIs<KanbanBoardEvent>(create(arrayOf(arrayOf("d", "b"), arrayOf("col", "todo", "To Do", "0"))))
        // The planner: `b`, an unnamed `col` and a `status`, encrypted content.
        val planner =
            create(
                arrayOf(arrayOf("d", "fasting-reminder:2026-11-02"), arrayOf("b", "5".repeat(64)), arrayOf("col", "day"), arrayOf("status", "open")),
                "7FHuCqnDbjbdfMnG5PJ9N/IQV5roKdcX9DlzXCXq+dK9OYVTjxUgqfcaM4PnFfISAADQ",
            )
        assertEquals(UnrecognizedKind30301Event::class, planner::class)
        assertFalse(planner is SearchableEvent)
        assertEquals("fasting-reminder:2026-11-02", (planner as UnrecognizedKind30301Event).dTag())
        // Half a verification is not one.
        assertEquals(UnrecognizedKind30301Event::class, create(arrayOf(arrayOf("i", "app")))::class)
        assertEquals(UnrecognizedKind30301Event::class, create(arrayOf(arrayOf("i", ""), arrayOf("status", "ftbfs")))::class)
        assertEquals(UnrecognizedKind30301Event::class, create(emptyArray())::class)
    }

    @Test
    fun probeAnswersAsTheBoard() {
        assertTrue(EventFactory.isKnownKind(BuildVerificationEvent.KIND))
        assertIs<KanbanBoardEvent>(EventFactory.probe(BuildVerificationEvent.KIND))
    }

    @Test
    fun malformedTagsAndContentReadAsNull() {
        val verification =
            assertIs<BuildVerificationEvent>(
                create(
                    arrayOf(
                        arrayOf("i", "app"),
                        arrayOf("status", "rebuilt-ish"),
                        arrayOf("x", "nope"),
                        arrayOf("file-attachment", "not-an-id"),
                        arrayOf("output-file", "log.txt"),
                        arrayOf("output-file", "log.txt", "zz"),
                        arrayOf("based-on", "short:$basedOnAuthor"),
                        arrayOf("issue-tracker-url", ""),
                    ),
                    "not json",
                ),
            )
        assertEquals("rebuilt-ish", verification.statusCode())
        assertNull(verification.status())
        assertEquals(emptyList(), verification.hashes())
        assertEquals(emptyList(), verification.fileAttachments())
        assertEquals(emptyList(), verification.outputFiles())
        assertNull(verification.basedOn())
        assertNull(verification.issueTrackerUrl())
        assertNull(verification.description())
        assertNull(verification.report())
        assertEquals("", verification.indexableContent())
        assertEquals(emptyList(), verification.linkedEventIds())
        assertEquals(emptyList(), verification.linkedPubKeys())

        // A based-on without a (valid) author still names the verification.
        val noAuthor = assertIs<BuildVerificationEvent>(create(arrayOf(arrayOf("i", "a"), arrayOf("status", "warning"), arrayOf("based-on", "$basedOnId:nobody"))))
        assertEquals(BasedOnTag(basedOnId, null), noAuthor.basedOn())
        assertEquals(listOf(basedOnId), noAuthor.linkedEventIds())
        assertEquals(emptyList(), noAuthor.linkedPubKeys())
    }

    @Test
    fun buildRoundTrips() {
        val hashes = listOf("a".repeat(64), "b".repeat(64))
        val template =
            BuildVerificationEvent.build(
                productId = "app.zeusln.zeus",
                version = "1.2.3",
                platform = "android",
                status = BuildStatus.REPRODUCIBLE,
                verifiedHashes = hashes,
                description = "Zeus 1.2.3 split APKs",
                report = "All four files match.",
                createdAt = 5L,
            ) {
                fileAttachments(listOf(attachment))
                outputFiles(listOf(OutputFileTag("build.log", "c".repeat(64))))
                issueTrackerUrl("https://example.com/issues/1")
                basedOn(BasedOnTag(basedOnId, basedOnAuthor))
            }
        assertEquals(BuildVerificationEvent.KIND, template.kind)
        val verification = assertIs<BuildVerificationEvent>(create(template.tags, template.content))
        assertEquals("app.zeusln.zeus:1.2.3:android:${"a".repeat(64)}", verification.dTag())
        assertEquals("app.zeusln.zeus", verification.productId())
        assertEquals("1.2.3", verification.version())
        assertEquals("android", verification.platform())
        assertEquals(BuildStatus.REPRODUCIBLE, verification.status())
        assertTrue(verification.isReproducible())
        assertEquals(hashes, verification.hashes())
        assertEquals(listOf(attachment), verification.fileAttachments())
        assertEquals(listOf(OutputFileTag("build.log", "c".repeat(64))), verification.outputFiles())
        assertEquals("https://example.com/issues/1", verification.issueTrackerUrl())
        assertEquals(BasedOnTag(basedOnId, basedOnAuthor), verification.basedOn())
        assertEquals("Zeus 1.2.3 split APKs", verification.description())
        assertEquals("All four files match.", verification.report())
    }
}
