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
package com.vitorpamplona.amethyst.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommitBarrierTest {
    @Test
    fun holdsTheCommitUntilTheHarnessSaysGo() =
        runBlocking {
            val dir = Files.createTempDirectory("barrier").toFile()
            try {
                val held = async(Dispatchers.IO) { CommitBarrier.await(dir) }
                // It says it is ready, then waits.
                withTimeout(10_000) {
                    while (dir.listFiles { f -> f.name.startsWith("ready-") }.isNullOrEmpty()) delay(20)
                }
                delay(300)
                assertFalse(held.isCompleted, "the Commit went out before the go")

                assertTrue(File(dir, "go").createNewFile())
                withTimeout(10_000) { held.await() }
                assertEquals(1, dir.listFiles { f -> f.name.startsWith("ready-") }?.size)
            } finally {
                dir.deleteRecursively()
            }
        }
}
