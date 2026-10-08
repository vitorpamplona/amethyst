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
package com.vitorpamplona.quartz.nip34Git.ci

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent

/**
 * The NIP-22-style trigger context of a `pull_request` run: the PR as root (`E`/`K`/`P`) and the PR
 * or PR Update (1619) that supplied the commit as parent (`e`/`k`/`p`).
 *
 * [sourceAuthor] is absent on a Manual Trigger (9840), whose only `p` is the coordinator. Result
 * events may carry more than one lowercase `p`; the first is the parent author, as ngit reads it.
 */
@Immutable
data class CiPullRequestContext(
    val pullRequestId: HexKey,
    val pullRequestAuthor: HexKey? = null,
    val pullRequestKind: Int? = GitPullRequestEvent.KIND,
    val sourceEventId: HexKey? = null,
    val sourceKind: Int? = null,
    val sourceAuthor: HexKey? = null,
)
