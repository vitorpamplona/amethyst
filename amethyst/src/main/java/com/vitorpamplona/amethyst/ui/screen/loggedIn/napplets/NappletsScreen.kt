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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.napplet.NappletLauncher
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.datasource.NappletsFilterAssemblerSubscription
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent

/**
 * Lists the napplet manifests currently in the local cache (NIP-5D kinds 15129/35129) and opens
 * the selected one in the sandboxed [NappletLauncher] host. The top bar carries a follow-list filter
 * (like the Pictures/Articles feeds) and each row is a rich card with the author, declared
 * capabilities, and the usual reaction bar (reply/boost/like/zap).
 */
@Composable
fun NappletsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Pull napplet manifests from the user's relays into LocalCache while this screen is open.
    NappletsFilterAssemblerSubscription(accountViewModel)

    val napplets by remember {
        Amethyst.instance.cache.observeEvents<Event>(
            Filter(kinds = listOf(RootNappletEvent.KIND, NamedNappletEvent.KIND)),
        )
    }.collectAsStateWithLifecycle(emptyList())

    val followFilter by accountViewModel.account.liveNappletsFollowLists
        .collectAsStateWithLifecycle()

    val visible =
        remember(napplets, followFilter) {
            napplets.filter { followFilter.matchAuthor(it.pubKey) }
        }

    Scaffold(
        topBar = { NappletsTopBar(accountViewModel, nav) },
    ) { padding ->
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.napplet_none_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val context = LocalContext.current
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(visible, key = { it.id }) { event ->
                    val manifest = event as? NappletManifest ?: return@items
                    NappletCard(
                        event = event,
                        manifest = manifest,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onClick = {
                            NappletLauncher.launch(
                                context = context,
                                manifest = manifest,
                                authorPubKey = event.pubKey,
                                identifier = (event as? NamedNappletEvent)?.identifier() ?: "",
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NappletCard(
    event: Event,
    manifest: NappletManifest,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    val note = remember(event.id) { Amethyst.instance.cache.getOrCreateNote(event) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserPicture(userHex = event.pubKey, size = 48.dp, accountViewModel = accountViewModel, nav = nav)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = manifest.title()?.ifBlank { null } ?: stringResource(R.string.napplet_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LoadUser(baseUserHex = event.pubKey, accountViewModel) { user ->
                    if (user != null) {
                        UsernameDisplay(user, accountViewModel = accountViewModel)
                    }
                }
            }
        }

        manifest.description()?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val requires = manifest.requires()
        if (requires.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                requires.forEach { capability ->
                    SuggestionChip(
                        onClick = onClick,
                        label = { Text(capability.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }

        ReactionsRow(
            baseNote = note,
            showReactionDetail = true,
            addPadding = false,
            editState = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
