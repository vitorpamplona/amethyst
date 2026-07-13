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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayUrlEditField
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * Create a new Concord Channel (encrypted community) from Amethyst. Mints the
 * genesis (metadata + #general), publishes it to the chosen relays (or the
 * account's outbox by default), joins it, and opens the new community.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordCreateScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val name = remember { mutableStateOf("") }
    val about = remember { mutableStateOf("") }
    val iconUrl = remember { mutableStateOf("") }
    val relays = remember { mutableListOf<NormalizedRelayUrl>().toMutableStateList() }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_create_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ConcordMetadataFields(
                name = name,
                about = about,
                iconUrl = iconUrl,
                robotSeed = "concord-new",
                accountViewModel = accountViewModel,
            )

            ConcordSectionHeader(
                title = stringRes(com.vitorpamplona.amethyst.R.string.concord_create_relays),
                description = stringRes(com.vitorpamplona.amethyst.R.string.concord_create_relays_desc),
            )
            relays.forEach { relay ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(relay.displayUrl(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { relays.remove(relay) }) {
                        SymbolIcon(symbol = MaterialSymbols.Close, contentDescription = stringRes(com.vitorpamplona.amethyst.R.string.remove))
                    }
                }
            }
            RelayUrlEditField(
                onNewRelay = { if (it !in relays) relays.add(it) },
                modifier = Modifier.fillMaxWidth(),
                accountViewModel = accountViewModel,
                nav = nav,
            )

            Button(
                onClick = {
                    if (name.value.isBlank() || working) return@Button
                    working = true
                    scope.launch {
                        val communityId =
                            accountViewModel.account.createConcordCommunity(
                                name = name.value.trim(),
                                description = about.value.trim().ifBlank { null },
                                relays = relays.map { it.url },
                                icon =
                                    iconUrl.value
                                        .trim()
                                        .ifBlank { null }
                                        ?.let { ImagePointer(url = it) },
                            )
                        working = false
                        if (communityId != null) nav.newStack(Route.ConcordServer(communityId))
                    }
                },
                enabled = name.value.isNotBlank() && !working,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_create_action))
            }
        }
    }
}

/** A section header (title + one-line description) matching the NIP-29 metadata form. */
@Composable
fun ConcordSectionHeader(
    title: String,
    description: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
