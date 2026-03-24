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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.compose.article.ArticleHeader
import com.vitorpamplona.amethyst.commons.compose.article.TableOfContents
import com.vitorpamplona.amethyst.commons.compose.article.extractTableOfContents
import com.vitorpamplona.amethyst.commons.compose.markdown.RenderMarkdown
import com.vitorpamplona.amethyst.commons.model.nip23LongContent.ReadingTimeCalculator
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val articleDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/**
 * Parses a NIP-23 address tag in the format "30023:pubkey:d-tag".
 * Returns a Triple of (kind, pubkey, dTag) or null if invalid.
 */
private fun parseAddressTag(addressTag: String): Triple<Int, String, String>? {
    val parts = addressTag.split(":", limit = 3)
    if (parts.size < 3) return null
    val kind = parts[0].toIntOrNull() ?: return null
    return Triple(kind, parts[1], parts[2])
}

/**
 * Desktop Article Reader Screen - renders long-form NIP-23 content with
 * a Medium-style layout: optional ToC sidebar, centered content column,
 * article header with hero image, markdown body, and reaction row.
 */
@Composable
fun ArticleReaderScreen(
    addressTag: String,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val scrollState = rememberScrollState()

    // Parse address tag
    val parsed = remember(addressTag) { parseAddressTag(addressTag) }
    val pubkey = parsed?.second
    val dTag = parsed?.third

    // Article state
    var article by remember(addressTag) { mutableStateOf<LongTextNoteEvent?>(null) }
    var eoseReceived by remember(addressTag) { mutableStateOf(false) }

    // Active ToC entry tracking (placeholder — no scroll-position-based tracking yet)
    var activeTocIndex by remember { mutableStateOf<Int?>(null) }

    // Link click handler for markdown
    val onLinkClick: (String) -> Unit =
        remember {
            { url: String ->
                if (url.startsWith("nostr:")) {
                    // TODO: Parse nostr: URI and navigate
                } else {
                    try {
                        java.awt.Desktop
                            .getDesktop()
                            .browse(java.net.URI(url))
                    } catch (_: Exception) {
                    }
                }
            }
        }

    // Load author metadata via coordinator
    LaunchedEffect(article, subscriptionsCoordinator) {
        val art = article ?: return@LaunchedEffect
        subscriptionsCoordinator?.loadMetadataForPubkeys(listOf(art.pubKey))
    }

    // Subscribe to the article by address components
    rememberSubscription(relayStatuses, addressTag, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || pubkey == null || dTag == null) {
            return@rememberSubscription null
        }

        SubscriptionConfig(
            subId = "article-${addressTag.hashCode()}",
            filters = listOf(FilterBuilders.longFormByAddress(pubkey, dTag)),
            relays = configuredRelays,
            onEvent = { event, _, _, _ ->
                if (event is LongTextNoteEvent) {
                    // Keep the most recent version
                    val current = article
                    if (current == null || event.createdAt > current.createdAt) {
                        article = event
                    }
                }
            },
            onEose = { _, _ ->
                eoseReceived = true
            },
        )
    }

    // Derived data from article
    val title = article?.title() ?: "Untitled"
    val content = article?.content ?: ""
    val tocEntries = remember(content) { extractTableOfContents(content) }
    val readingTime =
        remember(content) {
            if (content.isNotBlank()) ReadingTimeCalculator.calculate(content) else null
        }
    val bannerUrl = article?.image()
    val publishedAt =
        article?.let { art ->
            val ts = art.publishedAt() ?: art.createdAt
            Instant
                .ofEpochSecond(ts)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(articleDateFormat)
        }

    // Author info from local cache
    val authorUser = article?.let { localCache.getOrCreateUser(it.pubKey) }
    val authorName = authorUser?.toBestDisplayName()
    val authorPicture = authorUser?.profilePicture()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: back + bookmark placeholder
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Article",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // Loading / error / content states
        when {
            parsed == null -> {
                EmptyState(
                    title = "Invalid article address",
                    description = "Could not parse address: $addressTag",
                    onRefresh = onBack,
                    refreshLabel = "Go back",
                )
            }

            connectedRelays.isEmpty() -> {
                LoadingState("Connecting to relays...")
            }

            article == null && !eoseReceived -> {
                LoadingState("Loading article...")
            }

            article == null && eoseReceived -> {
                EmptyState(
                    title = "Article not found",
                    description = "This article may have been deleted or is not available from connected relays",
                    onRefresh = onBack,
                    refreshLabel = "Go back",
                )
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val showToc = maxWidth > 1100.dp && tocEntries.isNotEmpty()

                    Row(modifier = Modifier.fillMaxSize()) {
                        // ToC sidebar
                        if (showToc) {
                            TableOfContents(
                                entries = tocEntries,
                                activeEntryIndex = activeTocIndex,
                                onEntryClick = { entry ->
                                    activeTocIndex = entry.index
                                    // TODO: scroll to heading position
                                },
                                modifier = Modifier.padding(top = 16.dp, start = 8.dp),
                            )
                            VerticalDivider()
                        }

                        // Main content column
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 16.dp),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .widthIn(max = 680.dp)
                                        .align(Alignment.CenterHorizontally),
                            ) {
                                Spacer(Modifier.height(16.dp))

                                ArticleHeader(
                                    title = title,
                                    authorName = authorName,
                                    authorPicture = authorPicture,
                                    publishedAt = publishedAt,
                                    readingTimeMinutes = readingTime,
                                    bannerUrl = bannerUrl,
                                    onAuthorClick =
                                        article?.let {
                                            { onNavigateToProfile(it.pubKey) }
                                        },
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    thickness = 1.dp,
                                )

                                // Markdown body
                                RenderMarkdown(
                                    content = content,
                                    onLinkClick = onLinkClick,
                                )

                                Spacer(Modifier.height(32.dp))

                                // Topics / hashtags
                                val topics = article?.topics() ?: emptyList()
                                if (topics.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 16.dp),
                                    ) {
                                        topics.forEach { topic ->
                                            Text(
                                                text = "#$topic",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(thickness = 1.dp)

                                Spacer(Modifier.height(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
