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

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files

/**
 * Drives [runCli] in-process with captured stdout/stderr and an isolated
 * `~/.amy` (via the `amy.home` system-property seam in [DataDir.DEFAULT_ROOT]).
 * The global [Output.mode] is reset around every run so `--json` in one
 * invocation cannot leak into the next.
 */
data class CliResult(
    val exit: Int,
    val stdout: String,
    val stderr: String,
) {
    val stdoutLines: List<String> get() = stdout.trim().lines().filter { it.isNotBlank() }
}

fun amy(vararg argv: String): CliResult {
    val outBuf = ByteArrayOutputStream()
    val errBuf = ByteArrayOutputStream()
    val prevOut = System.out
    val prevErr = System.err
    val prevHome = System.getProperty("amy.home")
    val tempHome = Files.createTempDirectory("amy-test").toFile()
    System.setProperty("amy.home", tempHome.absolutePath)
    Output.mode = Output.Mode.TEXT
    return try {
        System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
        System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
        val exit = runCli(argv.toList().toTypedArray())
        CliResult(exit, outBuf.toString(Charsets.UTF_8), errBuf.toString(Charsets.UTF_8))
    } finally {
        System.setOut(prevOut)
        System.setErr(prevErr)
        Output.mode = Output.Mode.TEXT
        if (prevHome == null) System.clearProperty("amy.home") else System.setProperty("amy.home", prevHome)
        tempHome.deleteRecursively()
    }
}
