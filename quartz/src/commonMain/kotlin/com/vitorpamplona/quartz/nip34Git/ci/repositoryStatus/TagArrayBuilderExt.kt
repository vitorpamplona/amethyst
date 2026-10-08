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
package com.vitorpamplona.quartz.nip34Git.ci.repositoryStatus

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiRepositorySecret
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiServiceState
import com.vitorpamplona.quartz.nip34Git.ci.tags.RepositoryTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.SecretTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.ServiceStateTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowPathTag

/**
 * The first `a` MUST repeat the selected root; the remaining ones list every other reference in its
 * maintainer closure once, sorted and deduplicated.
 */
fun TagArrayBuilder<CiRepositoryStatusEvent>.statusRepositories(
    selected: ATag,
    maintainerRepositories: List<ATag>,
): TagArrayBuilder<CiRepositoryStatusEvent> {
    add(RepositoryTag.assemble(selected))
    val selectedId = selected.toTag()
    maintainerRepositories
        .filter { it.toTag() != selectedId }
        .distinctBy { it.toTag() }
        .sortedBy { it.toTag() }
        .forEach { add(RepositoryTag.assemble(it)) }
    return this
}

fun TagArrayBuilder<CiRepositoryStatusEvent>.serviceState(state: CiServiceState) = addUnique(ServiceStateTag.assemble(state))

fun TagArrayBuilder<CiRepositoryStatusEvent>.workflowPaths(globs: List<String>) = addAll(globs.map { WorkflowPathTag.assemble(it) })

fun TagArrayBuilder<CiRepositoryStatusEvent>.secrets(secrets: List<CiRepositorySecret>) = addAll(secrets.map { SecretTag.assemble(it) })
