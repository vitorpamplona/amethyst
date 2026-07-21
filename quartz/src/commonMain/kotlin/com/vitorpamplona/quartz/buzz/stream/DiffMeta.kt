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
package com.vitorpamplona.quartz.buzz.stream

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.stream.tags.BranchTag

/**
 * The tag-encoded metadata of a Buzz diff message ([StreamMessageDiffEvent], `kind:40008`).
 *
 * Field names, order, and optionality mirror the `DiffMeta` struct consumed by
 * `build_diff_message` in Buzz's `buzz-sdk/src/builders.rs`. [repoUrl] and
 * [commitSha] are required; every other field is optional. These live in tags (not
 * the JSON content) — the event's `content` carries the unified diff text.
 */
@Immutable
data class DiffMeta(
    val repoUrl: String,
    val commitSha: String,
    val filePath: String? = null,
    val parentCommit: String? = null,
    val branch: BranchTag? = null,
    val prNumber: Long? = null,
    val language: String? = null,
    val description: String? = null,
    val truncated: Boolean = false,
    val altText: String? = null,
)
