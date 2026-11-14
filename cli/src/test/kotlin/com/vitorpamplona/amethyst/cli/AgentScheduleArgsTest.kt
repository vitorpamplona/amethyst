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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `amy agent` and `amy schedule` refuse bad arguments with exit 2 before they need an account
 * or a relay, and an agent key handed over the wrong way is never echoed back.
 */
class AgentScheduleArgsTest {
    private val secret = "sk-or-v1-thisMustNeverBePrinted0123456789"

    @Test
    fun aKeyOnTheCommandLineIsRefusedAndNotEchoed() {
        val r = amy("--json", "agent", "set", "https://openrouter.ai/api/v1", secret)
        assertEquals(2, r.exit)
        assertTrue(r.stderr.contains("\"error\":\"bad_args\""))
        assertFalse(r.stdout.contains(secret) || r.stderr.contains(secret))
    }

    @Test
    fun aKeyForAPlainHttpHostIsRefusedAndNotEchoed() {
        val file = File.createTempFile("amy-key", ".txt").apply { writeText(secret + "\n") }
        try {
            val r = amy("agent", "set", "http://runner.example:8080/v1", "--key-file", file.absolutePath)
            assertEquals(2, r.exit)
            assertTrue(r.stderr.contains("https"))
            assertFalse(r.stdout.contains(secret) || r.stderr.contains(secret))
        } finally {
            file.delete()
        }
    }

    @Test
    fun aProviderThatNeedsAKeyNeedsOne() {
        assertEquals(2, amy("agent", "set", "https://openrouter.ai/api/v1").exit)
        assertEquals(2, amy("agent", "set", "https://openrouter.ai/api/v1", "--key-file", "/nonexistent/key").exit)
        assertEquals(2, amy("agent", "set", "notaurl").exit)
    }

    @Test
    fun agentVerbsAndHelp() {
        assertEquals(2, amy("agent").exit)
        assertEquals(2, amy("agent", "bogus").exit)
        assertEquals(2, amy("agent", "remove").exit)
        val help = amy("agent", "--help")
        assertEquals(0, help.exit)
        assertTrue(help.stderr.contains("--key-file"))
    }

    @Test
    fun aScheduleNeedsACronAndAZoneByName() {
        assertEquals(2, amy("schedule", "set").exit)
        assertEquals(2, amy("schedule", "set", "--cron", "61 * * * *").exit)
        assertEquals(2, amy("schedule", "set", "--cron", "@daily").exit)
        assertEquals(2, amy("schedule", "set", "--cron", "0 7 * * *", "--tz", "+05:00").exit)
        assertEquals(2, amy("schedule", "set", "--cron", "0 7 * * *", "--tz", "UTC+5").exit)
        assertEquals(2, amy("schedule", "set", "--cron", "0 7 * * *", "--tz", "Mars/Olympus").exit)
        assertEquals(2, amy("schedule", "set", "--cron", "0 7 * * *", "--bogus").exit)
        assertEquals(2, amy("schedule", "bogus").exit)
        assertEquals(0, amy("schedule", "--help").exit)
    }
}
