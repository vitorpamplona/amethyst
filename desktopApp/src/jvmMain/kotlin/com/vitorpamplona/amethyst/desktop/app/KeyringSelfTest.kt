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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import java.io.File

/**
 * `Amethyst --keyring-self-test`: writes, reads back and deletes a probe secret in the OS keyring
 * through the same storage a login uses, prints `PASS` or `FAIL` with the backend, and returns the
 * exit code (0 or 1). CI runs it on every packaged build, where the release-only breakages live
 * (classes stripped by ProGuard, a jar whose signature ProGuard broke, a missing sandbox
 * permission). It never falls back to the password file: no keyring is a failure here.
 *
 * `AMETHYST_SELF_TEST_REPORT=<file>` also writes the line to that file, for launchers with no
 * console (the Windows app is a GUI-subsystem executable).
 */
fun runKeyringSelfTest(): Int {
    val result = SecureKeyStorage.create(null).selfTest()
    val line = (if (result.ok) "PASS" else "FAIL") + " keyring=${result.keyring ?: "none"}: ${result.detail}"
    println("[keyring-self-test] $line")
    System.getenv("AMETHYST_SELF_TEST_REPORT")?.takeIf { it.isNotBlank() }?.let { path ->
        runCatching { File(path).writeText(line + "\n") }
    }
    return if (result.ok) 0 else 1
}
