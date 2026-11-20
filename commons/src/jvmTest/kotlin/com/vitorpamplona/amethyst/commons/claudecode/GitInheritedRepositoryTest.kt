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
import kotlin.test.assertTrue

/**
 * Git started from a git hook inherits GIT_DIR and GIT_INDEX_FILE, which win over the directory
 * it runs in. The app's git must work on the directory it was given.
 */
class GitInheritedRepositoryTest {
    private lateinit var tmp: File

    @BeforeTest
    fun setUp() {
        tmp = Files.createTempDirectory("inherited-repo").toFile()
    }

    @AfterTest
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun dropsTheVariablesThatNameARepositoryAndKeepsTheRest() {
        val env =
            mutableMapOf(
                "GIT_DIR" to "/elsewhere/.git",
                "GIT_INDEX_FILE" to "/elsewhere/.git/index",
                "GIT_WORK_TREE" to "/elsewhere",
                "GIT_COMMON_DIR" to "/elsewhere/.git",
                "GIT_CONFIG_COUNT" to "1",
                "PATH" to "/usr/bin",
            )
        env.forgetInheritedRepository()
        assertEquals(mapOf("GIT_CONFIG_COUNT" to "1", "PATH" to "/usr/bin"), env)
    }

    @Test
    fun gitInitWorksOnItsOwnDirectoryUnderAHooksEnvironment() {
        // Another repository, as the hook's GIT_DIR would name it.
        val elsewhere = File(tmp, "elsewhere").apply { mkdirs() }
        assertEquals(0, GitWorkspace.run(elsewhere, listOf("git", "init", "-q")).code)
        val target = File(tmp, "target").apply { mkdirs() }

        val process =
            ProcessBuilder("git", "init", "-q", "--bare")
                .directory(target)
                .apply {
                    environment()["GIT_DIR"] = File(elsewhere, ".git").absolutePath
                    environment().forgetInheritedRepository()
                }.start()
        assertEquals(0, process.waitFor())

        assertTrue(File(target, "HEAD").isFile, "the bare repository is where it was asked for")
        assertFalse(GitWorkspace.git(elsewhere, "config", "core.bare").orEmpty().trim() == "true", "the other repository is untouched")
    }
}
