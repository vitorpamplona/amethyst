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
package com.vitorpamplona.amethyst.commons.wiki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.feedNotes
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.relayClient.wiki.WikiTopicQueryState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.wiki_topic_empty
import com.vitorpamplona.amethyst.commons.resources.wiki_topic_redirects
import com.vitorpamplona.amethyst.commons.resources.wiki_topic_versions
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wiki.dal.WikiTopicFeedViewModel
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiRedirectEvent

/**
 * A NIP-54 topic: every author's article under one slug, to pick the version to read, and the
 * redirects that send the topic elsewhere. Wikilinks open it. Versions come in reading order: the
 * linking author's first, then people the account follows, then by trust rank, then the newest. Each
 * version is drawn as the feed draws an article, and opens it.
 */
@Composable
fun WikiTopicScreen(
    slug: String,
    from: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val model: WikiTopicFeedViewModel =
        rememberViewModel(key = "WikiTopic-$slug-$from", factory = WikiTopicFeedViewModel.Factory(slug, from, accountViewModel.account))
    WatchLifecycleAndUpdateModel(model)
    WikiTopicSubscription(slug, model.feedState, accountViewModel)

    val (notes, loading) = feedNotes(model.feedState)
    val articles = remember(notes) { notes.filter { it.event is WikiArticleEvent } }
    val redirects = remember(notes) { notes.filter { it.event is WikiRedirectEvent } }
    val title = remember(articles, slug) { topicTitle(articles, slug) }

    Scaffold(topBar = { TopBarWithBackButton(title, nav) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "header") { TopicHeader(title, articles.size, loading) }
            if (articles.isEmpty() && redirects.isEmpty() && !loading) {
                item(key = "empty") {
                    Text(
                        stringRes(Res.string.wiki_topic_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            items(articles, key = { "v-" + it.idHex }) { note ->
                NoteCompose(baseNote = note, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                HorizontalDivider(thickness = 0.5.dp)
            }
            if (redirects.isNotEmpty()) {
                item(key = "redirects-title") {
                    Text(
                        stringRes(Res.string.wiki_topic_redirects),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(redirects, key = { "r-" + it.idHex }) { note ->
                    NoteCompose(baseNote = note, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

/** The title the first version in reading order gives the topic, or its slug spelled out. */
private fun topicTitle(
    articles: List<Note>,
    slug: String,
): String =
    articles.firstNotNullOfOrNull { (it.event as? WikiArticleEvent)?.title()?.ifBlank { null } }
        ?: slug.replace('-', ' ').replaceFirstChar { it.uppercaseChar() }

@Composable
private fun TopicHeader(
    title: String,
    versions: Int,
    loading: Boolean,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringRes(Res.plurals.wiki_topic_versions, versions, versions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.weight(1f),
            )
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

/** Keeps the topic's versions arriving from the relays while its screen is on screen. */
@Composable
fun WikiTopicSubscription(
    slug: String,
    feed: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val state = remember(slug, accountViewModel.account, feed) { WikiTopicQueryState(slug, accountViewModel.account, feed) }
    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().wikiTopics)
}
