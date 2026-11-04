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
package com.vitorpamplona.amethyst.commons.nip34Git.title

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthorityIndex
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One authorised title change of an issue, patch or PR: [from] (the title before) to [to]. */
class GitRename(
    val event: LabelEvent,
    val from: String?,
    val to: String,
)

/**
 * The title and labels an issue, patch or PR shows once its after-the-fact edits apply: NIP-32
 * label events (kind 1985) naming it, `#subject` for renames and `#t` for labels, counted only
 * when its author or a confirmed repository member at the time signed them ([GitAuthority.isMemberAt]),
 * as gitworkshop does.
 */
object GitItemTitles {
    /** Patch-series markers that ride in `t` tags but are not labels. */
    private val SERIES_TAGS = setOf("root", "root-revision", "revision-root", "cover-letter")

    /** The item's own title: an issue's or PR's `subject`, a patch's description or `Subject:` line. */
    fun originalTitleOf(item: Event): String? =
        when (item) {
            is GitIssueEvent -> item.subject()
            is GitPullRequestEvent -> item.subject()
            is GitPatchEvent -> item.title()
            else -> null
        }?.takeIf { it.isNotBlank() }

    /** The authorised renames of [item], oldest first (`created_at`, then event id). */
    fun renamesOf(
        item: Event,
        labels: List<LabelEvent> =
            GitItemLabelIndex.byItem.value
                ?.get(item.id)
                .orEmpty(),
        authority: GitAuthority = GitAuthorityIndex.authority.value ?: GitAuthority.EMPTY,
    ): List<GitRename> {
        var previous = originalTitleOf(item)
        return labels
            .filter { it.subjectEdit() != null && authority.isMemberAt(it.pubKey, item, it.createdAt) }
            .sortedWith(compareBy<LabelEvent> { it.createdAt }.thenBy { it.id })
            .map { label ->
                val to = label.subjectEdit()!!
                GitRename(label, previous, to).also { previous = to }
            }
    }

    /** The title [item] shows: its latest authorised rename, else its own. */
    fun titleOf(
        item: Event,
        labels: List<LabelEvent> =
            GitItemLabelIndex.byItem.value
                ?.get(item.id)
                .orEmpty(),
        authority: GitAuthority = GitAuthorityIndex.authority.value ?: GitAuthority.EMPTY,
    ): String? = renamesOf(item, labels, authority).lastOrNull()?.to ?: originalTitleOf(item)

    /**
     * The labels [item] shows, sorted: its own `t` tags (patch-series markers aside) plus every `#t`
     * label from an authorised label event.
     */
    fun labelsOf(
        item: Event,
        labels: List<LabelEvent> =
            GitItemLabelIndex.byItem.value
                ?.get(item.id)
                .orEmpty(),
        authority: GitAuthority = GitAuthorityIndex.authority.value ?: GitAuthority.EMPTY,
    ): List<String> {
        val own = item.tags.hashtags().filter { it !in SERIES_TAGS }
        val added = labels.filter { authority.isMemberAt(it.pubKey, item, it.createdAt) }.flatMap { event -> event.labelsByNamespace(LabelEvent.HASHTAG_NAMESPACE).map { it.label } }
        return (own + added).filter { it.isNotBlank() }.distinct().sorted()
    }
}

/**
 * Every `#subject` or `#t` label event (kind 1985) in the cache, grouped by the events it labels,
 * kept current from [LocalCache.observeEvents]. Eager like the other NIP-34 indexes; `null` until the
 * first scan.
 */
object GitItemLabelIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val byItem: StateFlow<Map<HexKey, List<LabelEvent>>?> =
        LocalCache
            .observeEvents<LabelEvent>(Filter(kinds = listOf(LabelEvent.KIND)))
            .map { events ->
                val byItem = HashMap<HexKey, MutableList<LabelEvent>>()
                for (event in events) {
                    val namespaces = event.namespaces()
                    if (namespaces.none { it.namespace == LabelEvent.SUBJECT_NAMESPACE || it.namespace == LabelEvent.HASHTAG_NAMESPACE }) continue
                    event.labeledEvents().forEach { byItem.getOrPut(it) { ArrayList() }.add(event) }
                }
                byItem as Map<HexKey, List<LabelEvent>>
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)
}
