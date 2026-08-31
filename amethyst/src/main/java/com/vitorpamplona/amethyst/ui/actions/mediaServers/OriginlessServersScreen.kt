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
package com.vitorpamplona.amethyst.ui.actions.mediaServers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.originless.OriginlessUrls
import com.vitorpamplona.amethyst.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.rememberRelayDragState
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertPadding
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.grayText

@Composable
fun OriginlessServersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val originlessServersViewModel: OriginlessServersViewModel = viewModel()
    originlessServersViewModel.init(accountViewModel)

    LaunchedEffect(key1 = accountViewModel) {
        originlessServersViewModel.load()
    }

    OriginlessServersScaffold(originlessServersViewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OriginlessServersScaffold(
    originlessServersViewModel: OriginlessServersViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(id = R.string.originless_settings_title),
                nav = nav,
            )
        },
    ) { padding ->
        OriginlessServersBody(
            originlessServersViewModel = originlessServersViewModel,
            accountViewModel = accountViewModel,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        top = padding.calculateTopPadding(),
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding)
                    .imePaddingSafe(),
        )
    }
}

@Composable
private fun OriginlessServersBody(
    originlessServersViewModel: OriginlessServersViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val originlessServersState by originlessServersViewModel.fileServers.collectAsStateWithLifecycle()
    val originlessHealthState by originlessServersViewModel.health.collectAsStateWithLifecycle()
    val uploadsEnabled by accountViewModel.account.settings.originlessUploadsEnabled
        .collectAsStateWithLifecycle()
    val optimize by accountViewModel.account.settings.optimizeMediaOnUpload
        .collectAsStateWithLifecycle()

    val dragState =
        rememberRelayDragState(
            onMove = { from, to -> originlessServersViewModel.moveServer(from, to) },
            itemCount = { originlessServersState.size },
        )

    LaunchedEffect(dragState.isDragging) {
        if (!dragState.isDragging) originlessServersViewModel.persistPending()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = FeedPadding,
        userScrollEnabled = !dragState.isDragging,
    ) {
        item {
            SectionLabel(
                title = stringRes(id = R.string.originless_about_section),
                caption = stringRes(id = R.string.originless_about_caption),
                topPadding = 4.dp,
            )
            OriginlessGithubCard(modifier = Modifier.fillMaxWidth())
        }

        item {
            SectionLabel(
                title = stringRes(id = R.string.originless_uploads_section),
                caption = stringRes(id = R.string.originless_uploads_section_caption),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                UploadToggleRow(
                    title = stringRes(id = R.string.originless_uploads_switch),
                    caption = stringRes(id = R.string.originless_uploads_switch_caption),
                    checked = uploadsEnabled,
                    enabled = originlessServersState.isNotEmpty() || uploadsEnabled,
                    onCheckedChange = { accountViewModel.account.settings.changeOriginlessUploadsEnabled(it) },
                )
                if (uploadsEnabled) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    UploadToggleRow(
                        title = stringRes(id = R.string.blossom_optimize_media),
                        caption = stringRes(id = R.string.originless_media_caption),
                        checked = optimize,
                        onCheckedChange = { accountViewModel.account.settings.changeOptimizeMediaOnUpload(it) },
                    )
                }
            }
        }

        item {
            SectionLabel(
                title = stringRes(id = R.string.originless_gateways_section),
                caption = stringRes(id = R.string.originless_section_caption),
                topPadding = 20.dp,
            )
        }

        if (originlessServersState.isEmpty()) {
            item {
                Text(
                    text = stringRes(id = R.string.no_originless_server_message),
                    modifier = DoubleVertPadding,
                )
            }
        } else {
            itemsIndexed(
                originlessServersState,
                key = { _, server -> "originless" + server.baseUrl },
            ) { index, entry ->
                MediaServerRow(
                    index = index,
                    serverEntry = entry,
                    health = originlessHealthState[entry.baseUrl] ?: ServerHealth.Unknown,
                    dragState = dragState,
                    onDelete = { originlessServersViewModel.removeServer(serverUrl = it) },
                )
            }
        }

        item {
            OriginlessAddServerSection(
                addedHosts = originlessServersState.mapTo(HashSet()) { it.name },
                onAddServer = { originlessServersViewModel.addServer(it) },
            )
        }

        item {
            Spacer(DoubleHorzSpacer)
        }
    }
}

@Composable
private fun OriginlessGithubCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable {
                    runCatching { uriHandler.openUri(OriginlessUrls.PROJECT_URL) }
                }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.Code,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = stringRes(id = R.string.originless_github_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringRes(id = R.string.originless_github_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        Icon(
            symbol = MaterialSymbols.AutoMirrored.OpenInNew,
            contentDescription = stringRes(id = R.string.originless_github_title),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
