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
package com.vitorpamplona.amethyst.commons.claudecode

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublishedArtifactsTest {
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("published").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val link = "https://claude.ai/artifact/Xq7PbR2mWt9KcVn4LsDfG1"

    /**
     * The opening of an `Artifact` publish's result as Claude Code returns it (recorded 2026-11-25;
     * the path and the artifact's id changed), with the rest of its guidance cut.
     */
    private fun published(
        path: String,
        version: Int = 1,
        url: String = link,
    ) = "Published $path at $url (Version $version, version id 1795618265-5d6b) Icon: \"chart\".\n\n" +
        "Durable wake subscription: arming in the background — not registered yet.\n\n" +
        "To update: publish the same file path here (keeps this URL); from another conversation pass the URL as `url`, or that publish creates a separate artifact. " +
        "This artifact is private: only its owner and the people the owner has given access can open the link."

    @Test
    fun aFirstPublishIsANewPrivatePage() {
        val input = """{"file_path":"/work/report.html","icon":"chart","description":"Q3 numbers by team."}"""
        val artifact = PublishedArtifacts.of("Artifact", input, published("/work/report.html"))!!

        assertEquals(PublishedArtifact.Kind.PAGE, artifact.kind)
        assertEquals(PublishedArtifact.Change.CREATED, artifact.change)
        assertEquals(link, artifact.url)
        assertEquals("/work/report.html", artifact.file)
        assertEquals("Q3 numbers by team.", artifact.description)
        assertEquals(1, artifact.version)
        // A page names itself in its <title>, read separately.
        assertNull(artifact.title)
    }

    @Test
    fun whileItRunsThereIsACardButNoLink() {
        val artifact = PublishedArtifacts.of("Artifact", """{"file_path":"/work/report.html"}""", null)!!
        assertNull(artifact.url)
        assertEquals(PublishedArtifact.Change.CREATED, artifact.change)
    }

    @Test
    fun republishingTheSameFileIsAnUpdate() {
        val artifact = PublishedArtifacts.of("Artifact", """{"file_path":"/work/report.html"}""", published("/work/report.html", version = 3))!!
        assertEquals(PublishedArtifact.Change.UPDATED, artifact.change)
        assertEquals(3, artifact.version)
        assertEquals(link, artifact.url)
    }

    @Test
    fun anUpdateByLinkKeepsThatLink() {
        val input = """{"file_path":"/work/report.html","url":"$link"}"""
        val result = published("/work/report.html", version = 2, url = "https://claude.ai/artifact/Somewhere0Else1Entirely2")
        val artifact = PublishedArtifacts.of("Artifact", input, result)!!
        assertEquals(PublishedArtifact.Change.UPDATED, artifact.change)
        assertEquals(link, artifact.url)
    }

    @Test
    fun anArtifactMadeFromATypeIsNotTheType() {
        val type = "https://claude.ai/artifact/8jTsAFQMFDb2oA8MsPJ2eL"
        val input = """{"type_url":"$type","title":"Q3 review","auto_open":"after_first_write"}"""
        // The type is named before the new artifact; only the new one is the card's.
        val result = "Created from $type: $link\nRead https://claude.ai/code/artifact/0b1c2d3e-4f50-6172-8394-a5b6c7d8e9f0 first."
        val artifact = PublishedArtifacts.of("Artifact", input, result)!!

        assertEquals(PublishedArtifact.Kind.TYPED, artifact.kind)
        assertEquals("Q3 review", artifact.title)
        assertEquals(link, artifact.url)
        assertNull(artifact.file)
    }

    @Test
    fun assetsListingsAndReadsAreNotPublications() {
        assertNull(PublishedArtifacts.of("Artifact", """{"url":"$link","file_path":"/work/logo.png","asset":true}""", "Uploaded."))
        assertNull(PublishedArtifacts.of("Artifact", """{"action":"list","limit":3}""", "- Page — $link"))
        assertNull(PublishedArtifacts.of("Artifact", """{"action":"read","url":"$link"}""", "<html>"))
        assertNull(PublishedArtifacts.of("Artifact", """{"action":"delete","url":"$link"}""", "Deleted."))
        assertNull(PublishedArtifacts.of("Artifact", "not json", null))
        assertNull(PublishedArtifacts.of("Bash", """{"command":"echo $link"}""", link))
    }

    @Test
    fun openingOneShowsItsLink() {
        val artifact = PublishedArtifacts.of("Artifact", """{"action":"open","url":"$link"}""", "Opened.")!!
        assertEquals(PublishedArtifact.Change.OPENED, artifact.change)
        assertEquals(link, artifact.url)
        // A page, a deck or a doc: the call doesn't say.
        assertEquals(PublishedArtifact.Kind.UNKNOWN, artifact.kind)
    }

    @Test
    fun aDocsFillIsNotReadAtAll() {
        // A section's fill carries its whole text: no "create", so it is turned away before parsing.
        val fill = """{"container":{"kind":"project","id":"3f2a"},"batch":[{"verb":"update","payload":{"ops":[{"with":"${"x".repeat(50_000)}"}]}}]}"""
        assertNull(PublishedArtifacts.of("mcp__Claude_Docs__batch", fill, "ok"))
        // A batch that creates a comment, not a doc, is parsed and still no card.
        assertNull(PublishedArtifacts.of("mcp__Claude_Docs__batch", """{"container":{"id":"3f2a"},"batch":[{"verb":"create","object":"utterance"}]}""", "ok"))
    }

    @Test
    fun aPublishThatNamesItsLinkIsAboutThatOneWhateverTheFile() {
        val other = "https://claude.ai/artifact/Somewhere0Else1Entirely2"
        val first = call("Artifact", """{"file_path":"/w/r.html"}""", published("/w/r.html"))
        val elsewhere = call("Artifact", """{"file_path":"/w/r.html","url":"$other"}""", result = null)
        val rows = listOf(first, elsewhere).toChatRows()

        val card = assertIs<ChatRow.Published>(rows[1])
        assertFalse(card.repeat)
        assertEquals(other, card.artifact.url)
    }

    @Test
    fun aDocStartedInABlankDocsArtifactIsThatCard() {
        val type = "https://claude.ai/artifact/Rp9naXUCj2xozpUkyQy19W"
        val blank = call("Artifact", """{"type_url":"$type","title":"Docs"}""", "Created $link from $type")
        val birth = call("mcp__Claude_Docs__batch", """{"container":{"kind":"project","create":{"name":"Rollout plan","artifact":"$link"}},"batch":[]}""", "ok")
        val rows = listOf(blank, birth).toChatRows()

        // One card, the doc's: no "Updated" line for its birth.
        val card = assertIs<ChatRow.Published>(rows.single())
        assertEquals(PublishedArtifact.Kind.DOCUMENT, card.artifact.kind)
        assertEquals("Rollout plan", card.artifact.title)
        assertEquals(PublishedArtifact.Change.CREATED, card.artifact.change)

        val listed = PublishedArtifacts.inSession(listOf(blank, birth)).single()
        assertEquals(PublishedArtifact.Kind.DOCUMENT, listed.kind)
        assertEquals("Rollout plan", listed.title)
    }

    @Test
    fun aLocalPageIsJudgedByWhatItLeadsTo() {
        val page = File(dir, "report.html").apply { writeText("<title>R</title>") }
        assertEquals(page.canonicalFile, PublishedArtifacts.localPage("report.html", dir))
        assertNull(PublishedArtifacts.localPage("gone.html", dir))
        assertNull(PublishedArtifacts.localPage("run.sh", dir))

        // A page's name on a link to something else is not a page.
        val script = File(dir, "run.sh").apply { writeText("#!/bin/sh\n") }
        val disguised = File(dir, "disguised.html")
        Files.createSymbolicLink(disguised.toPath(), script.toPath())
        assertNull(PublishedArtifacts.localPage(disguised.path, null))
        // A link to a page is that page.
        val alias = File(dir, "alias.html")
        Files.createSymbolicLink(alias.toPath(), page.toPath())
        assertEquals(page.canonicalFile, PublishedArtifacts.localPage(alias.path, null))
    }

    @Test
    fun aNewDocIsADocumentByTheNameItWasGiven() {
        val doc = "https://claude.ai/artifact/Q3-launch-plan-3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90"
        val input = """{"container":{"kind":"project","create":{"name":"Q3 launch plan","doc":{"markdown":"# Q3 launch plan"}}},"batch":[]}"""
        val ack = """{"created":{"bound":true},"lids":{"f":"afc001d0-ce07","n":"be1476b2-62c1"}}""" + "\n\nOpen it: $doc"

        for (name in listOf("mcp__Claude_Docs__batch", "mcp__claude_ai_Claude_Docs__batch")) {
            val artifact = PublishedArtifacts.of(name, input, ack)!!
            assertEquals(PublishedArtifact.Kind.DOCUMENT, artifact.kind, name)
            assertEquals(PublishedArtifact.Change.CREATED, artifact.change)
            assertEquals("Q3 launch plan", artifact.title)
            assertEquals(doc, artifact.url)
        }
    }

    @Test
    fun aDocsFillOrAnotherServersBatchIsNoCard() {
        val fill = """{"container":{"kind":"project","id":"3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90"},"batch":[{"verb":"update"}]}"""
        assertNull(PublishedArtifacts.of("mcp__Claude_Docs__batch", fill, "ok"))
        assertNull(PublishedArtifacts.of("mcp__Claude_Docs__guide", """{"items":["topic.index"]}""", "# topic.index"))
        val create = """{"container":{"create":{"name":"x"}}}"""
        assertNull(PublishedArtifacts.of("mcp__other_docs__batch", create, link))
        assertTrue(PublishedArtifacts.isDocsTool("mcp__Claude_Docs__update", "update"))
        assertFalse(PublishedArtifacts.isDocsTool("mcp__Claude_Docs__update", "batch"))
    }

    @Test
    fun aPageIsCalledByItsTitle() {
        val page = File(dir, "report.html")
        page.writeText("<meta charset=\"utf-8\">\n<TITLE>\n  Sales &amp; Ops\n  Review </TITLE><style>body{}</style>")
        val artifact = PublishedArtifacts.of("Artifact", """{"file_path":"report.html"}""", published("report.html"))!!

        // A relative path is the session folder's.
        assertEquals("Sales & Ops Review", PublishedArtifacts.withPageTitle(artifact, dir).title)
        // One the call named keeps that name.
        assertEquals("Mine", PublishedArtifacts.withPageTitle(artifact.copy(title = "Mine"), dir).title)
        // No title in the page, or no page: its file's name stands in.
        page.writeText("<p>hello</p>")
        assertNull(PublishedArtifacts.withPageTitle(artifact, dir).title)
        assertEquals("report", PublishedArtifacts.fallbackTitle(artifact))
    }

    @Test
    fun pagesAreWhatABrowserShows() {
        assertTrue(PublishedArtifacts.isPage("/w/index.html"))
        assertTrue(PublishedArtifacts.isPage("site/INDEX.HTM"))
        assertTrue(PublishedArtifacts.isPage("logo.svg"))
        assertFalse(PublishedArtifacts.isPage("notes.md"))
        assertFalse(PublishedArtifacts.isPage("run.sh"))
    }

    private var id = 0L

    private fun call(
        name: String,
        input: String = "{}",
        result: String? = "ok",
        isError: Boolean = false,
        subagent: List<ChatItem> = emptyList(),
    ) = ChatItem.ToolCall(id++, "t$id", name, null, input, result, isError, subagent)

    @Test
    fun aPublishGetsACardAndItsLaterPublishesALine() {
        val write = call("Write", """{"file_path":"/w/r.html","content":"<title>R</title>"}""")
        val first = call("Artifact", """{"file_path":"/w/r.html"}""", published("/w/r.html"))
        val bash = call("Bash", """{"command":"ls"}""")
        val list = call("Artifact", """{"action":"list"}""", "- R — $link")
        val again = call("Artifact", """{"file_path":"/w/r.html"}""", published("/w/r.html", version = 2))
        val shown = call("Artifact", """{"action":"open","url":"$link"}""", "Opened.")
        val refused = call("Artifact", """{"file_path":"/w/other.html"}""", "Error: file too large", isError = true)
        val rows = listOf(write, first, bash, list, again, shown, refused).toChatRows()

        assertEquals(6, rows.size)
        assertEquals(write, (rows[0] as ChatRow.Single).item)
        val card = assertIs<ChatRow.Published>(rows[1])
        assertFalse(card.repeat)
        assertEquals(link, card.artifact.url)
        // A listing is a tool call like any other: folded with its neighbours.
        assertEquals(listOf(bash, list), assertIs<ChatRow.ToolGroup>(rows[2]).calls)
        assertTrue(assertIs<ChatRow.Published>(rows[3]).repeat)
        // Opening it is a line that names it from its card: the open call alone knows only the link.
        val opened = assertIs<ChatRow.Published>(rows[4])
        assertTrue(opened.repeat)
        assertEquals("/w/r.html", opened.artifact.file)
        // A publish that failed says why on its own row.
        assertEquals(refused, (rows[5] as ChatRow.Single).item)
    }

    @Test
    fun aRepublishOfTheSameFileIsALineWhileItRuns() {
        val first = call("Artifact", """{"file_path":"/w/r.html"}""", published("/w/r.html"))
        val again = call("Artifact", """{"file_path":"/w/r.html"}""", result = null)
        val other = call("Artifact", """{"file_path":"/w/s.html"}""", result = null)
        val rows = listOf(first, again, other).toChatRows()

        val line = assertIs<ChatRow.Published>(rows[1])
        assertTrue(line.repeat)
        // No result yet: the link and the change come from the card above.
        assertEquals(link, line.artifact.url)
        assertEquals(PublishedArtifact.Change.UPDATED, line.artifact.change)
        // Another file is another page: its own card.
        assertFalse(assertIs<ChatRow.Published>(rows[2]).repeat)
    }

    @Test
    fun openingANewDocIsALineThatNamesTheDoc() {
        val doc = "https://claude.ai/artifact/Plan-0b1c2d3e-4f50-6172-8394-a5b6c7d8e9f0"
        val birth = call("mcp__Claude_Docs__batch", """{"container":{"create":{"name":"Rollout plan"}},"batch":[]}""", "Open it: $doc")
        val open = call("Artifact", """{"action":"open","url":"$doc"}""", "Opened.")
        val line = assertIs<ChatRow.Published>(listOf(birth, open).toChatRows()[1])

        assertTrue(line.repeat)
        assertEquals(PublishedArtifact.Kind.DOCUMENT, line.artifact.kind)
        assertEquals("Rollout plan", line.artifact.title)
        assertEquals(PublishedArtifact.Change.OPENED, line.artifact.change)
    }

    @Test
    fun theSessionListHasEachArtifactOnceNewestFirst() {
        val doc = "https://claude.ai/artifact/Plan-0b1c2d3e-4f50-6172-8394-a5b6c7d8e9f0"
        val page = call("Artifact", """{"file_path":"/w/r.html","description":"First"}""", published("/w/r.html"))
        val docCall =
            call(
                "Agent",
                subagent = listOf(call("mcp__Claude_Docs__batch", """{"container":{"create":{"name":"Plan"}},"batch":[]}""", "link: $doc")),
            )
        val update = call("Artifact", """{"file_path":"/w/r.html"}""", published("/w/r.html", version = 2))
        val running = call("Artifact", """{"file_path":"/w/s.html"}""", null)
        val failed = call("Artifact", """{"file_path":"/w/t.html"}""", published("/w/t.html", url = "https://claude.ai/artifact/Failed0Publish1Xx"), isError = true)

        val all = PublishedArtifacts.inSession(listOf(page, docCall, update, running, failed))

        assertEquals(listOf(link, doc), all.map { it.url })
        // The page as its update left it, still with the description it was first given.
        assertEquals(PublishedArtifact.Change.UPDATED, all[0].change)
        assertEquals(2, all[0].version)
        assertEquals("First", all[0].description)
        assertEquals("Plan", all[1].title)
    }
}
