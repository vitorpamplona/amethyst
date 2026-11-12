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
package com.vitorpamplona.claudecode.protocol

/** How Claude Code decides on tool calls. [wire] is the value the CLI takes and reports. */
enum class PermissionMode(
    val wire: String,
) {
    /** Ask before anything that isn't read-only or already allowed. */
    DEFAULT("default"),

    /** Edits in the working directory go through; commands still ask. */
    ACCEPT_EDITS("acceptEdits"),

    /** Read and plan only, no changes. */
    PLAN("plan"),

    /** A classifier approves or escalates each call. */
    AUTO("auto"),

    /** Anything not already allowed is denied, nothing asks. */
    DONT_ASK("dontAsk"),

    /** Every call runs without a check. */
    BYPASS("bypassPermissions"),
    ;

    companion object {
        fun fromWire(value: String?): PermissionMode? =
            when (value) {
                null -> null
                // The interactive CLI calls the default mode "manual" in places.
                "manual", "normal" -> DEFAULT
                else -> entries.firstOrNull { it.wire == value }
            }
    }
}
