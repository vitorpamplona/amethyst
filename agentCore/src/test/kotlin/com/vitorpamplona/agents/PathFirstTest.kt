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
package com.vitorpamplona.agents

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PathFirstTest {
    @Test
    fun foldersGoFirstOnceAndTheRestKeepsItsOrder() {
        val env = mutableMapOf("PATH" to listOf("/usr/bin", "/opt/git/bin", "/bin").joinToString(File.pathSeparator))
        env.putPathFirst(listOf(File("/opt/git/bin")))
        assertEquals(listOf("/opt/git/bin", "/usr/bin", "/bin"), env.getValue("PATH").split(File.pathSeparator))
    }

    @Test
    fun aMissingPathIsCreated() {
        val env = mutableMapOf<String, String>()
        env.putPathFirst(listOf(File("/opt/git/bin")))
        assertEquals("/opt/git/bin", env["PATH"])
    }

    @Test
    fun nothingToAddChangesNothing() {
        val env = mutableMapOf("PATH" to "/usr/bin")
        env.putPathFirst(emptyList())
        assertEquals(mapOf("PATH" to "/usr/bin"), env)
    }
}
