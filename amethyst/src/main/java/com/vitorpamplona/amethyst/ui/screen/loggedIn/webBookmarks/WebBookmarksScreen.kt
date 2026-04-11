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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.webBookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import kotlinx.coroutines.launch

@Composable
fun WebBookmarksScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RenderWebBookmarksScreen(accountViewModel.feedStates.webBookmarks, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenderWebBookmarksScreen(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedState)

    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        WebBookmarkEditDialog(
            accountViewModel = accountViewModel,
            onDismiss = { showAddDialog = false },
            onSave = { url, title, description, tags ->
                accountViewModel.launchSigner {
                    accountViewModel.account.sendWebBookmark(url, title, description, tags)
                }
                showAddDialog = false
            },
        )
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            ShorterTopAppBar(
                title = {
                    Text(text = stringRes(id = R.string.web_bookmarks))
                },
                navigationIcon = {
                    IconButton(onClick = nav::popBack) {
                        ArrowBackIcon()
                    }
                },
            )
        },
        floatingButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Size55Modifier,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.web_bookmark_add_title),
                )
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it).fillMaxHeight()) {
            RefresheableBox(feedState) {
                SaveableFeedState(feedState, ScrollStateKeys.WEB_BOOKMARKS) { listState ->
                    RenderFeedContentState(
                        feedContentState = feedState,
                        accountViewModel = accountViewModel,
                        listState = listState,
                        nav = nav,
                        routeForLastRead = null,
                        onLoaded = { WebBookmarksFeedLoaded(it, listState, accountViewModel, nav) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WebBookmarksFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            WebBookmarkCard(item, accountViewModel, nav)

            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

@Composable
private fun WebBookmarkCard(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? WebBookmarkEvent ?: return
    val uriHandler = LocalUriHandler.current

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        WebBookmarkEditDialog(
            accountViewModel = accountViewModel,
            initialUrl = event.url(),
            initialTitle = event.title() ?: "",
            initialDescription = event.description(),
            initialTags = event.hashtags().joinToString(", "),
            onDismiss = { showEditDialog = false },
            onSave = { url, title, description, tags ->
                accountViewModel.launchSigner {
                    accountViewModel.account.sendWebBookmark(url, title, description, tags)
                }
                showEditDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.web_bookmark_delete)) },
            text = { Text(stringResource(R.string.web_bookmark_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    accountViewModel.launchSigner {
                        accountViewModel.account.deleteWebBookmark(event)
                    }
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    @Suppress("ProduceStateDoesNotAssignValue")
    val urlPreviewState by
        produceState(
            initialValue = UrlCachedPreviewer.cache.get(event.url()) ?: UrlPreviewState.Loading,
            key1 = event.url(),
        ) {
            if (value == UrlPreviewState.Loading) {
                accountViewModel.urlPreview(event.url()) { value = it }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(event.url()) }
                .padding(16.dp),
    ) {
        val previewInfo = (urlPreviewState as? UrlPreviewState.Loaded)?.previewInfo

        if (previewInfo?.imageUrlFullPath != null) {
            AsyncImage(
                model = previewInfo.imageUrlFullPath,
                contentDescription = event.title() ?: previewInfo.title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(QuoteBorder),
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title() ?: previewInfo?.title?.ifBlank { null } ?: event.url(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = previewInfo?.verifiedUrl?.host ?: event.url(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row {
                IconButton(onClick = { uriHandler.openUri(event.url()) }) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = stringResource(R.string.web_bookmark_open_url),
                    )
                }
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.web_bookmark_edit_title),
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.web_bookmark_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        val description = event.description().ifBlank { previewInfo?.description ?: "" }
        if (description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val tags = event.hashtags()
        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                tags.forEach { tag ->
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun WebBookmarkEditDialog(
    accountViewModel: AccountViewModel,
    initialUrl: String = "",
    initialTitle: String = "",
    initialDescription: String = "",
    initialTags: String = "",
    onDismiss: () -> Unit,
    onSave: (url: String, title: String?, description: String, tags: List<String>) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var tags by remember { mutableStateOf(initialTags) }
    var isLoadingPreview by remember { mutableStateOf(false) }
    var lastFetchedUrl by remember { mutableStateOf(initialUrl) }

    val isEditing = initialUrl.isNotEmpty()
    val scope = rememberCoroutineScope()

    fun fetchOpenGraphData(urlToFetch: String) {
        if (urlToFetch.isBlank() || urlToFetch == lastFetchedUrl) return

        val normalizedUrl = if (!urlToFetch.startsWith("http")) "https://$urlToFetch" else urlToFetch
        lastFetchedUrl = urlToFetch
        isLoadingPreview = true

        accountViewModel.urlPreview(normalizedUrl) { state ->
            scope.launch {
                when (state) {
                    is UrlPreviewState.Loaded -> {
                        val info = state.previewInfo
                        if (title.isBlank() && info.title.isNotBlank()) {
                            title = info.title
                        }
                        if (description.isBlank() && info.description.isNotBlank()) {
                            description = info.description
                        }
                    }

                    else -> {}
                }
                isLoadingPreview = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEditing) R.string.web_bookmark_edit_title else R.string.web_bookmark_add_title,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.web_bookmark_url_label)) },
                    placeholder = { Text(stringResource(R.string.web_bookmark_url_placeholder)) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused && url.isNotBlank()) {
                                    fetchOpenGraphData(url)
                                }
                            },
                    trailingIcon = {
                        if (isLoadingPreview) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.web_bookmark_title_label)) },
                    placeholder = { Text(stringResource(R.string.web_bookmark_title_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.web_bookmark_description_label)) },
                    placeholder = { Text(stringResource(R.string.web_bookmark_description_placeholder)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.web_bookmark_tags_label)) },
                    placeholder = { Text(stringResource(R.string.web_bookmark_tags_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isNotBlank()) {
                        val normalizedUrl = if (!url.startsWith("http")) "https://$url" else url
                        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onSave(normalizedUrl, title.ifBlank { null }, description, tagList)
                    }
                },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.web_bookmark_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
