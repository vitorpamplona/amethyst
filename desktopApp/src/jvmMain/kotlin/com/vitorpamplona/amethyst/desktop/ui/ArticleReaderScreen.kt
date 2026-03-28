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

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
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
import com.vitorpamplona.amethyst.desktop.getPlainText
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.highlights.DesktopHighlightStore
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createReactionsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createRepliesSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createRepostsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createZapsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.highlights.ArticleHighlightsPanel
import com.vitorpamplona.amethyst.desktop.ui.highlights.HighlightAnnotationDialog
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.launch
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
    nwcConnection: Nip47WalletConnect.Nip47URINorm? = null,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    highlightStore: DesktopHighlightStore? = null,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
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

    // Zoom level for article text
    var zoomLevel by remember { mutableStateOf(1.0f) }

    // Coroutine scope for highlight operations
    val scope = rememberCoroutineScope()

    // Highlight state — collect outside let to ensure proper Compose subscription
    val allHighlights by (highlightStore?.highlights ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap()))
        .collectAsState()
    val articleHighlights = allHighlights[addressTag] ?: emptyList()

    var showAnnotationDialog by remember { mutableStateOf<String?>(null) }
    var showHighlightsPanel by remember { mutableStateOf(false) }
    val focusRequester =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }

    // Active ToC entry tracking (placeholder — no scroll-position-based tracking yet)
    var activeTocIndex by remember { mutableStateOf<Int?>(null) }

    // Link click handler for markdown
    val onLinkClick: (String) -> Unit =
        remember(articleHighlights) {
            { url: String ->
                when {
                    url.startsWith("highlight://") -> {
                        showHighlightsPanel = true
                    }

                    url.startsWith("nostr:") -> {
                        // TODO: Parse nostr: URI and navigate
                    }

                    else -> {
                        try {
                            java.awt.Desktop
                                .getDesktop()
                                .browse(java.net.URI(url))
                        } catch (_: Exception) {
                        }
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

    // Interaction state
    val articleEventId = article?.id
    val eventIds = listOfNotNull(articleEventId)

    var zapReceipts by remember { mutableStateOf<List<ZapReceipt>>(emptyList()) }
    var reactionCount by remember { mutableStateOf(0) }
    var replyCount by remember { mutableStateOf(0) }
    var repostCount by remember { mutableStateOf(0) }
    var bookmarkList by remember { mutableStateOf<BookmarkListEvent?>(null) }
    var bookmarkedEventIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Subscribe to zaps
    rememberSubscription(relayStatuses, eventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || eventIds.isEmpty()) return@rememberSubscription null

        createZapsSubscription(
            relays = configuredRelays,
            eventIds = eventIds,
            onEvent = { event, _, _, _ ->
                if (event is LnZapEvent) {
                    val receipt = event.toZapReceipt(localCache) ?: return@createZapsSubscription
                    if (zapReceipts.none { it.createdAt == receipt.createdAt && it.senderPubKey == receipt.senderPubKey }) {
                        zapReceipts = zapReceipts + receipt
                    }
                }
            },
        )
    }

    // Subscribe to reactions
    rememberSubscription(relayStatuses, eventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || eventIds.isEmpty()) return@rememberSubscription null

        val reactionIds = mutableSetOf<String>()
        createReactionsSubscription(
            relays = configuredRelays,
            eventIds = eventIds,
            onEvent = { event, _, _, _ ->
                if (event is ReactionEvent && reactionIds.add(event.id)) {
                    reactionCount = reactionIds.size
                }
            },
        )
    }

    // Subscribe to replies
    rememberSubscription(relayStatuses, eventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || eventIds.isEmpty()) return@rememberSubscription null

        val replyIds = mutableSetOf<String>()
        createRepliesSubscription(
            relays = configuredRelays,
            eventIds = eventIds,
            onEvent = { event, _, _, _ ->
                if (replyIds.add(event.id)) {
                    replyCount = replyIds.size
                }
            },
        )
    }

    // Subscribe to reposts
    rememberSubscription(relayStatuses, eventIds, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || eventIds.isEmpty()) return@rememberSubscription null

        val repostIds = mutableSetOf<String>()
        createRepostsSubscription(
            relays = configuredRelays,
            eventIds = eventIds,
            onEvent = { event, _, _, _ ->
                if (event is RepostEvent && repostIds.add(event.id)) {
                    repostCount = repostIds.size
                }
            },
        )
    }

    // Subscribe to bookmark list
    rememberSubscription(relayStatuses, account, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && account != null) {
            SubscriptionConfig(
                subId = "article-bookmarks-${account.pubKeyHex.take(8)}",
                filters =
                    listOf(
                        FilterBuilders.byAuthors(
                            authors = listOf(account.pubKeyHex),
                            kinds = listOf(BookmarkListEvent.KIND),
                            limit = 1,
                        ),
                    ),
                relays = configuredRelays,
                onEvent = { event, _, _, _ ->
                    if (event is BookmarkListEvent) {
                        bookmarkList = event
                        bookmarkedEventIds =
                            event
                                .publicBookmarks()
                                .filterIsInstance<com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark>()
                                .map { it.eventId }
                                .toSet()
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
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

    val clipboardManager = LocalClipboard.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.isMetaPressed) {
                        when (event.key) {
                            Key.Equals -> {
                                zoomLevel = (zoomLevel + 0.1f).coerceAtMost(2.0f)
                                true
                            }

                            Key.Minus -> {
                                zoomLevel = (zoomLevel - 0.1f).coerceAtLeast(0.5f)
                                true
                            }

                            Key.Zero -> {
                                zoomLevel = 1.0f
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    } else {
                        false
                    }
                },
    ) {
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
                if (zoomLevel != 1.0f) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${(zoomLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

                                // Collapsible highlights section
                                if (articleHighlights.isNotEmpty()) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showHighlightsPanel = !showHighlightsPanel
                                                }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            if (showHighlightsPanel) {
                                                Icons.Default.KeyboardArrowDown
                                            } else {
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                                            },
                                            contentDescription = "Toggle highlights",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "${articleHighlights.size} highlight${if (articleHighlights.size != 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    if (showHighlightsPanel && highlightStore != null) {
                                        ArticleHighlightsPanel(
                                            highlights = articleHighlights,
                                            highlightStore = highlightStore,
                                            articleContent = content,
                                            signer = account?.signer,
                                            relayManager = relayManager,
                                            modifier = Modifier.padding(bottom = 16.dp),
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(bottom = 16.dp),
                                            thickness = 1.dp,
                                        )
                                    }
                                }

                                // Markdown body with right-click highlight via context menu
                                val defaultRepresentation = LocalContextMenuRepresentation.current
                                val highlightRepresentation =
                                    remember(
                                        defaultRepresentation,
                                        highlightStore,
                                        addressTag,
                                        title,
                                    ) {
                                        HighlightContextMenuRepresentation(
                                            delegate = defaultRepresentation,
                                            clipboardManager = clipboardManager,
                                            scope = scope,
                                            onHighlight = { text ->
                                                scope.launch {
                                                    highlightStore?.addHighlight(
                                                        articleAddressTag = addressTag,
                                                        text = text,
                                                        note = null,
                                                        articleTitle = title,
                                                    )
                                                }
                                            },
                                            onHighlightWithNote = { text ->
                                                showAnnotationDialog = text
                                            },
                                        )
                                    }

                                CompositionLocalProvider(
                                    LocalContextMenuRepresentation provides highlightRepresentation,
                                ) {
                                    SelectionContainer {
                                        RenderMarkdown(
                                            content = content,
                                            onLinkClick = onLinkClick,
                                            fontScale = zoomLevel,
                                            highlightedTexts = articleHighlights.map { it.text },
                                        )
                                    }
                                }

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

                                // Reaction actions
                                val art = article
                                if (art != null && account != null) {
                                    Spacer(Modifier.height(16.dp))
                                    NoteActionsRow(
                                        event = art,
                                        relayManager = relayManager,
                                        localCache = localCache,
                                        account = account,
                                        onReplyClick = { onNavigateToThread(art.id) },
                                        onZapFeedback = onZapFeedback,
                                        zapCount = zapReceipts.size,
                                        zapAmountSats = zapReceipts.sumOf { it.amountSats },
                                        zapReceipts = zapReceipts,
                                        reactionCount = reactionCount,
                                        replyCount = replyCount,
                                        repostCount = repostCount,
                                        nwcConnection = nwcConnection,
                                        isBookmarked = articleEventId in bookmarkedEventIds,
                                        bookmarkList = bookmarkList,
                                        onBookmarkChanged = { newList ->
                                            bookmarkList = newList
                                            bookmarkedEventIds =
                                                newList
                                                    .publicBookmarks()
                                                    .filterIsInstance<com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark>()
                                                    .map { it.eventId }
                                                    .toSet()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                Spacer(Modifier.height(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    showAnnotationDialog?.let { selectedText ->
        HighlightAnnotationDialog(
            selectedText = selectedText,
            onConfirm = { note ->
                scope.launch {
                    highlightStore?.addHighlight(
                        articleAddressTag = addressTag,
                        text = selectedText,
                        note = note,
                        articleTitle = title,
                    )
                }
                showAnnotationDialog = null
            },
            onDismiss = { showAnnotationDialog = null },
        )
    }
}

/**
 * Custom context menu representation that adds "Highlight" and "Highlight with Note"
 * items to the right-click menu inside a SelectionContainer.
 *
 * How it works: The SelectionContainer provides a "Copy" item that has access to the
 * selected text. Our items piggyback on Copy's onClick — calling it first to put the
 * selected text on the clipboard, then reading the clipboard to get the text.
 */
private class HighlightContextMenuRepresentation(
    private val delegate: ContextMenuRepresentation,
    private val clipboardManager: androidx.compose.ui.platform.Clipboard,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onHighlight: (String) -> Unit,
    private val onHighlightWithNote: (String) -> Unit,
) : ContextMenuRepresentation {
    @Composable
    override fun Representation(
        state: ContextMenuState,
        items: () -> List<ContextMenuItem>,
    ) {
        val extendedItems = {
            val original = items()
            val copyItem = original.find { it.label == "Copy" }

            if (copyItem != null) {
                original +
                    listOf(
                        ContextMenuItem("Highlight") {
                            copyItem.onClick()
                            scope.launch {
                                val text = clipboardManager.getPlainText()
                                if (!text.isNullOrBlank()) {
                                    onHighlight(text)
                                }
                            }
                        },
                        ContextMenuItem("Highlight with Note") {
                            copyItem.onClick()
                            scope.launch {
                                val text = clipboardManager.getPlainText()
                                if (!text.isNullOrBlank()) {
                                    onHighlightWithNote(text)
                                }
                            }
                        },
                    )
            } else {
                original
            }
        }

        delegate.Representation(state, extendedItems)
    }
}
