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
package com.vitorpamplona.quartz.nip34Git.pr

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.toATag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip34Git.pr.tags.CurrentCommitTag
import com.vitorpamplona.quartz.nip34Git.pr.tags.MergeBaseTag
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.tags.CloneTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.EucTag

fun TagArrayBuilder<GitPullRequestUpdateEvent>.repository(rep: ATag) = addUnique(rep.toATagArray())

fun TagArrayBuilder<GitPullRequestUpdateEvent>.repository(rep: EventHintBundle<GitRepositoryEvent>) = addUnique(rep.toATag().toATagArray())

fun TagArrayBuilder<GitPullRequestUpdateEvent>.euc(commit: String) = addUnique(EucTag.assemble(commit))

fun TagArrayBuilder<GitPullRequestUpdateEvent>.currentCommit(commit: String) = addUnique(CurrentCommitTag.assemble(commit))

fun TagArrayBuilder<GitPullRequestUpdateEvent>.cloneUrl(url: String) = add(CloneTag.assemble(url))

fun TagArrayBuilder<GitPullRequestUpdateEvent>.mergeBase(commit: String) = addUnique(MergeBaseTag.assemble(commit))

/** Adds the NIP-22 `E` tag pointing at the parent Pull Request. */
fun TagArrayBuilder<GitPullRequestUpdateEvent>.parentPullRequest(pr: EventHintBundle<GitPullRequestEvent>) =
    addUnique(
        RootEventTag.assemble(
            pr.event.id,
            pr.relay,
            pr.event.pubKey,
        ),
    )

/** Adds the NIP-22 `P` tag pointing at the parent Pull Request's author. */
fun TagArrayBuilder<GitPullRequestUpdateEvent>.parentPullRequestAuthor(pr: EventHintBundle<GitPullRequestEvent>) =
    addUnique(
        RootAuthorTag.assemble(
            pr.event.pubKey,
            pr.authorHomeRelay,
        ),
    )
