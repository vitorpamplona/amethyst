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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.softwareapps

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.thread.drawReplyLevel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.note.types.AppAuthorLine
import com.vitorpamplona.amethyst.ui.note.types.AppIcon
import com.vitorpamplona.amethyst.ui.note.types.AppLinksColumn
import com.vitorpamplona.amethyst.ui.note.types.Chip
import com.vitorpamplona.amethyst.ui.note.types.PlatformLicenseRow
import com.vitorpamplona.amethyst.ui.note.types.RenderSoftwareReleaseBody
import com.vitorpamplona.amethyst.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.ui.note.types.ScreenshotsStrip
import com.vitorpamplona.amethyst.ui.note.types.TopicChipFlow
import com.vitorpamplona.amethyst.ui.note.types.VersionChip
import com.vitorpamplona.amethyst.ui.note.types.findAllNip82Releases
import com.vitorpamplona.amethyst.ui.note.types.findLatestNip82Release
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.dal.ThreadFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.datasources.ThreadFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.PaddingHorizontal12Modifier
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.nip01Core.core.Address

@Composable
fun SoftwareAppDetailScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address, accountViewModel) { note ->
        note?.let {
            SoftwareAppDetailScreenContent(
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun SoftwareAppDetailScreenContent(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<SoftwareApplicationEvent>(note, accountViewModel)

    // Drive the comments thread off the app's address tag — ThreadAssembler
    // resolves that to the application note and then walks replies.
    val addressTag = note.idHex
    val threadViewModel: ThreadFeedViewModel =
        viewModel(
            key = addressTag + "SoftwareAppDetailThread",
            factory = ThreadFeedViewModel.Factory(accountViewModel.account, addressTag),
        )
    WatchLifecycleAndUpdateModel(threadViewModel)
    ThreadFilterAssemblerSubscription(addressTag, accountViewModel)
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarWithBackButton(
                caption = event?.name() ?: event?.appId() ?: note.dTag(),
                nav = nav,
            )
        },
        accountViewModel = accountViewModel,
    ) {
        val current = event
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringRes(R.string.loading_feed),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            }
        } else {
            SoftwareAppDetailBody(
                note = note,
                event = current,
                threadViewModel = threadViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun SoftwareAppDetailBody(
    note: AddressableNote,
    event: SoftwareApplicationEvent,
    threadViewModel: ThreadFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val icon = remember(event) { event.icon() }
    val name = remember(event) { event.name() ?: event.appId().orEmpty() }
    val summary = remember(event) { event.summary() }
    val description = remember(event) { event.content.trim() }
    val images = remember(event) { event.images() }
    val topics = remember(event) { event.topics() }
    val platforms = remember(event) { event.platforms() }
    val license = remember(event) { event.license() }
    val website = remember(event) { event.url() }
    val repo = remember(event) { event.repository() }

    val latestRelease = remember(event) { findLatestNip82Release(event) }
    val olderReleases = remember(event) { findAllNip82Releases(event).drop(1) }

    val threadState by threadViewModel.feedState.feedContent.collectAsStateWithLifecycle()
    val comments: List<Note> =
        when (val s = threadState) {
            is FeedState.Loaded ->
                s.feed
                    .collectAsStateWithLifecycle()
                    .value.list
                    .filter { it.idHex != note.idHex }
            else -> emptyList()
        }

    var showOlder by rememberSaveable(event.id) { mutableStateOf(false) }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = threadViewModel.llState,
    ) {
        item(key = "header") {
            AppDetailHeader(
                note = note,
                icon = icon,
                name = name,
                summary = summary,
                latestVersion = latestRelease?.version(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        if (images.isNotEmpty()) {
            item(key = "screenshots") {
                Spacer(Modifier.height(12.dp))
                ScreenshotsStrip(images, accountViewModel, contentPadding = PaddingValues(horizontal = 12.dp))
            }
        }

        if (description.isNotBlank()) {
            item(key = "about") {
                Spacer(Modifier.height(12.dp))
                Section(title = stringRes(R.string.nip82_section_about)) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (platforms.isNotEmpty() || license != null) {
            item(key = "platforms") {
                Spacer(Modifier.height(12.dp))
                Section(title = stringRes(R.string.nip82_section_platforms)) {
                    PlatformLicenseRow(platforms = platforms, license = license)
                }
            }
        }

        if (topics.isNotEmpty()) {
            item(key = "topics") {
                Spacer(Modifier.height(12.dp))
                Section(title = stringRes(R.string.nip82_section_topics)) {
                    TopicChipFlow(topics = topics, nav = nav)
                }
            }
        }

        if (website != null || repo != null) {
            item(key = "links") {
                Spacer(Modifier.height(12.dp))
                Section(title = stringRes(R.string.nip82_section_links)) {
                    AppLinksColumn(website = website, repository = repo)
                }
            }
        }

        if (latestRelease != null) {
            item(key = "latest-release") {
                Spacer(Modifier.height(12.dp))
                Column(PaddingHorizontal12Modifier) {
                    SectionLabel(stringRes(R.string.nip82_section_latest_release))
                    RenderSoftwareReleaseBody(
                        event = latestRelease,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        showAppId = false,
                    )
                }
            }
        }

        if (olderReleases.isNotEmpty()) {
            item(key = "older-releases-toggle") {
                Spacer(Modifier.height(8.dp))
                OlderReleasesToggle(
                    count = olderReleases.size,
                    expanded = showOlder,
                    onToggle = { showOlder = !showOlder },
                )
            }
            if (showOlder) {
                items(
                    olderReleases,
                    key = { "older-${it.id}" },
                ) { release ->
                    Spacer(Modifier.height(8.dp))
                    Column(PaddingHorizontal12Modifier) {
                        RenderSoftwareReleaseBody(
                            event = release,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            showAppId = false,
                        )
                    }
                }
            }
        }

        item(key = "reactions") {
            Spacer(Modifier.height(12.dp))
            ReactionsRow(
                baseNote = note,
                showReactionDetail = true,
                addPadding = true,
                editState = null,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            HorizontalDivider(thickness = DividerThickness)
        }

        if (comments.isEmpty()) {
            item(key = "no-comments") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringRes(R.string.nip82_no_comments),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    modifier = PaddingHorizontal12Modifier,
                )
            }
        }

        itemsIndexed(
            comments,
            key = { _, item -> item.idHex },
            contentType = { _, _ -> "comment" },
        ) { _, item ->
            val level = remember(item) { threadViewModel.levelFlowForItem(item) }.collectAsStateWithLifecycle(0)

            NoteCompose(
                baseNote = item,
                modifier =
                    Modifier.drawReplyLevel(
                        level = level,
                        color = MaterialTheme.colorScheme.placeholderText,
                        selected = MaterialTheme.colorScheme.placeholderText,
                    ),
                isBoostedNote = false,
                unPackReply = ReplyRenderType.NONE,
                quotesLeft = 3,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

@Composable
private fun AppDetailHeader(
    note: AddressableNote,
    icon: String?,
    name: String,
    summary: String?,
    latestVersion: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = PaddingHorizontal12Modifier,
    ) {
        AppIcon(icon = icon, name = name, sizeDp = 72)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AppAuthorLine(note, accountViewModel, nav)
            summary?.takeIf { it.isNotBlank() }?.let {
                Spacer(StdVertSpacer)
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        latestVersion?.let {
            Spacer(Modifier.width(8.dp))
            VersionChip(it)
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(PaddingHorizontal12Modifier) {
        SectionLabel(title)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.grayText,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun OlderReleasesToggle(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text =
                    if (expanded) {
                        stringRes(R.string.nip82_older_releases_hide)
                    } else {
                        stringRes(R.string.nip82_older_releases_show)
                    },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Chip(text = count.toString())
        }
    }
}
