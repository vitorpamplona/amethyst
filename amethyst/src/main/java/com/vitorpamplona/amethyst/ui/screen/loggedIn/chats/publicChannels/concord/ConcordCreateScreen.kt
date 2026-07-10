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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * Create a new Concord Channel (encrypted community) from Amethyst. Mints the
 * genesis (metadata + #general), publishes it to the given relays (or the
 * account's outbox by default), joins it, and opens the new community.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordCreateScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var relaysCsv by remember { mutableStateOf("") }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_create_name)) },
            )
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_create_about)) },
            )
            OutlinedTextField(
                value = relaysCsv,
                onValueChange = { relaysCsv = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_create_relays)) },
                placeholder = { Text("wss://relay.one, wss://relay.two") },
            )
            Button(
                onClick = {
                    if (name.isBlank() || working) return@Button
                    working = true
                    scope.launch {
                        val relays = relaysCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val communityId = accountViewModel.account.createConcordCommunity(name.trim(), about.trim().ifBlank { null }, relays)
                        working = false
                        if (communityId != null) nav.newStack(Route.ConcordServer(communityId))
                    }
                },
                enabled = name.isNotBlank() && !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(com.vitorpamplona.amethyst.R.string.concord_create_action))
            }
        }
    }
}
