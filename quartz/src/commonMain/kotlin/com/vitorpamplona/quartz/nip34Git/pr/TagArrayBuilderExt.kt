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
import com.vitorpamplona.quartz.nip01Core.tags.events.toETagArray
import com.vitorpamplona.quartz.nip14Subject.SubjectTag
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.tags.BranchNameTag
import com.vitorpamplona.quartz.nip34Git.pr.tags.CurrentCommitTag
import com.vitorpamplona.quartz.nip34Git.pr.tags.MergeBaseTag
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.tags.CloneTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.EucTag

fun TagArrayBuilder<GitPullRequestEvent>.repository(rep: ATag) = addUnique(rep.toATagArray())

fun TagArrayBuilder<GitPullRequestEvent>.repository(rep: EventHintBundle<GitRepositoryEvent>) = addUnique(rep.toATag().toATagArray())

// NIP-34 pull requests use the plain `["r", <commit>]` shape; the `"euc"` marker
// is only on the kind-30617 announcement (see the patch builder for the rationale).
fun TagArrayBuilder<GitPullRequestEvent>.euc(commit: String) = addUnique(arrayOf(EucTag.TAG_NAME, commit))

fun TagArrayBuilder<GitPullRequestEvent>.currentCommit(commit: String) = addUnique(CurrentCommitTag.assemble(commit))

fun TagArrayBuilder<GitPullRequestEvent>.cloneUrl(url: String) = add(CloneTag.assemble(url))

/** Emit all clone URLs as one NIP-34 multi-value `["clone", url1, url2, …]` tag (the spec/ngit form). */
fun TagArrayBuilder<GitPullRequestEvent>.cloneUrls(urls: List<String>) = addUnique(CloneTag.assemble(urls))

fun TagArrayBuilder<GitPullRequestEvent>.subject(subject: String) = addUnique(SubjectTag.assemble(subject))

fun TagArrayBuilder<GitPullRequestEvent>.branchName(name: String) = addUnique(BranchNameTag.assemble(name))

fun TagArrayBuilder<GitPullRequestEvent>.mergeBase(commit: String) = addUnique(MergeBaseTag.assemble(commit))

fun TagArrayBuilder<GitPullRequestEvent>.rootPatch(patch: EventHintBundle<GitPatchEvent>) = add(patch.toETagArray())
