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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.grayText

@Composable
fun AllMediaServersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val blossomServersViewModel: BlossomServersViewModel = viewModel()

    blossomServersViewModel.init(accountViewModel)

    LaunchedEffect(key1 = accountViewModel) {
        blossomServersViewModel.load()
    }

    MediaServersScaffold(blossomServersViewModel, accountViewModel) {
        nav.popBack()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaServersScaffold(
    blossomServersViewModel: BlossomServersViewModel,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.media_servers,
                onCancel = {
                    blossomServersViewModel.refresh()
                    onClose()
                },
                onPost = {
                    blossomServersViewModel.saveFileServers()
                    onClose()
                },
            )
        },
    ) { padding ->
        AllMediaBody(
            blossomServersViewModel = blossomServersViewModel,
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
                    .imePadding(),
        )
    }
}

/**
 * The on-device Blossom cache, rendered as a self-contained card so it reads as its
 * own feature rather than a stray toggle. Binds to the same two account settings as
 * before.
 */
@Composable
fun MediaCacheSection(accountViewModel: AccountViewModel) {
    val enabled by accountViewModel.account.settings.useLocalBlossomCache
        .collectAsStateWithLifecycle()
    val profilePicturesOnly by accountViewModel.account.settings.localBlossomCacheProfilePicturesOnly
        .collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 12.dp)) {
                Text(
                    text = stringRes(id = R.string.use_local_blossom_cache),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringRes(id = R.string.use_local_blossom_cache_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { accountViewModel.account.settings.changeUseLocalBlossomCache(it) },
            )
        }

        if (enabled) {
            CacheDetectionChip(accountViewModel)

            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 64.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringRes(id = R.string.local_blossom_cache_profile_pics_only),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringRes(id = R.string.local_blossom_cache_profile_pics_only_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
                Switch(
                    checked = profilePicturesOnly,
                    onCheckedChange = {
                        accountViewModel.account.settings.changeLocalBlossomCacheProfilePicturesOnly(it)
                    },
                )
            }
        }
    }
}

/**
 * Loopback-detection status for the local cache, shown only while the cache is enabled
 * (kept in its own composable so the loopback probe is subscribed only then).
 */
@Composable
private fun CacheDetectionChip(accountViewModel: AccountViewModel) {
    val probeAvailable by accountViewModel.useLocalBlossomBridgeForProfilePics
        .collectAsStateWithLifecycle()

    val color = if (probeAvailable) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.grayText

    Row(
        modifier = Modifier.padding(start = 64.dp, end = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text =
                if (probeAvailable) {
                    stringRes(id = R.string.local_blossom_cache_detected)
                } else {
                    stringRes(id = R.string.local_blossom_cache_not_detected)
                },
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
