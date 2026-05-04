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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.WarningType
import com.vitorpamplona.amethyst.model.parseWarningType
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.observeAccountIsHiddenWord
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.ShowUserButton
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.ui.note.elements.AddButton
import com.vitorpamplona.amethyst.ui.screen.RefreshingFeedUserFeedView
import com.vitorpamplona.amethyst.ui.screen.UserFeedState
import com.vitorpamplona.amethyst.ui.screen.UserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.dal.HiddenAccountsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.dal.HiddenWordsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.dal.SpammerAccountsFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.ui.theme.HorzPadding
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size15dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun SecurityFiltersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val hiddenFeedViewModel: HiddenAccountsFeedViewModel =
        viewModel(
            factory = HiddenAccountsFeedViewModel.Factory(accountViewModel.account),
        )

    val hiddenWordsFeedViewModel: HiddenWordsFeedViewModel =
        viewModel(
            factory = HiddenWordsFeedViewModel.Factory(accountViewModel.account),
        )

    val spammerFeedViewModel: SpammerAccountsFeedViewModel =
        viewModel(
            factory = SpammerAccountsFeedViewModel.Factory(accountViewModel.account),
        )

    WatchAccountAndBlockList(accountViewModel = accountViewModel) {
        hiddenFeedViewModel.invalidateData()
        spammerFeedViewModel.invalidateData()
        hiddenWordsFeedViewModel.invalidateData()
    }

    SecurityFiltersScreen(
        hiddenFeedViewModel,
        hiddenWordsFeedViewModel,
        spammerFeedViewModel,
        accountViewModel,
        nav,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SecurityFiltersScreen(
    hiddenFeedViewModel: HiddenAccountsFeedViewModel,
    hiddenWordsViewModel: HiddenWordsFeedViewModel,
    spammerFeedViewModel: SpammerAccountsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
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

    val pagerState = rememberPagerState { 3 }
    val coroutineScope = rememberCoroutineScope()

    var selectedUsers by remember { mutableStateOf(setOf<String>()) }
    var selectedWords by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(pagerState.currentPage) {
        selectedUsers = emptySet()
        selectedWords = emptySet()
    }

    val selectionMode = selectedUsers.isNotEmpty() || selectedWords.isNotEmpty()

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = if (pagerState.currentPage == 2) selectedWords.size else selectedUsers.size,
                    onCancel = {
                        selectedUsers = emptySet()
                        selectedWords = emptySet()
                    },
                    onRemove = {
                        if (pagerState.currentPage == 2) {
                            if (!accountViewModel.isWriteable()) {
                                accountViewModel.toastManager.toast(
                                    R.string.read_only_user,
                                    R.string.login_with_a_private_key_to_be_able_to_show_word,
                                )
                            } else {
                                accountViewModel.showWords(selectedWords.toList())
                                selectedWords = emptySet()
                            }
                        } else {
                            accountViewModel.showUsers(selectedUsers.toList())
                            selectedUsers = emptySet()
                        }
                    },
                )
            } else {
                TopBarWithBackButton(stringRes(id = R.string.security_filters), nav)
            }
        },
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxHeight(),
        ) {
            HeaderOptions(accountViewModel)

            HorizontalDivider(thickness = DividerThickness)

            SecondaryScrollableTabRow(
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
                    text = { Text(text = stringRes(R.string.blocked_users)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(text = stringRes(R.string.spamming_users)) },
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(text = stringRes(R.string.hidden_words)) },
                )
            }
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> {
                        SelectableHiddenUsersFeed(
                            viewModel = hiddenFeedViewModel,
                            selected = selectedUsers,
                            onToggle = { hex ->
                                selectedUsers =
                                    if (hex in selectedUsers) selectedUsers - hex else selectedUsers + hex
                            },
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }

                    1 -> {
                        RefreshingFeedUserFeedView(spammerFeedViewModel, accountViewModel, nav)
                    }

                    2 -> {
                        HiddenWordsFeed(
                            hiddenWordsViewModel = hiddenWordsViewModel,
                            accountViewModel = accountViewModel,
                            selected = selectedWords,
                            onToggle = { word ->
                                selectedWords =
                                    if (word in selectedWords) selectedWords - word else selectedWords + word
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    ShorterTopAppBar(
        title = {
            Text(
                text = stringRes(R.string.num_selected, selectedCount),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        navigationIcon = {
            CloseButton(
                modifier = HalfHorzPadding,
                onPress = onCancel,
            )
        },
        actions = {
            Button(
                modifier = HalfHorzPadding,
                onClick = onRemove,
            ) {
                Text(text = stringRes(R.string.unblock))
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun HeaderOptions(accountViewModel: AccountViewModel) {
    Column(
        Modifier
            .padding(top = Size10dp, bottom = Size10dp, start = Size15dp, end = Size15dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = spacedBy(10.dp),
    ) {
        SettingsRow(
            R.string.warn_when_posts_have_reports_from_your_follows_title,
            R.string.warn_when_posts_have_reports_from_your_follows_explainer,
        ) {
            var warnAboutReports by remember { mutableStateOf(accountViewModel.account.settings.syncedSettings.security.warnAboutPostsWithReports) }

            Switch(
                checked = warnAboutReports,
                onCheckedChange = {
                    warnAboutReports = it
                    accountViewModel.updateWarnReports(warnAboutReports)
                },
            )
        }

        SettingsRow(
            R.string.filter_spam_from_strangers_title,
            R.string.filter_spam_from_strangers_explainer,
        ) {
            var filterSpam by remember { mutableStateOf(accountViewModel.account.settings.syncedSettings.security.filterSpamFromStrangers.value) }

            Switch(
                checked = filterSpam,
                onCheckedChange = {
                    filterSpam = it
                    accountViewModel.updateFilterSpam(filterSpam)
                },
            )
        }

        SettingsRow(
            R.string.show_sensitive_content_title,
            R.string.show_sensitive_content_explainer,
        ) {
            var sensitive by remember { mutableStateOf(accountViewModel.account.settings.syncedSettings.security.showSensitiveContent.value) }

            val selectedItens =
                persistentListOf(
                    TitleExplainer(stringRes(WarningType.WARN.resourceId)),
                    TitleExplainer(stringRes(WarningType.SHOW.resourceId)),
                    TitleExplainer(stringRes(WarningType.HIDE.resourceId)),
                )

            TextSpinner(
                label = "",
                placeholder = selectedItens[parseWarningType(sensitive).screenCode].title,
                options = selectedItens,
                onSelect = {
                    accountViewModel.updateShowSensitiveContent(parseWarningType(it).prefCode)
                },
            )
        }

        SettingsRow(
            R.string.max_hashtag_limit_title,
            R.string.max_hashtag_limit_explainer,
        ) {
            var maxHashtags by remember {
                mutableStateOf(
                    accountViewModel.account.settings.syncedSettings.security.maxHashtagLimit.value.let {
                        if (it == 0) "" else it.toString()
                    },
                )
            }

            OutlinedTextField(
                value = maxHashtags,
                onValueChange = {
                    maxHashtags = it
                    accountViewModel.updateMaxHashtagLimit(it.toIntOrNull() ?: 0)
                },
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                singleLine = true,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun HiddenWordsFeed(
    hiddenWordsViewModel: HiddenWordsFeedViewModel,
    accountViewModel: AccountViewModel,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val selectionMode = selected.isNotEmpty()
    RefresheableBox(hiddenWordsViewModel, false) {
        StringFeedView(
            hiddenWordsViewModel,
            accountViewModel,
            post = { AddMuteWordTextField(accountViewModel) },
        ) { word ->
            MutedWordHeader(
                tag = word,
                account = accountViewModel,
                isSelected = word in selected,
                selectionMode = selectionMode,
                onToggle = { onToggle(word) },
                onLongClick = { onToggle(word) },
            )
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
            label = { Text(text = stringRes(R.string.hide_new_word_label)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            placeholder = {
                Text(
                    text = stringRes(R.string.hide_new_word_label),
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
                            hideIfWritable(accountViewModel, currentWordToAdd)
                        }
                    },
                ),
            singleLine = true,
            trailingIcon = {
                AddButton(isActive = hasChanged, modifier = HorzPadding) {
                    hideIfWritable(accountViewModel, currentWordToAdd)
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
    val transientSpammers by accountViewModel.account.hiddenUsers.transientHiddenUsers
        .collectAsStateWithLifecycle()
    val blockListState by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, transientSpammers, blockListState) {
        invalidate()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MutedWordHeader(
    tag: String,
    modifier: Modifier = StdPadding,
    account: AccountViewModel,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onToggle: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggle() },
                onLongClick = onLongClick,
            ).let {
                if (isSelected) {
                    it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                } else {
                    it
                }
            }

    Column(rowModifier) {
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

                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
                } else {
                    MutedWordActionOptions(tag, account)
                }
            }
        }
    }
}

@Composable
fun MutedWordActionOptions(
    word: String,
    accountViewModel: AccountViewModel,
) {
    val isMutedWord by observeAccountIsHiddenWord(accountViewModel.account, word)

    if (isMutedWord) {
        ShowWordButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
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
                accountViewModel.toastManager.toast(
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
        Text(text = stringRes(R.string.block_only), color = Color.White)
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
        Text(text = stringRes(text), color = Color.White, textAlign = TextAlign.Center)
    }
}

private fun hideIfWritable(
    accountViewModel: AccountViewModel,
    currentWordToAdd: MutableState<String>,
) {
    if (!accountViewModel.isWriteable()) {
        accountViewModel.toastManager.toast(
            R.string.read_only_user,
            R.string.login_with_a_private_key_to_be_able_to_hide_word,
        )
    } else {
        accountViewModel.hide(currentWordToAdd.value)
        currentWordToAdd.value = ""
    }
}

@Composable
private fun SelectableHiddenUsersFeed(
    viewModel: UserFeedViewModel,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val selectionMode = selected.isNotEmpty()
    RefresheableBox(viewModel, true) {
        val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

        CrossfadeIfEnabled(
            targetState = feedState,
            animationSpec = tween(durationMillis = 100),
            accountViewModel = accountViewModel,
        ) { state ->
            when (state) {
                is UserFeedState.Empty -> {
                    FeedEmpty { viewModel.invalidateData() }
                }

                is UserFeedState.FeedError -> {
                    FeedError(state.errorMessage) { viewModel.invalidateData() }
                }

                is UserFeedState.Loading -> {
                    LoadingFeed()
                }

                is UserFeedState.Loaded -> {
                    SelectableHiddenUsersList(
                        state = state,
                        selected = selected,
                        selectionMode = selectionMode,
                        onToggle = onToggle,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableHiddenUsersList(
    state: UserFeedState.Loaded,
    selected: Set<String>,
    selectionMode: Boolean,
    onToggle: (String) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by state.feed.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(items, key = { _, item -> item.pubkeyHex }) { _, user ->
            val isSelected = user.pubkeyHex in selected

            val rowModifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                onToggle(user.pubkeyHex)
                            } else {
                                nav.nav(routeFor(user))
                            }
                        },
                        onLongClick = { onToggle(user.pubkeyHex) },
                    ).let {
                        if (isSelected) {
                            it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        } else {
                            it
                        }
                    }

            Row(
                modifier = rowModifier.padding(horizontal = Size15dp, vertical = Size10dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserPicture(user, Size55dp, accountViewModel = accountViewModel, nav = nav)
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    UsernameDisplay(user, accountViewModel = accountViewModel)
                }
                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggle(user.pubkeyHex) })
                } else {
                    ShowUserButton { accountViewModel.show(user) }
                }
            }

            HorizontalDivider(thickness = DividerThickness)
        }
    }
}
