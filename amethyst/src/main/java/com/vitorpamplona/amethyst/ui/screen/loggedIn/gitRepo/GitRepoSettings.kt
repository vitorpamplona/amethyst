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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * Edit form for a NIP-34 repository announcement (kind 30617). Because the event
 * is addressable, saving republishes it under the same `d` tag — preserving the
 * earliest-unique-commit, relays, maintainers and personal-fork flag while letting
 * the owner edit the name, description, web/clone URLs and topics.
 */
@Composable
fun GitRepoSettingsDialog(
    event: GitRepositoryEvent,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(event.name() ?: event.dTag()) }
    var description by rememberSaveable { mutableStateOf(event.description() ?: "") }
    var webUrls by rememberSaveable { mutableStateOf(event.webs().joinToString("\n")) }
    var cloneUrls by rememberSaveable { mutableStateOf(event.clones().joinToString("\n")) }
    var topics by rememberSaveable { mutableStateOf(event.hashtags().joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.git_repo_settings_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringRes(R.string.git_repo_settings_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringRes(R.string.git_repo_settings_description)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cloneUrls,
                    onValueChange = { cloneUrls = it },
                    label = { Text(stringRes(R.string.git_repo_settings_clone_urls)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = webUrls,
                    onValueChange = { webUrls = it },
                    label = { Text(stringRes(R.string.git_repo_settings_web_urls)) },
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = topics,
                    onValueChange = { topics = it },
                    label = { Text(stringRes(R.string.git_repo_settings_topics)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    saveRepository(accountViewModel, event, name.trim(), description.trim(), webUrls, cloneUrls, topics)
                    onDismiss()
                },
            ) {
                Text(stringRes(R.string.git_repo_settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.git_new_issue_cancel)) }
        },
    )
}

private fun splitLines(raw: String): List<String> =
    raw
        .split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

private fun saveRepository(
    accountViewModel: AccountViewModel,
    event: GitRepositoryEvent,
    name: String,
    description: String,
    webUrls: String,
    cloneUrls: String,
    topics: String,
) {
    accountViewModel.launchSigner {
        val template =
            GitRepositoryEvent.build(
                name = name,
                description = description.ifBlank { null },
                webUrls = splitLines(webUrls),
                cloneUrls = splitLines(cloneUrls),
                relays = event.relays(),
                maintainers = event.maintainers(),
                hashtags = splitLines(topics).map { it.removePrefix("#") },
                earliestUniqueCommit = event.earliestUniqueCommit(),
                personalFork = event.isPersonalFork(),
                dTag = event.dTag(),
            )
        val signed = accountViewModel.account.signer.sign(template)
        accountViewModel.account.sendAutomatic(signed)
    }
}
