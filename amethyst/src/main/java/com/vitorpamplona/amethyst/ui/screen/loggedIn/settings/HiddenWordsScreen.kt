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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.feeds.FeedError
import com.vitorpamplona.amethyst.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.elements.AddButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.dal.HiddenWordsFeedViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.HorzPadding
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun HiddenWordsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: HiddenWordsFeedViewModel =
        viewModel(factory = HiddenWordsFeedViewModel.Factory(accountViewModel.account))

    InvalidateOnBlockListChange(accountViewModel) { viewModel.invalidateData() }

    var selected by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            BlockListTopBar(
                title = R.string.hidden_words,
                selectedCount = selected.size,
                onCancel = { selected = emptySet() },
                onUnblock = {
                    if (!accountViewModel.isWriteable()) {
                        accountViewModel.toastManager.toast(
                            R.string.read_only_user,
                            R.string.login_with_a_private_key_to_be_able_to_show_word,
                        )
                    } else {
                        accountViewModel.showWords(selected.toList())
                        selected = emptySet()
                    }
                },
                nav = nav,
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                AddMuteWordTextField(accountViewModel)
            }
        },
    ) { padding ->
        HiddenWordsList(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            selected = selected,
            onToggle = { selected = if (it in selected) selected - it else selected + it },
        )
    }
}

@Composable
private fun HiddenWordsList(
    modifier: Modifier = Modifier,
    viewModel: HiddenWordsFeedViewModel,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val feedState by viewModel.feedContent.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        when (val state = feedState) {
            is StringFeedState.Loaded -> {
                val items by state.feed.collectAsStateWithLifecycle()
                if (items.isEmpty()) {
                    EmptyState(R.string.security_hidden_words_empty)
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                    ) {
                        items(items, key = { it }) { word ->
                            MutedWordRow(
                                tag = word,
                                isSelected = word in selected,
                                selectionMode = selected.isNotEmpty(),
                                onToggle = { onToggle(word) },
                            )
                            HorizontalDivider(thickness = DividerThickness)
                        }
                    }
                }
            }

            is StringFeedState.Empty -> {
                EmptyState(R.string.security_hidden_words_empty)
            }

            is StringFeedState.Loading -> {
                LoadingFeed()
            }

            is StringFeedState.FeedError -> {
                FeedError(state.errorMessage) { viewModel.invalidateData() }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MutedWordRow(
    tag: String,
    isSelected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
) {
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggle() },
                onLongClick = onToggle,
            ).let {
                if (isSelected) {
                    it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                } else {
                    it
                }
            }

    Row(
        modifier = rowModifier.then(StdPadding),
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
        }
    }
}

@Composable
private fun AddMuteWordTextField(accountViewModel: AccountViewModel) {
    val currentWordToAdd = remember { mutableStateOf("") }
    val hasChanged by remember { derivedStateOf { currentWordToAdd.value.isNotBlank() } }

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
                onSend = { if (hasChanged) hideIfWritable(accountViewModel, currentWordToAdd) },
            ),
        singleLine = true,
        trailingIcon = {
            AddButton(isActive = hasChanged, modifier = HorzPadding) {
                hideIfWritable(accountViewModel, currentWordToAdd)
            }
        },
    )
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
        accountViewModel.hide(currentWordToAdd.value.trim())
        currentWordToAdd.value = ""
    }
}
