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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

@Composable
fun GitNewIssueScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address, accountViewModel) { note ->
        note?.let { GitNewIssueForm(it, accountViewModel, nav) }
    }
}

/**
 * Full-screen "New issue" composer: a subject and a markdown body. On submit it builds a
 * NIP-34 [GitIssueEvent] addressed to [repoNote], signs it with the account signer and
 * broadcasts it, then returns to the issues list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitNewIssueForm(
    repoNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var labels by rememberSaveable { mutableStateOf("") }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            ShorterTopAppBar(
                title = { Text(stringRes(R.string.git_new_issue_title)) },
                navigationIcon = {
                    Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = nav::popBack) { ArrowBackIcon() }
                    }
                },
                actions = {
                    TextButton(
                        enabled = subject.isNotBlank(),
                        onClick = {
                            sendGitIssue(accountViewModel, repoNote, subject.trim(), body.trim(), parseLabels(labels))
                            nav.popBack()
                        },
                    ) {
                        Text(stringRes(R.string.git_new_issue_create))
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(LocalDisappearingScaffoldPadding.current)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text(stringRes(R.string.git_new_issue_subject)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringRes(R.string.git_new_issue_body)) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = labels,
                onValueChange = { labels = it },
                label = { Text(stringRes(R.string.git_new_issue_labels)) },
                placeholder = { Text(stringRes(R.string.git_new_issue_labels_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Splits a free-text label field on commas/whitespace, stripping any leading `#`. */
private fun parseLabels(raw: String): List<String> =
    raw
        .split(',', ' ', '\n', '\t')
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotEmpty() }
        .distinct()

private fun sendGitIssue(
    accountViewModel: AccountViewModel,
    repoNote: AddressableNote,
    subject: String,
    body: String,
    labels: List<String>,
) {
    val repositoryHint = repoNote.toEventHint<GitRepositoryEvent>() ?: return
    accountViewModel.launchSigner {
        val template = GitIssueEvent.build(subject, body, repositoryHint, emptyList(), labels)
        val signed = accountViewModel.account.signer.sign(template)
        accountViewModel.account.sendAutomatic(signed)
    }
}
