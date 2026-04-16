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
package com.vitorpamplona.quartz.nip34Git.patch

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.toATag
import com.vitorpamplona.quartz.nip34Git.patch.tags.CommitPgpSigTag
import com.vitorpamplona.quartz.nip34Git.patch.tags.CommitTag
import com.vitorpamplona.quartz.nip34Git.patch.tags.Committer
import com.vitorpamplona.quartz.nip34Git.patch.tags.CommitterTag
import com.vitorpamplona.quartz.nip34Git.patch.tags.ParentCommitTag
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.tags.EucTag

fun TagArrayBuilder<GitPatchEvent>.repository(rep: ATag) = addUnique(rep.toATagArray())

fun TagArrayBuilder<GitPatchEvent>.repository(rep: EventHintBundle<GitRepositoryEvent>) = addUnique(rep.toATag().toATagArray())

/**
 * Adds the earliest-unique-commit `r` tag used by patches to point at the
 * target repository. NIP-34 uses the plain `["r", <commit>]` shape for
 * patches (unlike the repository announcement which marks the tag with
 * `euc`). We also emit the marked form so the same event can be matched by
 * implementations that look for the marker.
 */
fun TagArrayBuilder<GitPatchEvent>.euc(commit: String) = addUnique(EucTag.assemble(commit))

fun TagArrayBuilder<GitPatchEvent>.commit(commit: String) = addUnique(CommitTag.assemble(commit))

fun TagArrayBuilder<GitPatchEvent>.parentCommit(commit: String) = addUnique(ParentCommitTag.assemble(commit))

fun TagArrayBuilder<GitPatchEvent>.commitPgpSig(sig: String) = addUnique(CommitPgpSigTag.assemble(sig))

fun TagArrayBuilder<GitPatchEvent>.committer(
    name: String?,
    email: String?,
    timestamp: String?,
    timezoneInMinutes: String?,
) = addUnique(CommitterTag.assemble(name, email, timestamp, timezoneInMinutes))

fun TagArrayBuilder<GitPatchEvent>.committer(committer: Committer) = committer(committer.name, committer.email, committer.timestamp, committer.timezoneInMinutes)
