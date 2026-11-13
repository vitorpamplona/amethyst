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

import com.vitorpamplona.claudecode.process.ClaudeCodeStarter
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The service against a real session folder, with a CLI that never starts. */
class ClaudeCodeServiceTest {
    private lateinit var config: File
    private lateinit var work: File
    private lateinit var scope: CoroutineScope
    private lateinit var service: ClaudeCodeService
    private val id = "11111111-2222-3333-4444-555555555555"
    private lateinit var sessionFile: File

    @BeforeTest
    fun setUp() {
        config = Files.createTempDirectory("claude-config").toFile()
        work = Files.createTempDirectory("work").toFile()
        val project = File(config, "projects/-work").apply { mkdirs() }
        val cwd = work.absolutePath.replace("\\", "\\\\")
        sessionFile = File(project, "$id.jsonl")
        sessionFile.writeText(
            (1..40).joinToString("\n", postfix = "\n") { i ->
                val parent = if (i == 1) "null" else "\"m${i - 1}\""
                if (i % 2 == 1) {
                    """{"type":"user","uuid":"m$i","parentUuid":$parent,"sessionId":"$id","cwd":"$cwd","message":{"role":"user","content":"old question $i"}}"""
                } else {
                    """{"type":"assistant","uuid":"m$i","parentUuid":$parent,"sessionId":"$id","cwd":"$cwd","message":{"role":"assistant","content":[{"type":"text","text":"old answer $i"}]}}"""
                }
            },
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        service =
            ClaudeCodeService(
                store = ClaudeSessionStore(config),
                launcher = ClaudeCodeStarter { _, _ -> throw IOException("not in tests") },
                scope = scope,
                findExecutable = { null },
            )
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        config.deleteRecursively()
        work.deleteRecursively()
    }

    private suspend fun waitFor(condition: () -> Boolean) =
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }

    @Test
    fun deletingAForkThatNeverStartedLeavesItsSourceAlone() =
        runBlocking {
            service.refresh()
            val fork = assertNotNull(service.fork(id))
            waitFor { service.rows.value.any { it.key == fork.key } }
            val row = service.rows.value.single { it.key == fork.key }
            // The fork still carries the source's id inside; its row must not.
            assertNull(row.sessionId)

            service.delete(row)
            assertTrue(sessionFile.isFile, "the source session was deleted")
            assertTrue(service.rows.value.none { it.key == fork.key })
        }

    @Test
    fun aMessageSentBeforeTheHistoryLoadsLandsAfterIt() =
        runBlocking {
            service.refresh()
            val chat = assertNotNull(service.chat(id))
            chat.send("new question")
            waitFor {
                chat.state.value.items
                    .any { it is ChatItem.UserText && it.text == "new question" }
            }
            val texts =
                chat.state.value.items
                    .filterIsInstance<ChatItem.UserText>()
                    .map { it.text }
            assertEquals("new question", texts.last())
            assertEquals("old question 1", texts.first())
        }
}
