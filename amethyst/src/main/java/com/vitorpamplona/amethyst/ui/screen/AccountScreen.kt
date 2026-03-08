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
package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.followimport.Kind3EventData
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LoggedInPage
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginOrSignupScreen
import com.vitorpamplona.amethyst.ui.screen.signup.ImportFollowListSection
import com.vitorpamplona.amethyst.ui.screen.signup.ImportFollowListViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(accountStateViewModel: AccountStateViewModel) {
    // Pauses relay services when the app pauses
    ManageRelayServices()
    ManageWebOkHttp()

    val accountState by accountStateViewModel.accountContent.collectAsStateWithLifecycle()

    Log.d("ActivityLifecycle", "AccountScreen $accountState $accountStateViewModel")

    Crossfade(
        targetState = accountState,
        animationSpec = tween(durationMillis = 100),
    ) { state ->
        when (state) {
            is AccountState.Loading -> {
                LoadingSetup()
            }

            is AccountState.LoggedOff -> {
                LoggedOffSetup(accountStateViewModel)
            }

            is AccountState.LoggedIn -> {
                if (state.isNewAccount) {
                    NewAccountImportFollowsSetup(state.account, accountStateViewModel)
                } else {
                    LoggedInSetup(state, accountStateViewModel)
                }
            }
        }
    }
}

@Composable
fun ManageRelayServices() {
    val relayServices by Amethyst.instance.relayProxyClientConnector.relayServices
        .collectAsStateWithLifecycle()
}

@Composable
fun ManageWebOkHttp() {
    val torWebServices by Amethyst.instance.okHttpClients.defaultHttpClient
        .collectAsStateWithLifecycle()
    val openWebServices by Amethyst.instance.okHttpClients.defaultHttpClientWithoutProxy
        .collectAsStateWithLifecycle()
}

@Composable
fun LoadingSetup() {
    // A surface container using the 'background' color from the theme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringRes(R.string.loading_account))
        }
    }
}

@Composable
fun LoggedOffSetup(accountStateViewModel: AccountStateViewModel) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LoginOrSignupScreen(null, accountStateViewModel, isFirstLogin = true)
    }
}

@Composable
fun LoggedInSetup(
    state: AccountState.LoggedIn,
    accountStateViewModel: AccountStateViewModel,
) {
    SetAccountCentricViewModelStore(state) {
        LoggedInPage(
            account = state.account,
            route = state.route,
            accountStateViewModel = accountStateViewModel,
        )
    }
}

@Composable
fun NewAccountImportFollowsSetup(
    account: Account,
    accountStateViewModel: AccountStateViewModel,
) {
    val importViewModel: ImportFollowListViewModel = viewModel()

    LaunchedEffect(account) {
        importViewModel.configure(
            fetchEvent = { kind, author, limit, onEvent ->
                val filter =
                    Filter(
                        kinds = listOf(kind),
                        authors = listOf(author),
                        limit = limit,
                    )
                val relayUrls =
                    listOf(
                        "wss://relay.damus.io",
                        "wss://nos.lol",
                        "wss://relay.nostr.band",
                        "wss://purplepag.es",
                    )
                val filterMap =
                    relayUrls.associate { url ->
                        RelayUrlNormalizer.normalize(url) to listOf(filter)
                    }
                val listener =
                    object : com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener {
                        override fun onEvent(
                            event: com.vitorpamplona.quartz.nip01Core.core.Event,
                            isLive: Boolean,
                            relay: com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            if (event is ContactListEvent) {
                                onEvent(
                                    Kind3EventData(
                                        pTags =
                                            event.tags
                                                .filter { it.size >= 2 && it[0] == "p" }
                                                .map { it.drop(1) },
                                        createdAt = event.createdAt,
                                    ),
                                )
                            }
                        }
                    }
                val subId =
                    com.vitorpamplona.quartz.nip01Core.relay.client.single
                        .newSubId()
                Amethyst.instance.client.openReqSubscription(subId, filterMap, listener)
                AutoCloseable { Amethyst.instance.client.close(subId) }
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        ImportFollowListSection(
            onFollowsApplied = { entries ->
                withContext(Dispatchers.IO) {
                    for (entry in entries) {
                        val user = account.cache.getOrCreateUser(entry.pubkeyHex)
                        account.follow(user)
                    }
                }
            },
            onSkip = { accountStateViewModel.finishNewAccountSetup() },
            onDone = { accountStateViewModel.finishNewAccountSetup() },
            viewModel = importViewModel,
        )
    }
}
