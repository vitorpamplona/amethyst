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
package com.vitorpamplona.amethyst.desktop.napplet

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.cef.browser.CefBrowser
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefDownloadItemCallback
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.util.Date
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [PageDownloads] as Chromium drives it, with Chromium's side faked: no browser needed. */
class PageDownloadsTest {
    private val tmp = createTempDirectory("page-downloads-test").toFile()
    private val holding = File(tmp, "holding")
    private val downloads = File(tmp, "home/Downloads")
    private var realHome: String? = null

    // The handler never looks at the browser.
    private val browser = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CefBrowser::class.java)) { _, _, _ -> null } as CefBrowser

    @Before
    fun setUp() {
        realHome = System.getProperty("user.home")
        System.setProperty("user.home", File(tmp, "home").absolutePath)
    }

    @After
    fun tearDown() {
        System.setProperty("user.home", realHome!!)
        tmp.deleteRecursively()
    }

    private class Item(
        private val id: Int,
        var inProgress: Boolean = true,
        var complete: Boolean = false,
    ) : CefDownloadItem {
        override fun isValid() = true

        override fun isInProgress() = inProgress

        override fun isComplete() = complete

        override fun isCanceled() = !inProgress && !complete

        override fun getCurrentSpeed() = 0L

        override fun getPercentComplete() = if (complete) 100 else 10

        override fun getTotalBytes() = 1_000L

        override fun getReceivedBytes() = 100L

        override fun getStartTime() = Date()

        override fun getEndTime() = Date()

        override fun getFullPath() = ""

        override fun getId() = id

        override fun getURL() = "https://example.com/file$id.bin"

        override fun getSuggestedFileName() = "file$id.bin"

        override fun getContentDisposition() = ""

        override fun getMimeType() = "application/octet-stream"
    }

    private class Control : CefDownloadItemCallback {
        var paused = 0
        var resumed = 0
        var canceled = 0

        override fun cancel() {
            canceled++
        }

        override fun pause() {
            paused++
        }

        override fun resume() {
            resumed++
        }
    }

    private class Answer : CefBeforeDownloadCallback {
        @Volatile
        var path: String? = null

        override fun Continue(
            downloadPath: String,
            showDialog: Boolean,
        ) {
            path = downloadPath
        }
    }

    // A download as Chromium starts one: a report first, then the question; returns where it went.
    private suspend fun start(
        page: PageDownloads,
        item: Item,
        control: Control,
    ): Answer {
        page.onDownloadUpdated(browser, item, control)
        val answer = Answer()
        page.onBeforeDownload(browser, item, item.suggestedFileName, answer)
        return answer
    }

    private suspend fun placed(answer: Answer): File =
        withTimeout(10_000) {
            while (answer.path == null) delay(10)
            File(answer.path!!)
        }

    private suspend fun stateOf(
        page: PageDownloads,
        id: Int,
        state: BrowserDownload.State,
    ): BrowserDownload = withTimeout(10_000) { page.list.first { list -> list.any { it.id == id && it.state == state } } }.first { it.id == id }

    @Test
    fun aDownloadWaitsInTheAppsFolderHeldThenMovesToDownloads() =
        runBlocking {
            val page = PageDownloads(holding)
            val item = Item(1)
            val control = Control()
            val target = placed(start(page, item, control))
            assertTrue(target.startsWith(holding), "it runs in the app's folder, not the user's: $target")
            assertEquals("file1.bin", target.name)

            // Held while asked.
            page.onDownloadUpdated(browser, item, control)
            assertEquals(1, control.paused)
            assertEquals(
                BrowserDownload.State.ASKING,
                page.list.value
                    .single()
                    .state,
            )

            // Saved: it goes on, and once complete it is the user's.
            page.save(1)
            assertEquals(1, control.resumed)
            target.writeText("the file")
            item.inProgress = false
            item.complete = true
            page.onDownloadUpdated(browser, item, control)
            val done = stateOf(page, 1, BrowserDownload.State.DONE)
            assertEquals(File(downloads, "file1.bin"), done.file)
            assertEquals("the file", done.file!!.readText())
            withTimeout(10_000) { while (target.parentFile.exists()) delay(10) }
        }

    @Test
    fun aSmallFileCompleteBeforeTheAnswerMovesOnSave() =
        runBlocking {
            val page = PageDownloads(holding)
            val item = Item(1)
            val control = Control()
            val target = placed(start(page, item, control))
            target.writeText("small")
            item.inProgress = false
            item.complete = true
            page.onDownloadUpdated(browser, item, control)
            delay(200)
            assertFalse(File(downloads, "file1.bin").exists(), "not the user's until they say so")
            assertEquals(
                BrowserDownload.State.ASKING,
                page.list.value
                    .single()
                    .state,
            )

            page.save(1)
            assertEquals("small", stateOf(page, 1, BrowserDownload.State.DONE).file!!.readText())
        }

    @Test
    fun aDownloadDroppedWhileAskedStops() =
        runBlocking {
            val page = PageDownloads(holding)
            val item = Item(1)
            val target = placed(start(page, item, Control()))
            item.inProgress = false
            page.onDownloadUpdated(browser, item, Control())
            stateOf(page, 1, BrowserDownload.State.STOPPED)
            page.save(1)
            assertEquals(
                BrowserDownload.State.STOPPED,
                page.list.value
                    .single()
                    .state,
                "there is nothing left to save",
            )
            withTimeout(10_000) { while (target.parentFile.exists()) delay(10) }
        }

    @Test
    fun aCanceledQuestionLeavesTheList() =
        runBlocking {
            val page = PageDownloads(holding)
            val control = Control()
            start(page, Item(1), control)
            page.cancel(1)
            assertEquals(1, control.canceled)
            assertTrue(page.list.value.isEmpty())
        }

    @Test
    fun pastThreeQuestionsTheRestAreRefusedUnasked() =
        runBlocking {
            val page = PageDownloads(holding)
            val controls = (1..5).map { Control() }
            controls.forEachIndexed { i, control -> start(page, Item(i + 1), control) }
            assertEquals(PageDownloads.MAX_QUESTIONS, page.list.value.count { it.state == BrowserDownload.State.ASKING })
            assertEquals(listOf(0, 0, 0, 1, 1), controls.map { it.canceled }, "the fourth and fifth are stopped at once")
        }

    @Test
    fun closingThePageStopsItsDownloads() =
        runBlocking {
            val page = PageDownloads(holding)
            val asked = Control()
            val running = Control()
            start(page, Item(1), asked)
            start(page, Item(2), running)
            page.save(2)
            page.close()
            assertEquals(1, asked.canceled, "an unanswered one stops")
            assertEquals(1, running.canceled, "and so does one on its way")
        }

    @Test
    fun theListDropsOnlyFinishedOnesPastFive() =
        runBlocking {
            val page = PageDownloads(holding)
            // Two finished, then three asking: five shown.
            for (id in 1..2) {
                val item = Item(id)
                val control = Control()
                placed(start(page, item, control))
                item.inProgress = false
                page.onDownloadUpdated(browser, item, control)
                stateOf(page, id, BrowserDownload.State.STOPPED)
            }
            for (id in 3..5) start(page, Item(id), Control())
            assertEquals(5, page.list.value.size)
            // Answered, a question's place frees up for one more: the oldest finished one goes.
            page.save(3)
            start(page, Item(6), Control())
            assertEquals(listOf(2, 3, 4, 5, 6), page.list.value.map { it.id })
        }
}
