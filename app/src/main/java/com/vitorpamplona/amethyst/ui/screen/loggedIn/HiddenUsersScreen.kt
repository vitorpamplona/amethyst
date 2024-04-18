/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.elements.AddButton
import com.vitorpamplona.amethyst.ui.screen.NostrHiddenAccountsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHiddenWordsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrSpammerAccountsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableBox
import com.vitorpamplona.amethyst.ui.screen.RefreshingFeedUserFeedView
import com.vitorpamplona.amethyst.ui.screen.StringFeedView
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.HorzPadding
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.launch

@Composable
fun HiddenUsersScreen(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val hiddenFeedViewModel: NostrHiddenAccountsFeedViewModel =
        viewModel(
            factory = NostrHiddenAccountsFeedViewModel.Factory(accountViewModel.account),
        )

    val hiddenWordsFeedViewModel: NostrHiddenWordsFeedViewModel =
        viewModel(
            factory = NostrHiddenWordsFeedViewModel.Factory(accountViewModel.account),
        )

    val spammerFeedViewModel: NostrSpammerAccountsFeedViewModel =
        viewModel(
            factory = NostrSpammerAccountsFeedViewModel.Factory(accountViewModel.account),
        )

    WatchAccountAndBlockList(accountViewModel = accountViewModel) {
        hiddenFeedViewModel.invalidateData()
        spammerFeedViewModel.invalidateData()
        hiddenWordsFeedViewModel.invalidateData()
    }

    HiddenUsersScreen(
        hiddenFeedViewModel,
        hiddenWordsFeedViewModel,
        spammerFeedViewModel,
        accountViewModel,
        nav,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HiddenUsersScreen(
    hiddenFeedViewModel: NostrHiddenAccountsFeedViewModel,
    hiddenWordsViewModel: NostrHiddenWordsFeedViewModel,
    spammerFeedViewModel: NostrSpammerAccountsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Hidden Users Start")
                    hiddenWordsViewModel.invalidateData()
                    hiddenFeedViewModel.invalidateData()
                    spammerFeedViewModel.invalidateData()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxHeight()) {
        val pagerState = rememberPagerState { 3 }
        val coroutineScope = rememberCoroutineScope()
        var warnAboutReports by remember {
            mutableStateOf(accountViewModel.account.warnAboutPostsWithReports)
        }
        var filterSpam by remember { mutableStateOf(accountViewModel.account.filterSpamFromStrangers) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = warnAboutReports,
                onCheckedChange = {
                    warnAboutReports = it
                    accountViewModel.account.updateOptOutOptions(warnAboutReports, filterSpam)
                },
            )

            Text(stringResource(R.string.warn_when_posts_have_reports_from_your_follows))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = filterSpam,
                onCheckedChange = {
                    filterSpam = it
                    accountViewModel.account.updateOptOutOptions(warnAboutReports, filterSpam)
                },
            )

            Text(stringResource(R.string.filter_spam_from_strangers))
        }

        ScrollableTabRow(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            edgePadding = 8.dp,
            selectedTabIndex = pagerState.currentPage,
            modifier = TabRowHeight,
            divider = { HorizontalDivider(thickness = DividerThickness) },
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(text = stringResource(R.string.blocked_users)) },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(text = stringResource(R.string.spamming_users)) },
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                text = { Text(text = stringResource(R.string.hidden_words)) },
            )
        }
        HorizontalPager(state = pagerState) { page ->
            when (page) {
                0 -> RefreshingFeedUserFeedView(hiddenFeedViewModel, accountViewModel, nav)
                1 -> RefreshingFeedUserFeedView(spammerFeedViewModel, accountViewModel, nav)
                2 -> HiddenWordsFeed(hiddenWordsViewModel, accountViewModel)
            }
        }
    }
}

@Composable
private fun HiddenWordsFeed(
    hiddenWordsViewModel: NostrHiddenWordsFeedViewModel,
    accountViewModel: AccountViewModel,
) {
    RefresheableBox(hiddenWordsViewModel, false) {
        StringFeedView(
            hiddenWordsViewModel,
            post = { AddMuteWordTextField(accountViewModel) },
        ) {
            MutedWordHeader(tag = it, account = accountViewModel)
        }
    }
}

@Composable
private fun AddMuteWordTextField(accountViewModel: AccountViewModel) {
    Row {
        val currentWordToAdd = remember { mutableStateOf("") }
        val hasChanged by remember { derivedStateOf { currentWordToAdd.value != "" } }

        OutlinedTextField(
            value = currentWordToAdd.value,
            onValueChange = { currentWordToAdd.value = it },
            label = { Text(text = stringResource(R.string.hide_new_word_label)) },
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.hide_new_word_label),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            keyboardActions =
                KeyboardActions(
                    onSend = {
                        if (hasChanged) {
                            accountViewModel.hide(currentWordToAdd.value)
                            currentWordToAdd.value = ""
                        }
                    },
                ),
            singleLine = true,
            trailingIcon = {
                AddButton(isActive = hasChanged, modifier = HorzPadding) {
                    accountViewModel.hide(currentWordToAdd.value)
                    currentWordToAdd.value = ""
                }
            },
        )
    }
}

@Composable
fun WatchAccountAndBlockList(
    accountViewModel: AccountViewModel,
    invalidate: () -> Unit,
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val blockListState by accountViewModel.account.flowHiddenUsers.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, accountState, blockListState) {
        invalidate()
    }
}

@Composable
fun MutedWordHeader(
    tag: String,
    modifier: Modifier = StdPadding,
    account: AccountViewModel,
) {
    Column(
        Modifier.fillMaxWidth(),
    ) {
        Column(modifier = modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    tag,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )

                MutedWordActionOptions(tag, account)
            }
        }
    }
}

@Composable
fun MutedWordActionOptions(
    word: String,
    accountViewModel: AccountViewModel,
) {
    val isMutedWord by
        accountViewModel.account.liveHiddenUsers
            .map { word in it.hiddenWords }
            .distinctUntilChanged()
            .observeAsState()

    if (isMutedWord == true) {
        ShowWordButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_show_word,
                )
            } else {
                accountViewModel.showWord(word)
            }
        }
    } else {
        HideWordButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_hide_word,
                )
            } else {
                accountViewModel.hideWord(word)
            }
        }
    }
}

@Composable
fun HideWordButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(R.string.block_only), color = Color.White)
    }
}

@Composable
fun ShowWordButton(
    text: Int = R.string.unblock,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.padding(start = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(text), color = Color.White, textAlign = TextAlign.Center)
    }
}
