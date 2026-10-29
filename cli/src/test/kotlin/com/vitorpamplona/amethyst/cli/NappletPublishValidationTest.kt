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
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `amy napplet publish` refuses flags that would build an invalid NIP-5D manifest before it uploads
 * anything, so amy never publishes a napplet that its own `list`/`fetch`/`serve` then reject.
 */
class NappletPublishValidationTest {
    private val dir: File = Files.createTempDirectory("napplet-publish").toFile()
    private val index = File(dir, "index.html").apply { writeText("<!doctype html><title>t</title>") }

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun publish(vararg flags: String) = amy("napplet", "publish", index.path, "--server", "https://blossom.example.com", "--description", "A napplet", *flags)

    private fun assertRefused(
        vararg flags: String,
        mentions: String,
    ) {
        val r = publish(*flags)
        assertEquals(2, r.exit, "expected bad_args for ${flags.toList()}: ${r.stderr}")
        assertTrue(r.stderr.contains(mentions), "expected '$mentions' in: ${r.stderr}")
    }

    @Test
    fun refusesNonBareDomains() {
        assertRefused("--requires", "NAP-RELAY", mentions = "NAP-RELAY")
        assertRefused("--optional", "Theme", mentions = "Theme")
    }

    @Test
    fun refusesAnEmptyIdentifier() {
        assertRefused("--d", "", mentions = "--d")
    }

    @Test
    fun refusesANonOriginServer() {
        val r = amy("napplet", "publish", index.path, "--server", "file:///etc", "--description", "A napplet")
        assertEquals(2, r.exit, r.stderr)
        assertTrue(r.stderr.contains("--server"), r.stderr)
    }

    @Test
    fun refusesABadSourceOrIcon() {
        assertRefused("--source", "C:\\napplet", mentions = "--source")
        assertRefused("--icon", "nothex", "--icon-mime", "image/png", mentions = "--icon")
    }

    @Test
    fun refusesAMissingDescription() {
        val r = amy("napplet", "publish", index.path, "--server", "https://blossom.example.com")
        assertEquals(2, r.exit, r.stderr)
    }

    @Test
    fun validFlagsGetPastValidation() {
        // No account in the isolated ~/.amy, so it stops at the account step, past every flag check.
        val r = publish("--requires", "relay", "--optional", "theme", "--d", "feed", "--source", "https://github.com/x/y")
        assertTrue(r.stderr.contains("no account configured"), r.stderr)
    }
}
