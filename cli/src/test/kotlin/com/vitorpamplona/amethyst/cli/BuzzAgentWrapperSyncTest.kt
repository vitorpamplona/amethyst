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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gated-runner wrappers exist twice: `cli/src/main/resources/buzz-agent/` is what
 * `amy buzz agent up` extracts into `~/.amy/buzz-agent`, and `tools/buzz-agent/` is the
 * readable reference `tools/buzz-agent/README.md` tells people to point `--exec` at.
 *
 * Nothing in the build copies one to the other, so a fix applied to only one half ships a
 * runner that behaves differently from its own documentation. This pins them together.
 */
class BuzzAgentWrapperSyncTest {
    @Test
    fun bundledWrappersMatchTheToolsReference() {
        val bundled = repoRoot.resolve("cli/src/main/resources/buzz-agent")
        val reference = repoRoot.resolve("tools/buzz-agent")

        val scripts =
            bundled
                .listFiles()
                ?.filter { it.isFile }
                .orEmpty()
                .sortedBy { it.name }
        assertTrue(scripts.isNotEmpty(), "no bundled wrappers found in $bundled")

        scripts.forEach { script ->
            val twin = reference.resolve(script.name)
            assertTrue(twin.isFile, "${script.name} has no counterpart in tools/buzz-agent — add one")
            assertEquals(
                script.readText(),
                twin.readText(),
                "${script.name} drifted between cli/src/main/resources/buzz-agent and tools/buzz-agent — " +
                    "apply the change to both copies",
            )
        }
    }

    /** Walks up from the test's working directory to the checkout root (the dir holding both trees). */
    private val repoRoot: File
        get() =
            generateSequence(File(".").absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "tools/buzz-agent").isDirectory && File(it, "cli/src/main/resources/buzz-agent").isDirectory }
                ?: error("could not locate the repo root from ${File(".").absolutePath}")
}
