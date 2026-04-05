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
package com.vitorpamplona.amethyst.cli.output

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Ported from whitenoise-rs src/cli/output.rs tests.
 * Tests output formatting in both JSON and human-readable modes.
 */
class OutputTest {
    private lateinit var originalOut: PrintStream
    private lateinit var originalErr: PrintStream

    @BeforeTest
    fun setup() {
        originalOut = System.out
        originalErr = System.err
    }

    @AfterTest
    fun teardown() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        Output.jsonMode = false
    }

    private fun captureStdout(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos))
        block()
        System.setOut(originalOut)
        return baos.toString().trim()
    }

    private fun captureStderr(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        System.setErr(PrintStream(baos))
        block()
        System.setErr(originalErr)
        return baos.toString().trim()
    }

    // -- Ported from whitenoise-rs: render_string --
    @Test
    fun successMessageInTextMode() {
        Output.jsonMode = false
        val output = captureStdout { Output.success("hello world") }
        assertTrue(output.contains("hello world"))
    }

    // -- Ported from whitenoise-rs: render_string (json mode) --
    @Test
    fun successMessageInJsonMode() {
        Output.jsonMode = true
        val output = captureStdout { Output.success("hello world") }
        assertTrue(output.contains("\"status\""))
        assertTrue(output.contains("\"ok\""))
        assertTrue(output.contains("\"hello world\""))
    }

    // -- Ported from whitenoise-rs: render_null --
    @Test
    fun errorMessageInTextMode() {
        Output.jsonMode = false
        val output = captureStderr { Output.error("something failed") }
        assertTrue(output.contains("something failed"))
    }

    // -- Ported from whitenoise-rs: render_null (json mode) --
    @Test
    fun errorMessageInJsonMode() {
        Output.jsonMode = true
        val output = captureStdout { Output.error("something failed") }
        assertTrue(output.contains("\"error\""))
        assertTrue(output.contains("\"something failed\""))
    }

    // -- Ported from whitenoise-rs: render_object --
    @Test
    fun successWithDataInJsonMode() {
        Output.jsonMode = true
        val data =
            buildJsonObject {
                put("name", "test")
                put("count", 42)
            }
        val output = captureStdout { Output.success("created", data) }
        assertTrue(output.contains("\"name\""))
        assertTrue(output.contains("\"test\""))
        assertTrue(output.contains("42"))
    }

    // -- Ported from whitenoise-rs: render_array --
    @Test
    fun tableRendersInTextMode() {
        Output.jsonMode = false
        val output =
            captureStdout {
                Output.table(
                    headers = listOf("name", "value"),
                    rows =
                        listOf(
                            listOf("alice", "100"),
                            listOf("bob", "200"),
                        ),
                )
            }
        assertTrue(output.contains("name"))
        assertTrue(output.contains("alice"))
        assertTrue(output.contains("bob"))
        assertTrue(output.contains("100"))
        assertTrue(output.contains("200"))
    }

    // -- Ported from whitenoise-rs: render_array (json) --
    @Test
    fun tableRendersInJsonMode() {
        Output.jsonMode = true
        val output =
            captureStdout {
                Output.table(
                    headers = listOf("name", "value"),
                    rows =
                        listOf(
                            listOf("alice", "100"),
                            listOf("bob", "200"),
                        ),
                )
            }
        assertTrue(output.contains("\"name\""))
        assertTrue(output.contains("\"alice\""))
        assertTrue(output.contains("\"bob\""))
    }

    // -- Ported from whitenoise-rs: render_empty_array --
    @Test
    fun emptyTableRendersInTextMode() {
        Output.jsonMode = false
        val output =
            captureStdout {
                Output.table(
                    headers = listOf("name"),
                    rows = emptyList(),
                )
            }
        assertTrue(output.contains("(empty)"))
    }

    // -- Ported from whitenoise-rs: render_key_value_pairs --
    @Test
    fun keyValueRendersInTextMode() {
        Output.jsonMode = false
        val output =
            captureStdout {
                Output.keyValue(
                    listOf(
                        "pubkey" to "abc123",
                        "npub" to "npub1xyz",
                    ),
                )
            }
        assertTrue(output.contains("pubkey"))
        assertTrue(output.contains("abc123"))
        assertTrue(output.contains("npub"))
        assertTrue(output.contains("npub1xyz"))
    }

    // -- Ported from whitenoise-rs: render_key_value_pairs (json) --
    @Test
    fun keyValueRendersInJsonMode() {
        Output.jsonMode = true
        val output =
            captureStdout {
                Output.keyValue(
                    listOf(
                        "pubkey" to "abc123",
                        "npub" to "npub1xyz",
                    ),
                )
            }
        assertTrue(output.contains("\"pubkey\""))
        assertTrue(output.contains("\"abc123\""))
        assertTrue(output.contains("\"npub\""))
        assertTrue(output.contains("\"npub1xyz\""))
    }

    // -- Ported from whitenoise-rs: render_data_object --
    @Test
    fun dataRendersJsonObject() {
        Output.jsonMode = true
        val obj =
            buildJsonObject {
                put("id", "event123")
                put("kind", 9)
            }
        val output = captureStdout { Output.data(obj) }
        assertTrue(output.contains("\"id\""))
        assertTrue(output.contains("\"event123\""))
        assertTrue(output.contains("9"))
    }

    // -- Ported from whitenoise-rs: table column alignment --
    @Test
    fun tableAlignColumns() {
        Output.jsonMode = false
        val output =
            captureStdout {
                Output.table(
                    headers = listOf("short", "longer_header"),
                    rows =
                        listOf(
                            listOf("a", "b"),
                            listOf("longer_value", "c"),
                        ),
                )
            }
        // Verify headers and separator lines exist
        val lines = output.lines()
        assertTrue(lines.size >= 3, "should have header, separator, and data lines")
        assertTrue(lines[1].contains("-"), "second line should be separator")
    }

    // -- Ported from whitenoise-rs: boolean display --
    @Test
    fun jsonModePreservesBooleans() {
        Output.jsonMode = true
        val data =
            buildJsonObject {
                put("active", true)
                put("muted", false)
            }
        val output = captureStdout { Output.success("status", data) }
        assertTrue(output.contains("true"))
        assertTrue(output.contains("false"))
    }
}
