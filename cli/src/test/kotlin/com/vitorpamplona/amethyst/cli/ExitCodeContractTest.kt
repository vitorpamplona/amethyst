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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The documented exit-code contract (README/DEVELOPMENT): 0 success,
 * 1 runtime error, 2 bad arguments, 124 timeout. These tests pin the
 * codes an interop script keys on.
 */
class ExitCodeContractTest {
    @Test
    fun noSubcommandExits2() {
        assertEquals(2, amy().exit)
    }

    @Test
    fun unknownSubcommandExits2() {
        val r = amy("frobnicate")
        assertEquals(2, r.exit)
        assertTrue(r.stderr.contains("unknown subcommand"))
    }

    @Test
    fun unknownSubcommandPrintsShortVerbListNotFullUsage() {
        val r = amy("frobnicate")
        // The full reference is ~400 lines; the error path must stay one screen.
        assertTrue(r.stderr.lines().size < 30, "expected a short verb list, got ${r.stderr.lines().size} lines")
    }

    @Test
    fun missingPositionalExits2() {
        assertEquals(2, amy("decode").exit)
    }

    @Test
    fun unknownSubVerbExits2() {
        val r = amy("notes", "bogusverb")
        assertEquals(2, r.exit)
        assertTrue(r.stderr.contains("bad_args"))
    }

    @Test
    fun nonNumericFilterFlagExits2() {
        assertEquals(2, amy("filter", "--limit", "ten").exit)
    }

    @Test
    fun unresolvableAuthorErrorsInsteadOfSilentlyDropping() {
        // Historically `--author bob@example.com` was silently dropped and the
        // query ran unfiltered — the worst possible failure mode for a script.
        val r = amy("filter", "--author", "bob@example.com")
        assertEquals(2, r.exit)
        assertTrue(r.stderr.contains("--author"))
    }

    @Test
    fun unknownFlagTypoExits2() {
        val r = amy("key", "generate", "--limt", "5")
        assertEquals(2, r.exit)
        assertTrue(r.stderr.contains("--limt"))
    }

    @Test
    fun topLevelHelpExits0() {
        assertEquals(0, amy("--help").exit)
    }

    @Test
    fun groupHelpExits0AndDoesNotRun() {
        val r = amy("notes", "--help")
        assertEquals(0, r.exit)
        assertTrue(r.stderr.contains("post"), "group help should list its sub-verbs")
    }

    @Test
    fun flatCommandHelpExits0AndDoesNotRun() {
        // `fetch --help` used to RUN A REAL NETWORK FETCH. It must print usage
        // and exit 0 without touching the network (an isolated empty ~/.amy +
        // instant return is the observable proof).
        val r = amy("fetch", "--help")
        assertEquals(0, r.exit)
        assertTrue(r.stderr.contains("fetch"))
        assertTrue(r.stdout.isBlank(), "help must not emit a result object")
    }

    @Test
    fun powMineTimeoutExits124() {
        val template = """{"created_at":1,"kind":1,"tags":[],"content":"x"}"""
        val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
        val r = amy("pow", "mine", "--target", "60", "--timeout", "1", "--pubkey", pubkey, template)
        assertEquals(124, r.exit)
    }

    @Test
    fun jsonErrorsAreSingleJsonObjectOnStderr() {
        val r = amy("--json", "decode")
        assertEquals(2, r.exit)
        assertTrue(r.stdout.isBlank(), "errors must not write stdout")
        val errLines =
            r.stderr
                .trim()
                .lines()
                .filter { it.isNotBlank() && it.startsWith("{") }
        assertEquals(1, errLines.size, "expected exactly one JSON error object on stderr, got: ${r.stderr}")
        val parsed = Output.mapper.readTree(errLines.single())
        assertEquals("bad_args", parsed["error"].asText())
    }

    @Test
    fun errorsAreNotDoubleReported() {
        // Args used to print a plain-text line AND throw (reported again by
        // main) — two error lines per failure.
        val r = amy("decode")
        val errorLines =
            r.stderr
                .trim()
                .lines()
                .filter { it.contains("missing", ignoreCase = true) }
        assertEquals(1, errorLines.size, "expected one error line, got: ${r.stderr}")
    }
}
