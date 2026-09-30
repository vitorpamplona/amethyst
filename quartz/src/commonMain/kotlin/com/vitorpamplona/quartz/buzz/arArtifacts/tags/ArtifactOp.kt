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
package com.vitorpamplona.quartz.buzz.arArtifacts.tags

/**
 * The lifecycle operation a NIP-AR revision performs, carried by the `op` tag ([OpTag]).
 * Ground truth: `ArtifactOp` in Buzz's `buzz-core/src/artifact.rs`.
 */
enum class ArtifactOp(
    val code: String,
) {
    /** First revision of a new identity; the only op without `prev`. */
    CREATE("create"),

    /** Content change within the same home channel. */
    UPDATE("update"),

    /** Content snapshot published into a new home channel. */
    MOVE("move"),

    /** Soft delete: empty content, no `title`; only [RESTORE] may follow. */
    DELETE("delete"),

    /** Complete snapshot that revives a deleted artifact. */
    RESTORE("restore"),
    ;

    companion object {
        fun parse(code: String): ArtifactOp? =
            when (code) {
                CREATE.code -> CREATE
                UPDATE.code -> UPDATE
                MOVE.code -> MOVE
                DELETE.code -> DELETE
                RESTORE.code -> RESTORE
                else -> null
            }
    }
}
