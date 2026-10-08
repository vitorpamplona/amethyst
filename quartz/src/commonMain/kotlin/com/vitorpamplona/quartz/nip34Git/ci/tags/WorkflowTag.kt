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
package com.vitorpamplona.quartz.nip34Git.ci.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/** The workflow file a run executed: its path in the repository and the SHA-256 of its content. */
@Immutable
data class CiWorkflowFile(
    val path: String,
    val sha256: String,
)

/**
 * Nostr CI `w` tag: `["w", "<workflow-file-path>", "<sha256-of-workflow-file-content>"]`.
 *
 * Both values are required. The hash is what pins the run to one exact workflow (a Manual Trigger
 * replays "the exact workflow identified by its `w` tag"), so a path without a 64-hex hash is
 * rejected rather than read as a weaker claim, as ngit does.
 */
class WorkflowTag {
    companion object {
        const val TAG_NAME = "w"

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): CiWorkflowFile? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2].length == 64 && Hex.isHex(tag[2])) { return null }
            return CiWorkflowFile(tag[1], tag[2])
        }

        fun assemble(
            path: String,
            sha256: String,
        ) = arrayOf(TAG_NAME, path, sha256)

        fun assemble(workflow: CiWorkflowFile) = assemble(workflow.path, workflow.sha256)
    }
}
