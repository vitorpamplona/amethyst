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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.highlights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size10dp

/**
 * The "New Highlight" composer. Reached either from the "Add highlight" action or when a
 * browser shares a text selection to Amethyst (routed in as [Route.NewHighlight]). It is a
 * deliberately small subset of the short-note composer — no polls, zaps, media, scheduling,
 * etc. — because a NIP-84 highlight is just a passage, its source, and an optional note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHighlightScreen(
    quote: String? = null,
    url: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    comment: String? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: NewHighlightPostViewModel = viewModel()
    postViewModel.init(accountViewModel)

    LaunchedEffect(Unit) {
        postViewModel.load(quote, url, prefix, suffix, comment)
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                titleRes = R.string.new_highlight_title,
                isActive = postViewModel::canPost,
                onCancel = { nav.popBack() },
                onPost = {
                    // Uses the accountViewModel scope so releasing the post ViewModel on
                    // popBack can't cancel the in-flight publish.
                    accountViewModel.launchSigner {
                        postViewModel.sendHighlight()
                        nav.popBack()
                    }
                },
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(Size10dp),
                verticalArrangement = Arrangement.spacedBy(Size10dp),
            ) {
                OutlinedTextField(
                    value = postViewModel.quote,
                    onValueChange = { postViewModel.quote = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.new_highlight_passage_label)) },
                    minLines = 3,
                )

                OutlinedTextField(
                    value = postViewModel.url,
                    onValueChange = { postViewModel.url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.new_highlight_source_label)) },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = postViewModel.comment,
                    onValueChange = { postViewModel.comment = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.new_highlight_note_label)) },
                    minLines = 2,
                )
            }
        }
    }
}
