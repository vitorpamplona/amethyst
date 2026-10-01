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
package com.vitorpamplona.quartz.buzz.mpProjects.tags

/**
 * A NIP-MP project's listing visibility. Absent or unrecognized values read as [LISTED]: a
 * typo in a metadata field is not a privacy signal, so it must never hide a project.
 */
enum class ProjectVisibility(
    val code: String,
) {
    LISTED("listed"),
    UNLISTED("unlisted"),
    ;

    companion object {
        /** The writer-side parse: only the two defined values. */
        fun parseStrict(code: String): ProjectVisibility? =
            when (code) {
                LISTED.code -> LISTED
                UNLISTED.code -> UNLISTED
                else -> null
            }

        /** The reader-side interpretation: anything but `unlisted` is [LISTED]. */
        fun interpret(code: String?): ProjectVisibility = if (code == UNLISTED.code) UNLISTED else LISTED
    }
}
